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

/** Launch-time JFR agent for Starsector Preflight. */
public final class PreflightAgent {
    private PreflightAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        start(agentArgs);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        start(agentArgs);
    }

    private static void start(String agentArgs) {
        try {
            AgentOptions options = AgentOptions.parse(agentArgs);
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
            started.commit();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(recording, destination), "Preflight-JFR-Stop"));
            log("Recording startup to " + destination);
        } catch (Throwable error) {
            log("Profiler disabled: " + error.getMessage());
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

    private static void stop(Recording recording, Path destination) {
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
    }

    @Name("preflight.AgentStopping")
    @Label("Preflight Agent Stopping")
    @Category("Starsector Preflight")
    static final class AgentStopping extends Event {
    }
}
