package com.fs.starfarer;

/** Tiny child-JVM target used to verify packaged javaagent probe behavior. */
public final class SyntheticLauncher {
    private SyntheticLauncher() {
    }

    public static void main(String[] args) {
        System.out.println("synthetic-starsector-launcher");
    }
}