package org.newdawn.slick.openal;

import java.io.IOException;
import java.io.InputStream;

/** Synthetic packaged-agent identity only; not a decoder implementation. */
public final class OggDecoder {
    public byte[] decode(InputStream input) throws IOException {
        return input.readNBytes(16);
    }

    public void reset() {
    }
}
