package dev.starsector.preflight.agent;

import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Target-specific rewrite for the reviewed TextureLoader decoded-image seam. */
final class TextureCompatibilityPlan {
    static final String TARGET_CLASS = "com/fs/graphics/TextureLoader";
    static final String DECODE_METHOD = "Ô00000";
    static final String DECODE_DESCRIPTOR = "(Ljava/lang/String;)Ljava/awt/image/BufferedImage;";
    private static final String ORIGINAL_METHOD = "preflight$original$decodeImage";
    private static final String RUNTIME = "dev/starsector/preflight/agent/TextureCompatibilityRuntime";

    private TextureCompatibilityPlan() {
    }

    static byte[] transform(ClassSignature signature, byte[] originalBytes) {
        if (!TARGET_CLASS.equals(signature.internalName())
                || !signature.hasMethod(DECODE_METHOD, DECODE_DESCRIPTOR)) {
            return null;
        }
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        new ClassReader(originalBytes).accept(owner, 0);
        if (!TARGET_CLASS.equals(owner.name)) {
            return null;
        }

        MethodNode original = null;
        for (MethodNode method : owner.methods) {
            if (ORIGINAL_METHOD.equals(method.name) && DECODE_DESCRIPTOR.equals(method.desc)) {
                return null;
            }
            if (DECODE_METHOD.equals(method.name) && DECODE_DESCRIPTOR.equals(method.desc)) {
                if (original != null) {
                    return null;
                }
                original = method;
            }
        }
        if (original == null
                || (original.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                || (original.access & Opcodes.ACC_STATIC) != 0) {
            return null;
        }

        int originalAccess = original.access;
        String originalSignature = original.signature;
        List<String> originalExceptions = original.exceptions == null ? List.of() : List.copyOf(original.exceptions);
        original.name = ORIGINAL_METHOD;
        original.access = (originalAccess
                & (Opcodes.ACC_FINAL | Opcodes.ACC_BRIDGE | Opcodes.ACC_VARARGS | Opcodes.ACC_STRICT))
                | Opcodes.ACC_PRIVATE
                | Opcodes.ACC_SYNTHETIC;
        rewriteSelfCalls(owner.name, original);

        MethodNode wrapper = new MethodNode(
                Opcodes.ASM9,
                originalAccess,
                DECODE_METHOD,
                DECODE_DESCRIPTOR,
                originalSignature,
                originalExceptions.toArray(String[]::new));
        LabelNode fallback = new LabelNode();
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                RUNTIME,
                "load",
                DECODE_DESCRIPTOR,
                false));
        wrapper.instructions.add(new InsnNode(Opcodes.DUP));
        wrapper.instructions.add(new JumpInsnNode(Opcodes.IFNULL, fallback));
        wrapper.instructions.add(new InsnNode(Opcodes.ARETURN));
        wrapper.instructions.add(fallback);
        wrapper.instructions.add(new FrameNode(
                Opcodes.F_SAME1,
                0,
                null,
                1,
                new Object[] {"java/awt/image/BufferedImage"}));
        wrapper.instructions.add(new InsnNode(Opcodes.POP));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        wrapper.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                owner.name,
                ORIGINAL_METHOD,
                DECODE_DESCRIPTOR,
                false));
        wrapper.instructions.add(new InsnNode(Opcodes.ARETURN));
        wrapper.maxStack = 2;
        wrapper.maxLocals = 2;
        owner.methods.add(wrapper);

        ClassWriter writer = new ClassWriter(0);
        owner.accept(writer);
        return writer.toByteArray();
    }

    private static void rewriteSelfCalls(String owner, MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call
                    && owner.equals(call.owner)
                    && DECODE_METHOD.equals(call.name)
                    && DECODE_DESCRIPTOR.equals(call.desc)) {
                call.name = ORIGINAL_METHOD;
                call.setOpcode(Opcodes.INVOKESPECIAL);
            }
        }
    }
}
