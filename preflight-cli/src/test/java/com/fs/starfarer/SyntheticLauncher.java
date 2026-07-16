package com.fs.starfarer;

/** Tiny child-JVM target used to verify packaged javaagent probe behavior. */
public final class SyntheticLauncher {
    private SyntheticLauncher() {
    }

    public static void main(String[] args) throws Exception {
        new org.newdawn.slick.openal.OggDecoder().reset();
        int sampleRate = new com.fs.starfarer.loading.A().load();
        System.out.println("synthetic-starsector-launcher:" + sampleRate);
    }
}
