package dev.starsector.preflight.cli;

import dev.starsector.preflight.core.Hashes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable identity captured by the Preflight wrapper before the child launcher starts. */
record RunIdentity(
        Path preflightJar,
        String preflightJarSha256,
        Map<String, Object> wrapperRuntime) {
    static final String SCOPE = "preflight-wrapper-process";

    RunIdentity {
        preflightJar = preflightJar.toAbsolutePath().normalize();
        if (!preflightJarSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Preflight JAR identity must be a SHA-256 value");
        }
        wrapperRuntime = Map.copyOf(wrapperRuntime);
    }

    static RunIdentity capture(Path jar) throws IOException {
        Path realJar = jar.toAbsolutePath().normalize().toRealPath();
        if (!Files.isRegularFile(realJar)) {
            throw new IOException("Expected the Preflight runtime to be a regular file: " + realJar);
        }
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("javaVersion", property("java.version"));
        runtime.put("javaVendor", property("java.vendor"));
        runtime.put("javaRuntimeName", property("java.runtime.name"));
        runtime.put("javaRuntimeVersion", property("java.runtime.version"));
        runtime.put("javaVmName", property("java.vm.name"));
        runtime.put("javaVmVendor", property("java.vm.vendor"));
        runtime.put("javaVmVersion", property("java.vm.version"));
        runtime.put("osName", property("os.name"));
        runtime.put("osVersion", property("os.version"));
        runtime.put("osArch", property("os.arch"));
        return new RunIdentity(realJar, Hashes.sha256(realJar), runtime);
    }

    String wrapperJavaVersion() {
        return wrapperRuntime.get("javaVersion").toString();
    }

    private static String property(String name) {
        return System.getProperty(name, "");
    }
}
