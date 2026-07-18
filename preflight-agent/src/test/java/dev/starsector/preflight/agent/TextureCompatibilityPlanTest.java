package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class TextureCompatibilityPlanTest {
    @Test
    void insertsFailOpenLookupOnlyAfterOriginalPreloaderMiss() throws Exception {
        byte[] original = syntheticTextureLoader(true);

        byte[] transformed = TextureCompatibilityPlan.transform(ClassSignature.parse(original), original);

        assertNotNull(transformed);
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(transformed).accept(node, 0);
        MethodNode decode = method(node, "Ô00000", TextureCompatibilityPlan.DECODE_DESCRIPTOR);
        assertNotNull(decode);
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction = decode.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call) {
                calls.add(call);
            }
        }
        assertEquals(2, calls.size());
        assertEquals("com/fs/graphics/L", calls.get(0).owner);
        assertEquals("class", calls.get(0).name);
        assertEquals("dev/starsector/preflight/agent/TextureCompatibilityRuntime", calls.get(1).owner);
        assertEquals("load", calls.get(1).name);
        assertNull(TextureCompatibilityPlan.transform(ClassSignature.parse(transformed), transformed));
    }

    @Test
    void declinesDecodeBodyWithoutExactPreloaderHandoff() throws Exception {
        byte[] original = syntheticTextureLoader(false);

        assertNull(TextureCompatibilityPlan.transform(ClassSignature.parse(original), original));
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .findFirst()
                .orElse(null);
    }

    private static byte[] syntheticTextureLoader(boolean includePreloader) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                TextureCompatibilityPlan.TARGET_CLASS,
                null,
                "java/lang/Object",
                null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        MethodVisitor decode = writer.visitMethod(
                Opcodes.ACC_PRIVATE,
                TextureCompatibilityPlan.DECODE_METHOD,
                TextureCompatibilityPlan.DECODE_DESCRIPTOR,
                null,
                null);
        decode.visitCode();
        if (includePreloader) {
            decode.visitVarInsn(Opcodes.ALOAD, 1);
            decode.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/fs/graphics/L",
                    "class",
                    TextureCompatibilityPlan.DECODE_DESCRIPTOR,
                    false);
            decode.visitVarInsn(Opcodes.ASTORE, 2);
            decode.visitVarInsn(Opcodes.ALOAD, 2);
            Label miss = new Label();
            decode.visitJumpInsn(Opcodes.IFNULL, miss);
            decode.visitVarInsn(Opcodes.ALOAD, 2);
            decode.visitInsn(Opcodes.ARETURN);
            decode.visitLabel(miss);
        }
        decode.visitInsn(Opcodes.ACONST_NULL);
        decode.visitInsn(Opcodes.ARETURN);
        decode.visitMaxs(0, 0);
        decode.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
