package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.codehaus.janino.JavaSourceClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupCodeAttributionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void attributesJaninoDefinitionsAndCompilationCategoriesDeterministically() {
        StartupCodeAttribution attribution = new StartupCodeAttribution();
        List<StartupCodeAttribution.Frame> frames = List.of(
                new StartupCodeAttribution.Frame(
                        "org/codehaus/janino/JavaSourceClassLoader",
                        "generateBytecodes",
                        "(Ljava/lang/String;)Ljava/util/Map;",
                        1),
                new StartupCodeAttribution.Frame(
                        "org/codehaus/janino/UnitCompiler",
                        "compileUnit",
                        "(ZZZLorg/codehaus/janino/UnitCompiler$ClassFileConsumer;)V",
                        2),
                new StartupCodeAttribution.Frame(
                        "com/fs/starfarer/loading/ScriptLoader",
                        "load",
                        "()V",
                        4));
        attribution.recordClassDefine(
                "data.scripts.GeneratedOne",
                "org.codehaus.janino.JavaSourceClassLoader",
                "janino-loader",
                "C:\\Starsector\\mods\\Example\\data\\scripts\\GeneratedOne.java",
                frames);
        attribution.recordClassDefine(
                "data.scripts.GeneratedTwo",
                "org/codehaus/janino/JavaSourceClassLoader",
                "janino-loader",
                "C:/Starsector/mods/Example/data/scripts/GeneratedTwo.java",
                frames);

        attribution.recordCompilation(
                "org/codehaus/janino/UnitCompiler", "compileUnit", "()V", Duration.ofMillis(8).toNanos());
        attribution.recordCompilation(
                "org/codehaus/janino/Parser", "parseCompilationUnit", "()V", Duration.ofMillis(3).toNanos());
        attribution.recordCompilation(
                "com/fs/starfarer/loading/ScriptLoader", "load", "()V", Duration.ofMillis(2).toNanos());
        attribution.recordCompilation(
                "data/scripts/GeneratedOne", "run", "()V", Duration.ofMillis(1).toNanos());

        String json = Json.object(attribution.toMap());
        assertTrue(json.contains("\"janinoEvents\":2"), json);
        assertTrue(json.contains("\"janinoUniqueClasses\":2"), json);
        assertTrue(json.contains("data/scripts/GeneratedOne"), json);
        assertTrue(json.contains("C:/Starsector/mods/Example/data/scripts/GeneratedOne.java"), json);
        assertTrue(json.contains("org/codehaus/janino/JavaSourceClassLoader"), json);
        assertTrue(json.contains("generateBytecodes"), json);
        assertTrue(json.contains("\"JANINO\":{\"events\":2,\"durationMs\":11.0}"), json);
        assertTrue(json.contains("\"STARSECTOR\":{\"events\":1,\"durationMs\":2.0}"), json);
        assertTrue(json.contains("\"MOD_OR_LIBRARY\":{\"events\":1,\"durationMs\":1.0}"), json);
        assertTrue(json.indexOf("UnitCompiler") < json.indexOf("Parser"), json);
    }

    @Test
    void realJfrClassDefineRetainsCustomJaninoLoaderIdentity() throws Exception {
        Path recordingPath = temporaryDirectory.resolve("class-define.jfr");
        byte[] bytecode = classBytes(SyntheticGeneratedClass.class);
        JavaSourceClassLoader loader = new JavaSourceClassLoader(getClass().getClassLoader());

        try (Recording recording = new Recording()) {
            recording.enable("jdk.ClassDefine").withStackTrace();
            recording.start();
            Class<?> generated = loader.defineGenerated(SyntheticGeneratedClass.class.getName(), bytecode);
            assertEquals(loader, generated.getClassLoader());
            recording.stop();
            recording.dump(recordingPath);
        }

        StartupCodeAttribution attribution = new StartupCodeAttribution();
        try (RecordingFile recording = new RecordingFile(recordingPath)) {
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                if ("jdk.ClassDefine".equals(event.getEventType().getName())) {
                    attribution.recordClassDefine(event);
                }
            }
        }

        String json = Json.object(attribution.toMap());
        assertTrue(json.contains("org/codehaus/janino/JavaSourceClassLoader"), json);
        assertTrue(json.contains("synthetic-janino"), json);
        assertTrue(json.contains("dev/starsector/preflight/cli/SyntheticGeneratedClass"), json);
        assertTrue(json.contains("\"janinoEvents\":1"), json);
        assertTrue(json.contains("\"metadataFailures\":0"), json);
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing test class resource " + resource);
            return input.readAllBytes();
        }
    }
}
