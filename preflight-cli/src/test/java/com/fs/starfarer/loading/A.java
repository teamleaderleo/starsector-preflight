package com.fs.starfarer.loading;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import sound.D;
import sound.Sound;

/** Repository-owned Starsector-loading consumer fixture. */
public final class A {
    public int load() throws IOException {
        D decoded = new Sound().load(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
        return decoded.sampleRate();
    }
}
