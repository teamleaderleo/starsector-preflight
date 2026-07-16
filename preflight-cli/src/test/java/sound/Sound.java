package sound;

import java.io.IOException;
import java.io.InputStream;

/** Repository-owned higher-level sound wrapper fixture. */
public final class Sound {
    public D load(InputStream input) throws IOException {
        F decoded = new J().o00000(input);
        new ooOO().consume(decoded);
        return new D(decoded);
    }
}
