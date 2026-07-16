package sound;

import com.jcraft.jorbis.Info;
import java.io.IOException;
import java.io.InputStream;

/** Repository-owned exact primary-seam fixture for packaged agent testing. */
public final class J {
    private F last;

    public F o00000(InputStream input) throws IOException {
        try {
            byte[] encoded = input.readAllBytes();
            int literalLength = "packaged-repository-owned-sound-contract-literal".length();
            int sampleRate = new Info().synthesis_headerin(normalize(encoded.length + literalLength));
            F decoded = new F(encoded, sampleRate);
            last = decoded;
            return decoded;
        } catch (IOException error) {
            throw error;
        }
    }

    private int normalize(int value) {
        return Math.max(1, value);
    }

    public F last() {
        return last;
    }
}
