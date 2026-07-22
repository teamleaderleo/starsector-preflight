package dev.starsector.preflight.agent;

import dev.starsector.preflight.core.PreparedTexture;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime bridge for upload-ready SPFT pixels with bounded direct-buffer ownership. */
public final class TexturePreparedPixelRuntime {
    static final String PLAN_ID = "texture-prepared-pixels-v2";
    static final int MAX_TEXTURE_BYTES = 32 * 1024 * 1024;
    static final long MAX_ACTIVE_DIRECT_BYTES = 64L * 1024 * 1024;
    static final int MAX_ACTIVE_BUFFERS = 1_024;

    private static final int ZERO_CHUNK_BYTES = 8 * 1024;
    private static final byte[] ZERO_CHUNK = new byte[ZERO_CHUNK_BYTES];
    private static final Object LOCK = new Object();
    private static final IdentityHashMap<ByteBuffer, Integer> ACTIVE = new IdentityHashMap<>();
    private static final IdentityHashMap<Thread, ArrayDeque<ByteBuffer>> IN_FLIGHT = new IdentityHashMap<>();
    private static final Telemetry TELEMETRY = new Telemetry();
    private static volatile boolean selected;
    private static long activeBytes;
    private static long peakBytes;
    private static int pendingBuffers;

    private TexturePreparedPixelRuntime() {
    }

    static void beginSession() {
        selected = false;
        synchronized (LOCK) {
            ACTIVE.clear();
            IN_FLIGHT.clear();
            activeBytes = 0;
            peakBytes = 0;
            pendingBuffers = 0;
        }
        TELEMETRY.reset();
    }

    static void select(TextureAdapterMode mode) {
        selected = mode == TextureAdapterMode.PREPARED_PIXELS;
    }

    static boolean ready() {
        return selected && TextureCompatibilityRuntime.ready();
    }

    /** Returns a lightweight prepared-texture carrier, or {@code null} for original decode fallback. */
    public static BufferedImage load(String logicalPath) {
        if (!ready()) {
            return null;
        }
        PreparedTexture texture = TextureCompatibilityRuntime.lookup(logicalPath);
        if (texture == null) {
            return null;
        }
        UploadLayout layout = uploadLayout(texture);
        if (layout == null || layout.uploadBytes() > MAX_TEXTURE_BYTES) {
            TELEMETRY.dimensionFallback();
            TELEMETRY.fallback();
            TextureCompatibilityRuntime.declined(TextureCompatibilityRuntime.FallbackReason.UNSUPPORTED_TEXTURE);
            return null;
        }
        TELEMETRY.carrier();
        return new CarrierImage(logicalPath, texture, layout);
    }

    public static boolean isCarrier(BufferedImage image) {
        return image instanceof CarrierImage;
    }

    public static String originalPath(BufferedImage image) {
        return image instanceof CarrierImage carrier ? carrier.logicalPath : null;
    }

    /** Creates one bounded direct upload buffer and returns stored derived colors. */
    public static PreparedPixel prepare(BufferedImage image) {
        if (!(image instanceof CarrierImage carrier)) {
            return null;
        }
        TELEMETRY.directAttempt();
        PreparedTexture texture = carrier.texture;
        UploadLayout layout = carrier.layout;
        int bytes = layout.uploadBytes();
        if (!reserve(bytes)) {
            TELEMETRY.fallback();
            TextureCompatibilityRuntime.declined(TextureCompatibilityRuntime.FallbackReason.DIRECT_MEMORY_LIMIT);
            return null;
        }

        ByteBuffer buffer = null;
        boolean registered = false;
        try {
            buffer = ByteBuffer.allocateDirect(bytes);
            writeUploadPixels(buffer, texture, layout);
            buffer.flip();
            synchronized (LOCK) {
                pendingBuffers--;
                ACTIVE.put(buffer, bytes);
                IN_FLIGHT.computeIfAbsent(Thread.currentThread(), ignored -> new ArrayDeque<>()).addLast(buffer);
                registered = true;
            }
            PreparedPixel result = new PreparedPixel(
                    buffer,
                    color(texture.color0Rgba()),
                    color(texture.color1Rgba()),
                    color(texture.color2Rgba()),
                    layout.uploadWidth(),
                    layout.uploadHeight(),
                    texture.channels(),
                    bytes);
            TELEMETRY.hit(bytes, layout.paddingBytes());
            if (carrier.creditSharedHit()) {
                TextureCompatibilityRuntime.hit(texture.pixelBytes());
            }
            return result;
        } catch (ThreadDeath | VirtualMachineError fatal) {
            if (registered) {
                release(buffer);
            } else {
                undoReservation(bytes);
            }
            throw fatal;
        } catch (Throwable error) {
            if (registered) {
                release(buffer);
            } else {
                undoReservation(bytes);
            }
            TELEMETRY.internalError();
            TELEMETRY.fallback();
            TextureCompatibilityRuntime.internalFailure();
            TextureCompatibilityRuntime.declined(TextureCompatibilityRuntime.FallbackReason.PREPARED_PIXEL_BRIDGE);
            return null;
        }
    }

    /** Releases the newest prepared buffer owned by the current converter caller. */
    public static void releaseCurrentThreadBuffer() {
        ByteBuffer buffer = null;
        synchronized (LOCK) {
            ArrayDeque<ByteBuffer> buffers = IN_FLIGHT.get(Thread.currentThread());
            if (buffers != null) {
                buffer = buffers.pollLast();
                if (buffers.isEmpty()) {
                    IN_FLIGHT.remove(Thread.currentThread());
                }
            }
        }
        release(buffer);
    }

    /** Releases accounting after Starsector's original cleanup method has run. */
    public static void release(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        Integer bytes;
        synchronized (LOCK) {
            removeTrackedLocked(buffer);
            bytes = ACTIVE.remove(buffer);
            if (bytes != null) {
                activeBytes -= bytes;
                if (activeBytes < 0) {
                    activeBytes = 0;
                }
            }
        }
        if (bytes != null) {
            TELEMETRY.release(bytes);
        }
    }

    static Map<String, Object> telemetry() {
        long currentBytes;
        long maximumBytes;
        int currentBuffers;
        int currentPending;
        synchronized (LOCK) {
            currentBytes = activeBytes;
            maximumBytes = peakBytes;
            currentBuffers = ACTIVE.size();
            currentPending = pendingBuffers;
        }
        return TELEMETRY.snapshot(
                currentBytes,
                maximumBytes,
                currentBuffers,
                currentPending,
                ready());
    }

    private static boolean reserve(int bytes) {
        synchronized (LOCK) {
            if (bytes <= 0
                    || bytes > MAX_TEXTURE_BYTES
                    || ACTIVE.size() + pendingBuffers >= MAX_ACTIVE_BUFFERS
                    || activeBytes > MAX_ACTIVE_DIRECT_BYTES - bytes) {
                return false;
            }
            pendingBuffers++;
            activeBytes += bytes;
            peakBytes = Math.max(peakBytes, activeBytes);
            return true;
        }
    }

    private static void undoReservation(int bytes) {
        synchronized (LOCK) {
            if (pendingBuffers > 0) {
                pendingBuffers--;
            }
            activeBytes -= bytes;
            if (activeBytes < 0) {
                activeBytes = 0;
            }
        }
    }

    static int expectedUploadDimension(int sourceDimension) {
        if (sourceDimension <= 0) {
            return -1;
        }
        int highest = Integer.highestOneBit(sourceDimension);
        if (highest == sourceDimension) {
            return sourceDimension;
        }
        return highest > (1 << 30) ? -1 : highest << 1;
    }

    private static UploadLayout uploadLayout(PreparedTexture texture) {
        int originalWidth = texture.originalWidth();
        int originalHeight = texture.originalHeight();
        int uploadWidth = expectedUploadDimension(originalWidth);
        int uploadHeight = expectedUploadDimension(originalHeight);
        if (uploadWidth <= 0 || uploadHeight <= 0) {
            return null;
        }

        // SPFT v1 stores source-sized bottom-up rows. The lower live seam needs the
        // next-power-of-two OpenGL backing allocation while retaining source dimensions
        // on the carrier for Starsector's texture-coordinate calculations.
        if (texture.uploadWidth() != originalWidth || texture.uploadHeight() != originalHeight) {
            return null;
        }
        try {
            long sourceBytes = Math.multiplyExact(
                    Math.multiplyExact((long) originalWidth, originalHeight),
                    texture.channels());
            long uploadBytes = Math.multiplyExact(
                    Math.multiplyExact((long) uploadWidth, uploadHeight),
                    texture.channels());
            if (sourceBytes != texture.pixelBytes() || uploadBytes > Integer.MAX_VALUE) {
                return null;
            }
            return new UploadLayout(
                    uploadWidth,
                    uploadHeight,
                    (int) uploadBytes,
                    Math.toIntExact(uploadBytes - sourceBytes));
        } catch (ArithmeticException error) {
            return null;
        }
    }

    private static void writeUploadPixels(
            ByteBuffer buffer,
            PreparedTexture texture,
            UploadLayout layout) {
        byte[] source = texture.pixels();
        int sourceStride = Math.multiplyExact(texture.originalWidth(), texture.channels());
        int uploadStride = Math.multiplyExact(layout.uploadWidth(), texture.channels());
        int rightPadding = uploadStride - sourceStride;
        for (int row = 0; row < texture.originalHeight(); row++) {
            buffer.put(source, row * sourceStride, sourceStride);
            putZeroes(buffer, rightPadding);
        }
        putZeroes(buffer, Math.multiplyExact(
                layout.uploadHeight() - texture.originalHeight(),
                uploadStride));
        if (buffer.position() != layout.uploadBytes()) {
            throw new IllegalStateException(
                    "Prepared upload wrote " + buffer.position() + " bytes; expected " + layout.uploadBytes());
        }
    }

    private static void putZeroes(ByteBuffer buffer, int count) {
        int remaining = count;
        while (remaining > 0) {
            int chunk = Math.min(remaining, ZERO_CHUNK.length);
            buffer.put(ZERO_CHUNK, 0, chunk);
            remaining -= chunk;
        }
    }

    private static void removeTrackedLocked(ByteBuffer buffer) {
        var threadIterator = IN_FLIGHT.entrySet().iterator();
        while (threadIterator.hasNext()) {
            Map.Entry<Thread, ArrayDeque<ByteBuffer>> entry = threadIterator.next();
            var bufferIterator = entry.getValue().iterator();
            while (bufferIterator.hasNext()) {
                if (bufferIterator.next() == buffer) {
                    bufferIterator.remove();
                    if (entry.getValue().isEmpty()) {
                        threadIterator.remove();
                    }
                    return;
                }
            }
        }
    }

    private static Color color(int rgba) {
        return new Color(
                PreparedTexture.red(rgba),
                PreparedTexture.green(rgba),
                PreparedTexture.blue(rgba),
                PreparedTexture.alpha(rgba));
    }

    /** Typed bridge object consumed only by the exact transformed TextureLoader class. */
    public record PreparedPixel(
            ByteBuffer buffer,
            Color color0,
            Color color1,
            Color color2,
            int width,
            int height,
            int channels,
            int pixelBytes) {
    }

    private record UploadLayout(
            int uploadWidth,
            int uploadHeight,
            int uploadBytes,
            int paddingBytes) {
    }

    private static final class CarrierImage extends BufferedImage {
        private final String logicalPath;
        private final PreparedTexture texture;
        private final UploadLayout layout;
        private final AtomicBoolean sharedHitCredited = new AtomicBoolean();

        private CarrierImage(String logicalPath, PreparedTexture texture, UploadLayout layout) {
            super(1, 1, texture.channels() == 4 ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            this.logicalPath = logicalPath;
            this.texture = texture;
            this.layout = layout;
        }

        private boolean creditSharedHit() {
            return sharedHitCredited.compareAndSet(false, true);
        }

        @Override
        public int getWidth() {
            return texture.originalWidth();
        }

        @Override
        public int getHeight() {
            return texture.originalHeight();
        }
    }

    private static final class Telemetry {
        private long carriers;
        private long directAttempts;
        private long hits;
        private long fallbacks;
        private long dimensionFallbacks;
        private long paddedUploads;
        private long paddingBytes;
        private long internalErrors;
        private long releases;
        private long bytesBypassed;
        private long releasedBytes;

        synchronized void reset() {
            carriers = 0;
            directAttempts = 0;
            hits = 0;
            fallbacks = 0;
            dimensionFallbacks = 0;
            paddedUploads = 0;
            paddingBytes = 0;
            internalErrors = 0;
            releases = 0;
            bytesBypassed = 0;
            releasedBytes = 0;
        }

        synchronized void carrier() {
            carriers++;
        }

        synchronized void directAttempt() {
            directAttempts++;
        }

        synchronized void hit(long bytes, long padding) {
            hits++;
            bytesBypassed = saturatedAdd(bytesBypassed, bytes);
            if (padding > 0) {
                paddedUploads++;
                paddingBytes = saturatedAdd(paddingBytes, padding);
            }
        }

        synchronized void fallback() {
            fallbacks++;
        }

        synchronized void dimensionFallback() {
            dimensionFallbacks++;
        }

        synchronized void internalError() {
            internalErrors++;
        }

        synchronized void release(long bytes) {
            releases++;
            releasedBytes = saturatedAdd(releasedBytes, bytes);
        }

        synchronized Map<String, Object> snapshot(
                long activeDirectBytes,
                long peakDirectBytes,
                int activeBuffers,
                int pendingBuffers,
                boolean ready) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("planId", PLAN_ID);
            values.put("ready", ready);
            values.put("maxTextureBytes", MAX_TEXTURE_BYTES);
            values.put("maxActiveDirectBytes", MAX_ACTIVE_DIRECT_BYTES);
            values.put("maxActiveBuffers", MAX_ACTIVE_BUFFERS);
            values.put("carriers", carriers);
            values.put("directAttempts", directAttempts);
            values.put("hits", hits);
            values.put("fallbacks", fallbacks);
            values.put("dimensionFallbacks", dimensionFallbacks);
            values.put("paddedUploads", paddedUploads);
            values.put("paddingBytes", paddingBytes);
            values.put("internalErrors", internalErrors);
            values.put("releases", releases);
            values.put("bytesBypassed", bytesBypassed);
            values.put("releasedBytes", releasedBytes);
            values.put("activeDirectBytes", activeDirectBytes);
            values.put("peakDirectBytes", peakDirectBytes);
            values.put("activeBuffers", activeBuffers);
            values.put("pendingBuffers", pendingBuffers);
            values.put("imageDecodesBypassed", hits);
            values.put("conversionCallsBypassed", hits);
            values.put("derivedColorCalculationsBypassed", hits);
            return Map.copyOf(values);
        }
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
