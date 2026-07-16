package dev.starsector.preflight.synthetic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Persistent exact provider index for loose files and JAR entries in explicit mod order. */
final class SyntheticExtendedResourceIndex {
    private static final byte[] MAGIC = {'S', 'P', 'X', 'R'};
    private static final int VERSION = 2;
    private static final int HEADER_BYTES = 12;
    private static final int CHECKSUM_BYTES = 32;
    private static final int FILE_OVERHEAD_BYTES = HEADER_BYTES + CHECKSUM_BYTES;

    private static final int MAX_MODS = 1_000;
    private static final int MAX_MOD_ORDER_BYTES = 1024 * 1024;
    private static final int MAX_MOD_NAME_CHARS = 255;
    private static final int MAX_PHYSICAL_FILES = 100_000;
    private static final int MAX_PROVIDERS = 100_000;
    private static final int MAX_JAR_ENTRIES = 100_000;
    private static final int MAX_STRING_BYTES = 16 * 1024;
    private static final long MAX_JAR_FILE_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_RESOURCE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_INDEX_FILE_BYTES = 128 * 1024 * 1024;

    enum Kind {
        JAR,
        LOOSE
    }

    enum Status {
        HIT,
        MISS,
        CORRUPT,
        ERROR
    }

    record Provider(
            Kind kind,
            String relativeSource,
            String entryName,
            String sha256,
            long bytes) {
        Provider {
            Objects.requireNonNull(kind, "kind");
            relativeSource = relativePath(relativeSource);
            entryName = entryName == null ? "" : entryName;
            if (kind == Kind.JAR) {
                entryName = logicalPath(entryName);
            } else if (!entryName.isEmpty()) {
                throw new IllegalArgumentException("Loose provider cannot have a JAR entry name");
            }
            requireHash(sha256);
            if (bytes < 0 || bytes > MAX_RESOURCE_BYTES) {
                throw new IllegalArgumentException("Provider resource byte count is outside the limit");
            }
        }
    }

    record Build(
            SyntheticExtendedResourceIndex index,
            long jarScans,
            long looseVisits,
            long physicalFilesVisited,
            long jarEntriesVisited,
            long bytesHashed) {
        Build {
            Objects.requireNonNull(index, "index");
            if (jarScans < 0
                    || looseVisits < 0
                    || physicalFilesVisited < 0
                    || jarEntriesVisited < 0
                    || bytesHashed < 0) {
                throw new IllegalArgumentException("Negative provider-index work counter");
            }
        }
    }

    record Lookup(Status status, SyntheticExtendedResourceIndex index, String detail) {
        Lookup {
            Objects.requireNonNull(status, "status");
            detail = boundedDetail(detail == null ? "" : detail);
            if ((status == Status.HIT) != (index != null)) {
                throw new IllegalArgumentException("Provider-index lookup invariant");
            }
        }
    }

    private final Path profileRoot;
    private final String profileFingerprintSha256;
    private final Map<String, Provider> providers;
    private final int collidedPaths;
    private final int collisionEvents;

    private SyntheticExtendedResourceIndex(
            Path profileRoot,
            String profileFingerprintSha256,
            Map<String, Provider> providers,
            int collidedPaths,
            int collisionEvents) {
        this.profileRoot = profileRoot.toAbsolutePath().normalize();
        requireHash(profileFingerprintSha256);
        this.profileFingerprintSha256 = profileFingerprintSha256;
        this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(providers));
        if (collidedPaths < 0
                || collisionEvents < collidedPaths
                || collidedPaths > providers.size()) {
            throw new IllegalArgumentException("Invalid provider collision counts");
        }
        this.collidedPaths = collidedPaths;
        this.collisionEvents = collisionEvents;
    }

    Map<String, Provider> providers() {
        return providers;
    }

    int providerCount() {
        return providers.size();
    }

    int collidedPaths() {
        return collidedPaths;
    }

    int collisionEvents() {
        return collisionEvents;
    }

    String profileFingerprintSha256() {
        return profileFingerprintSha256;
    }

    static Build build(Path profileRoot, String expectedFingerprintSha256) throws IOException {
        requireHash(expectedFingerprintSha256);
        Path requestedRoot = profileRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(requestedRoot)) throw new IOException("Symbolic profile root");
        Path root = requestedRoot.toRealPath();
        Path modsRoot = root.resolve("mods");
        if (Files.isSymbolicLink(modsRoot)
                || !Files.isDirectory(modsRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing or symbolic mods directory");
        }

        List<String> modOrder = readModOrder(root.resolve("mod-order.txt"));
        TreeMap<String, Provider> winners = new TreeMap<>();
        Set<String> collided = new TreeSet<>();
        long collisionEvents = 0;
        long jarScans = 0;
        long looseVisits = 0;
        long physicalFilesVisited = 0;
        long jarEntriesVisited = 0;
        long bytesHashed = 0;

        for (String modName : modOrder) {
            Path modRoot = modsRoot.resolve(modName).normalize();
            if (!modRoot.startsWith(modsRoot)
                    || Files.isSymbolicLink(modRoot)
                    || !Files.isDirectory(modRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Missing or unsafe mod directory: " + modName);
            }
            Path realModRoot = modRoot.toRealPath();
            if (!realModRoot.startsWith(modsRoot.toRealPath())) {
                throw new IOException("Mod directory escaped the profile root: " + modName);
            }

            List<Path> jars = new ArrayList<>();
            List<Path> loose = new ArrayList<>();
            try (var stream = Files.walk(realModRoot)) {
                var iterator = stream.iterator();
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("Symbolic profile source: " + path);
                    }
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                    physicalFilesVisited = Math.addExact(physicalFilesVisited, 1);
                    if (physicalFilesVisited > MAX_PHYSICAL_FILES) {
                        throw new IOException("Extended profile exceeds physical file limit");
                    }
                    if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                        jars.add(path);
                    } else {
                        loose.add(path);
                    }
                }
            }

            jars.sort(Comparator.comparing(path -> normalize(root.relativize(path))));
            loose.sort(Comparator.comparing(path -> normalize(realModRoot.relativize(path))));

            for (Path jarPath : jars) {
                long jarBytes = Files.size(jarPath);
                if (jarBytes <= 0 || jarBytes > MAX_JAR_FILE_BYTES) {
                    throw new IOException("JAR file size is outside its limit: " + jarPath);
                }
                jarScans = Math.addExact(jarScans, 1);
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    List<JarEntry> entries = new ArrayList<>();
                    Set<String> namesInJar = new TreeSet<>();
                    Enumeration<JarEntry> enumeration = jar.entries();
                    while (enumeration.hasMoreElements()) {
                        JarEntry entry = enumeration.nextElement();
                        if (entry.isDirectory()) continue;
                        jarEntriesVisited = Math.addExact(jarEntriesVisited, 1);
                        if (jarEntriesVisited > MAX_JAR_ENTRIES) {
                            throw new IOException("Extended profile exceeds JAR entry limit");
                        }
                        String name = logicalPath(entry.getName());
                        if (!namesInJar.add(name)) {
                            throw new IOException("Duplicate JAR entry name: " + name);
                        }
                        entries.add(entry);
                    }
                    entries.sort(Comparator.comparing(JarEntry::getName));
                    for (JarEntry entry : entries) {
                        String logical = logicalPath(entry.getName());
                        byte[] bytes = readJarEntry(jar, entry, logical);
                        bytesHashed = Math.addExact(bytesHashed, bytes.length);
                        Provider provider = new Provider(
                                Kind.JAR,
                                normalize(root.relativize(jarPath)),
                                logical,
                                sha256(bytes),
                                bytes.length);
                        if (put(winners, collided, logical, provider)) collisionEvents++;
                    }
                }
            }

            for (Path file : loose) {
                looseVisits = Math.addExact(looseVisits, 1);
                String logical = logicalPath(realModRoot.relativize(file).toString());
                HashedFile hashed = hashFileBounded(file, MAX_RESOURCE_BYTES);
                bytesHashed = Math.addExact(bytesHashed, hashed.bytes());
                Provider provider = new Provider(
                        Kind.LOOSE,
                        normalize(root.relativize(file)),
                        "",
                        hashed.sha256(),
                        hashed.bytes());
                if (put(winners, collided, logical, provider)) collisionEvents++;
            }
        }

        if (collisionEvents > Integer.MAX_VALUE) {
            throw new IOException("Provider collision event count overflow");
        }
        return new Build(
                new SyntheticExtendedResourceIndex(
                        root,
                        expectedFingerprintSha256,
                        winners,
                        collided.size(),
                        (int) collisionEvents),
                jarScans,
                looseVisits,
                physicalFilesVisited,
                jarEntriesVisited,
                bytesHashed);
    }

    byte[] readBytes(String logicalName) throws IOException {
        String logical = logicalPath(logicalName);
        Provider provider = providers.get(logical);
        if (provider == null) throw new IOException("Missing indexed provider: " + logical);
        Path source = resolveSource(provider.relativeSource());
        byte[] bytes;
        if (provider.kind() == Kind.LOOSE) {
            bytes = readBounded(source, MAX_RESOURCE_BYTES);
        } else {
            long jarBytes = Files.size(source);
            if (jarBytes <= 0 || jarBytes > MAX_JAR_FILE_BYTES) {
                throw new IOException("Indexed JAR size is outside its limit: " + source);
            }
            try (JarFile jar = new JarFile(source.toFile())) {
                JarEntry entry = jar.getJarEntry(provider.entryName());
                bytes = readJarEntry(jar, entry, logical);
            }
        }
        if (bytes.length != provider.bytes() || !sha256(bytes).equals(provider.sha256())) {
            throw new IOException("Indexed provider identity changed: " + logical);
        }
        return bytes;
    }

    String providerDigest() {
        MessageDigest digest = sha256Digest();
        updateString(digest, profileFingerprintSha256);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(collidedPaths).array());
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(collisionEvents).array());
        for (Map.Entry<String, Provider> entry : providers.entrySet()) {
            updateString(digest, entry.getKey());
            Provider provider = entry.getValue();
            updateString(digest, provider.kind().name());
            updateString(digest, provider.relativeSource());
            updateString(digest, provider.entryName());
            updateString(digest, provider.sha256());
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(provider.bytes()).array());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static Path cachePath(Path cacheRoot, String profileFingerprintSha256) {
        requireHash(profileFingerprintSha256);
        Path root = cacheRoot.toAbsolutePath().normalize();
        Path path = root.resolve("synthetic-startup/extended-index-v2")
                .resolve(profileFingerprintSha256.substring(0, 2))
                .resolve(profileFingerprintSha256 + ".spxr")
                .normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Index path escaped cache root");
        return path;
    }

    void write(Path target) throws IOException {
        byte[] payload = encodePayload();
        int fileSize = Math.addExact(payload.length, FILE_OVERHEAD_BYTES);
        if (fileSize > MAX_INDEX_FILE_BYTES) throw new IOException("Index file exceeds byte limit");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(fileSize);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(payload.length);
            output.write(payload);
            output.write(sha256Bytes(payload));
        }
        if (bytes.size() != fileSize) throw new IOException("Index serialized size mismatch");
        atomicReplace(target, bytes.toByteArray());
    }

    static Lookup lookup(Path target, Path profileRoot, String expectedFingerprintSha256) {
        try {
            requireHash(expectedFingerprintSha256);
        } catch (RuntimeException error) {
            return new Lookup(Status.ERROR, null, message(error));
        }
        Path absolute;
        try {
            absolute = target.toAbsolutePath().normalize();
        } catch (RuntimeException error) {
            return new Lookup(Status.ERROR, null, message(error));
        }
        if (!Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            return new Lookup(Status.MISS, null, "No exact extended provider index");
        }
        if (Files.isSymbolicLink(absolute)
                || !Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS)) {
            return new Lookup(Status.ERROR, null, "Index target is not a non-symlink regular file");
        }

        byte[] file;
        try {
            file = readBounded(absolute, MAX_INDEX_FILE_BYTES);
        } catch (NoSuchFileException error) {
            return new Lookup(Status.MISS, null, "Index disappeared during lookup");
        } catch (IOException error) {
            return new Lookup(Status.ERROR, null, message(error));
        }
        if (file.length < FILE_OVERHEAD_BYTES) {
            return new Lookup(Status.CORRUPT, null, "Index file is truncated");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(file))) {
            if (!MessageDigest.isEqual(input.readNBytes(MAGIC.length), MAGIC)) {
                throw new IOException("Index magic mismatch");
            }
            if (input.readInt() != VERSION) throw new IOException("Index version mismatch");
            int payloadLength = input.readInt();
            if (payloadLength < 44
                    || Math.addExact(payloadLength, FILE_OVERHEAD_BYTES) != file.length) {
                throw new IOException("Index payload length mismatch");
            }
            byte[] payload = input.readNBytes(payloadLength);
            byte[] checksum = input.readNBytes(CHECKSUM_BYTES);
            if (payload.length != payloadLength || checksum.length != CHECKSUM_BYTES) {
                throw new EOFException("Truncated index payload");
            }
            if (input.available() != 0) throw new IOException("Trailing index bytes");
            if (!MessageDigest.isEqual(checksum, sha256Bytes(payload))) {
                throw new IOException("Index checksum mismatch");
            }
            SyntheticExtendedResourceIndex index = decodePayload(
                    payload,
                    profileRoot,
                    expectedFingerprintSha256);
            return new Lookup(Status.HIT, index, "Exact extended provider index hit");
        } catch (IOException | IllegalArgumentException | ArithmeticException error) {
            return new Lookup(Status.CORRUPT, null, message(error));
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            return new Lookup(Status.ERROR, null, message(error));
        }
    }

    private byte[] encodePayload() throws IOException {
        int payloadSize = 32 + Integer.BYTES * 3;
        for (Map.Entry<String, Provider> entry : providers.entrySet()) {
            payloadSize = Math.addExact(payloadSize, serializedStringBytes(entry.getKey()));
            payloadSize = Math.addExact(payloadSize, 1);
            Provider provider = entry.getValue();
            payloadSize = Math.addExact(payloadSize, serializedStringBytes(provider.relativeSource()));
            payloadSize = Math.addExact(payloadSize, serializedStringBytes(provider.entryName()));
            payloadSize = Math.addExact(payloadSize, 32 + Long.BYTES);
            if (payloadSize > MAX_INDEX_FILE_BYTES - FILE_OVERHEAD_BYTES) {
                throw new IOException("Index payload exceeds byte limit");
            }
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(payloadSize);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(HexFormat.of().parseHex(profileFingerprintSha256));
            output.writeInt(collidedPaths);
            output.writeInt(collisionEvents);
            output.writeInt(providers.size());
            for (Map.Entry<String, Provider> entry : providers.entrySet()) {
                writeString(output, entry.getKey());
                Provider provider = entry.getValue();
                output.writeByte(provider.kind().ordinal());
                writeString(output, provider.relativeSource());
                writeString(output, provider.entryName());
                output.write(HexFormat.of().parseHex(provider.sha256()));
                output.writeLong(provider.bytes());
            }
        }
        if (bytes.size() != payloadSize) throw new IOException("Index payload size mismatch");
        return bytes.toByteArray();
    }

    private static SyntheticExtendedResourceIndex decodePayload(
            byte[] payload,
            Path profileRoot,
            String expectedFingerprintSha256) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            byte[] fingerprint = input.readNBytes(32);
            if (fingerprint.length != 32
                    || !MessageDigest.isEqual(
                    fingerprint,
                    HexFormat.of().parseHex(expectedFingerprintSha256))) {
                throw new IOException("Index profile identity mismatch");
            }
            int collidedPaths = input.readInt();
            int collisionEvents = input.readInt();
            int providerCount = input.readInt();
            if (collidedPaths < 0
                    || collisionEvents < collidedPaths
                    || providerCount < 0
                    || providerCount > MAX_PROVIDERS
                    || collidedPaths > providerCount) {
                throw new IOException("Invalid provider-index counts");
            }

            TreeMap<String, Provider> providers = new TreeMap<>();
            String previous = null;
            for (int i = 0; i < providerCount; i++) {
                String logical = logicalPath(readString(input));
                if (previous != null && previous.compareTo(logical) >= 0) {
                    throw new IOException("Provider entries are not strictly sorted");
                }
                previous = logical;
                int kindOrdinal = input.readUnsignedByte();
                if (kindOrdinal >= Kind.values().length) {
                    throw new IOException("Invalid provider kind");
                }
                String relativeSource = readString(input);
                String entryName = readString(input);
                byte[] hash = input.readNBytes(32);
                if (hash.length != 32) throw new EOFException("Truncated provider hash");
                long bytes = input.readLong();
                providers.put(logical, new Provider(
                        Kind.values()[kindOrdinal],
                        relativeSource,
                        entryName,
                        HexFormat.of().formatHex(hash),
                        bytes));
            }
            if (input.available() != 0) throw new IOException("Trailing provider payload bytes");

            Path requestedRoot = profileRoot.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(requestedRoot)) throw new IOException("Symbolic profile root");
            Path realRoot = requestedRoot.toRealPath();
            return new SyntheticExtendedResourceIndex(
                    realRoot,
                    expectedFingerprintSha256,
                    providers,
                    collidedPaths,
                    collisionEvents);
        }
    }

    private Path resolveSource(String relativeSource) throws IOException {
        Path source = profileRoot.resolve(relativeSource).normalize();
        if (!source.startsWith(profileRoot)
                || Files.isSymbolicLink(source)
                || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Indexed provider source is missing or unsafe");
        }
        Path real = source.toRealPath();
        if (!real.startsWith(profileRoot.toRealPath())) {
            throw new IOException("Indexed provider source escaped profile root");
        }
        return real;
    }

    private static boolean put(
            Map<String, Provider> winners,
            Set<String> collided,
            String logical,
            Provider provider) throws IOException {
        Provider previous = winners.put(logical, provider);
        if (winners.size() > MAX_PROVIDERS) throw new IOException("Provider limit exceeded");
        if (previous == null) return false;
        collided.add(logical);
        return true;
    }

    private static List<String> readModOrder(Path path) throws IOException {
        byte[] bytes = readBounded(path, MAX_MOD_ORDER_BYTES);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.UTF_8))) {
            throw new IOException("Mod order is not canonical UTF-8");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        for (String raw : text.split("\\n", -1)) {
            String name = raw.trim();
            if (name.isEmpty() || name.startsWith("#")) continue;
            if (name.length() > MAX_MOD_NAME_CHARS
                    || name.equals(".")
                    || name.equals("..")
                    || name.contains("/")
                    || name.contains("\\")
                    || !seen.add(name)) {
                throw new IOException("Invalid mod order entry");
            }
            result.add(name);
            if (result.size() > MAX_MODS) throw new IOException("Mod order exceeds limit");
        }
        if (result.isEmpty()) throw new IOException("Mod order is empty");
        return result;
    }

    private static byte[] readJarEntry(JarFile jar, JarEntry entry, String logical)
            throws IOException {
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Missing indexed JAR entry: " + logical);
        }
        long declared = entry.getSize();
        if (declared > MAX_RESOURCE_BYTES) {
            throw new IOException("JAR entry exceeds declared byte limit: " + logical);
        }
        try (InputStream input = jar.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(MAX_RESOURCE_BYTES + 1);
            if (bytes.length > MAX_RESOURCE_BYTES) {
                throw new IOException("JAR entry exceeds byte limit: " + logical);
            }
            return bytes;
        }
    }

    private static HashedFile hashFileBounded(Path path, int maxBytes) throws IOException {
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) continue;
                total = Math.addExact(total, read);
                if (total > maxBytes) throw new IOException("Resource exceeds byte limit: " + path);
                digest.update(buffer, 0, read);
            }
        }
        return new HashedFile(total, HexFormat.of().formatHex(digest.digest()));
    }

    private static byte[] readBounded(Path path, int maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new IOException("File exceeds byte limit: " + path);
            return bytes;
        }
    }

    private static int serializedStringBytes(String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("Index string exceeds byte limit");
        return Math.addExact(Integer.BYTES, bytes.length);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("Index string exceeds byte limit");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IOException("Invalid index string length");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Truncated index string");
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) {
            throw new IOException("Index string is not canonical UTF-8");
        }
        return value;
    }

    private static String logicalPath(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.replace('\\', '/');
        if (normalized.isEmpty()
                || normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:/.*")
                || normalized.equals(".")
                || normalized.equals("..")
                || normalized.contains("//")
                || normalized.startsWith("../")
                || normalized.contains("/../")
                || normalized.endsWith("/..")
                || normalized.contains("/./")
                || normalized.endsWith("/.")) {
            throw new IllegalArgumentException("Invalid logical path: " + value);
        }
        return normalized;
    }

    private static String relativePath(String value) {
        return logicalPath(value);
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static void requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected lowercase SHA-256");
        }
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(sha256Bytes(bytes));
    }

    private static byte[] sha256Bytes(byte[] bytes) {
        return sha256Digest().digest(bytes);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static void atomicReplace(Path target, byte[] bytes) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("Index target has no parent");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent)
                || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Index parent is unsafe");
        }
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(absolute)
                || !Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Index target is not replaceable");
        }
        Path temporary = parent.resolve(
                absolute.getFileName() + ".tmp-" + ProcessHandle.current().pid() + "-" + System.nanoTime());
        boolean moved = false;
        try {
            Files.write(
                    temporary,
                    bytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            try {
                Files.move(
                        temporary,
                        absolute,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static String boundedDetail(String value) {
        String normalized = value.replace('\u0000', '?').strip();
        return normalized.length() <= 2_048
                ? normalized
                : normalized.substring(0, 2_048) + "...";
    }

    private static String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private record HashedFile(long bytes, String sha256) {
    }
}
