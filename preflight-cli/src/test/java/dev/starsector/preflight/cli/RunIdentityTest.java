package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Hashes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunIdentityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void capturesTheExactFileHashAndDeterministicRuntimeFields() throws Exception {
        Path jar = temporaryDirectory.resolve("preflight.jar");
        Files.write(jar, new byte[] {1, 2, 3, 4});

        RunIdentity identity = RunIdentity.capture(jar);

        assertEquals(jar.toRealPath(), identity.preflightJar());
        assertEquals(Hashes.sha256(jar), identity.preflightJarSha256());
        assertEquals(System.getProperty("java.version"), identity.wrapperJavaVersion());
        assertEquals(List.of(
                "javaVersion",
                "javaVendor",
                "javaRuntimeName",
                "javaRuntimeVersion",
                "javaVmName",
                "javaVmVendor",
                "javaVmVersion",
                "osName",
                "osVersion",
                "osArch"), new ArrayList<>(identity.wrapperRuntime().keySet()));
        assertTrue(identity.wrapperRuntime().values().stream().allMatch(String.class::isInstance));
    }
}
