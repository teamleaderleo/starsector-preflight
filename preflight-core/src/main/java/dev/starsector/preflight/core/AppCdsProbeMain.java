package dev.starsector.preflight.core;

/** Tiny application class set used only for AppCDS capability probing. */
public final class AppCdsProbeMain {
    public static final String MARKER = "starsector-preflight-appcds-probe-v1";

    private AppCdsProbeMain() {
    }

    public static void main(String[] args) {
        System.out.println(MARKER);
        System.out.println(ProbePayload.VALUE);
    }

    static final class ProbePayload {
        private static final String VALUE = Integer.toHexString(AppCdsProbeMain.class.getName().hashCode());

        private ProbePayload() {
        }
    }
}
