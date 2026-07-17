package com.jcraft.jorbis;

/** Repository-owned stand-in used only by the installed-decoder child-JVM test. */
public final class JOrbisException extends Exception {
    public JOrbisException(String message) {
        super(message);
    }
}
