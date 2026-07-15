package dev.starsector.preflight.synthetic;

import dev.starsector.preflight.core.Hashes;
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
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.imageio.ImageIO;

/** Deterministic generated profiles for the extended synthetic startup workload. */
public final class SyntheticExtendedProfile {
    static final String VERSION = "synthetic-extended-profile-v1";
    static final String MANIFEST = "synthetic-extended.properties";
    private static final int MAX_FILES = 100_000;
    private static final long MAX_TOTAL_BYTES = 16L * 1024 * 1024 * 1024;
    private static final int MAX_PATH_BYTES = 4_096;

    private SyntheticExtendedProfile() {}

    public enum Scale {
        TINY(4, 36, 48, 12, 8, 4),
        MEDIUM(10, 2_010, 400, 50, 40, 8),
        LARGE(77, 17_967, 24_000, 1_400, 3_800, 78);

        private final int mods, resources, images, audio, javaSources, jars;
        Scale(int mods, int resources, int images, int audio, int javaSources, int jars) {
            this.mods = mods; this.resources = resources; this.images = images;
            this.audio = audio; this.javaSources = javaSources; this.jars = jars;
        }
        public int mods(){return mods;} public int resources(){return resources;} public int images(){return images;}
        public int audio(){return audio;} public int javaSources(){return javaSources;} public int jars(){return jars;}
        public int physicalFiles(){return resources + images + audio + javaSources + jars + jars;}
        public static Scale parse(String value){return valueOf(value.trim().toUpperCase(Locale.ROOT));}
    }

    public record Manifest(String version, long seed, Scale scale, int physicalFiles,
                           int effectFiles, int streamedFiles, String fingerprintSha256) {
        public Manifest {
            if (!VERSION.equals(version) || physicalFiles < 0 || effectFiles < 0 || streamedFiles < 0
                    || fingerprintSha256 == null || !fingerprintSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid extended synthetic manifest");
            }
        }
    }

    public record Fingerprint(int files, long bytes, String sha256) {
        public Fingerprint {
            if (files < 0 || bytes < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid extended synthetic fingerprint");
            }
        }
    }

    public static Manifest generate(Path root, long seed, Scale scale) throws IOException {
        Path profile = root.toAbsolutePath().normalize();
        prepareRoot(profile);
        Path mods = profile.resolve("mods");
        Files.createDirectories(mods);
        List<Path> modRoots = new ArrayList<>();
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < scale.mods(); i++) {
            String name = String.format(Locale.ROOT, "mod-%03d", i);
            Path mod = mods.resolve(name);
            Files.createDirectories(mod);
            modRoots.add(mod);
            order.append(name).append('\n');
        }
        Files.writeString(profile.resolve("mod-order.txt"), order, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        Random random = new Random(seed);
        writeRepeatedResources(modRoots, scale.resources(), seed);
        writeRepeatedImages(modRoots, scale.images(), seed, random);
        int streamed = Math.max(1, scale.audio() / 5);
        int effects = scale.audio() - streamed;
        for (int i = 0; i < scale.audio(); i++) {
            int mod = (i * 3 + 1) % scale.mods();
            String group = i < effects ? "effects" : "music";
            Path target = modRoots.get(mod).resolve(String.format(Locale.ROOT,
                    "sounds/%s/audio-%05d.wav", group, i));
            writeWave(target, seed, i);
        }
        for (int i = 0; i < scale.javaSources(); i++) {
            int mod = (i * 5 + 2) % scale.mods();
            Path target = modRoots.get(mod).resolve(String.format(Locale.ROOT,
                    "data/scripts/generated/Source%05d.java", i));
            writeJava(target, seed, i);
        }
        for (int i = 0; i < scale.jars(); i++) {
            int mod = (i * 7 + 3) % scale.mods();
            Path jar = modRoots.get(mod).resolve(String.format(Locale.ROOT, "jars/synthetic-%03d.jar", i));
            writeJar(jar, seed, i, Math.max(1, scale.jars() / 2));
            int overrideMod = (mod + 1) % scale.mods();
            Path loose = modRoots.get(overrideMod).resolve(String.format(Locale.ROOT,
                    "classpath/generated/shared-%03d.txt", i % Math.max(1, scale.jars() / 2)));
            writeBytes(loose, ("loose-override-" + seed + "-" + i + "\n").getBytes(StandardCharsets.UTF_8));
        }
        Fingerprint fingerprint = fingerprint(profile);
        if (fingerprint.files() != scale.physicalFiles()) {
            throw new IOException("Generated physical file count differs from scale: " + fingerprint.files());
        }
        Manifest manifest = new Manifest(VERSION, seed, scale, fingerprint.files(), effects, streamed, fingerprint.sha256());
        writeManifest(profile.resolve(MANIFEST), manifest);
        return manifest;
    }

    public static Manifest readManifest(Path profile) throws IOException {
        Properties p = new Properties();
        try (InputStream input = Files.newInputStream(profile.toAbsolutePath().normalize().resolve(MANIFEST))) {
            p.load(input);
        }
        try {
            return new Manifest(p.getProperty("version"), Long.parseLong(p.getProperty("seed")),
                    Scale.parse(p.getProperty("scale")), Integer.parseInt(p.getProperty("physicalFiles")),
                    Integer.parseInt(p.getProperty("effectFiles")), Integer.parseInt(p.getProperty("streamedFiles")),
                    p.getProperty("fingerprintSha256"));
        } catch (RuntimeException error) {
            throw new IOException("Invalid extended synthetic manifest", error);
        }
    }

    public static Fingerprint fingerprint(Path profileRoot) throws IOException {
        Path profile = profileRoot.toAbsolutePath().normalize();
        Path mods = profile.resolve("mods");
        if (Files.isSymbolicLink(profile) || Files.isSymbolicLink(mods)
                || !Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing or symbolic extended profile mods directory");
        }
        MessageDigest digest = sha256();
        byte[] order = readBounded(profile.resolve("mod-order.txt"), 1024 * 1024);
        updateBytes(digest, order);
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(mods)) {
            var it = stream.iterator();
            while (it.hasNext()) {
                Path path = it.next();
                if (Files.isSymbolicLink(path)) throw new IOException("Extended profile contains a symbolic link: " + path);
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                if (files.size() >= MAX_FILES) throw new IOException("Extended profile exceeds file limit");
                files.add(path);
            }
        }
        files.sort(Comparator.comparing(path -> normalize(mods.relativize(path))));
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        for (Path file : files) {
            byte[] relative = normalize(mods.relativize(file)).getBytes(StandardCharsets.UTF_8);
            if (relative.length > MAX_PATH_BYTES) throw new IOException("Extended profile path exceeds limit");
            updateBytes(digest, relative);
            long fileBytes = 0;
            try (InputStream input = Files.newInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    fileBytes = Math.addExact(fileBytes, read);
                    total = Math.addExact(total, read);
                    if (total > MAX_TOTAL_BYTES) throw new IOException("Extended profile exceeds byte limit");
                    digest.update(buffer, 0, read);
                }
            }
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(fileBytes).array());
        }
        return new Fingerprint(files.size(), total, HexFormat.of().formatHex(digest.digest()));
    }

    private static void writeRepeatedResources(List<Path> mods, int count, long seed) throws IOException {
        int unique = uniqueCount(count);
        for (int i = 0; i < count; i++) {
            int logical = i < unique ? i : i - unique;
            int base = (logical * 11 + 3) % mods.size();
            int mod = i < unique ? base : (base + 1) % mods.size();
            Path target = mods.get(mod).resolve(String.format(Locale.ROOT,
                    "data/generated/resource-%05d.json", logical));
            writeBytes(target, ("{\"seed\":" + seed + ",\"source\":" + i + "}\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeRepeatedImages(List<Path> mods, int count, long seed, Random random) throws IOException {
        int unique = uniqueCount(count);
        for (int i = 0; i < count; i++) {
            int logical = i < unique ? i : i - unique;
            int base = logical % mods.size();
            int mod = i < unique ? base : (base + 1) % mods.size();
            Path target = mods.get(mod).resolve(String.format(Locale.ROOT,
                    "graphics/generated/image-%05d.png", logical));
            int width = 2 + i % 7, height = 2 + i % 5;
            BufferedImage image = new BufferedImage(width, height,
                    i % 2 == 0 ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_3BYTE_BGR);
            long mixed = seed ^ (0x9e3779b97f4a7c15L * (i + 1L)) ^ random.nextLong();
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int value = (int) (mixed + x * 0x45d9f3bL + y * 0x119de1f3L);
                image.setRGB(x, y, 0xff000000 | (value & 0x00ffffff));
            }
            Files.createDirectories(target.getParent());
            if (!ImageIO.write(image, "png", target.toFile())) throw new IOException("PNG writer unavailable");
        }
    }

    private static void writeWave(Path path, long seed, int index) throws IOException {
        int channels = index % 3 == 0 ? 2 : 1;
        int sampleRate = index % 2 == 0 ? 22_050 : 44_100;
        int frames = 64 + index % 33;
        int dataBytes = frames * channels * 2;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(44 + dataBytes);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeBytes("RIFF"); writeLeInt(output, 36 + dataBytes); output.writeBytes("WAVEfmt ");
            writeLeInt(output, 16); writeLeShort(output, 1); writeLeShort(output, channels);
            writeLeInt(output, sampleRate); writeLeInt(output, sampleRate * channels * 2);
            writeLeShort(output, channels * 2); writeLeShort(output, 16); output.writeBytes("data");
            writeLeInt(output, dataBytes);
            for (int frame = 0; frame < frames; frame++) for (int channel = 0; channel < channels; channel++) {
                long phase = seed + index * 31L + frame * 17L + channel * 7L;
                writeLeShort(output, (short) ((phase * 1103515245L + 12345L) >>> 16));
            }
        }
        writeBytes(path, bytes.toByteArray());
    }

    private static void writeJava(Path path, long seed, int index) throws IOException {
        String className = String.format(Locale.ROOT, "Source%05d", index);
        String dependency = index == 0 ? "0" : String.format(Locale.ROOT, "Source%05d.SEED", index - 1);
        String source = "package synthetic.generated;\npublic final class " + className + " {\n"
                + "  public static final long SEED = " + seed + "L + " + dependency + ";\n"
                + "  public int value() { Runnable r = new Runnable(){ public void run(){} }; r.run(); return Inner.VALUE + " + index + "; }\n"
                + "  static final class Inner { static final int VALUE = " + (index * 13 + 7) + "; }\n}\n";
        writeBytes(path, source.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeJar(Path path, long seed, int index, int sharedCount) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> names = List.of(String.format(Locale.ROOT, "classpath/generated/jar-only-%03d.txt", index),
                String.format(Locale.ROOT, "classpath/generated/shared-%03d.txt", index % sharedCount));
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            for (String name : names.stream().sorted().toList()) {
                JarEntry entry = new JarEntry(name); entry.setTime(0L); output.putNextEntry(entry);
                output.write(("jar-" + seed + "-" + index + "-" + name + "\n").getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static int uniqueCount(int count){return Math.max(1, count - Math.max(1, count / 4));}
    private static void writeBytes(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    private static void writeManifest(Path path, Manifest m) throws IOException {
        Properties p = new Properties(); p.setProperty("version", m.version()); p.setProperty("seed", Long.toString(m.seed()));
        p.setProperty("scale", m.scale().name()); p.setProperty("physicalFiles", Integer.toString(m.physicalFiles()));
        p.setProperty("effectFiles", Integer.toString(m.effectFiles())); p.setProperty("streamedFiles", Integer.toString(m.streamedFiles()));
        p.setProperty("fingerprintSha256", m.fingerprintSha256());
        try (var out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) { p.store(out, VERSION); }
    }
    private static void prepareRoot(Path root) throws IOException {
        if (root.getParent() == null) throw new IOException("Refusing filesystem root");
        Path home = Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (root.equals(home) || root.equals(cwd)) throw new IOException("Refusing home or working directory");
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) { Files.createDirectories(root); return; }
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Profile root is unsafe");
        try (var stream = Files.list(root)) { if (stream.findAny().isEmpty()) return; }
        Properties p = new Properties(); Path marker = root.resolve(MANIFEST);
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Occupied directory lacks generator marker");
        try (var input = Files.newInputStream(marker)) { p.load(input); }
        if (!VERSION.equals(p.getProperty("version"))) throw new IOException("Generator marker differs");
        try (var stream = Files.list(root)) { for (Path child : stream.toList()) delete(child); }
    }
    private static void delete(Path root) throws IOException { try (var stream = Files.walk(root)) { for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    private static byte[] readBounded(Path path, int max) throws IOException { try (InputStream in = Files.newInputStream(path)) { byte[] b = in.readNBytes(max + 1); if (b.length > max) throw new IOException("File exceeds limit"); return b; } }
    private static void updateBytes(MessageDigest d, byte[] b){d.update(ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}
    private static MessageDigest sha256(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String normalize(Path p){return p.toString().replace('\\','/');}
    private static void writeLeShort(DataOutputStream out,int v)throws IOException{out.writeByte(v&255);out.writeByte(v>>>8&255);} private static void writeLeInt(DataOutputStream out,int v)throws IOException{out.writeByte(v&255);out.writeByte(v>>>8&255);out.writeByte(v>>>16&255);out.writeByte(v>>>24&255);}
}
