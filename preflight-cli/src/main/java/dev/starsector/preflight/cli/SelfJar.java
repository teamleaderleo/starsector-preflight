package dev.starsector.preflight.cli;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class SelfJar {
    private SelfJar() {
    }

    static Path locate() {
        try {
            Path location = Path.of(PreflightCli.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();
            if (!Files.isRegularFile(location)
                    || !location.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                throw new IllegalStateException(
                        "Preflight is running from classes instead of its packaged JAR; run `mvn package` and use preflight-cli/target/preflight.jar");
            }
            return location;
        } catch (URISyntaxException error) {
            throw new IllegalStateException("Could not locate the Preflight JAR", error);
        }
    }
}
