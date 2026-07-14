package dev.starsector.preflight.cli;

import java.nio.file.Path;
import java.util.List;

record LaunchTarget(
        Path installRoot,
        Path launcher,
        Path workingDirectory,
        List<String> command,
        String kind,
        int score,
        String source) {
}
