package dev.starsector.preflight.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Streams combined child output while retaining a bounded chronological tail. */
final class ChildProcessOutput {
    static final int MAX_CAPTURE_BYTES = 1024 * 1024;
    private static final int COPY_BUFFER_BYTES = 16 * 1024;

    private ChildProcessOutput() {
    }

    static Result run(ProcessBuilder builder, Path outputFile) throws IOException, InterruptedException {
        return run(builder, outputFile, System.out);
    }

    static Result run(ProcessBuilder builder, Path outputFile, PrintStream operatorStream)
            throws IOException, InterruptedException {
        builder.redirectErrorStream(true);
        builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        Process process = builder.start();
        TailBuffer capture = new TailBuffer(MAX_CAPTURE_BYTES);
        byte[] copy = new byte[COPY_BUFFER_BYTES];
        try (InputStream input = process.getInputStream()) {
            int count;
            while ((count = input.read(copy)) >= 0) {
                if (count == 0) {
                    continue;
                }
                operatorStream.write(copy, 0, count);
                operatorStream.flush();
                capture.append(copy, 0, count);
            }
        } catch (IOException error) {
            process.destroyForcibly();
            throw error;
        }
        int exitCode = process.waitFor();
        Path absolute = outputFile.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] retained = capture.bytes();
        Files.write(absolute, retained);
        return new Result(exitCode, absolute, capture.totalBytes(), retained.length, capture.truncated());
    }

    record Result(int exitCode, Path file, long totalBytes, int capturedBytes, boolean truncated) {
        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("file", file);
            values.put("combinedStdoutStderr", true);
            values.put("totalBytes", totalBytes);
            values.put("capturedBytes", capturedBytes);
            values.put("truncated", truncated);
            values.put("maxCaptureBytes", MAX_CAPTURE_BYTES);
            return values;
        }
    }

    private static final class TailBuffer {
        private final byte[] bytes;
        private int start;
        private int size;
        private long totalBytes;

        private TailBuffer(int capacity) {
            this.bytes = new byte[capacity];
        }

        private void append(byte[] source, int offset, int length) {
            totalBytes = saturatedAdd(totalBytes, length);
            for (int i = 0; i < length; i++) {
                int index = (start + size) % bytes.length;
                bytes[index] = source[offset + i];
                if (size < bytes.length) {
                    size++;
                } else {
                    start = (start + 1) % bytes.length;
                }
            }
        }

        private byte[] bytes() {
            byte[] result = new byte[size];
            int first = Math.min(size, bytes.length - start);
            System.arraycopy(bytes, start, result, 0, first);
            if (first < size) {
                System.arraycopy(bytes, 0, result, first, size - first);
            }
            return result;
        }

        private long totalBytes() {
            return totalBytes;
        }

        private boolean truncated() {
            return totalBytes > size;
        }

        private static long saturatedAdd(long left, long right) {
            return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
        }
    }
}
