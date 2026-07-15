package dev.starsector.preflight.cli;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;

/** Statistical CPU attribution for the startup domains targeted by Preflight caches. */
final class StartupCpuAttribution {
    private static final int METHOD_LIMIT = 2_000;
    private static final int STACK_LIMIT = 2_000;
    private static final int THREAD_LIMIT = 200;
    private static final int OUTPUT_LIMIT = 75;
    private static final int STACK_DEPTH = 10;

    private static final Comparator<MethodSummary> METHOD_RANKING = Comparator
            .comparingLong(MethodSummary::samples)
            .reversed()
            .thenComparing(MethodSummary::className)
            .thenComparing(MethodSummary::methodName)
            .thenComparing(MethodSummary::descriptor);
    private static final Comparator<StackSummary> STACK_RANKING = Comparator
            .comparingLong(StackSummary::samples)
            .reversed()
            .thenComparing(StackSummary::category)
            .thenComparing(summary -> String.join("\n", summary.frames()));

    private final Map<String, Long> categories = new TreeMap<>();
    private final Map<MethodKey, MutableMethod> leafMethods = new TreeMap<>();
    private final Map<MethodKey, MutableMethod> attributedMethods = new TreeMap<>();
    private final Map<StackKey, MutableStack> stacks = new TreeMap<>();
    private final Map<String, MutableThread> threads = new TreeMap<>();
    private long samples;
    private long missingStacks;
    private boolean leafMethodsTruncated;
    private boolean attributedMethodsTruncated;
    private boolean stacksTruncated;
    private boolean threadsTruncated;

    void record(RecordedEvent event) {
        String thread = "<unknown>";
        try {
            var sampledThread = event.getThread("sampledThread");
            if (sampledThread != null && sampledThread.getJavaName() != null) {
                thread = sampledThread.getJavaName();
            }
        } catch (RuntimeException ignored) {
            // Thread identity is diagnostic only.
        }
        List<Frame> frames = new ArrayList<>();
        try {
            RecordedStackTrace stack = event.getStackTrace();
            if (stack != null) {
                for (RecordedFrame frame : stack.getFrames()) {
                    RecordedMethod method = frame.getMethod();
                    if (method != null && method.getType() != null) {
                        frames.add(new Frame(
                                normalizeClass(method.getType().getName()),
                                safe(method.getName()),
                                safe(method.getDescriptor())));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // The sample remains countable even if stack metadata is incomplete.
        }
        record(thread, frames);
    }

    void record(String threadName, List<Frame> frames) {
        samples++;
        List<Frame> safeFrames = frames == null ? List.of() : List.copyOf(frames);
        if (safeFrames.isEmpty()) missingStacks++;

        Frame leaf = safeFrames.isEmpty() ? null : safeFrames.get(0);
        if (leaf != null) retainMethod(leafMethods, leaf, true);

        Attribution attribution = attribute(safeFrames);
        categories.merge(attribution.category(), 1L, Long::sum);
        if (attribution.frame() != null) {
            retainMethod(attributedMethods, attribution.frame(), false);
        }
        retainStack(attribution.category(), safeFrames);
        retainThread(safe(threadName), attribution.category());
    }

    Map<String, Object> toMap() {
        Map<String, Object> categoryOutput = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            long count = categories.getOrDefault(category.name(), 0L);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("samples", count);
            values.put("percent", samples == 0 ? 0.0 : count * 100.0 / samples);
            categoryOutput.put(category.name(), values);
        }

        List<MethodSummary> topLeaf = leafMethods.values().stream()
                .map(MutableMethod::summary)
                .sorted(METHOD_RANKING)
                .limit(OUTPUT_LIMIT)
                .toList();
        List<MethodSummary> topAttributed = attributedMethods.values().stream()
                .map(MutableMethod::summary)
                .sorted(METHOD_RANKING)
                .limit(OUTPUT_LIMIT)
                .toList();
        List<StackSummary> topStacks = stacks.values().stream()
                .map(MutableStack::summary)
                .sorted(STACK_RANKING)
                .limit(OUTPUT_LIMIT)
                .toList();
        List<ThreadSummary> topThreads = threads.values().stream()
                .map(MutableThread::summary)
                .sorted(Comparator.comparingLong(ThreadSummary::samples).reversed()
                        .thenComparing(ThreadSummary::threadName))
                .limit(OUTPUT_LIMIT)
                .toList();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("samples", samples);
        output.put("missingStacks", missingStacks);
        output.put("categories", categoryOutput);
        output.put("retainedLeafMethods", leafMethods.size());
        output.put("leafMethodsTruncated", leafMethodsTruncated);
        output.put("topLeafMethods", topLeaf.stream().map(MethodSummary::toMap).toList());
        output.put("retainedAttributedMethods", attributedMethods.size());
        output.put("attributedMethodsTruncated", attributedMethodsTruncated);
        output.put("topAttributedMethods", topAttributed.stream().map(MethodSummary::toMap).toList());
        output.put("retainedStacks", stacks.size());
        output.put("stacksTruncated", stacksTruncated);
        output.put("topStacks", topStacks.stream().map(StackSummary::toMap).toList());
        output.put("retainedThreads", threads.size());
        output.put("threadsTruncated", threadsTruncated);
        output.put("topThreads", topThreads.stream().map(ThreadSummary::toMap).toList());
        output.put("interpretation", "Execution samples are statistical CPU evidence, not exact wall-clock attribution");
        return output;
    }

    private void retainMethod(Map<MethodKey, MutableMethod> target, Frame frame, boolean leaf) {
        MethodKey key = new MethodKey(frame.className(), frame.methodName(), frame.descriptor());
        MutableMethod method = target.get(key);
        if (method == null) {
            if (target.size() >= METHOD_LIMIT) {
                if (leaf) leafMethodsTruncated = true;
                else attributedMethodsTruncated = true;
                return;
            }
            method = new MutableMethod(key);
            target.put(key, method);
        }
        method.samples++;
    }

    private void retainStack(String category, List<Frame> frames) {
        List<String> retained = frames.stream()
                .limit(STACK_DEPTH)
                .map(Frame::display)
                .toList();
        StackKey key = new StackKey(category, retained);
        MutableStack stack = stacks.get(key);
        if (stack == null) {
            if (stacks.size() >= STACK_LIMIT) {
                stacksTruncated = true;
                return;
            }
            stack = new MutableStack(key);
            stacks.put(key, stack);
        }
        stack.samples++;
    }

    private void retainThread(String threadName, String category) {
        MutableThread thread = threads.get(threadName);
        if (thread == null) {
            if (threads.size() >= THREAD_LIMIT) {
                threadsTruncated = true;
                return;
            }
            thread = new MutableThread(threadName);
            threads.put(threadName, thread);
        }
        thread.samples++;
        thread.categories.merge(category, 1L, Long::sum);
    }

    private static Attribution attribute(List<Frame> frames) {
        for (Frame frame : frames) {
            Category category = category(frame.className());
            if (category != null) return new Attribution(category.name(), frame);
        }
        return new Attribution(frames.isEmpty() ? Category.NO_STACK.name() : Category.JDK_ONLY.name(), null);
    }

    private static Category category(String className) {
        String value = normalizeClass(className);
        if (value.startsWith("com/jcraft/jorbis/")
                || value.startsWith("com/jcraft/jogg/")
                || value.startsWith("sound/")) return Category.AUDIO_DECODE;
        if (value.startsWith("org/codehaus/janino/")
                || value.startsWith("org/codehaus/commons/compiler/")) return Category.JANINO;
        if (value.startsWith("com/fs/graphics/")
                || value.startsWith("java/awt/image/")
                || value.startsWith("sun/awt/image/")
                || value.startsWith("com/sun/imageio/")
                || value.startsWith("javax/imageio/")) return Category.TEXTURE_IMAGE;
        if (value.startsWith("com/fs/starfarer/loading/")) return Category.STARSECTOR_LOADING;
        if (value.startsWith("com/fs/starfarer/campaign/rules/")) return Category.RULES;
        if (value.startsWith("org/json/")) return Category.JSON;
        if (value.startsWith("com/fs/")) return Category.STARSECTOR_OTHER;
        if (jdk(value)) return null;
        return Category.MOD_OR_LIBRARY;
    }

    private static boolean jdk(String className) {
        return className.startsWith("java/")
                || className.startsWith("javax/")
                || className.startsWith("jdk/")
                || className.startsWith("sun/")
                || className.startsWith("com/sun/");
    }

    private static String normalizeClass(String value) {
        return safe(value).replace('.', '/');
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "<unknown>" : value;
    }

    enum Category {
        AUDIO_DECODE,
        JANINO,
        TEXTURE_IMAGE,
        STARSECTOR_LOADING,
        RULES,
        JSON,
        STARSECTOR_OTHER,
        MOD_OR_LIBRARY,
        JDK_ONLY,
        NO_STACK
    }

    record Frame(String className, String methodName, String descriptor) {
        Frame {
            className = normalizeClass(className);
            methodName = safe(methodName);
            descriptor = safe(descriptor);
        }

        String display() {
            return className + "." + methodName + descriptor;
        }
    }

    private record Attribution(String category, Frame frame) {
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

    private static final class MutableMethod {
        private final MethodKey key;
        private long samples;

        private MutableMethod(MethodKey key) {
            this.key = key;
        }

        private MethodSummary summary() {
            return new MethodSummary(key.className(), key.methodName(), key.descriptor(), samples);
        }
    }

    private record MethodSummary(String className, String methodName, String descriptor, long samples) {
        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("className", className);
            values.put("methodName", methodName);
            values.put("descriptor", descriptor);
            values.put("samples", samples);
            return values;
        }
    }

    private record StackKey(String category, List<String> frames) implements Comparable<StackKey> {
        StackKey {
            frames = List.copyOf(frames);
        }

        @Override
        public int compareTo(StackKey other) {
            int categoryOrder = category.compareTo(other.category);
            if (categoryOrder != 0) return categoryOrder;
            int count = Math.min(frames.size(), other.frames.size());
            for (int i = 0; i < count; i++) {
                int frameOrder = frames.get(i).compareTo(other.frames.get(i));
                if (frameOrder != 0) return frameOrder;
            }
            return Integer.compare(frames.size(), other.frames.size());
        }
    }

    private static final class MutableStack {
        private final StackKey key;
        private long samples;

        private MutableStack(StackKey key) {
            this.key = key;
        }

        private StackSummary summary() {
            return new StackSummary(key.category(), key.frames(), samples);
        }
    }

    private record StackSummary(String category, List<String> frames, long samples) {
        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("category", category);
            values.put("samples", samples);
            values.put("frames", frames);
            return values;
        }
    }

    private static final class MutableThread {
        private final String name;
        private final Map<String, Long> categories = new TreeMap<>();
        private long samples;

        private MutableThread(String name) {
            this.name = name;
        }

        private ThreadSummary summary() {
            return new ThreadSummary(name, samples, Map.copyOf(categories));
        }
    }

    private record ThreadSummary(String threadName, long samples, Map<String, Long> categories) {
        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("threadName", threadName);
            values.put("samples", samples);
            values.put("categories", categories);
            return values;
        }
    }
}
