package dev.starsector.preflight.cli;

import dev.starsector.preflight.agent.AdapterMode;
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
        Path adapterCacheDirectory,
        Path adapterTextureManifest,
        Path adapterResourceIndex,
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
        Path adapterCacheDirectory = null;
        Path adapterTextureManifest = null;
        Path adapterResourceIndex = null;
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
                case "--adapter-cache-dir" -> adapterCacheDirectory = Path.of(requireValue(args, ++i, arg));
                case "--adapter-texture-manifest" ->
                        adapterTextureManifest = Path.of(requireValue(args, ++i, arg));
                case "--adapter-resource-index" -> adapterResourceIndex = Path.of(requireValue(args, ++i, arg));
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
        int textureContextValues = (adapterCacheDirectory == null ? 0 : 1)
                + (adapterTextureManifest == null ? 0 : 1)
                + (adapterResourceIndex == null ? 0 : 1);
        if (textureContextValues != 0 && textureContextValues != 3) {
            throw new IllegalArgumentException("Prepared image adapter requires --adapter-cache-dir, "
                    + "--adapter-texture-manifest, and --adapter-resource-index together");
        }
        if (textureContextValues == 3 && adapterMode != AdapterMode.ENABLED) {
            throw new IllegalArgumentException("Prepared image cache options require --adapter");
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
                adapterCacheDirectory,
                adapterTextureManifest,
                adapterResourceIndex,
                List.copyOf(forwarded));
    }

    boolean hasAdapterTextureContext() {
        return adapterCacheDirectory != null;
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
