package dev.starsector.preflight.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Exact target rewrite for upload-ready prepared pixels below the ImageIO compatibility seam. */
final class TexturePreparedPixelPlan {
    static final String TARGET_CLASS = "com/fs/graphics/TextureLoader";
    static final String TEXTURE_OBJECT = "com/fs/graphics/Object";
    static final String DECODE_METHOD = "Ô00000";
    static final String DECODE_DESCRIPTOR = "(Ljava/lang/String;)Ljava/awt/image/BufferedImage;";
    static final String CONVERT_METHOD = "o00000";
    static final String CONVERT_DESCRIPTOR =
            "(Ljava/awt/image/BufferedImage;Lcom/fs/graphics/Object;)Ljava/nio/ByteBuffer;";
    static final String CLEANUP_METHOD = "o00000";
    static final String CLEANUP_DESCRIPTOR = "(Ljava/nio/ByteBuffer;Ljava/lang/String;)V";

    private static final String ORIGINAL_DECODE = "preflight$original$decodeImage";
    private static final String ORIGINAL_CONVERT = "preflight$original$convertPixels";
    private static final String ORIGINAL_CLEANUP = "preflight$original$cleanupBuffer";
    private static final String RUNTIME = "dev/starsector/preflight/agent/TexturePreparedPixelRuntime";
    private static final String PREPARED_PIXEL = RUNTIME + "$PreparedPixel";
    private static final String COLOR_DESCRIPTOR = "Ljava/awt/Color;";

    private TexturePreparedPixelPlan() {
    }

    static byte[] transform(ClassSignature signature, byte[] originalBytes) {
        if (!TARGET_CLASS.equals(signature.internalName())
                || !signature.hasMethod(DECODE_METHOD, DECODE_DESCRIPTOR)
                || !signature.hasMethod(CONVERT_METHOD, CONVERT_DESCRIPTOR)
                || !signature.hasMethod(CLEANUP_METHOD, CLEANUP_DESCRIPTOR)) {
            return null;
        }

        ClassNode owner = new ClassNode(Opcodes.ASM9);
        new ClassReader(originalBytes).accept(owner, ClassReader.EXPAND_FRAMES);
        if (!TARGET_CLASS.equals(owner.name)) {
            return null;
        }

        MethodNode decode = uniqueMethod(owner, DECODE_METHOD, DECODE_DESCRIPTOR);
        MethodNode convert = uniqueMethod(owner, CONVERT_METHOD, CONVERT_DESCRIPTOR);
        MethodNode cleanup = uniqueMethod(owner, CLEANUP_METHOD, CLEANUP_DESCRIPTOR);
        if (!eligibleInstance(decode) || !eligibleInstance(convert) || !eligibleStatic(cleanup)
                || hasMethod(owner, ORIGINAL_DECODE, DECODE_DESCRIPTOR)
                || hasMethod(owner, ORIGINAL_CONVERT, CONVERT_DESCRIPTOR)
                || hasMethod(owner, ORIGINAL_CLEANUP, CLEANUP_DESCRIPTOR)) {
            return null;
        }

        List<ColorField> colors = reviewedColorFields(convert);
        if (colors.size() != 3) {
            return null;
        }

        MethodMetadata decodeMetadata = rename(owner.name, decode, ORIGINAL_DECODE, DECODE_METHOD, DECODE_DESCRIPTOR);
        MethodMetadata convertMetadata = rename(owner.name, convert, ORIGINAL_CONVERT, CONVERT_METHOD, CONVERT_DESCRIPTOR);
        MethodMetadata cleanupMetadata = rename(owner.name, cleanup, ORIGINAL_CLEANUP, CLEANUP_METHOD, CLEANUP_DESCRIPTOR);
        owner.methods.add(decodeWrapper(owner.name, decodeMetadata));
        owner.methods.add(convertWrapper(owner.name, convertMetadata, colors));
        owner.methods.add(cleanupWrapper(owner.name, cleanupMetadata));

        ClassWriter writer = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        owner.accept(writer);
        return writer.toByteArray();
    }

    private static List<ColorField> reviewedColorFields(MethodNode convert) {
        Map<String, ColorField> fields = new LinkedHashMap<>();
        boolean rasterRead = false;
        for (AbstractInsnNode instruction = convert.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTFIELD
                    && TEXTURE_OBJECT.equals(field.owner)
                    && COLOR_DESCRIPTOR.equals(field.desc)) {
                fields.putIfAbsent(field.name + field.desc, new ColorField(field.name, field.desc));
            }
            if (instruction instanceof MethodInsnNode call
                    && "java/awt/image/BufferedImage".equals(call.owner)
                    && ("getData".equals(call.name) || "getRaster".equals(call.name))) {
                rasterRead = true;
            }
        }
        return rasterRead && fields.size() == 3 ? List.copyOf(fields.values()) : List.of();
    }

    private static MethodNode decodeWrapper(String owner, MethodMetadata metadata) {
        MethodNode wrapper = method(metadata, DECODE_METHOD, DECODE_DESCRIPTOR);
        LabelNode fallback = new LabelNode();
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, RUNTIME, "load", DECODE_DESCRIPTOR, false));
        wrapper.instructions.add(new InsnNode(Opcodes.DUP));
        wrapper.instructions.add(new JumpInsnNode(Opcodes.IFNULL, fallback));
        wrapper.instructions.add(new InsnNode(Opcodes.ARETURN));
        wrapper.instructions.add(fallback);
        wrapper.instructions.add(new InsnNode(Opcodes.POP));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner, ORIGINAL_DECODE, DECODE_DESCRIPTOR, false));
        wrapper.instructions.add(new InsnNode(Opcodes.ARETURN));
        return wrapper;
    }

    private static MethodNode convertWrapper(
            String owner,
            MethodMetadata metadata,
            List<ColorField> colors) {
        MethodNode wrapper = method(metadata, CONVERT_METHOD, CONVERT_DESCRIPTOR);
        LabelNode ordinary = new LabelNode();
        LabelNode preparedFallback = new LabelNode();

        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                RUNTIME,
                "isCarrier",
                "(Ljava/awt/image/BufferedImage;)Z",
                false));
        wrapper.instructions.add(new JumpInsnNode(Opcodes.IFEQ, ordinary));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                RUNTIME,
                "prepare",
                "(Ljava/awt/image/BufferedImage;)L" + PREPARED_PIXEL + ";",
                false));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        wrapper.instructions.add(new JumpInsnNode(Opcodes.IFNULL, preparedFallback));

        for (int i = 0; i < colors.size(); i++) {
            wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
            wrapper.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    PREPARED_PIXEL,
                    "color" + i,
                    "()Ljava/awt/Color;",
                    false));
            ColorField field = colors.get(i);
            wrapper.instructions.add(new FieldInsnNode(
                    Opcodes.PUTFIELD, TEXTURE_OBJECT, field.name(), field.descriptor()));
        }
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                PREPARED_PIXEL,
                "buffer",
                "()Ljava/nio/ByteBuffer;",
                false));
        wrapper.instructions.add(new InsnNode(Opcodes.ARETURN));

        wrapper.instructions.add(preparedFallback);
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                RUNTIME,
                "originalPath",
                "(Ljava/awt/image/BufferedImage;)Ljava/lang/String;",
                false));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner, ORIGINAL_DECODE, DECODE_DESCRIPTOR, false));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ASTORE, 4));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner, ORIGINAL_CONVERT, CONVERT_DESCRIPTOR, false));
        wrapper.instructions.add(new InsnNode(Opcodes.ARETURN));

        wrapper.instructions.add(ordinary);
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner, ORIGINAL_CONVERT, CONVERT_DESCRIPTOR, false));
        wrapper.instructions.add(new InsnNode(Opcodes.ARETURN));
        return wrapper;
    }

    private static MethodNode cleanupWrapper(String owner, MethodMetadata metadata) {
        MethodNode wrapper = method(metadata, CLEANUP_METHOD, CLEANUP_DESCRIPTOR);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        wrapper.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Throwable"));

        wrapper.instructions.add(start);
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, owner, ORIGINAL_CLEANUP, CLEANUP_DESCRIPTOR, false));
        wrapper.instructions.add(end);
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, RUNTIME, "release", "(Ljava/nio/ByteBuffer;)V", false));
        wrapper.instructions.add(new InsnNode(Opcodes.RETURN));

        wrapper.instructions.add(handler);
        wrapper.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, RUNTIME, "release", "(Ljava/nio/ByteBuffer;)V", false));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        wrapper.instructions.add(new InsnNode(Opcodes.ATHROW));
        return wrapper;
    }

    private static MethodMetadata rename(
            String owner,
            MethodNode original,
            String replacement,
            String sourceName,
            String descriptor) {
        MethodMetadata metadata = new MethodMetadata(
                original.access,
                original.signature,
                original.exceptions == null ? List.of() : List.copyOf(original.exceptions));
        original.name = replacement;
        original.access = (original.access
                & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_BRIDGE
                | Opcodes.ACC_VARARGS | Opcodes.ACC_STRICT))
                | Opcodes.ACC_PRIVATE
                | Opcodes.ACC_SYNTHETIC;
        rewriteSelfCalls(owner, original, sourceName, replacement, descriptor);
        return metadata;
    }

    private static MethodNode method(MethodMetadata metadata, String name, String descriptor) {
        return new MethodNode(
                Opcodes.ASM9,
                metadata.access(),
                name,
                descriptor,
                metadata.signature(),
                metadata.exceptions().toArray(String[]::new));
    }

    private static MethodNode uniqueMethod(ClassNode owner, String name, String descriptor) {
        MethodNode found = null;
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                if (found != null) {
                    return null;
                }
                found = method;
            }
        }
        return found;
    }

    private static boolean hasMethod(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().anyMatch(method -> name.equals(method.name) && descriptor.equals(method.desc));
    }

    private static boolean eligibleInstance(MethodNode method) {
        return method != null
                && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC)) == 0;
    }

    private static boolean eligibleStatic(MethodNode method) {
        return method != null
                && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0
                && (method.access & Opcodes.ACC_STATIC) != 0;
    }

    private static void rewriteSelfCalls(
            String owner,
            MethodNode method,
            String sourceName,
            String replacement,
            String descriptor) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call
                    && owner.equals(call.owner)
                    && sourceName.equals(call.name)
                    && descriptor.equals(call.desc)) {
                call.name = replacement;
                call.setOpcode((method.access & Opcodes.ACC_STATIC) != 0
                        ? Opcodes.INVOKESTATIC
                        : Opcodes.INVOKESPECIAL);
            }
        }
    }

    private record ColorField(String name, String descriptor) {
    }

    private record MethodMetadata(int access, String signature, List<String> exceptions) {
        private MethodMetadata {
            exceptions = List.copyOf(exceptions);
        }
    }

    private static final class SafeClassWriter extends ClassWriter {
        private SafeClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return type1.equals(type2) ? type1 : "java/lang/Object";
        }
    }
}
