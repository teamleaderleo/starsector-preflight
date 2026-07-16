package dev.starsector.preflight.synthetic;

import dev.starsector.preflight.core.Json;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** One isolated provider-index pass over a deterministic extended profile. */
public final class SyntheticExtendedIndexWorker {
    private SyntheticExtendedIndexWorker() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println(
                    "Usage: SyntheticExtendedIndexWorker <profile-root> <cache-root> <report.json>");
            System.exit(2);
        }
        try {
            run(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void run(Path profileRoot, Path cacheRoot, Path reportPath) throws IOException {
        SyntheticExtendedProfile.Manifest manifest = SyntheticExtendedProfile.readManifest(profileRoot);
        SyntheticExtendedProfile.Fingerprint fingerprint = SyntheticExtendedProfile.fingerprint(profileRoot);
        if (fingerprint.files() != manifest.physicalFiles()
                || !fingerprint.sha256().equals(manifest.fingerprintSha256())) {
            throw new IOException("Extended profile manifest is stale or belongs to another profile");
        }

        Path indexPath = SyntheticExtendedResourceIndex.cachePath(
                cacheRoot,
                fingerprint.sha256());
        SyntheticExtendedResourceIndex.Lookup lookup = SyntheticExtendedResourceIndex.lookup(
                indexPath,
                profileRoot,
                fingerprint.sha256());

        int hits = 0;
        int misses = 0;
        int corruptFallbacks = 0;
        int readErrors = 0;
        int writeErrors = 0;
        int writes = 0;
        long jarScans = 0;
        long looseVisits = 0;
        long physicalFilesVisited = 0;
        long jarEntriesVisited = 0;
        long bytesHashed = 0;

        SyntheticExtendedResourceIndex index;
        if (lookup.status() == SyntheticExtendedResourceIndex.Status.HIT) {
            hits = 1;
            index = lookup.index();
        } else {
            switch (lookup.status()) {
                case MISS -> misses = 1;
                case CORRUPT -> corruptFallbacks = 1;
                case ERROR -> readErrors = 1;
                case HIT -> throw new AssertionError("Handled above");
            }
            SyntheticExtendedResourceIndex.Build build = SyntheticExtendedResourceIndex.build(
                    profileRoot,
                    fingerprint.sha256());
            index = build.index();
            jarScans = build.jarScans();
            looseVisits = build.looseVisits();
            physicalFilesVisited = build.physicalFilesVisited();
            jarEntriesVisited = build.jarEntriesVisited();
            bytesHashed = build.bytesHashed();
            try {
                index.write(indexPath);
                writes = 1;
            } catch (IOException | RuntimeException error) {
                writeErrors = 1;
            }
        }

        MessageDigest outputDigest = sha256Digest();
        long providerBytesRead = 0;
        for (Map.Entry<String, SyntheticExtendedResourceIndex.Provider> entry
                : index.providers().entrySet()) {
            byte[] bytes = index.readBytes(entry.getKey());
            providerBytesRead = Math.addExact(providerBytesRead, bytes.length);
            updateLengthPrefixed(outputDigest, entry.getKey().getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(outputDigest, bytes);
        }

        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("format", "starsector-preflight-synthetic-extended-index-v2");
        report.put("processId", ProcessHandle.current().pid());
        report.put("scale", manifest.scale());
        report.put("profileFilesValidated", fingerprint.files());
        report.put("profileBytesValidated", fingerprint.bytes());
        report.put("profileFingerprintSha256", fingerprint.sha256());
        report.put("indexLookupStatus", lookup.status());
        report.put("indexHits", hits);
        report.put("indexMisses", misses);
        report.put("indexCorruptFallbacks", corruptFallbacks);
        report.put("indexReadErrors", readErrors);
        report.put("indexWriteErrors", writeErrors);
        report.put("indexWrites", writes);
        report.put("jarScans", jarScans);
        report.put("looseVisits", looseVisits);
        report.put("physicalFilesVisited", physicalFilesVisited);
        report.put("jarEntriesVisited", jarEntriesVisited);
        report.put("bytesHashed", bytesHashed);
        report.put("providerCount", index.providerCount());
        report.put("collidedPaths", index.collidedPaths());
        report.put("collisionEvents", index.collisionEvents());
        report.put("providerBytesRead", providerBytesRead);
        report.put("providerDigestSha256", index.providerDigest());
        report.put(
                "providerOutputSha256",
                HexFormat.of().formatHex(outputDigest.digest()));
        report.put("lookupDetail", lookup.detail());

        Path absoluteReport = reportPath.toAbsolutePath().normalize();
        Path parent = absoluteReport.getParent();
        if (parent == null) throw new IOException("Report path has no parent");
        Files.createDirectories(parent);
        Files.writeString(
                absoluteReport,
                Json.object(report) + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    private static void updateLengthPrefixed(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
