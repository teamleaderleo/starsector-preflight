package dev.starsector.preflight.cli;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;

/** Bounded attribution of repeated class-definition and JIT compilation work during startup. */
final class StartupCodeAttribution {
    private static final int LOADER_LIMIT = 200;
    private static final int COMPILATION_METHOD_LIMIT = 2_000;
    private static final int DEFINITION_METHOD_LIMIT = 500;
    private static final int OUTPUT_METHOD_LIMIT = 75;
    private static final int CLASS_SAMPLE_LIMIT = 2_000;
    private static final int SOURCE_SAMPLE_LIMIT = 100;

    private static final Comparator<CompilationSummary> COMPILATION_RANKING = Comparator
            .comparingLong(CompilationSummary::durationNanos)
            .reversed()
            .thenComparing(Comparator.comparingLong(CompilationSummary::events).reversed())
            .thenComparing(CompilationSummary::className)
            .thenComparing(CompilationSummary::methodName)
            .thenComparing(CompilationSummary::descriptor);
    private static final Comparator<DefinitionMethodSummary> DEFINITION_RANKING = Comparator
            .comparingLong(DefinitionMethodSummary::events)
            .reversed()
            .thenComparingInt(DefinitionMethodSummary::minimumDepth)
            .thenComparing(DefinitionMethodSummary::className)
            .thenComparing(DefinitionMethodSummary::methodName)
            .thenComparing(DefinitionMethodSummary::descriptor);

    private final Map<LoaderKey, MutableLoader> loaders = new TreeMap<>();
    private final Map<MethodKey, MutableCompilation> compilationMethods = new TreeMap<>();
    private final Map<MethodKey, MutableDefinitionMethod> janinoDefinitionMethods = new TreeMap<>();
    private final Map<String, MutableCategory> compilationCategories = new TreeMap<>();
    private final Set<String> janinoClasses = new TreeSet<>();
    private final Set<String> janinoSourceSamples = new LinkedHashSet<>();
    private long classDefineEvents;
    private long classDefineMetadataFailures;
    private long janinoClassDefineEvents;
    private long compilationEvents;
    private long compilationDurationNanos;
    private long compilationMetadataFailures;
    private long janinoCompilationEvents;
    private long janinoCompilationDurationNanos;
    private boolean loadersTruncated;
    private boolean compilationMethodsTruncated;
    private boolean definitionMethodsTruncated;
    private boolean janinoClassesTruncated;
    private boolean sourceSamplesTruncated;

    void recordClassDefine(RecordedEvent event) {
        classDefineEvents++;
        try {
            RecordedClass definedClass = event.getValue("definedClass");
            if (definedClass == null) {
                classDefineMetadataFailures++;
                return;
            }
            String className = normalizeClass(definedClass.getName());
            RecordedClassLoader loader = definedClass.getClassLoader();
            String loaderName = loader == null ? "<bootstrap>" : safe(loader.getName());
            String loaderType = "<bootstrap>";
            if (loader != null && loader.getType() != null) {
                loaderType = normalizeClass(loader.getType().getName());
            }
            String source = stringField(event, "source");
            List<Frame> frames = frames(event);
            recordClassDefine(className, loaderType, loaderName, source, frames);
        } catch (RuntimeException error) {
            classDefineMetadataFailures++;
        }
    }

    void recordCompilation(RecordedEvent event, long durationNanos) {
        compilationEvents++;
        compilationDurationNanos += Math.max(0, durationNanos);
        try {
            RecordedMethod method = event.getValue("method");
            if (method == null || method.getType() == null) {
                compilationMetadataFailures++;
                return;
            }
            recordCompilation(
                    normalizeClass(method.getType().getName()),
                    safe(method.getName()),
                    safe(method.getDescriptor()),
                    durationNanos);
        } catch (RuntimeException error) {
            compilationMetadataFailures++;
        }
    }

    void recordClassDefine(
            String className,
            String loaderType,
            String loaderName,
            String source,
            List<Frame> frames) {
        className = normalizeClass(className);
        loaderType = normalizeClass(loaderType);
        loaderName = safe(loaderName);
        LoaderKey loaderKey = new LoaderKey(loaderType, loaderName);
        MutableLoader loader = loaders.get(loaderKey);
        if (loader == null) {
            if (loaders.size() >= LOADER_LIMIT) {
                loadersTruncated = true;
            } else {
                loader = new MutableLoader(loaderKey);
                loaders.put(loaderKey, loader);
            }
        }
        if (loader != null) loader.events++;

        if (!janinoLoader(loaderType)) {
            return;
        }
        janinoClassDefineEvents++;
        if (janinoClasses.size() < CLASS_SAMPLE_LIMIT || janinoClasses.contains(className)) {
            janinoClasses.add(className);
        } else {
            janinoClassesTruncated = true;
        }
        String normalizedSource = IoTraceAttribution.normalizePath(source);
        if (!normalizedSource.isBlank() && !janinoSourceSamples.contains(normalizedSource)) {
            if (janinoSourceSamples.size() >= SOURCE_SAMPLE_LIMIT) {
                sourceSamplesTruncated = true;
            } else {
                janinoSourceSamples.add(normalizedSource);
            }
        }

        Set<MethodKey> seen = new LinkedHashSet<>();
        for (Frame frame : frames == null ? List.<Frame>of() : frames) {
            String frameClass = normalizeClass(frame.className());
            if (!relevantDefinitionFrame(frameClass)) continue;
            MethodKey key = new MethodKey(frameClass, safe(frame.methodName()), safe(frame.descriptor()));
            if (!seen.add(key)) continue;
            MutableDefinitionMethod method = janinoDefinitionMethods.get(key);
            if (method == null) {
                if (janinoDefinitionMethods.size() >= DEFINITION_METHOD_LIMIT) {
                    definitionMethodsTruncated = true;
                    continue;
                }
                method = new MutableDefinitionMethod(key);
                janinoDefinitionMethods.put(key, method);
            }
            method.events++;
            method.minimumDepth = Math.min(method.minimumDepth, Math.max(0, frame.depth()));
        }
    }

    void recordCompilation(String className, String methodName, String descriptor, long durationNanos) {
        String normalizedClass = normalizeClass(className);
        MethodKey key = new MethodKey(normalizedClass, safe(methodName), safe(descriptor));
        MutableCompilation method = compilationMethods.get(key);
        if (method == null) {
            if (compilationMethods.size() >= COMPILATION_METHOD_LIMIT) {
                compilationMethodsTruncated = true;
            } else {
                method = new MutableCompilation(key);
                compilationMethods.put(key, method);
            }
        }
        long duration = Math.max(0, durationNanos);
        if (method != null) {
            method.events++;
            method.durationNanos += duration;
        }
        String category = category(normalizedClass);
        MutableCategory categoryState = compilationCategories.computeIfAbsent(category, ignored -> new MutableCategory());
        categoryState.events++;
        categoryState.durationNanos += duration;
        if ("JANINO".equals(category)) {
            janinoCompilationEvents++;
            janinoCompilationDurationNanos += duration;
        }
    }

    Map<String, Object> toMap() {
        List<LoaderSummary> loaderSummaries = loaders.values().stream()
                .map(MutableLoader::summary)
                .sorted(Comparator.comparingLong(LoaderSummary::events).reversed()
                        .thenComparing(LoaderSummary::loaderType)
                        .thenComparing(LoaderSummary::loaderName))
                .toList();
        List<CompilationSummary> topCompilationMethods = compilationMethods.values().stream()
                .map(MutableCompilation::summary)
                .sorted(COMPILATION_RANKING)
                .limit(OUTPUT_METHOD_LIMIT)
                .toList();
        List<DefinitionMethodSummary> topDefinitionMethods = janinoDefinitionMethods.values().stream()
                .map(MutableDefinitionMethod::summary)
                .sorted(DEFINITION_RANKING)
                .limit(OUTPUT_METHOD_LIMIT)
                .toList();

        Map<String, Object> classDefinitions = new LinkedHashMap<>();
        classDefinitions.put("events", classDefineEvents);
        classDefinitions.put("metadataFailures", classDefineMetadataFailures);
        classDefinitions.put("retainedLoaders", loaders.size());
        classDefinitions.put("loadersTruncated", loadersTruncated);
        classDefinitions.put("byLoader", loaderSummaries.stream().map(LoaderSummary::toMap).toList());
        classDefinitions.put("janinoEvents", janinoClassDefineEvents);
        classDefinitions.put("janinoUniqueClasses", janinoClasses.size());
        classDefinitions.put("janinoClassSamples", List.copyOf(janinoClasses));
        classDefinitions.put("janinoClassesTruncated", janinoClassesTruncated);
        classDefinitions.put("janinoSourceSamples", List.copyOf(janinoSourceSamples));
        classDefinitions.put("janinoSourceSamplesTruncated", sourceSamplesTruncated);
        classDefinitions.put("janinoDefinitionMethodsRetained", janinoDefinitionMethods.size());
        classDefinitions.put("janinoDefinitionMethodsTruncated", definitionMethodsTruncated);
        classDefinitions.put("topJaninoDefinitionStackMethods",
                topDefinitionMethods.stream().map(DefinitionMethodSummary::toMap).toList());

        Map<String, Object> categories = new LinkedHashMap<>();
        compilationCategories.forEach((name, state) -> categories.put(name, state.toMap()));
        Map<String, Object> compilations = new LinkedHashMap<>();
        compilations.put("events", compilationEvents);
        compilations.put("durationMs", millis(compilationDurationNanos));
        compilations.put("metadataFailures", compilationMetadataFailures);
        compilations.put("retainedMethods", compilationMethods.size());
        compilations.put("methodsTruncated", compilationMethodsTruncated);
        compilations.put("janinoEvents", janinoCompilationEvents);
        compilations.put("janinoDurationMs", millis(janinoCompilationDurationNanos));
        compilations.put("byCategory", categories);
        compilations.put("topMethods", topCompilationMethods.stream().map(CompilationSummary::toMap).toList());

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("classDefinitions", classDefinitions);
        output.put("compilations", compilations);
        return output;
    }

    private static List<Frame> frames(RecordedEvent event) {
        List<Frame> result = new ArrayList<>();
        RecordedStackTrace stack = event.getStackTrace();
        if (stack == null) return List.of();
        int depth = 0;
        for (RecordedFrame frame : stack.getFrames()) {
            RecordedMethod method = frame.getMethod();
            if (method != null && method.getType() != null) {
                result.add(new Frame(
                        normalizeClass(method.getType().getName()),
                        safe(method.getName()),
                        safe(method.getDescriptor()),
                        depth));
            }
            depth++;
        }
        return List.copyOf(result);
    }

    private static String stringField(RecordedEvent event, String name) {
        if (!event.hasField(name)) return "";
        try {
            return safe(event.getString(name));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean janinoLoader(String loaderType) {
        String normalized = normalizeClass(loaderType);
        return normalized.equals("org/codehaus/janino/JavaSourceClassLoader")
                || normalized.endsWith("/JavaSourceClassLoader") && normalized.contains("janino");
    }

    private static boolean relevantDefinitionFrame(String className) {
        return className.startsWith("org/codehaus/janino/")
                || className.startsWith("org/codehaus/commons/compiler/")
                || className.startsWith("com/fs/");
    }

    private static String category(String className) {
        String normalized = normalizeClass(className);
        if (normalized.startsWith("org/codehaus/janino/")
                || normalized.startsWith("org/codehaus/commons/compiler/")) return "JANINO";
        if (normalized.startsWith("com/fs/")) return "STARSECTOR";
        if (normalized.startsWith("java/") || normalized.startsWith("javax/")
                || normalized.startsWith("jdk/") || normalized.startsWith("sun/")) return "JDK";
        return "MOD_OR_LIBRARY";
    }

    private static String normalizeClass(String value) {
        return safe(value).replace('.', '/');
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    record Frame(String className, String methodName, String descriptor, int depth) {
    }

    private record LoaderKey(String loaderType, String loaderName) implements Comparable<LoaderKey> {
        @Override
        public int compareTo(LoaderKey other) {
            int type = loaderType.compareTo(other.loaderType);
            return type != 0 ? type : loaderName.compareTo(other.loaderName);
        }
    }

    private record MethodKey(String className, String methodName, String descriptor)
            implements Comparable<MethodKey> {
        @Override
        public int compareTo(MethodKey other) {
            int classOrder = className.compareTo(other.className);
            if (classOrder != 0) return classOrder;
            int methodOrder = methodName.compareTo(other.methodName);
            if (methodOrder != 0) return methodOrder;
            return descriptor.compareTo(other.descriptor);
        }
    }

    private static final class MutableLoader {
        private final LoaderKey key;
        private long events;

        private MutableLoader(LoaderKey key) {
            this.key = key;
        }

        private LoaderSummary summary() {
            return new LoaderSummary(key.loaderType(), key.loaderName(), events);
        }
    }

    private static final class MutableCompilation {
        private final MethodKey key;
        private long events;
        private long durationNanos;

        private MutableCompilation(MethodKey key) {
            this.key = key;
        }

        private CompilationSummary summary() {
            return new CompilationSummary(
                    key.className(), key.methodName(), key.descriptor(), events, durationNanos);
        }
    }

    private static final class MutableDefinitionMethod {
        private final MethodKey key;
        private long events;
        private int minimumDepth = Integer.MAX_VALUE;

        private MutableDefinitionMethod(MethodKey key) {
            this.key = key;
        }

        private DefinitionMethodSummary summary() {
            return new DefinitionMethodSummary(
                    key.className(), key.methodName(), key.descriptor(), events,
                    minimumDepth == Integer.MAX_VALUE ? -1 : minimumDepth);
        }
    }

    private static final class MutableCategory {
        private long events;
        private long durationNanos;

        private Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("events", events);
            values.put("durationMs", millis(durationNanos));
            return values;
        }
    }

    private record LoaderSummary(String loaderType, String loaderName, long events) {
        private Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("loaderType", loaderType);
            values.put("loaderName", loaderName);
            values.put("events", events);
            return values;
        }
    }

    private record CompilationSummary(
            String className,
            String methodName,
            String descriptor,
            long events,
            long durationNanos) {
        private Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("className", className);
            values.put("methodName", methodName);
            values.put("descriptor", descriptor);
            values.put("events", events);
            values.put("durationMs", millis(durationNanos));
            return values;
        }
    }

    private record DefinitionMethodSummary(
            String className,
            String methodName,
            String descriptor,
            long events,
            int minimumDepth) {
        private Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("className", className);
            values.put("methodName", methodName);
            values.put("descriptor", descriptor);
            values.put("events", events);
            values.put("minimumDepth", minimumDepth);
            return values;
        }
    }
}
