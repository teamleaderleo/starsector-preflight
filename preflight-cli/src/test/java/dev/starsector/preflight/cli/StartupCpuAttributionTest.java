package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Json;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartupCpuAttributionTest {
    @Test
    void classifiesCacheableDomainsThroughGenericJdkLeafFrames() {
        StartupCpuAttribution attribution = new StartupCpuAttribution();
        StartupCpuAttribution.Frame arrays = frame(
                "jdk/internal/util/ArraysSupport", "newLength", "(III)I");

        attribution.record("audio-worker", List.of(
                arrays,
                frame("java/io/ByteArrayOutputStream", "ensureCapacity", "(I)V"),
                frame("sound/J", "o00000", "(Ljava/io/InputStream;)Lsound/F;")));
        attribution.record("audio-worker", List.of(
                frame("com/jcraft/jorbis/Mdct", "mdct_kernel", "([F[FIIII)[F"),
                frame("sound/void", "return", "()V")));
        attribution.record("script-worker", List.of(
                arrays,
                frame("java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                frame("org/codehaus/janino/JavaSourceClassLoader", "generateBytecodes", "(Ljava/lang/String;)Ljava/util/Map;")));
        attribution.record("texture-worker", List.of(
                frame("java/awt/image/ComponentSampleModel", "getPixel", "(II[ILjava/awt/image/DataBuffer;)[I"),
                frame("com/fs/graphics/TextureLoader", "o00000", "()Ljava/nio/ByteBuffer;")));
        attribution.record("loading-worker", List.of(
                frame("java/util/HashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
                frame("com/fs/starfarer/loading/SpecStore", "load", "()V")));
        attribution.record("rules-worker", List.of(
                frame("com/fs/starfarer/campaign/rules/Rules", "load", "()V")));
        attribution.record("json-worker", List.of(
                frame("org/json/JSONTokener", "nextValue", "()Ljava/lang/Object;")));
        attribution.record("mod-worker", List.of(
                frame("org/magiclib/Example", "load", "()V")));
        attribution.record("jdk-worker", List.of(
                frame("java/util/HashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")));
        attribution.record("missing-worker", List.of());

        String json = Json.object(attribution.toMap());
        assertTrue(json.contains("\"samples\":10"), json);
        assertCategory(json, "AUDIO_DECODE", 2, 20.0);
        assertCategory(json, "JANINO", 1, 10.0);
        assertCategory(json, "TEXTURE_IMAGE", 1, 10.0);
        assertCategory(json, "STARSECTOR_LOADING", 1, 10.0);
        assertCategory(json, "RULES", 1, 10.0);
        assertCategory(json, "JSON", 1, 10.0);
        assertCategory(json, "MOD_OR_LIBRARY", 1, 10.0);
        assertCategory(json, "JDK_ONLY", 1, 10.0);
        assertCategory(json, "NO_STACK", 1, 10.0);
        assertTrue(json.contains("sound/J"), json);
        assertTrue(json.contains("generateBytecodes"), json);
        assertTrue(json.contains("java/awt/image/ComponentSampleModel"), json);
        assertTrue(json.contains("\"threadName\":\"audio-worker\",\"samples\":2"), json);
        assertTrue(json.contains("Execution samples are statistical CPU evidence"), json);
    }

    @Test
    void ranksLeafAttributedMethodsAndStacksDeterministically() {
        StartupCpuAttribution attribution = new StartupCpuAttribution();
        List<StartupCpuAttribution.Frame> audio = List.of(
                frame("jdk/internal/util/ArraysSupport", "newLength", "(III)I"),
                frame("sound/J", "o00000", "(Ljava/io/InputStream;)Lsound/F;"));
        List<StartupCpuAttribution.Frame> janino = List.of(
                frame("java/util/HashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"),
                frame("org/codehaus/janino/UnitCompiler", "compileUnit", "()V"));
        for (int i = 0; i < 4; i++) attribution.record("audio", audio);
        for (int i = 0; i < 3; i++) attribution.record("janino", janino);

        String json = Json.object(attribution.toMap());
        int arrays = json.indexOf("jdk/internal/util/ArraysSupport");
        int hashMap = json.indexOf("java/util/HashMap");
        int sound = json.indexOf("sound/J");
        int compiler = json.indexOf("org/codehaus/janino/UnitCompiler");
        assertTrue(arrays >= 0 && arrays < hashMap, json);
        assertTrue(sound >= 0 && sound < compiler, json);
        assertTrue(json.contains("\"category\":\"AUDIO_DECODE\",\"samples\":4"), json);
        assertTrue(json.contains("\"category\":\"JANINO\",\"samples\":3"), json);
    }

    private static StartupCpuAttribution.Frame frame(String className, String method, String descriptor) {
        return new StartupCpuAttribution.Frame(className, method, descriptor);
    }

    private static void assertCategory(String json, String category, long samples, double percent) {
        String expected = "\"" + category + "\":{\"samples\":" + samples
                + ",\"percent\":" + percent + "}";
        assertTrue(json.contains(expected), json);
    }
}
