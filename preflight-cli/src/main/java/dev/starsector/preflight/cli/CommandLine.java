package dev.starsector.preflight.cli;

import dev.starsector.preflight.agent.AdapterMode;
import dev.starsector.preflight.agent.TextureAdapterMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

record CommandLine(
        Path game,
        Path launcher,
        Path traceDirectory,
        boolean dryRun,
        boolean summarize,
        boolean scan,
        AdapterMode adapterMode,
        Path adapterTargets,
        Path textureCacheDirectory,
        Path textureManifest,
        Path textureIndex,
        TextureAdapterMode textureAdapterMode,
        List<String> forwardedArgs) {
    static CommandLine parse(String[] args, int offset) {
        Path game = null;
        Path launcher = null;
        Path traceDirectory = null;
        boolean dryRun = false;
        boolean summarize = true;
        boolean scan = true;
        AdapterMode adapterMode = AdapterMode.OFF;
        boolean adapterModeSpecified = false;
        Path adapterTargets = null;
        Path textureCacheDirectory = null;
        Path textureManifest = null;
        Path textureIndex = null;
        TextureAdapterMode textureAdapterMode = TextureAdapterMode.COMPATIBILITY;
        boolean textureModeSpecified = false;
        List<String> forwarded = new ArrayList<>();
        for (int i = offset; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--game" -> game = Path.of(requireValue(args, ++i, arg));
                case "--launcher" -> launcher = Path.of(requireValue(args, ++i, arg));
                case "--trace-dir" -> traceDirectory = Path.of(requireValue(args, ++i, arg));
                case "--dry-run" -> dryRun = true;
                case "--no-summary" -> summarize = false;
                case "--no-scan" -> scan = false;
                case "--adapter-probe" -> {
                    adapterMode = chooseAdapterMode(adapterMode, adapterModeSpecified, AdapterMode.PROBE);
                    adapterModeSpecified = true;
                }
                case "--adapter" -> {
                    adapterMode = chooseAdapterMode(adapterMode, adapterModeSpecified, AdapterMode.ENABLED);
                    adapterModeSpecified = true;
                }
                case "--no-adapter" -> {
                    adapterMode = chooseAdapterMode(adapterMode, adapterModeSpecified, AdapterMode.OFF);
                    adapterModeSpecified = true;
                }
                case "--adapter-targets" -> adapterTargets = Path.of(requireValue(args, ++i, arg));
                case "--texture-cache-dir" -> textureCacheDirectory = Path.of(requireValue(args, ++i, arg));
                case "--texture-manifest" -> textureManifest = Path.of(requireValue(args, ++i, arg));
                case "--texture-index" -> textureIndex = Path.of(requireValue(args, ++i, arg));
                case "--texture-mode" -> {
                    textureAdapterMode = TextureAdapterMode.valueOf(
                            requireValue(args, ++i, arg).trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
                    textureModeSpecified = true;
                }
                case "--" -> {
                    for (int j = i + 1; j < args.length; j++) {
                        forwarded.add(args[j]);
                    }
                    i = args.length;
                }
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (adapterTargets != null && adapterMode == AdapterMode.OFF) {
            throw new IllegalArgumentException("--adapter-targets requires --adapter-probe or --adapter");
        }
        int textureOptions = (textureCacheDirectory == null ? 0 : 1)
                + (textureManifest == null ? 0 : 1)
                + (textureIndex == null ? 0 : 1);
        if (textureOptions != 0 && textureOptions != 3) {
            throw new IllegalArgumentException(
                    "--texture-cache-dir, --texture-manifest, and --texture-index must be supplied together");
        }
        if (textureOptions == 3 && adapterMode != AdapterMode.ENABLED) {
            throw new IllegalArgumentException("Texture adapter options require --adapter");
        }
        if (textureModeSpecified && adapterMode != AdapterMode.ENABLED) {
            throw new IllegalArgumentException("--texture-mode requires --adapter");
        }
        if (textureModeSpecified && textureOptions != 3) {
            throw new IllegalArgumentException("--texture-mode requires the complete texture cache context");
        }
        return new CommandLine(
                game,
                launcher,
                traceDirectory,
                dryRun,
                summarize,
                scan,
                adapterMode,
                adapterTargets,
                textureCacheDirectory,
                textureManifest,
                textureIndex,
                textureAdapterMode,
                List.copyOf(forwarded));
    }

    private static AdapterMode chooseAdapterMode(
            AdapterMode current,
            boolean alreadySpecified,
            AdapterMode requested) {
        if (alreadySpecified && current != requested) {
            throw new IllegalArgumentException("Conflicting adapter mode options");
        }
        return requested;
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }
}
