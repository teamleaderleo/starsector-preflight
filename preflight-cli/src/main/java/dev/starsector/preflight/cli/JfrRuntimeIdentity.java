package dev.starsector.preflight.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import jdk.jfr.consumer.RecordedEvent;

/** Bounded identity evidence emitted by the JVM that produced a JFR recording. */
final class JfrRuntimeIdentity {
    static final String SCOPE = "jfr-recorded-process";

    private static final Set<String> SYSTEM_PROPERTIES = Set.of(
            "java.version",
            "java.vendor",
            "java.runtime.name",
            "java.runtime.version",
            "java.vm.name",
            "java.vm.vendor",
            "java.vm.version",
            "os.name",
            "os.version",
            "os.arch");

    private final Map<String, Object> jvmInformation = new LinkedHashMap<>();
    private final Map<String, Object> systemProperties = new LinkedHashMap<>();
    private final Map<String, Object> osInformation = new LinkedHashMap<>();
    private final Map<String, Object> cpuInformation = new LinkedHashMap<>();

    void record(RecordedEvent event) {
        switch (event.getEventType().getName()) {
            case "jdk.InitialSystemProperty" -> recordSystemProperty(event);
            case "jdk.JVMInformation" -> recordJvmInformation(event);
            case "jdk.OSInformation" -> putString(osInformation, event, "osVersion");
            case "jdk.CPUInformation" -> recordCpuInformation(event);
            default -> {
            }
        }
    }

    Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("scope", SCOPE);
        values.put("complete", complete());
        values.put("jvmInformation", orderedCopy(jvmInformation));
        values.put("systemProperties", orderedSystemProperties());
        values.put("osInformation", orderedCopy(osInformation));
        values.put("cpuInformation", orderedCopy(cpuInformation));
        return values;
    }

    private void recordSystemProperty(RecordedEvent event) {
        String key = stringField(event, "key");
        String value = stringField(event, "value");
        if (SYSTEM_PROPERTIES.contains(key) && value != null) {
            systemProperties.put(key, value);
        }
    }

    private void recordJvmInformation(RecordedEvent event) {
        putString(jvmInformation, event, "jvmName");
        putString(jvmInformation, event, "jvmVersion");
        putString(jvmInformation, event, "jvmArguments");
        putString(jvmInformation, event, "jvmFlags");
        putString(jvmInformation, event, "javaArguments");
        putLong(jvmInformation, event, "pid");
    }

    private void recordCpuInformation(RecordedEvent event) {
        putString(cpuInformation, event, "cpu");
        putString(cpuInformation, event, "description");
        putLong(cpuInformation, event, "sockets");
        putLong(cpuInformation, event, "cores");
        putLong(cpuInformation, event, "hwThreads");
    }

    private boolean complete() {
        return systemProperties.containsKey("java.version")
                && systemProperties.containsKey("java.vm.name")
                && systemProperties.containsKey("java.vm.version")
                && systemProperties.containsKey("os.name")
                && systemProperties.containsKey("os.arch");
    }

    private Map<String, Object> orderedSystemProperties() {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (String key : new String[] {
                "java.version",
                "java.vendor",
                "java.runtime.name",
                "java.runtime.version",
                "java.vm.name",
                "java.vm.vendor",
                "java.vm.version",
                "os.name",
                "os.version",
                "os.arch"
        }) {
            if (systemProperties.containsKey(key)) {
                ordered.put(key, systemProperties.get(key));
            }
        }
        return Collections.unmodifiableMap(ordered);
    }

    private static Map<String, Object> orderedCopy(Map<String, Object> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static void putString(Map<String, Object> target, RecordedEvent event, String field) {
        String value = stringField(event, field);
        if (value != null) {
            target.put(field, value);
        }
    }

    private static void putLong(Map<String, Object> target, RecordedEvent event, String field) {
        if (!event.hasField(field)) {
            return;
        }
        try {
            target.put(field, event.getLong(field));
        } catch (RuntimeException ignored) {
            // Event layouts vary across supported JDK builds; missing optional evidence stays absent.
        }
    }

    private static String stringField(RecordedEvent event, String field) {
        if (!event.hasField(field)) {
            return null;
        }
        try {
            return event.getString(field);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
