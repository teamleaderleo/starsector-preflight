package dev.starsector.preflight.core;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

/** Minimal bounded classfile parser used to verify a generated bundle's map key. */
final class ClassFileIdentity {
    private static final int CLASSFILE_MAGIC = 0xcafebabe;
    private static final int MAX_CONSTANT_POOL_ENTRIES = 65_535;

    private ClassFileIdentity() {
    }

    static String binaryName(byte[] classfile) throws IOException {
        if (classfile == null || classfile.length < 10) {
            throw new IOException("Classfile is too small");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(classfile))) {
            if (input.readInt() != CLASSFILE_MAGIC) {
                throw new IOException("Classfile magic header is invalid");
            }
            input.readUnsignedShort(); // minor
            input.readUnsignedShort(); // major
            int count = input.readUnsignedShort();
            if (count < 2 || count > MAX_CONSTANT_POOL_ENTRIES) {
                throw new IOException("Classfile constant-pool count is invalid: " + count);
            }
            byte[] tags = new byte[count];
            int[] classNameIndexes = new int[count];
            String[] utf8 = new String[count];
            for (int index = 1; index < count; index++) {
                int tag = input.readUnsignedByte();
                tags[index] = (byte) tag;
                switch (tag) {
                    case 1 -> utf8[index] = input.readUTF();
                    case 3, 4 -> skipFully(input, 4);
                    case 5, 6 -> {
                        skipFully(input, 8);
                        index++;
                    }
                    case 7 -> classNameIndexes[index] = input.readUnsignedShort();
                    case 8, 16, 19, 20 -> skipFully(input, 2);
                    case 9, 10, 11, 12, 17, 18 -> skipFully(input, 4);
                    case 15 -> skipFully(input, 3);
                    default -> throw new IOException("Unsupported classfile constant-pool tag: " + tag);
                }
            }
            input.readUnsignedShort(); // access flags
            int thisClass = input.readUnsignedShort();
            if (thisClass <= 0 || thisClass >= count || tags[thisClass] != 7) {
                throw new IOException("Classfile this_class entry is invalid");
            }
            int nameIndex = classNameIndexes[thisClass];
            if (nameIndex <= 0 || nameIndex >= count || tags[nameIndex] != 1 || utf8[nameIndex] == null) {
                throw new IOException("Classfile this_class name entry is invalid");
            }
            String internalName = utf8[nameIndex];
            if (internalName.isBlank() || internalName.startsWith("[") || internalName.indexOf('.') >= 0) {
                throw new IOException("Classfile internal name is invalid: " + internalName);
            }
            return internalName.replace('/', '.');
        } catch (EOFException error) {
            throw new IOException("Classfile ended inside its constant pool", error);
        }
    }

    private static void skipFully(DataInputStream input, int bytes) throws IOException {
        if (input.skipBytes(bytes) != bytes) {
            throw new EOFException("Classfile ended inside its constant pool");
        }
    }
}
