package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class TextureCompatibilityPlanTest {
    @Test
    void movesOriginalBodyAndAddsDirectFailOpenWrapper() throws Exception {
        byte[] original = syntheticTextureLoader();
        ClassSignature signature = ClassSignature.parse(original);

        byte[] transformed = TextureCompatibilityPlan.transform(signature, original);

        assertNotNull(transformed);
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(transformed).accept(node, 0);
        MethodNode wrapper = method(node, "Ô00000", TextureCompatibilityPlan.DECODE_DESCRIPTOR);
        MethodNode fallback = method(node, "preflight$original$decodeImage", TextureCompatibilityPlan.DECODE_DESCRIPTOR);
        assertNotNull(wrapper);
        assertNotNull(fallback);
        assertTrue((fallback.access & Opcodes.ACC_PRIVATE) != 0);
        assertTrue((fallback.access & Opcodes.ACC_SYNTHETIC) != 0);
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction = wrapper.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call) {
                calls.add(call);
            }
        }
        assertEquals(2, calls.size());
        assertEquals("dev/starsector/preflight/agent/TextureCompatibilityRuntime", calls.get(0).owner);
        assertEquals("load", calls.get(0).name);
        assertEquals(Opcodes.INVOKESTATIC, calls.get(0).getOpcode());
        assertEquals("preflight$original$decodeImage", calls.get(1).name);
        assertEquals(Opcodes.INVOKESPECIAL, calls.get(1).getOpcode());

        assertNull(TextureCompatibilityPlan.transform(ClassSignature.parse(transformed), transformed));
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .findFirst()
                .orElse(null);
    }

    private static byte[] syntheticTextureLoader() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
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
        decode.visitInsn(Opcodes.ACONST_NULL);
        decode.visitInsn(Opcodes.ARETURN);
        decode.visitMaxs(0, 0);
        decode.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
