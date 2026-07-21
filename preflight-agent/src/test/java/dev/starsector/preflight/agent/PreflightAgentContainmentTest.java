package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PreflightAgentContainmentTest {
    @Test
    void containsOrdinaryAdapterStartupFailures() {
        Object result = PreflightAgent.contain("Synthetic adapter", () -> {
            throw new LinkageError("synthetic linkage failure");
        });

        assertNull(result);
    }

    @Test
    void propagatesVirtualMachineErrors() {
        assertThrows(SyntheticVmError.class, () -> PreflightAgent.contain("Synthetic adapter", () -> {
            throw new SyntheticVmError();
        }));
    }

    @Test
    void propagatesThreadDeath() {
        assertThrows(ThreadDeath.class, () -> PreflightAgent.contain("Synthetic adapter", () -> {
            throw new ThreadDeath();
        }));
    }

    private static final class SyntheticVmError extends VirtualMachineError {
    }
}
