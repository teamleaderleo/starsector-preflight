package dev.starsector.preflight.synthetic;

import dev.starsector.preflight.core.GeneratedBytecodeCache;
import dev.starsector.preflight.core.GeneratedBytecodeCacheWrapper;
import dev.starsector.preflight.core.GeneratedBytecodeContext;
import dev.starsector.preflight.core.Hashes;
import dev.starsector.preflight.core.Json;
import dev.starsector.preflight.core.PreparedAudio;
import dev.starsector.preflight.core.PreparedAudioCache;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Separate-process extended workload using the production SPAU and SPJB cache implementations. */
public final class SyntheticProductionCacheWorker {
    private static final int MAX_IMPLEMENTATION_BYTES = 1024 * 1024;
    private static final int MAX_CLASSPATH_BYTES = 1024 * 1024;
    private static final long MAX_JAVA_EXECUTABLE_BYTES = 64L * 1024L * 1024L;
    private static final String BYTECODE_IMPLEMENTATION_SUFFIX =
            "synthetic-production-cache-workload-v1/jdk-complete-source-set";
    private static final String PROTECTION_DOMAIN_POLICY =
            "synthetic-map-class-loader-v1/platform-parent/default-protection-domain";

    private SyntheticProductionCacheWorker() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println(
                    "Usage: SyntheticProductionCacheWorker <profile-root> <cache-root> <report.json>");
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

    static void run(Path profileRoot, Path cacheRoot, Path reportPath) throws Exception {
        SyntheticExtendedProfile.Manifest manifest = SyntheticExtendedProfile.readManifest(profileRoot);
        SyntheticExtendedProfile.Fingerprint fingerprint = SyntheticExtendedProfile.fingerprint(profileRoot);
        if (fingerprint.files() != manifest.physicalFiles()
                || !fingerprint.sha256().equals(manifest.fingerprintSha256())) {
            throw new IOException("Extended profile manifest is stale or belongs to another profile");
        }

        IndexPass indexPass = loadIndex(profileRoot, cacheRoot, fingerprint.sha256());
        SyntheticExtendedResourceIndex index = indexPass.index();
        String providerDigest = index.providerDigest();

        AudioPass audio = runAudio(index, cacheRoot);
        BytecodePass bytecode = runBytecode(
                index,
                cacheRoot,
                fingerprint.sha256(),
                providerDigest);

        MessageDigest combined = sha256Digest();
        updateLengthPrefixed(combined, fingerprint.sha256().getBytes(StandardCharsets.US_ASCII));
        updateLengthPrefixed(combined, providerDigest.getBytes(StandardCharsets.US_ASCII));
        updateLengthPrefixed(combined, audio.outputSha256().getBytes(StandardCharsets.US_ASCII));
        updateLengthPrefixed(combined, bytecode.outputSha256().getBytes(StandardCharsets.US_ASCII));

        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("format", "starsector-preflight-synthetic-production-caches-v1");
        report.put("processId", ProcessHandle.current().pid());
        report.put("scale", manifest.scale());
        report.put("profileFilesValidated", fingerprint.files());
        report.put("profileFingerprintSha256", fingerprint.sha256());
        report.put("providerDigestSha256", providerDigest);
        report.put("indexLookupStatus", indexPass.lookupStatus());
        report.put("indexWrites", indexPass.writes());
        report.put("indexWriteErrors", indexPass.writeErrors());
        report.put("indexJarScans", indexPass.jarScans());
        report.put("indexLooseVisits", indexPass.looseVisits());
        report.put("indexPhysicalFilesVisited", indexPass.physicalFilesVisited());
        report.put("indexJarEntriesVisited", indexPass.jarEntriesVisited());
        report.put("audioEffectFiles", audio.effectFiles());
        report.put("audioStreamedFiles", audio.streamedFiles());
        report.put("audioHits", audio.hits());
        report.put("audioMisses", audio.misses());
        report.put("audioIneligible", audio.ineligible());
        report.put("audioCorruptFallbacks", audio.corruptFallbacks());
        report.put("audioReadErrors", audio.readErrors());
        report.put("audioDecoderCalls", audio.decoderCalls());
        report.put("audioWrites", audio.writes());
        report.put("audioWriteErrors", audio.writeErrors());
        report.put("audioOutputSha256", audio.outputSha256());
        report.put("bytecodeRequestedClass", bytecode.requestedClass());
        report.put("bytecodeLookupStatus", bytecode.lookupStatus());
        report.put("bytecodeSourceDisposition", bytecode.sourceDisposition());
        report.put("bytecodeCacheSource", bytecode.cacheSource());
        report.put("bytecodeCacheUsable", bytecode.cacheUsable());
        report.put("bytecodeCacheDetail", bytecode.cacheDetail());
        report.put("bytecodeCompilerCalls", bytecode.compilerCalls());
        report.put("bytecodeWriteAttempted", bytecode.writeAttempted());
        report.put("bytecodeWriteSucceeded", bytecode.writeSucceeded());
        report.put("bytecodeClassCount", bytecode.classCount());
        report.put("bytecodeRequestedValue", bytecode.requestedValue());
        report.put("bytecodeContextKeySha256", bytecode.contextKeySha256());
        report.put("bytecodeOutputSha256", bytecode.outputSha256());
        report.put("combinedOutputSha256", HexFormat.of().formatHex(combined.digest()));

        Path absoluteReport = reportPath.toAbsolutePath().normalize();
        Path parent = absoluteReport.getParent();
        if (parent == null) throw new IOException("Report path has no parent");
        Files.createDirectories(parent);
        Files.writeString(
                absoluteReport,
                Json.object(report) + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    private static IndexPass loadIndex(
            Path profileRoot,
            Path cacheRoot,
            String profileFingerprintSha256) throws IOException {
        Path target = SyntheticExtendedResourceIndex.cachePath(cacheRoot, profileFingerprintSha256);
        SyntheticExtendedResourceIndex.Lookup lookup = SyntheticExtendedResourceIndex.lookup(
                target,
                profileRoot,
                profileFingerprintSha256);
        if (lookup.status() == SyntheticExtendedResourceIndex.Status.HIT) {
            return new IndexPass(
                    lookup.index(),
                    lookup.status(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0);
        }

        SyntheticExtendedResourceIndex.Build build = SyntheticExtendedResourceIndex.build(
                profileRoot,
                profileFingerprintSha256);
        int writes = 0;
        int writeErrors = 0;
        try {
            build.index().write(target);
            writes = 1;
        } catch (IOException | RuntimeException error) {
            writeErrors = 1;
        }
        return new IndexPass(
                build.index(),
                lookup.status(),
                writes,
                writeErrors,
                build.jarScans(),
                build.looseVisits(),
                build.physicalFilesVisited(),
                build.jarEntriesVisited());
    }

    private static AudioPass runAudio(
            SyntheticExtendedResourceIndex index,
            Path cacheRoot) throws IOException {
        String decoderIdentity = SyntheticWavePreparedAudio.decoderPolicyIdentitySha256();
        int effectFiles = 0;
        int streamedFiles = 0;
        int hits = 0;
        int misses = 0;
        int ineligible = 0;
        int corruptFallbacks = 0;
        int readErrors = 0;
        int decoderCalls = 0;
        int writes = 0;
        int writeErrors = 0;
        MessageDigest output = sha256Digest();

        for (Map.Entry<String, SyntheticExtendedResourceIndex.Provider> entry
                : index.providers().entrySet()) {
            String logical = entry.getKey();
            if (!logical.endsWith(".wav")) continue;
            SyntheticExtendedResourceIndex.Provider provider = entry.getValue();
            PreparedAudio.Policy policy = logical.contains("/music/")
                    ? PreparedAudio.Policy.STREAMED
                    : PreparedAudio.Policy.FULLY_DECODED_EFFECT;
            if (policy == PreparedAudio.Policy.STREAMED) streamedFiles++;
            else effectFiles++;

            PreparedAudioCache.Lookup lookup = PreparedAudioCache.lookup(
                    cacheRoot,
                    provider.sha256(),
                    decoderIdentity,
                    policy);
            PreparedAudio prepared = null;
            switch (lookup.status()) {
                case HIT -> {
                    hits++;
                    prepared = lookup.audio();
                }
                case INELIGIBLE -> {
                    if (policy != PreparedAudio.Policy.STREAMED) {
                        throw new IOException("Eligible synthetic WAV became ineligible");
                    }
                    ineligible++;
                }
                case MISS, CORRUPT, ERROR -> {
                    if (policy != PreparedAudio.Policy.FULLY_DECODED_EFFECT) {
                        throw new IOException("Streamed synthetic WAV attempted cache fallback");
                    }
                    if (lookup.status() == PreparedAudioCache.Status.MISS) misses++;
                    else if (lookup.status() == PreparedAudioCache.Status.CORRUPT) corruptFallbacks++;
                    else readErrors++;
                    byte[] source = index.readBytes(logical);
                    decoderCalls++;
                    prepared = SyntheticWavePreparedAudio.decode(source, provider.sha256());
                    try {
                        PreparedAudioCache.write(cacheRoot, prepared);
                        writes++;
                    } catch (IOException | RuntimeException error) {
                        writeErrors++;
                    }
                }
            }

            updateLengthPrefixed(output, logical.getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(output, provider.sha256().getBytes(StandardCharsets.US_ASCII));
            updateLengthPrefixed(output, policy.name().getBytes(StandardCharsets.US_ASCII));
            if (prepared != null) updatePreparedAudio(output, prepared);
        }
        return new AudioPass(
                effectFiles,
                streamedFiles,
                hits,
                misses,
                ineligible,
                corruptFallbacks,
                readErrors,
                decoderCalls,
                writes,
                writeErrors,
                HexFormat.of().formatHex(output.digest()));
    }

    private static BytecodePass runBytecode(
            SyntheticExtendedResourceIndex index,
            Path cacheRoot,
            String profileFingerprintSha256,
            String providerDigestSha256) throws Exception {
        TreeMap<String, byte[]> sources = new TreeMap<>();
        for (String logical : index.providers().keySet()) {
            if (logical.endsWith(".java")) sources.put(logical, index.readBytes(logical));
        }
        if (sources.isEmpty()) throw new IOException("Extended profile contains no Java sources");
        String lastLogical = sources.lastKey();
        String requestedClass = "synthetic.generated."
                + lastLogical.substring(lastLogical.lastIndexOf('/') + 1, lastLogical.length() - 5);
        String sourceSetSha256 = sourceSetSha256(sources);
        String producerClassSha256 = classBytesSha256(SyntheticJdkSourceCompiler.class);
        String javaIdentity = javaExecutableIdentity();
        String loaderIdentity = identitySha256(
                "synthetic-loader-v1",
                SyntheticJdkSourceCompiler.class.getClassLoader().getClass().getName(),
                javaIdentity);
        String syntheticBuildIdentity = identitySha256(
                "synthetic-extended-build-v1",
                profileFingerprintSha256,
                providerDigestSha256);
        String compilerImplementationIdentity = identitySha256(
                "synthetic-jdk-compiler-implementation-v1",
                javaIdentity,
                SyntheticJdkSourceCompiler.class.getName(),
                producerClassSha256,
                BYTECODE_IMPLEMENTATION_SUFFIX,
                System.getProperty("java.runtime.version", "unknown"),
                System.getProperty("java.vendor", "unknown"),
                System.getProperty("os.arch", "unknown"));
        String classpathIdentity = runtimeClasspathIdentitySha256(javaIdentity);
        String compilerOptionsIdentity = identitySha256(
                "synthetic-jdk-compiler-options-v1",
                SyntheticJdkSourceCompiler.OPTIONS.toArray(String[]::new));
        String protectionDomainIdentity = identitySha256(
                "synthetic-protection-domain-policy-v1",
                PROTECTION_DOMAIN_POLICY);

        GeneratedBytecodeContext context = new GeneratedBytecodeContext(
                syntheticBuildIdentity,
                compilerImplementationIdentity,
                classpathIdentity,
                sourceSetSha256,
                compilerOptionsIdentity,
                loaderIdentity,
                protectionDomainIdentity);

        AtomicInteger compilerCalls = new AtomicInteger();
        GeneratedBytecodeCacheWrapper.Result result = GeneratedBytecodeCacheWrapper.generate(
                cacheRoot,
                context,
                requestedClass,
                ignored -> SyntheticJdkSourceCompiler.compile(sources, compilerCalls));
        Map<String, byte[]> bytecodes = result.classes();
        if (bytecodes == null || !bytecodes.containsKey(requestedClass)) {
            throw new IOException("Complete generated bytecode bundle omitted requested class");
        }

        MessageDigest output = sha256Digest();
        for (Map.Entry<String, byte[]> entry : new TreeMap<>(bytecodes).entrySet()) {
            updateLengthPrefixed(output, entry.getKey().getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(output, entry.getValue());
        }
        MapClassLoader loader = new MapClassLoader(bytecodes);
        Class<?> type = Class.forName(requestedClass, true, loader);
        Object instance = type.getDeclaredConstructor().newInstance();
        Method valueMethod = type.getMethod("value");
        int requestedValue = ((Number) valueMethod.invoke(instance)).intValue();
        output.update(ByteBuffer.allocate(Integer.BYTES).putInt(requestedValue).array());

        String sourceDisposition = result.source() == GeneratedBytecodeCacheWrapper.Source.CACHE_HIT
                ? "CACHE_REUSED"
                : "ORIGINAL_GENERATED";
        boolean writeAttempted = result.source() == GeneratedBytecodeCacheWrapper.Source.ORIGINAL_STORED
                || result.source() == GeneratedBytecodeCacheWrapper.Source.ORIGINAL_STORE_FAILED;
        boolean writeSucceeded =
                result.source() == GeneratedBytecodeCacheWrapper.Source.ORIGINAL_STORED;

        return new BytecodePass(
                requestedClass,
                result.lookupStatus(),
                sourceDisposition,
                result.source(),
                result.cacheUsable(),
                result.detail(),
                compilerCalls.get(),
                writeAttempted,
                writeSucceeded,
                bytecodes.size(),
                requestedValue,
                context.keySha256(),
                HexFormat.of().formatHex(output.digest()));
    }

    private static void updatePreparedAudio(MessageDigest digest, PreparedAudio audio) {
        updateLengthPrefixed(digest, audio.sourceSha256().getBytes(StandardCharsets.US_ASCII));
        updateLengthPrefixed(
                digest,
                audio.decoderPolicyIdentitySha256().getBytes(StandardCharsets.US_ASCII));
        updateLengthPrefixed(digest, audio.policy().name().getBytes(StandardCharsets.US_ASCII));
        updateLengthPrefixed(digest, audio.encoding().name().getBytes(StandardCharsets.US_ASCII));
        updateLengthPrefixed(digest, audio.byteOrder().name().getBytes(StandardCharsets.US_ASCII));
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(audio.bitsPerSample()).array());
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(audio.sampleRateHz()).array());
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(audio.channels()).array());
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(audio.frameCount()).array());
        updateLengthPrefixed(digest, audio.pcmBytes());
    }

    private static String sourceSetSha256(Map<String, byte[]> sources) {
        MessageDigest digest = sha256Digest();
        for (Map.Entry<String, byte[]> entry : new TreeMap<>(sources).entrySet()) {
            updateLengthPrefixed(digest, entry.getKey().getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(digest, entry.getValue());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String runtimeClasspathIdentitySha256(String javaIdentity) throws IOException {
        byte[] classpath = System.getProperty("java.class.path", "").getBytes(StandardCharsets.UTF_8);
        if (classpath.length > MAX_CLASSPATH_BYTES) {
            throw new IOException("Runtime classpath exceeds identity byte limit");
        }
        MessageDigest digest = sha256Digest();
        updateLengthPrefixed(
                digest,
                "synthetic-runtime-classpath-v1".getBytes(StandardCharsets.US_ASCII));
        updateLengthPrefixed(digest, javaIdentity.getBytes(StandardCharsets.UTF_8));
        updateLengthPrefixed(digest, classpath);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String identitySha256(String schema, String... values) {
        MessageDigest digest = sha256Digest();
        updateLengthPrefixed(digest, schema.getBytes(StandardCharsets.US_ASCII));
        for (String value : values) {
            updateLengthPrefixed(
                    digest,
                    (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String classBytesSha256(Class<?> type) throws IOException {
        String resource = '/' + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing implementation class bytes: " + type.getName());
            byte[] bytes = input.readNBytes(MAX_IMPLEMENTATION_BYTES + 1);
            if (bytes.length > MAX_IMPLEMENTATION_BYTES) {
                throw new IOException("Implementation class exceeds identity byte limit");
            }
            return Hashes.sha256(bytes);
        }
    }

    private static String javaExecutableIdentity() throws IOException {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe"
                : "java";
        Path path = Path.of(System.getProperty("java.home"), "bin", executable).toRealPath();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Java executable is not a regular file");
        }
        long bytes = Files.size(path);
        if (bytes <= 0 || bytes > MAX_JAVA_EXECUTABLE_BYTES) {
            throw new IOException("Java executable size is outside identity limit");
        }
        MessageDigest digest = sha256Digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total = Math.addExact(total, read);
                if (total > MAX_JAVA_EXECUTABLE_BYTES) {
                    throw new IOException("Java executable changed during identity hashing");
                }
                digest.update(buffer, 0, read);
            }
        }
        return path + "|" + bytes + "|" + HexFormat.of().formatHex(digest.digest());
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

    private record IndexPass(
            SyntheticExtendedResourceIndex index,
            SyntheticExtendedResourceIndex.Status lookupStatus,
            int writes,
            int writeErrors,
            long jarScans,
            long looseVisits,
            long physicalFilesVisited,
            long jarEntriesVisited) {
    }

    private record AudioPass(
            int effectFiles,
            int streamedFiles,
            int hits,
            int misses,
            int ineligible,
            int corruptFallbacks,
            int readErrors,
            int decoderCalls,
            int writes,
            int writeErrors,
            String outputSha256) {
    }

    private record BytecodePass(
            String requestedClass,
            GeneratedBytecodeCache.Status lookupStatus,
            String sourceDisposition,
            GeneratedBytecodeCacheWrapper.Source cacheSource,
            boolean cacheUsable,
            String cacheDetail,
            int compilerCalls,
            boolean writeAttempted,
            boolean writeSucceeded,
            int classCount,
            int requestedValue,
            String contextKeySha256,
            String outputSha256) {
    }

    private static final class MapClassLoader extends ClassLoader {
        private final Map<String, byte[]> bytecodes;

        private MapClassLoader(Map<String, byte[]> bytecodes) {
            super(ClassLoader.getPlatformClassLoader());
            this.bytecodes = new TreeMap<>();
            for (Map.Entry<String, byte[]> entry : bytecodes.entrySet()) {
                this.bytecodes.put(entry.getKey(), entry.getValue().clone());
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = bytecodes.get(name);
            if (bytes == null) throw new ClassNotFoundException(name);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
