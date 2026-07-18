package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class TexturePreparedPixelPlanTest {
    @Test
    void rewritesReviewedDecodeConvertAndCleanupPattern() throws Exception {
        byte[] original = textureLoader(3, true);
        byte[] transformed = TexturePreparedPixelPlan.transform(ClassSignature.parse(original), original);

        assertNotNull(transformed);
        ClassNode node = read(transformed);
        assertNotNull(method(node, "preflight$original$decodeImage", TexturePreparedPixelPlan.DECODE_DESCRIPTOR));
        assertNotNull(method(node, "preflight$original$convertPixels", TexturePreparedPixelPlan.CONVERT_DESCRIPTOR));
        assertNotNull(method(node, "preflight$original$cleanupBuffer", TexturePreparedPixelPlan.CLEANUP_DESCRIPTOR));

        MethodNode decode = method(node, TexturePreparedPixelPlan.DECODE_METHOD, TexturePreparedPixelPlan.DECODE_DESCRIPTOR);
        MethodNode directDecode = method(node, "preflight$original$decodeImage", TexturePreparedPixelPlan.DECODE_DESCRIPTOR);
        assertTrue(hasCall(decode, "com/fs/graphics/L", "class"));
        assertTrue(hasCall(decode, "TexturePreparedPixelRuntime", "load"));
        assertFalse(hasCall(directDecode, "com/fs/graphics/L", "class"));
        assertFalse(hasCall(directDecode, "TexturePreparedPixelRuntime", "load"));

        MethodNode convert = method(node, TexturePreparedPixelPlan.CONVERT_METHOD, TexturePreparedPixelPlan.CONVERT_DESCRIPTOR);
        assertEquals(List.of("derived0", "derived1", "derived2"), fieldWrites(convert));
        assertTrue(hasCall(convert, "TexturePreparedPixelRuntime$PreparedPixel", "buffer"));

        MethodNode cleanup = method(node, TexturePreparedPixelPlan.CLEANUP_METHOD, TexturePreparedPixelPlan.CLEANUP_DESCRIPTOR);
        assertTrue(hasCall(cleanup, "TexturePreparedPixelRuntime", "release"));
        assertNull(TexturePreparedPixelPlan.transform(ClassSignature.parse(transformed), transformed));
    }

    @Test
    void rejectsAmbiguousOrUnreviewedConversionShape() throws Exception {
        byte[] twoColors = textureLoader(2, true);
        byte[] noRasterRead = textureLoader(3, false);

        assertNull(TexturePreparedPixelPlan.transform(ClassSignature.parse(twoColors), twoColors));
        assertNull(TexturePreparedPixelPlan.transform(ClassSignature.parse(noRasterRead), noRasterRead));
    }

    private static List<String> fieldWrites(MethodNode method) {
        List<String> writes = new ArrayList<>();
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD) {
                writes.add(field.name);
            }
        }
        return List.copyOf(writes);
    }

    private static boolean hasCall(MethodNode method, String ownerFragment, String name) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.contains(ownerFragment)
                    && name.equals(call.name)) {
                return true;
            }
        }
        return false;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .findFirst()
                .orElse(null);
    }

    private static byte[] textureLoader(int colorFields, boolean rasterRead) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, TexturePreparedPixelPlan.TARGET_CLASS,
                null, "java/lang/Object", null);
        constructor(writer);

        MethodVisitor decode = writer.visitMethod(
                Opcodes.ACC_PRIVATE,
                TexturePreparedPixelPlan.DECODE_METHOD,
                TexturePreparedPixelPlan.DECODE_DESCRIPTOR,
                null,
                null);
        decode.visitCode();
        decode.visitVarInsn(Opcodes.ALOAD, 1);
        decode.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/fs/graphics/L",
                "class",
                TexturePreparedPixelPlan.DECODE_DESCRIPTOR,
                false);
        decode.visitVarInsn(Opcodes.ASTORE, 2);
        decode.visitVarInsn(Opcodes.ALOAD, 2);
        Label direct = new Label();
        decode.visitJumpInsn(Opcodes.IFNULL, direct);
        decode.visitVarInsn(Opcodes.ALOAD, 2);
        decode.visitInsn(Opcodes.ARETURN);
        decode.visitLabel(direct);
        decode.visitInsn(Opcodes.ACONST_NULL);
        decode.visitInsn(Opcodes.ARETURN);
        decode.visitMaxs(0, 0);
        decode.visitEnd();

        MethodVisitor convert = writer.visitMethod(
                Opcodes.ACC_PRIVATE,
                TexturePreparedPixelPlan.CONVERT_METHOD,
                TexturePreparedPixelPlan.CONVERT_DESCRIPTOR,
                null,
                null);
        convert.visitCode();
        if (rasterRead) {
            convert.visitVarInsn(Opcodes.ALOAD, 1);
            convert.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/awt/image/BufferedImage",
                    "getData",
                    "()Ljava/awt/image/Raster;",
                    false);
            convert.visitInsn(Opcodes.POP);
        }
        for (int i = 0; i < colorFields; i++) {
            convert.visitVarInsn(Opcodes.ALOAD, 2);
            convert.visitInsn(Opcodes.ACONST_NULL);
            convert.visitFieldInsn(
                    Opcodes.PUTFIELD,
                    TexturePreparedPixelPlan.TEXTURE_OBJECT,
                    "derived" + i,
                    "Ljava/awt/Color;");
        }
        convert.visitInsn(Opcodes.ACONST_NULL);
        convert.visitInsn(Opcodes.ARETURN);
        convert.visitMaxs(0, 0);
        convert.visitEnd();

        MethodVisitor cleanup = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                TexturePreparedPixelPlan.CLEANUP_METHOD,
                TexturePreparedPixelPlan.CLEANUP_DESCRIPTOR,
                null,
                null);
        cleanup.visitCode();
        cleanup.visitInsn(Opcodes.RETURN);
        cleanup.visitMaxs(0, 0);
        cleanup.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void constructor(ClassWriter writer) {
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }
}
