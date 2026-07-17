package dev.starsector.preflight.agent;

import dev.starsector.preflight.core.PreparedTexture;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime bridge for upload-ready SPFT pixels with bounded direct-buffer ownership. */
public final class TexturePreparedPixelRuntime {
    static final String PLAN_ID = "texture-prepared-pixels-v1";
    static final int MAX_TEXTURE_BYTES = 32 * 1024 * 1024;
    static final long MAX_ACTIVE_DIRECT_BYTES = 64L * 1024 * 1024;
    static final int MAX_ACTIVE_BUFFERS = 1_024;

    private static final Object LOCK = new Object();
    private static final IdentityHashMap<ByteBuffer, Integer> ACTIVE = new IdentityHashMap<>();
    private static final Telemetry TELEMETRY = new Telemetry();
    private static long activeBytes;
    private static long peakBytes;
    private static int pendingBuffers;

    private TexturePreparedPixelRuntime() {
    }

    static void beginSession() {
        synchronized (LOCK) {
            ACTIVE.clear();
            activeBytes = 0;
            peakBytes = 0;
            pendingBuffers = 0;
        }
        TELEMETRY.reset();
    }

    static boolean ready() {
        return TextureCompatibilityRuntime.ready();
    }

    /** Returns a lightweight prepared-texture carrier, or {@code null} for original decode fallback. */
    public static BufferedImage load(String logicalPath) {
        PreparedTexture texture = TextureCompatibilityRuntime.lookup(logicalPath);
        if (texture == null) {
            return null;
        }
        if (texture.originalWidth() != texture.uploadWidth()
                || texture.originalHeight() != texture.uploadHeight()
                || texture.pixelBytes() > MAX_TEXTURE_BYTES) {
            TELEMETRY.fallback();
            TextureCompatibilityRuntime.declined(TextureCompatibilityRuntime.FallbackReason.UNSUPPORTED_TEXTURE);
            return null;
        }
        TELEMETRY.carrier();
        return new CarrierImage(logicalPath, texture);
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
        int bytes = texture.pixelBytes();
        if (!reserve(bytes)) {
            TELEMETRY.fallback();
            TextureCompatibilityRuntime.declined(TextureCompatibilityRuntime.FallbackReason.DIRECT_MEMORY_LIMIT);
            return null;
        }

        ByteBuffer buffer = null;
        boolean registered = false;
        try {
            buffer = ByteBuffer.allocateDirect(bytes);
            buffer.put(texture.pixels());
            buffer.flip();
            synchronized (LOCK) {
                pendingBuffers--;
                ACTIVE.put(buffer, bytes);
                registered = true;
            }
            PreparedPixel result = new PreparedPixel(
                    buffer,
                    color(texture.color0Rgba()),
                    color(texture.color1Rgba()),
                    color(texture.color2Rgba()),
                    texture.originalWidth(),
                    texture.originalHeight(),
                    texture.channels(),
                    bytes);
            TELEMETRY.hit(bytes);
            TextureCompatibilityRuntime.hit(bytes);
            return result;
        } catch (ThreadDeath | VirtualMachineError fatal) {
            if (!registered) {
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

    /** Releases accounting after Starsector's original cleanup method has run. */
    public static void release(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        Integer bytes;
        synchronized (LOCK) {
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

    private static final class CarrierImage extends BufferedImage {
        private final String logicalPath;
        private final PreparedTexture texture;

        private CarrierImage(String logicalPath, PreparedTexture texture) {
            super(1, 1, texture.channels() == 4 ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            this.logicalPath = logicalPath;
            this.texture = texture;
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
        private long internalErrors;
        private long releases;
        private long bytesBypassed;
        private long releasedBytes;

        synchronized void reset() {
            carriers = 0;
            directAttempts = 0;
            hits = 0;
            fallbacks = 0;
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

        synchronized void hit(long bytes) {
            hits++;
            bytesBypassed = saturatedAdd(bytesBypassed, bytes);
        }

        synchronized void fallback() {
            fallbacks++;
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
