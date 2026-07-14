package dev.starsector.preflight.core;

import java.io.IOException;
import java.nio.file.Path;

/** Source invalidation checks for prepared texture blobs. */
public final class PreparedTextureValidator {
    private PreparedTextureValidator() {
    }

    public static Result validateSource(Path source, PreparedTexture texture) throws IOException {
        String actual = Hashes.sha256(source);
        return new Result(texture.sourceSha256().equals(actual), texture.sourceSha256(), actual);
    }

    public record Result(boolean valid, String expectedSha256, String actualSha256) {
    }
}
