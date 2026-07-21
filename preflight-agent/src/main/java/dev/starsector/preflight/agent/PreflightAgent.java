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
        AgentOptions options = contain("Agent options", () -> AgentOptions.parse(agentArgs));
        if (options == null) {
            return;
        }

        AdapterRuntime.Session adapterSession = contain(
                "Adapter initialization",
                () -> AdapterRuntime.start(options, instrumentation));
        Recording recording = startRecording(options);
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> {
                        stopRecording(recording, options.destination());
                        closeAdapter(adapterSession);
                    },
                    "Preflight-Shutdown"));
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            log("Could not register shutdown hook: " + message(error));
            stopRecording(recording, options.destination());
            closeAdapter(adapterSession);
        }
    }

    static <T> T contain(String component, Startup<T> startup) {
        try {
            return startup.start();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            log(component + " disabled: " + message(error));
            return null;
        }
    }

    private static void closeAdapter(AdapterRuntime.Session adapterSession) {
        if (adapterSession == null) {
            return;
        }
        contain("Adapter report finalization", () -> {
            adapterSession.close();
            return null;
        });
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
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            log("Profiler disabled: " + message(error));
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
            log("Failed to finish recording: " + message(error));
        }
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    private static void log(String message) {
        System.err.println("[Preflight] " + message);
    }

    @FunctionalInterface
    interface Startup<T> {
        T start() throws Throwable;
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
