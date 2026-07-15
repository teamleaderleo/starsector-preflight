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
        Session session = new Session(report, options.adapterMode() != AdapterMode.OFF);
        if (options.adapterMode() == AdapterMode.OFF) {
            return session;
        }
        if (killSwitch(System.getenv(), System.getProperties())) {
            report.killSwitch("Adapter kill switch is active; no transformer installed");
            return session;
        }

        AdapterTargetRegistry registry;
        try {
            registry = loadRegistry(options.adapterTargets(), report);
        } catch (IOException error) {
            report.contained("Could not load adapter target registry", error);
            return session;
        }

        try {
            instrumentation.addTransformer(new AdapterProbeTransformer(
                    options.adapterMode(), registry, options.candidatePrefixes(), report), false);
            report.transformerInstalled(registry.targets().size());
            if (registry.targets().isEmpty()) {
                report.diagnostic("No adapter targets are allowlisted; probe-only observation remains safe");
            }
        } catch (Throwable error) {
            report.contained("Could not install adapter transformer", error);
        }
        return session;
    }

    static boolean killSwitch(Map<String, String> environment, Properties properties) {
        String property = properties.getProperty("preflight.adapter.disabled");
        String environmentValue = environment.get("PREFLIGHT_DISABLE_ADAPTER");
        return truthy(property) || truthy(environmentValue);
    }

    private static AdapterTargetRegistry loadRegistry(Path path, AdapterReport report) throws IOException {
        if (path == null) {
            return AdapterTargetRegistry.empty();
        }
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new IOException("Adapter target file does not exist: " + absolute);
        }
        AdapterTargetRegistry registry = AdapterTargetRegistry.load(absolute);
        report.diagnostic("Loaded " + registry.targets().size() + " adapter target(s) from " + absolute);
        return registry;
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

    static final class Session implements AutoCloseable {
        private final AdapterReport report;
        private final boolean writeReport;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Session(AdapterReport report, boolean writeReport) {
            this.report = report;
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
        }
    }
}
