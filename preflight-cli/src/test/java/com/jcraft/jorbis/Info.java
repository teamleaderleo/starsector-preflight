package com.jcraft.jorbis;

/** Repository-owned stand-in used only by the packaged javaagent integration fixture. */
public final class Info {
    public int synthesis_headerin(int value) {
        return value + 44_100;
    }
}
