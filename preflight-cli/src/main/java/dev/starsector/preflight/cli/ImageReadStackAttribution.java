package dev.starsector.preflight.cli;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;

/** Correlates expensive image reads with application methods on their JFR stack traces. */
final class ImageReadStackAttribution {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "webp", "bmp", "gif", "tga", "wbmp");
    private static final int METHOD_LIMIT = 500;
    private static final int OUTPUT_LIMIT = 75;
    private static final int PATH_SAMPLE_LIMIT = 10;
    private static final Comparator<MethodSummary> RANKING = Comparator
            .comparingLong(MethodSummary::behavioralScore)
            .reversed()
            .thenComparingLong(MethodSummary::events)
            .reversed()
            .thenComparingLong(MethodSummary::bytes)
            .reversed()
            .thenComparing(MethodSummary::className)
            .thenComparing(MethodSummary::methodName)
            .thenComparing(MethodSummary::descriptor);

    private final Map<MethodKey, MutableMethod> methods = new TreeMap<>();
    private long imageReadEvents;
    private long imageReadBytes;
    private long imageReadDurationNanos;
    private long eventsWithStack;
    private long eventsWithoutStack;
    private long consideredFrames;
    private long excludedFrames;
    private boolean methodsTruncated;

    void record(RecordedEvent event, String path, long bytes, long durationNanos) {
        if (!isImage(path)) {
            return;
        }
        List<Frame> frames = new ArrayList<>();
        try {
            RecordedStackTrace stackTrace = event.getStackTrace();
            if (stackTrace != null) {
                int depth = 0;
                for (RecordedFrame frame : stackTrace.getFrames()) {
                    RecordedMethod method = frame.getMethod();
                    if (method != null && method.getType() != null) {
                        frames.add(new Frame(
                                normalizeClassName(method.getType().getName()),
                                method.getName(),
                                method.getDescriptor(),
                                depth));
                    }
                    depth++;
                }
            }
        } catch (RuntimeException ignored) {
            // Stack metadata is diagnostic. The aggregate file-read report remains authoritative.
        }
        record(path, bytes, durationNanos, frames);
    }

    void record(String path, long bytes, long durationNanos, List<Frame> frames) {
        if (!isImage(path)) {
            return;
        }
        imageReadEvents++;
        imageReadBytes += Math.max(0, bytes);
        imageReadDurationNanos += Math.max(0, durationNanos);
        if (frames == null || frames.isEmpty()) {
            eventsWithoutStack++;
            return;
        }
        eventsWithStack++;

        String normalizedPath = IoTraceAttribution.normalizePath(path);
        Set<MethodKey> seenInEvent = new LinkedHashSet<>();
        for (Frame frame : frames) {
            if (frame == null || excluded(frame.className())) {
                excludedFrames++;
                continue;
            }
            consideredFrames++;
            MethodKey key = new MethodKey(
                    normalizeClassName(frame.className()),
                    safe(frame.methodName()),
                    safe(frame.descriptor()));
            if (!seenInEvent.add(key)) {
                continue;
            }
            MutableMethod method = methods.get(key);
            if (method == null) {
                if (methods.size() >= METHOD_LIMIT) {
                    methodsTruncated = true;
                    continue;
                }
                method = new MutableMethod(key);
                methods.put(key, method);
            }
            method.record(normalizedPath, bytes, durationNanos, Math.max(0, frame.depth()));
        }
    }

    Map<String, Object> toMap() {
        List<MethodSummary> ranked = methods.values().stream()
                .map(MutableMethod::summary)
                .sorted(RANKING)
                .limit(OUTPUT_LIMIT)
                .toList();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("imageReadEvents", imageReadEvents);
        output.put("imageReadBytes", imageReadBytes);
        output.put("imageReadDurationMs", millis(imageReadDurationNanos));
        output.put("eventsWithStack", eventsWithStack);
        output.put("eventsWithoutStack", eventsWithoutStack);
        output.put("consideredFrames", consideredFrames);
        output.put("excludedFrames", excludedFrames);
        output.put("retainedMethods", methods.size());
        output.put("methodsTruncated", methodsTruncated);
        output.put("topMethods", ranked.stream().map(MethodSummary::toMap).toList());
        return output;
    }

    private static boolean isImage(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        int dot = normalized.lastIndexOf('.');
        if (dot <= slash || dot == normalized.length() - 1) {
            return false;
        }
        return IMAGE_EXTENSIONS.contains(normalized.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static boolean excluded(String className) {
        String normalized = normalizeClassName(className);
        return normalized.isEmpty()
                || normalized.startsWith("java/")
                || normalized.startsWith("javax/")
                || normalized.startsWith("jdk/")
                || normalized.startsWith("sun/")
                || normalized.startsWith("com/sun/")
                || normalized.startsWith("org/junit/")
                || normalized.startsWith("org/apache/maven/")
                || normalized.startsWith("org/codehaus/plexus/");
    }

    private static String normalizeClassName(String value) {
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
        private final Set<String> paths = new LinkedHashSet<>();
        private long events;
        private long bytes;
        private long durationNanos;
        private int minimumDepth = Integer.MAX_VALUE;
        private long depthWeight;
        private boolean pathsTruncated;

        private MutableMethod(MethodKey key) {
            this.key = key;
        }

        private void record(String path, long eventBytes, long eventDurationNanos, int depth) {
            events++;
            bytes += Math.max(0, eventBytes);
            durationNanos += Math.max(0, eventDurationNanos);
            minimumDepth = Math.min(minimumDepth, depth);
            depthWeight += Math.max(1, 24 - Math.min(23, depth));
            if (!paths.contains(path)) {
                if (paths.size() >= PATH_SAMPLE_LIMIT) {
                    pathsTruncated = true;
                } else {
                    paths.add(path);
                }
            }
        }

        private MethodSummary summary() {
            long distinctPathBonus = Math.min(20, paths.size()) * 12L;
            long eventScore = Math.min(1_000, events) * 20L;
            long depthScore = depthWeight * 2L;
            long score = eventScore + distinctPathBonus + depthScore;
            return new MethodSummary(
                    key.className(),
                    key.methodName(),
                    key.descriptor(),
                    score,
                    events,
                    bytes,
                    durationNanos,
                    minimumDepth == Integer.MAX_VALUE ? -1 : minimumDepth,
                    depthWeight,
                    List.copyOf(paths),
                    pathsTruncated);
        }
    }

    private record MethodSummary(
            String className,
            String methodName,
            String descriptor,
            long behavioralScore,
            long events,
            long bytes,
            long durationNanos,
            int minimumDepth,
            long depthWeight,
            List<String> pathSamples,
            boolean pathsTruncated) {
        private Map<String, Object> toMap() {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("className", className);
            output.put("methodName", methodName);
            output.put("descriptor", descriptor);
            output.put("behavioralScore", behavioralScore);
            output.put("events", events);
            output.put("bytes", bytes);
            output.put("durationMs", millis(durationNanos));
            output.put("minimumDepth", minimumDepth);
            output.put("depthWeight", depthWeight);
            output.put("pathSamples", pathSamples);
            output.put("pathsTruncated", pathsTruncated);
            return output;
        }
    }
}
