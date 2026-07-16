package com.jcraft.jorbis;

/** Repository-owned stand-in used only to exercise exact call-edge collection. */
public final class Info {
    public int synthesis_headerin(int value) {
        return value + 22_050;
    }
}
