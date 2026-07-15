package dev.starsector.preflight.agent;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/** Installs the optional probe transformer while keeping every unknown build unmodified. */
final class AdapterRuntime {
    private AdapterRuntime() {
    }

    static Session start(AgentOptions options, Instrumentation instrumentation) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(instrumentation, "instrumentation");
        AdapterReport report = new AdapterReport(
                options.adapterMode(),
                options.adapterReport(),
                options.adapterTargets(),
                options.candidatePrefixes());
        BytecodeShapeReport shapeReport = new BytecodeShapeReport(shapeDestination(options.adapterReport()));
        Session session = new Session(report, shapeReport, options.adapterMode() != AdapterMode.OFF);
        PreparedImageBridge.disable("Prepared image cache is not active for this agent session");
        if (options.adapterMode() == AdapterMode.OFF) {
            return session;
        }
        if (killSwitch(System.getenv(), System.getProperties())) {
            report.killSwitch("Adapter kill switch is active; no transformer installed");
            return session;
        }

        if (options.adapterMode() == AdapterMode.ENABLED) {
            configurePreparedImage(options, report);
        } else {
            report.diagnostic("Probe mode never configures or activates prepared image cache consumption");
        }

        AdapterTargetRegistry registry;
        try {
            registry = loadRegistry(options, report);
        } catch (Throwable error) {
            rethrowIfFatal(error);
            report.contained("Could not load adapter target registry", error);
            return session;
        }

        try {
            instrumentation.addTransformer(new AdapterProbeTransformer(
                    options.adapterMode(),
                    registry,
                    options.candidatePrefixes(),
                    report,
                    shapeReport), false);
            report.transformerInstalled(registry.targets().size());
            if (registry.targets().isEmpty()) {
                report.diagnostic("No adapter targets are allowlisted; probe-only observation remains safe");
            }
        } catch (Throwable error) {
            rethrowIfFatal(error);
            report.contained("Could not install adapter transformer", error);
        }
        return session;
    }

    static boolean killSwitch(Map<String, String> environment, Properties properties) {
        String property = properties.getProperty("preflight.adapter.disabled");
        String environmentValue = environment.get("PREFLIGHT_DISABLE_ADAPTER");
        return truthy(property) || truthy(environmentValue);
    }

    private static void configurePreparedImage(AgentOptions options, AdapterReport report) {
        if (!options.hasCompleteTextureCacheContext()) {
            PreparedImageBridge.disable(
                    "Prepared image plan requires cache root, texture manifest, and resource index");
            report.diagnostic("Prepared image plan unavailable: explicit cache root, manifest, and index are required");
            return;
        }
        try {
            PreparedImageBridge.configure(
                    options.textureCacheRoot(),
                    options.textureManifest(),
                    options.resourceIndex());
            report.diagnostic("Prepared image cache context validated for exact opt-in adapter activation");
        } catch (Throwable error) {
            rethrowIfFatal(error);
            PreparedImageBridge.disable("Prepared image cache configuration failed: " + message(error));
            report.contained("Prepared image plan unavailable", error);
        }
    }

    private static AdapterTargetRegistry loadRegistry(AgentOptions options, AdapterReport report) throws IOException {
        Path path = options.adapterTargets();
        if (path != null) {
            Path absolute = path.toAbsolutePath().normalize();
            if (!Files.isRegularFile(absolute)) {
                throw new IOException("Adapter target file does not exist: " + absolute);
            }
            AdapterTargetRegistry registry = AdapterTargetRegistry.load(absolute);
            report.diagnostic("Loaded " + registry.targets().size() + " adapter target(s) from " + absolute);
            return registry;
        }
        if (options.adapterMode() == AdapterMode.ENABLED && PreparedImageBridge.ready()) {
            AdapterTargetRegistry registry = AdapterTargetRegistry.builtInPreparedImage();
            report.diagnostic("Loaded the reviewed built-in vanilla prepared image target");
            return registry;
        }
        return AdapterTargetRegistry.empty();
    }

    private static Path shapeDestination(Path adapterReport) {
        Path absolute = adapterReport.toAbsolutePath().normalize();
        String name = absolute.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        return absolute.resolveSibling(stem + "-bytecode-shape.json");
    }

    private static boolean truthy(String value) {
        if (value == null) {
            return false;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on", "enabled" -> true;
            default -> false;
        };
    }

    private static void rethrowIfFatal(Throwable error) {
        if (error instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
        if (error instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    static final class Session implements AutoCloseable {
        private final AdapterReport report;
        private final BytecodeShapeReport shapeReport;
        private final boolean writeReport;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Session(AdapterReport report, BytecodeShapeReport shapeReport, boolean writeReport) {
            this.report = report;
            this.shapeReport = shapeReport;
            this.writeReport = writeReport;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true) || !writeReport) {
                return;
            }
            try {
                report.write();
            } catch (IOException error) {
                System.err.println("[Preflight] Failed to write adapter report: " + error.getMessage());
            }
            try {
                shapeReport.write();
            } catch (IOException error) {
                System.err.println("[Preflight] Failed to write bytecode shape report: " + error.getMessage());
            }
        }
    }
}
