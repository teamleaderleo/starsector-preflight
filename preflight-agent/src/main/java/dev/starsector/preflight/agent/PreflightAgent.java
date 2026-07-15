package dev.starsector.preflight.agent;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Category;
import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;

/** Launch-time JFR and optional fail-closed adapter agent for Starsector Preflight. */
public final class PreflightAgent {
    private PreflightAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        start(agentArgs, instrumentation);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        start(agentArgs, instrumentation);
    }

    private static void start(String agentArgs, Instrumentation instrumentation) {
        AgentOptions options;
        try {
            options = AgentOptions.parse(agentArgs);
        } catch (Throwable error) {
            log("Agent disabled: " + error.getMessage());
            return;
        }

        AdapterRuntime.Session adapterSession = AdapterRuntime.start(options, instrumentation);
        Recording recording = startRecording(options);
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> {
                        stopRecording(recording, options.destination());
                        adapterSession.close();
                    },
                    "Preflight-Shutdown"));
        } catch (Throwable error) {
            log("Could not register shutdown hook: " + error.getMessage());
            stopRecording(recording, options.destination());
            adapterSession.close();
        }
    }

    private static Recording startRecording(AgentOptions options) {
        try {
            Path destination = options.destination().toAbsolutePath().normalize();
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Configuration configuration = Configuration.getConfiguration(options.settings());
            Recording recording = new Recording(configuration);
            recording.setName("Starsector Preflight startup");
            recording.setToDisk(true);
            recording.setDumpOnExit(false);
            recording.setDestination(destination);
            configureStartupEvents(recording);
            recording.start();

            AgentStarted started = new AgentStarted();
            started.destination = destination.toString();
            started.adapterMode = options.adapterMode().name();
            started.commit();
            log("Recording startup to " + destination);
            if (options.adapterMode() != AdapterMode.OFF) {
                log("Adapter mode " + options.adapterMode() + "; report "
                        + options.adapterReport().toAbsolutePath().normalize());
            }
            return recording;
        } catch (Throwable error) {
            log("Profiler disabled: " + error.getMessage());
            return null;
        }
    }

    private static void configureStartupEvents(Recording recording) {
        recording.enable("jdk.FileRead").withThreshold(Duration.ofMillis(1)).withStackTrace();
        recording.enable("jdk.FileWrite").withThreshold(Duration.ofMillis(1)).withStackTrace();
        recording.enable("jdk.ClassLoad").withStackTrace();
        recording.enable("jdk.ClassDefine").withStackTrace();
        recording.enable("jdk.Compilation").withThreshold(Duration.ofMillis(1)).withStackTrace();
        recording.enable("jdk.GCPhasePause").withStackTrace();
        recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1)).withStackTrace();
        recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1)).withStackTrace();
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10)).withStackTrace();
    }

    private static void stopRecording(Recording recording, Path destination) {
        if (recording == null) {
            return;
        }
        try {
            if (recording.getState() == RecordingState.RUNNING) {
                AgentStopping stopping = new AgentStopping();
                stopping.commit();
                try {
                    recording.stop();
                } catch (IllegalStateException ignored) {
                    // The JVM may stop the destination-backed recording concurrently during shutdown.
                }
            }
            if (recording.getState() != RecordingState.CLOSED) {
                recording.close();
            }
            if (Files.isRegularFile(destination)) {
                log("Wrote startup recording to " + destination);
            } else {
                log("Recording ended without creating " + destination);
            }
        } catch (Throwable error) {
            log("Failed to finish recording: " + error.getMessage());
        }
    }

    private static void log(String message) {
        System.err.println("[Preflight] " + message);
    }

    @Name("preflight.AgentStarted")
    @Label("Preflight Agent Started")
    @Category("Starsector Preflight")
    static final class AgentStarted extends Event {
        @Label("Destination")
        String destination;

        @Label("Adapter Mode")
        String adapterMode;
    }

    @Name("preflight.AgentStopping")
    @Label("Preflight Agent Stopping")
    @Category("Starsector Preflight")
    static final class AgentStopping extends Event {
    }
}