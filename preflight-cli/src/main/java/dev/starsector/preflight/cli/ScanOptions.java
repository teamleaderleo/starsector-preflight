package dev.starsector.preflight.cli;

import java.nio.file.Path;

record ScanOptions(Path game, Path launcher, Path output) {
    static ScanOptions parse(String[] args, int offset) {
        Path game = null;
        Path launcher = null;
        Path output = null;
        for (int i = offset; i < args.length; i++) {
            switch (args[i]) {
                case "--game" -> game = Path.of(requireValue(args, ++i, "--game"));
                case "--launcher" -> launcher = Path.of(requireValue(args, ++i, "--launcher"));
                case "--json" -> output = Path.of(requireValue(args, ++i, "--json"));
                default -> throw new IllegalArgumentException("Unknown scan option: " + args[i]);
            }
        }
        return new ScanOptions(game, launcher, output);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }
}
