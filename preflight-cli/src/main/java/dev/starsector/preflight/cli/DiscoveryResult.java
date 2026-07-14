package dev.starsector.preflight.cli;

import java.util.List;

record DiscoveryResult(
        LaunchTarget selected,
        List<LaunchTarget> candidates,
        List<String> diagnostics) {
}
