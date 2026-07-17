package dev.starsector.preflight.core;

/** Explicit benchmark cache and adapter states used for comparable launch scenarios. */
public enum BenchmarkScenarioMode {
    OFF_COLDISH("off-coldish"),
    OFF_WARM("off-warm"),
    ENABLED_BUILD_MISS("enabled-build-miss"),
    ENABLED_WARM_HIT("enabled-warm-hit"),
    ENABLED_CORRUPT_ARTIFACT("enabled-corrupt-artifact"),
    ENABLED_CHANGED_PROFILE("enabled-changed-profile");

    private final String value;

    BenchmarkScenarioMode(String value) {
        this.value = value;
    }

    public static BenchmarkScenarioMode parse(String value) {
        for (BenchmarkScenarioMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown benchmark scenario mode: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
