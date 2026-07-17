package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
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

        MethodNode convert = method(node, TexturePreparedPixelPlan.CONVERT_METHOD, TexturePreparedPixelPlan.CONVERT_DESCRIPTOR);
        List<FieldInsnNode> writes = convert.instructions.stream()
                .filter(FieldInsnNode.class::isInstance)
                .map(FieldInsnNode.class::cast)
                .filter(field -> field.getOpcode() == Opcodes.PUTFIELD)
                .toList();
        assertEquals(List.of("derived0", "derived1", "derived2"), writes.stream().map(field -> field.name).toList());
        assertTrue(convert.instructions.stream()
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.contains("TexturePreparedPixelRuntime$PreparedPixel")
                        && "buffer".equals(call.name)));

        MethodNode cleanup = method(node, TexturePreparedPixelPlan.CLEANUP_METHOD, TexturePreparedPixelPlan.CLEANUP_DESCRIPTOR);
        assertTrue(cleanup.instructions.stream()
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .anyMatch(call -> "release".equals(call.name)));
        assertNull(TexturePreparedPixelPlan.transform(ClassSignature.parse(transformed), transformed));
    }

    @Test
    void rejectsAmbiguousOrUnreviewedConversionShape() throws Exception {
        byte[] twoColors = textureLoader(2, true);
        byte[] noRasterRead = textureLoader(3, false);

        assertNull(TexturePreparedPixelPlan.transform(ClassSignature.parse(twoColors), twoColors));
        assertNull(TexturePreparedPixelPlan.transform(ClassSignature.parse(noRasterRead), noRasterRead));
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
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
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
