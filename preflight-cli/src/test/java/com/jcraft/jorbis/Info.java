package com.jcraft.jorbis;

/** Repository-owned stand-in used only by packaged child-JVM integration fixtures. */
public final class Info {
    public int channels;
    public int rate;

    public int synthesis_headerin(int value) {
        return value + 44_100;
    }
}
