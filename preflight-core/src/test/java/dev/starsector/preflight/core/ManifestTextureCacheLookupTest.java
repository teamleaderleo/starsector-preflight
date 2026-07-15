package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestTextureCacheLookupTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsVerifiedBlobAsHit() throws Exception {
        PreparedTexture texture = texture("aa", PreparedTexture.Transformation.IDENTITY);
        Path relative = Path.of("blobs", "aa", "texture.spft");
        PreparedTextureIO.write(temporaryDirectory.resolve(relative), texture);
        TextureManifest manifest = manifest(texture, relative);

        TextureCacheLookup.Lookup lookup = new ManifestTextureCacheLookup(temporaryDirectory, manifest)
                .lookup("GRAPHICS\\SHIPS//EXAMPLE.PNG");

        assertEquals(TextureCacheLookup.Status.HIT, lookup.status());
        assertSame(texture.getClass(), lookup.texture().getClass());
        assertEquals(texture, lookup.texture());
    }

    @Test
    void classifiesMissingCorruptStaleUnsupportedAndDisabledEntries() throws Exception {
        PreparedTexture texture = texture("bb", PreparedTexture.Transformation.IDENTITY);
        Path relative = Path.of("blobs", "bb", "texture.spft");
        TextureManifest manifest = manifest(texture, relative);
        ManifestTextureCacheLookup lookup = new ManifestTextureCacheLookup(temporaryDirectory, manifest);

        assertEquals(TextureCacheLookup.Status.MISS, lookup.lookup("graphics/ships/example.png").status());
        assertEquals(TextureCacheLookup.Status.MISS, lookup.lookup("graphics/unknown.png").status());

        Path blob = temporaryDirectory.resolve(relative);
        Files.createDirectories(blob.getParent());
        Files.write(blob, new byte[] {1, 2, 3});
        assertEquals(TextureCacheLookup.Status.CORRUPT, lookup.lookup("graphics/ships/example.png").status());

        PreparedTextureIO.write(blob, texture);
        TextureManifest staleManifest = new TextureManifest("profile", Map.of(
                "graphics/ships/example.png",
                new TextureManifest.Entry(
                        "cc".repeat(32),
                        PreparedTexture.Transformation.IDENTITY,
                        relative.toString(),
                        1,
                        1,
                        4,
                        4)));
        TextureCacheLookup.Lookup stale = new ManifestTextureCacheLookup(temporaryDirectory, staleManifest)
                .lookup("graphics/ships/example.png");
        assertEquals(TextureCacheLookup.Status.STALE, stale.status());
        assertTrue(stale.detail().contains("hash"));

        TextureManifest unsupportedManifest = new TextureManifest("profile", Map.of(
                "graphics/ships/example.png",
                new TextureManifest.Entry(
                        texture.sourceSha256(),
                        PreparedTexture.Transformation.ALPHA_ADDER,
                        relative.toString(),
                        1,
                        1,
                        4,
                        4)));
        assertEquals(
                TextureCacheLookup.Status.UNSUPPORTED,
                new ManifestTextureCacheLookup(temporaryDirectory, unsupportedManifest)
                        .lookup("graphics/ships/example.png")
                        .status());
        assertEquals(
                TextureCacheLookup.Status.DISABLED,
                new ManifestTextureCacheLookup(temporaryDirectory, manifest, false)
                        .lookup("graphics/ships/example.png")
                        .status());
    }

    @Test
    void invalidLogicalPathIsContainedAsLookupError() {
        PreparedTexture texture = texture("dd", PreparedTexture.Transformation.IDENTITY);
        TextureManifest manifest = manifest(texture, Path.of("blobs", "dd", "texture.spft"));

        TextureCacheLookup.Lookup lookup = new ManifestTextureCacheLookup(temporaryDirectory, manifest)
                .lookup("../outside.png");

        assertEquals(TextureCacheLookup.Status.ERROR, lookup.status());
        assertTrue(lookup.detail().contains("Invalid logical path"));
    }

    private static TextureManifest manifest(PreparedTexture texture, Path relative) {
        return new TextureManifest("profile", Map.of(
                "graphics/ships/example.png",
                new TextureManifest.Entry(
                        texture.sourceSha256(),
                        texture.transformation(),
                        relative.toString(),
                        texture.uploadWidth(),
                        texture.uploadHeight(),
                        texture.channels(),
                        texture.pixelBytes())));
    }

    private static PreparedTexture texture(String bytePair, PreparedTexture.Transformation transformation) {
        return new PreparedTexture(
                bytePair.repeat(32),
                transformation,
                1,
                1,
                1,
                1,
                4,
                PreparedTexture.rgba(1, 2, 3, 255),
                PreparedTexture.rgba(4, 5, 6, 255),
                PreparedTexture.rgba(7, 8, 9, 255),
                new byte[] {10, 20, 30, 40});
    }
}
