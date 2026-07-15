package dev.starsector.preflight.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Reads and validates one prepared texture from a profile manifest without leaking cache failures. */
public final class ManifestTextureCacheLookup implements TextureCacheLookup {
    private final Path cacheRoot;
    private final TextureManifest manifest;
    private final boolean enabled;

    public ManifestTextureCacheLookup(Path cacheRoot, TextureManifest manifest) {
        this(cacheRoot, manifest, true);
    }

    public ManifestTextureCacheLookup(Path cacheRoot, TextureManifest manifest, boolean enabled) {
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot").toAbsolutePath().normalize();
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.enabled = enabled;
    }

    @Override
    public Lookup lookup(String logicalPath) {
        if (!enabled) {
            return Lookup.failure(Status.DISABLED, "Prepared texture cache is disabled");
        }

        TextureManifest.Entry entry;
        try {
            entry = manifest.entry(logicalPath).orElse(null);
        } catch (RuntimeException error) {
            return Lookup.failure(Status.ERROR, "Invalid logical path: " + message(error));
        }
        if (entry == null) {
            return Lookup.miss("No prepared texture entry for the logical path");
        }
        if (entry.transformation() != PreparedTexture.Transformation.IDENTITY) {
            return Lookup.failure(
                    Status.UNSUPPORTED,
                    "Runtime cache lookup does not support transformation " + entry.transformation());
        }

        Path blob = cacheRoot.resolve(entry.blobRelativePath()).normalize();
        if (!blob.startsWith(cacheRoot)) {
            return Lookup.failure(Status.ERROR, "Manifest blob path escapes the cache root");
        }
        if (!Files.isRegularFile(blob)) {
            return Lookup.miss("Prepared texture blob is missing: " + blob);
        }

        PreparedTexture texture;
        try {
            texture = PreparedTextureIO.read(blob);
        } catch (IOException error) {
            return Lookup.failure(Status.CORRUPT, "Prepared texture blob could not be read: " + message(error));
        } catch (RuntimeException error) {
            return Lookup.failure(Status.ERROR, "Prepared texture lookup failed: " + message(error));
        }

        String mismatch = mismatch(entry, texture);
        if (mismatch != null) {
            return Lookup.failure(Status.STALE, mismatch);
        }
        return Lookup.hit(texture);
    }

    private static String mismatch(TextureManifest.Entry entry, PreparedTexture texture) {
        if (!entry.sourceSha256().equals(texture.sourceSha256())) {
            return "Prepared texture source hash differs from the manifest";
        }
        if (entry.transformation() != texture.transformation()) {
            return "Prepared texture transformation differs from the manifest";
        }
        if (entry.width() != texture.uploadWidth() || entry.height() != texture.uploadHeight()) {
            return "Prepared texture dimensions differ from the manifest";
        }
        if (entry.channels() != texture.channels()) {
            return "Prepared texture channel count differs from the manifest";
        }
        if (entry.pixelBytes() != texture.pixelBytes()) {
            return "Prepared texture pixel length differs from the manifest";
        }
        return null;
    }

    private static String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
