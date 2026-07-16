package dev.starsector.preflight.synthetic;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.imageio.ImageIO;

/** Deterministic generated profiles for the extended synthetic startup workload. */
public final class SyntheticExtendedProfile {
    static final String VERSION = "synthetic-extended-profile-v2";
    static final String MANIFEST = "synthetic-extended.properties";

    private static final int MAX_FILES = 100_000;
    private static final long MAX_TOTAL_BYTES = 16L * 1024L * 1024L * 1024L;
    private static final int MAX_PATH_BYTES = 4_096;
    private static final int MAX_MANIFEST_BYTES = 16 * 1024;

    private SyntheticExtendedProfile() {
    }

    public enum Scale {
        TINY(4, 36, 48, 12, 8, 4),
        MEDIUM(10, 2_010, 400, 50, 40, 8),
        LARGE(77, 17_967, 24_000, 1_400, 3_800, 78);

        private final int mods;
        private final int resources;
        private final int images;
        private final int audio;
        private final int javaSources;
        private final int jars;

        Scale(int mods, int resources, int images, int audio, int javaSources, int jars) {
            this.mods = mods;
            this.resources = resources;
            this.images = images;
            this.audio = audio;
            this.javaSources = javaSources;
            this.jars = jars;
        }

        public int mods() {
            return mods;
        }

        public int resources() {
            return resources;
        }

        public int images() {
            return images;
        }

        public int audio() {
            return audio;
        }

        public int javaSources() {
            return javaSources;
        }

        public int jars() {
            return jars;
        }

        public int physicalFiles() {
            return resources + images + audio + javaSources + jars + jars;
        }

        public static Scale parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    public record Manifest(
            String version,
            long seed,
            Scale scale,
            int physicalFiles,
            int effectFiles,
            int streamedFiles,
            String fingerprintSha256) {
        public Manifest {
            if (!VERSION.equals(version)
                    || scale == null
                    || physicalFiles != scale.physicalFiles()
                    || effectFiles < 0
                    || streamedFiles < 0
                    || effectFiles + streamedFiles != scale.audio()
                    || fingerprintSha256 == null
                    || !fingerprintSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid extended synthetic manifest");
            }
        }
    }

    public record Fingerprint(int files, long bytes, String sha256) {
        public Fingerprint {
            if (files < 0
                    || bytes < 0
                    || sha256 == null
                    || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid extended synthetic fingerprint");
            }
        }
    }

    public static Manifest generate(Path root, long seed, Scale scale) throws IOException {
        if (scale == null) throw new IOException("Synthetic scale is required");
        Path profile = prepareEmptyRoot(root);
        Path mods = profile.resolve("mods");
        Files.createDirectory(mods);

        List<Path> modRoots = new ArrayList<>();
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < scale.mods(); i++) {
            String name = String.format(Locale.ROOT, "mod-%03d", i);
            Path mod = mods.resolve(name);
            Files.createDirectory(mod);
            modRoots.add(mod);
            order.append(name).append('\n');
        }
        Files.writeString(
                profile.resolve("mod-order.txt"),
                order,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);

        Random random = new Random(seed);
        writeRepeatedResources(modRoots, scale.resources(), seed);
        writeRepeatedImages(modRoots, scale.images(), seed, random);

        int streamed = Math.max(1, scale.audio() / 5);
        int effects = scale.audio() - streamed;
        for (int i = 0; i < scale.audio(); i++) {
            int mod = (i * 3 + 1) % scale.mods();
            String group = i < effects ? "effects" : "music";
            Path target = modRoots.get(mod).resolve(String.format(
                    Locale.ROOT,
                    "sounds/%s/audio-%05d.wav",
                    group,
                    i));
            writeWave(target, seed, i);
        }

        for (int i = 0; i < scale.javaSources(); i++) {
            int mod = (i * 5 + 2) % scale.mods();
            Path target = modRoots.get(mod).resolve(String.format(
                    Locale.ROOT,
                    "data/scripts/generated/Source%05d.java",
                    i));
            writeJava(target, seed, i);
        }

        int sharedJarPaths = Math.max(1, scale.jars() / 2);
        for (int i = 0; i < scale.jars(); i++) {
            int mod = (i * 7 + 3) % scale.mods();
            Path jar = modRoots.get(mod).resolve(String.format(
                    Locale.ROOT,
                    "jars/synthetic-%03d.jar",
                    i));
            writeJar(jar, seed, i, sharedJarPaths);

            int overrideMod = (mod + 1) % scale.mods();
            Path loose = modRoots.get(overrideMod).resolve(String.format(
                    Locale.ROOT,
                    "classpath/generated/shared-%03d.txt",
                    i % sharedJarPaths));
            writeBytes(loose, ("loose-override-" + seed + "-" + i + "\n")
                    .getBytes(StandardCharsets.UTF_8));
        }

        Fingerprint fingerprint = fingerprint(profile);
        if (fingerprint.files() != scale.physicalFiles()) {
            throw new IOException(
                    "Generated physical file count differs from scale: " + fingerprint.files());
        }
        Manifest manifest = new Manifest(
                VERSION,
                seed,
                scale,
                fingerprint.files(),
                effects,
                streamed,
                fingerprint.sha256());
        writeManifest(profile.resolve(MANIFEST), manifest);
        return manifest;
    }

    public static Manifest readManifest(Path profileRoot) throws IOException {
        Path profile = profileRoot.toAbsolutePath().normalize();
        byte[] bytes = readBounded(profile.resolve(MANIFEST), MAX_MANIFEST_BYTES);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.UTF_8))) {
            throw new IOException("Extended synthetic manifest is not canonical UTF-8");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String raw : text.split("\\n", -1)) {
            if (raw.isEmpty()) continue;
            int separator = raw.indexOf('=');
            if (separator <= 0 || separator == raw.length() - 1) {
                throw new IOException("Invalid extended synthetic manifest line");
            }
            String previous = values.put(raw.substring(0, separator), raw.substring(separator + 1));
            if (previous != null) throw new IOException("Duplicate extended synthetic manifest key");
        }
        try {
            if (values.size() != 7) throw new IllegalArgumentException("Unexpected manifest key count");
            return new Manifest(
                    values.get("version"),
                    Long.parseLong(values.get("seed")),
                    Scale.parse(values.get("scale")),
                    Integer.parseInt(values.get("physicalFiles")),
                    Integer.parseInt(values.get("effectFiles")),
                    Integer.parseInt(values.get("streamedFiles")),
                    values.get("fingerprintSha256"));
        } catch (RuntimeException error) {
            throw new IOException("Invalid extended synthetic manifest", error);
        }
    }

    public static Fingerprint fingerprint(Path profileRoot) throws IOException {
        Path profile = profileRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(profile)) throw new IOException("Symbolic profile root");
        Path realProfile = profile.toRealPath();
        Path mods = realProfile.resolve("mods");
        if (Files.isSymbolicLink(mods)
                || !Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing or symbolic extended profile mods directory");
        }

        MessageDigest digest = sha256();
        updateBytes(digest, readBounded(realProfile.resolve("mod-order.txt"), 1024 * 1024));

        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(mods)) {
            var iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Extended profile contains a symbolic link: " + path);
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                if (files.size() >= MAX_FILES) {
                    throw new IOException("Extended profile exceeds file limit");
                }
                files.add(path);
            }
        }
        files.sort(Comparator.comparing(path -> normalize(mods.relativize(path))));

        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        for (Path file : files) {
            byte[] relative = normalize(mods.relativize(file)).getBytes(StandardCharsets.UTF_8);
            if (relative.length > MAX_PATH_BYTES) {
                throw new IOException("Extended profile path exceeds limit");
            }
            updateBytes(digest, relative);
            long fileBytes = 0;
            try (InputStream input = Files.newInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (read == 0) continue;
                    fileBytes = Math.addExact(fileBytes, read);
                    total = Math.addExact(total, read);
                    if (total > MAX_TOTAL_BYTES) {
                        throw new IOException("Extended profile exceeds byte limit");
                    }
                    digest.update(buffer, 0, read);
                }
            }
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(fileBytes).array());
        }
        return new Fingerprint(
                files.size(),
                total,
                HexFormat.of().formatHex(digest.digest()));
    }

    private static Path prepareEmptyRoot(Path requested) throws IOException {
        if (requested == null) throw new IOException("Profile root is required");
        Path root = requested.toAbsolutePath().normalize();
        if (root.getParent() == null) throw new IOException("Refusing filesystem root");
        Path home = Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (root.equals(home) || root.equals(cwd)) {
            throw new IOException("Refusing home or working directory");
        }
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(root);
        }
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Profile root is unsafe");
        }
        try (var children = Files.list(root)) {
            if (children.findAny().isPresent()) {
                throw new IOException("Profile root must be empty; existing files are preserved");
            }
        }
        return root.toRealPath();
    }

    private static void writeRepeatedResources(List<Path> mods, int count, long seed)
            throws IOException {
        int unique = uniqueCount(count);
        for (int i = 0; i < count; i++) {
            int logical = i < unique ? i : i - unique;
            int base = (logical * 11 + 3) % mods.size();
            int mod = i < unique ? base : (base + 1) % mods.size();
            Path target = mods.get(mod).resolve(String.format(
                    Locale.ROOT,
                    "data/generated/resource-%05d.json",
                    logical));
            writeBytes(target, ("{\"seed\":" + seed + ",\"source\":" + i + "}\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeRepeatedImages(
            List<Path> mods,
            int count,
            long seed,
            Random random) throws IOException {
        int unique = uniqueCount(count);
        for (int i = 0; i < count; i++) {
            int logical = i < unique ? i : i - unique;
            int base = logical % mods.size();
            int mod = i < unique ? base : (base + 1) % mods.size();
            Path target = mods.get(mod).resolve(String.format(
                    Locale.ROOT,
                    "graphics/generated/image-%05d.png",
                    logical));
            int width = 2 + i % 7;
            int height = 2 + i % 5;
            BufferedImage image = new BufferedImage(
                    width,
                    height,
                    i % 2 == 0 ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_3BYTE_BGR);
            long mixed = seed ^ (0x9e3779b97f4a7c15L * (i + 1L)) ^ random.nextLong();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int value = (int) (mixed + x * 0x45d9f3bL + y * 0x119de1f3L);
                    image.setRGB(x, y, 0xff000000 | (value & 0x00ffffff));
                }
            }
            Files.createDirectories(target.getParent());
            if (!ImageIO.write(image, "png", target.toFile())) {
                throw new IOException("PNG writer unavailable");
            }
        }
    }

    private static void writeWave(Path path, long seed, int index) throws IOException {
        int channels = index % 3 == 0 ? 2 : 1;
        int sampleRate = index % 2 == 0 ? 22_050 : 44_100;
        int frames = 64 + index % 33;
        int dataBytes = Math.multiplyExact(Math.multiplyExact(frames, channels), 2);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(44 + dataBytes);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeBytes("RIFF");
            writeLeInt(output, 36 + dataBytes);
            output.writeBytes("WAVEfmt ");
            writeLeInt(output, 16);
            writeLeShort(output, 1);
            writeLeShort(output, channels);
            writeLeInt(output, sampleRate);
            writeLeInt(output, sampleRate * channels * 2);
            writeLeShort(output, channels * 2);
            writeLeShort(output, 16);
            output.writeBytes("data");
            writeLeInt(output, dataBytes);
            for (int frame = 0; frame < frames; frame++) {
                for (int channel = 0; channel < channels; channel++) {
                    long phase = seed + index * 31L + frame * 17L + channel * 7L;
                    writeLeShort(output, (short) ((phase * 1103515245L + 12345L) >>> 16));
                }
            }
        }
        writeBytes(path, bytes.toByteArray());
    }

    private static void writeJava(Path path, long seed, int index) throws IOException {
        String className = String.format(Locale.ROOT, "Source%05d", index);
        String dependency = index == 0
                ? "0"
                : String.format(Locale.ROOT, "Source%05d.SEED", index - 1);
        String source = "package synthetic.generated;\n"
                + "public final class " + className + " {\n"
                + "  public static final long SEED = " + seed + "L + " + dependency + ";\n"
                + "  public int value() { Runnable r = new Runnable(){ public void run(){} }; "
                + "r.run(); return Inner.VALUE + " + index + "; }\n"
                + "  static final class Inner { static final int VALUE = "
                + (index * 13 + 7) + "; }\n"
                + "}\n";
        writeBytes(path, source.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeJar(Path path, long seed, int index, int sharedCount)
            throws IOException {
        Files.createDirectories(path.getParent());
        List<String> names = List.of(
                String.format(Locale.ROOT, "classpath/generated/jar-only-%03d.txt", index),
                String.format(Locale.ROOT, "classpath/generated/shared-%03d.txt", index % sharedCount));
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(
                path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE))) {
            for (String name : names.stream().sorted().toList()) {
                JarEntry entry = new JarEntry(name);
                entry.setTime(0L);
                output.putNextEntry(entry);
                output.write(("jar-" + seed + "-" + index + "-" + name + "\n")
                        .getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static int uniqueCount(int count) {
        return Math.max(1, count - Math.max(1, count / 4));
    }

    private static void writeBytes(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void writeManifest(Path path, Manifest manifest) throws IOException {
        String text = "version=" + manifest.version() + "\n"
                + "seed=" + manifest.seed() + "\n"
                + "scale=" + manifest.scale().name() + "\n"
                + "physicalFiles=" + manifest.physicalFiles() + "\n"
                + "effectFiles=" + manifest.effectFiles() + "\n"
                + "streamedFiles=" + manifest.streamedFiles() + "\n"
                + "fingerprintSha256=" + manifest.fingerprintSha256() + "\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_MANIFEST_BYTES) throw new IOException("Manifest exceeds byte limit");
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static byte[] readBounded(Path path, int max) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(max + 1);
            if (bytes.length > max) throw new IOException("File exceeds limit: " + path);
            return bytes;
        }
    }

    private static void updateBytes(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static void writeLeShort(DataOutputStream output, int value) throws IOException {
        output.writeByte(value & 0xff);
        output.writeByte((value >>> 8) & 0xff);
    }

    private static void writeLeInt(DataOutputStream output, int value) throws IOException {
        output.writeByte(value & 0xff);
        output.writeByte((value >>> 8) & 0xff);
        output.writeByte((value >>> 16) & 0xff);
        output.writeByte((value >>> 24) & 0xff);
    }
}
