package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextureManifestValidatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesReferencedBlobsAndDetectsCorruption() throws Exception {
        Path cache = temporaryDirectory.resolve("cache");
        Path blob = cache.resolve("blobs/aa/texture.spft");
        PreparedTexture texture = texture();
        PreparedTextureIO.write(blob, texture);
        TextureManifest manifest = new TextureManifest(
                "profile",
                Map.of("graphics/example.png", entry(texture, "blobs/aa/texture.spft")));

        TextureManifestValidator.Result valid = TextureManifestValidator.validate(cache, manifest);
        assertTrue(valid.valid());
        assertEquals(1, valid.checkedEntries());

        byte[] corrupt = Files.readAllBytes(blob);
        corrupt[corrupt.length / 2] ^= 0x11;
        Files.write(blob, corrupt);
        TextureManifestValidator.Result invalid = TextureManifestValidator.validate(cache, manifest);
        assertFalse(invalid.valid());
        assertEquals(TextureManifestValidator.Kind.BLOB_INVALID, invalid.problems().get(0).kind());
    }

    @Test
    void detectsMissingBlobsAndMetadataMismatch() throws Exception {
        Path cache = temporaryDirectory.resolve("cache");
        PreparedTexture texture = texture();
        TextureManifest missing = new TextureManifest(
                "profile",
                Map.of("graphics/example.png", entry(texture, "blobs/missing.spft")));
        assertEquals(
                TextureManifestValidator.Kind.BLOB_MISSING,
                TextureManifestValidator.validate(cache, missing).problems().get(0).kind());

        Path blob = cache.resolve("blobs/aa/texture.spft");
        PreparedTextureIO.write(blob, texture);
        TextureManifest mismatch = new TextureManifest(
                "profile",
                Map.of("graphics/example.png", new TextureManifest.Entry(
                        texture.sourceSha256(),
                        texture.transformation(),
                        "blobs/aa/texture.spft",
                        1,
                        2,
                        4,
                        8)));
        assertEquals(
                TextureManifestValidator.Kind.METADATA_MISMATCH,
                TextureManifestValidator.validate(cache, mismatch).problems().get(0).kind());
    }

    private static PreparedTexture texture() {
        return new PreparedTexture(
                "aa".repeat(32),
                PreparedTexture.Transformation.IDENTITY,
                2,
                1,
                2,
                1,
                4,
                0,
                0,
                0,
                new byte[8]);
    }

    private static TextureManifest.Entry entry(PreparedTexture texture, String path) {
        return new TextureManifest.Entry(
                texture.sourceSha256(),
                texture.transformation(),
                path,
                texture.uploadWidth(),
                texture.uploadHeight(),
                texture.channels(),
                texture.pixelBytes());
    }
}
