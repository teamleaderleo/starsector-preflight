package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.PreparedTexture;
import dev.starsector.preflight.core.PreparedTextureIO;
import dev.starsector.preflight.core.ResourceIndex;
import dev.starsector.preflight.core.ResourceIndexIO;
import dev.starsector.preflight.core.TextureManifest;
import dev.starsector.preflight.core.TextureManifestIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreparedImageTransformationTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetBridge() {
        PreparedImageBridge.resetForTests();
    }

    @Test
    void verifiedHitReturnsPreparedImageAndMissExecutesOriginalMethod() throws Exception {
        CacheFixture cache = cacheFixture();
        PreparedImageBridge.configure(cache.cache(), cache.manifest(), cache.index());
        byte[] original = classBytes(SyntheticTextureLoader.class);
        ClassSignature signature = ClassSignature.parse(original);
        AdapterTarget target = target(signature);

        byte[] transformed = PreparedImageTransformation.transform(target, signature, original);

        assertNotNull(transformed);
        ClassSignature rewritten = ClassSignature.parse(transformed);
        assertTrue(rewritten.hasMethod(
                PreparedImageTransformation.METHOD_NAME,
                PreparedImageTransformation.METHOD_DESCRIPTOR));
        assertTrue(rewritten.hasMethod(
                PreparedImageTransformation.METHOD_NAME + "$preflight$original",
                PreparedImageTransformation.METHOD_DESCRIPTOR));

        Object loader = loadTransformed(SyntheticTextureLoader.class, transformed);
        Method load = loader.getClass().getMethod("load", String.class);
        BufferedImage hit = (BufferedImage) load.invoke(loader, cache.logicalPath());
        BufferedImage miss = (BufferedImage) load.invoke(loader, "graphics/missing.png");

        assertEquals(0xff336699, hit.getRGB(0, 0));
        assertEquals(SyntheticTextureLoader.ORIGINAL_PIXEL, miss.getRGB(0, 0));
        assertEquals(1, PreparedImageBridge.snapshot().hits());
        assertEquals(1, PreparedImageBridge.snapshot().fallbacks());
    }

    @Test
    void originalExceptionsRemainVisibleOnFallback() throws Exception {
        byte[] original = classBytes(SyntheticTextureLoader.class);
        ClassSignature signature = ClassSignature.parse(original);
        byte[] transformed = PreparedImageTransformation.transform(target(signature), signature, original);
        Object loader = loadTransformed(SyntheticTextureLoader.class, transformed);
        Method load = loader.getClass().getMethod("load", String.class);

        InvocationTargetException thrown = assertThrows(
                InvocationTargetException.class,
                () -> load.invoke(loader, "explode"));

        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertEquals("original failure", thrown.getCause().getMessage());
    }

    @Test
    void structurallyDifferentTargetIsNotRewritten() throws Exception {
        byte[] original = classBytes(SyntheticTextureLoader.class);
        ClassSignature signature = ClassSignature.parse(original);
        AdapterTarget incompatible = new AdapterTarget(
                "incompatible",
                signature.internalName(),
                signature.sha256(),
                PreparedImageTransformation.PLAN_ID,
                List.of(new AdapterTarget.RequiredMethod("other", "()V")),
                "STARSECTOR_CORE",
                "core.jar",
                "",
                "loader/Class",
                "app");

        assertEquals(null, PreparedImageTransformation.transform(incompatible, signature, original));
    }

    private CacheFixture cacheFixture() throws Exception {
        Path root = temporaryDirectory.resolve("game/core");
        String logicalPath = "graphics/hit.png";
        Path source = root.resolve(logicalPath);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "source");
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
        String fingerprint = "profile";
        ResourceIndex index = new ResourceIndex(
                fingerprint,
                List.of(new ResourceIndex.Root("core", root, true)),
                Map.of(logicalPath, List.of(new ResourceIndex.Provider(
                        0,
                        logicalPath,
                        attributes.size(),
                        Math.max(0, attributes.lastModifiedTime().toMillis())))));
        PreparedTexture texture = new PreparedTexture(
                "b".repeat(64),
                PreparedTexture.Transformation.IDENTITY,
                1,
                1,
                1,
                1,
                3,
                0,
                0,
                0,
                new byte[] {0x33, 0x66, (byte) 0x99});
        Path cache = temporaryDirectory.resolve("cache");
        PreparedTextureIO.write(cache.resolve("blobs/hit.spft"), texture);
        TextureManifest manifest = new TextureManifest(fingerprint, Map.of(
                logicalPath,
                new TextureManifest.Entry(
                        texture.sourceSha256(),
                        texture.transformation(),
                        "blobs/hit.spft",
                        1,
                        1,
                        3,
                        3)));
        Path manifestPath = temporaryDirectory.resolve("manifest.spfm");
        Path indexPath = temporaryDirectory.resolve("index.spfi");
        TextureManifestIO.write(manifestPath, manifest);
        ResourceIndexIO.write(indexPath, index);
        return new CacheFixture(cache, manifestPath, indexPath, logicalPath);
    }

    private static AdapterTarget target(ClassSignature signature) {
        return new AdapterTarget(
                "synthetic-prepared-image",
                signature.internalName(),
                signature.sha256(),
                PreparedImageTransformation.PLAN_ID,
                List.of(new AdapterTarget.RequiredMethod(
                        PreparedImageTransformation.METHOD_NAME,
                        PreparedImageTransformation.METHOD_DESCRIPTOR)),
                "STARSECTOR_CORE",
                "core.jar",
                "",
                "loader/Class",
                "app");
    }

    private static Object loadTransformed(Class<?> sourceType, byte[] bytes) throws Exception {
        String binaryName = sourceType.getName();
        ClassLoader loader = new ClassLoader(sourceType.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null && name.equals(binaryName)) {
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (loaded == null) {
                        loaded = super.loadClass(name, false);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
        };
        return loader.loadClass(binaryName).getConstructor().newInstance();
    }

    private static byte[] classBytes(Class<?> type) throws Exception {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing test class " + resource);
            }
            return input.readAllBytes();
        }
    }

    public static final class SyntheticTextureLoader {
        static final int ORIGINAL_PIXEL = 0xffaabbcc;

        public BufferedImage load(String path) {
            return Ô00000(path);
        }

        private BufferedImage Ô00000(String path) {
            if (path.equals("explode")) {
                throw new IllegalStateException("original failure");
            }
            BufferedImage result = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            result.setRGB(0, 0, ORIGINAL_PIXEL);
            return result;
        }
    }

    private record CacheFixture(Path cache, Path manifest, Path index, String logicalPath) {
    }
}
