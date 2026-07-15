package dev.starsector.preflight.agent;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Minimal Java 17 classfile parser used for fail-closed adapter signature checks. */
record ClassSignature(
        String internalName,
        String sha256,
        int majorVersion,
        int access,
        List<Method> methods) {
    private static final int CLASS_MAGIC = 0xCAFEBABE;
    private static final int MAX_CONSTANT_POOL = 65_535;
    private static final int MAX_MEMBERS = 65_535;

    ClassSignature {
        internalName = Objects.requireNonNull(internalName, "internalName");
        sha256 = Objects.requireNonNull(sha256, "sha256");
        methods = List.copyOf(methods);
    }

    static ClassSignature parse(byte[] classBytes) throws IOException {
        Objects.requireNonNull(classBytes, "classBytes");
        if (classBytes.length < 10) {
            throw new IOException("Classfile is too short");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            if (input.readInt() != CLASS_MAGIC) {
                throw new IOException("Classfile magic does not match CAFEBABE");
            }
            input.readUnsignedShort(); // minor
            int major = input.readUnsignedShort();
            Object[] constantPool = readConstantPool(input);
            int access = input.readUnsignedShort();
            int thisClass = input.readUnsignedShort();
            input.readUnsignedShort(); // super
            String internalName = className(constantPool, thisClass);

            int interfaces = boundedCount(input.readUnsignedShort(), "interfaces");
            for (int i = 0; i < interfaces; i++) {
                input.readUnsignedShort();
            }

            skipMembers(input, constantPool, "fields");
            List<Method> methods = readMethods(input, constantPool);
            skipAttributes(input);
            methods.sort(Comparator.comparing(Method::name)
                    .thenComparing(Method::descriptor)
                    .thenComparingInt(Method::access));
            return new ClassSignature(internalName, sha256(classBytes), major, access, methods);
        } catch (EOFException error) {
            throw new IOException("Classfile ended unexpectedly", error);
        }
    }

    boolean hasMethod(String name, String descriptor) {
        return methods.stream().anyMatch(method -> method.name().equals(name)
                && method.descriptor().equals(descriptor));
    }

    private static Object[] readConstantPool(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        if (count < 1 || count > MAX_CONSTANT_POOL) {
            throw new IOException("Invalid constant-pool count: " + count);
        }
        Object[] pool = new Object[count];
        for (int i = 1; i < count; i++) {
            int tag = input.readUnsignedByte();
            switch (tag) {
                case 1 -> pool[i] = input.readUTF();
                case 3, 4 -> input.skipNBytes(4);
                case 5, 6 -> {
                    input.skipNBytes(8);
                    i++;
                }
                case 7 -> pool[i] = new ClassReference(input.readUnsignedShort());
                case 8, 16, 19, 20 -> input.skipNBytes(2);
                case 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                case 15 -> input.skipNBytes(3);
                default -> throw new IOException("Unsupported constant-pool tag: " + tag);
            }
        }
        return pool;
    }

    private static void skipMembers(DataInputStream input, Object[] pool, String kind) throws IOException {
        int count = boundedCount(input.readUnsignedShort(), kind);
        for (int i = 0; i < count; i++) {
            input.readUnsignedShort();
            utf8(pool, input.readUnsignedShort());
            utf8(pool, input.readUnsignedShort());
            skipAttributes(input);
        }
    }

    private static List<Method> readMethods(DataInputStream input, Object[] pool) throws IOException {
        int count = boundedCount(input.readUnsignedShort(), "methods");
        List<Method> methods = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int access = input.readUnsignedShort();
            String name = utf8(pool, input.readUnsignedShort());
            String descriptor = utf8(pool, input.readUnsignedShort());
            methods.add(new Method(name, descriptor, access));
            skipAttributes(input);
        }
        return methods;
    }

    private static void skipAttributes(DataInputStream input) throws IOException {
        int count = boundedCount(input.readUnsignedShort(), "attributes");
        for (int i = 0; i < count; i++) {
            input.readUnsignedShort();
            long length = Integer.toUnsignedLong(input.readInt());
            input.skipNBytes(length);
        }
    }

    private static int boundedCount(int count, String kind) throws IOException {
        if (count < 0 || count > MAX_MEMBERS) {
            throw new IOException("Invalid " + kind + " count: " + count);
        }
        return count;
    }

    private static String className(Object[] pool, int index) throws IOException {
        if (index <= 0 || index >= pool.length || !(pool[index] instanceof ClassReference reference)) {
            throw new IOException("Invalid class constant-pool index: " + index);
        }
        return utf8(pool, reference.nameIndex());
    }

    private static String utf8(Object[] pool, int index) throws IOException {
        if (index <= 0 || index >= pool.length || !(pool[index] instanceof String value)) {
            throw new IOException("Invalid UTF-8 constant-pool index: " + index);
        }
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    record Method(String name, String descriptor, int access) {
        Method {
            name = Objects.requireNonNull(name, "name");
            descriptor = Objects.requireNonNull(descriptor, "descriptor");
        }
    }

    private record ClassReference(int nameIndex) {
    }
}