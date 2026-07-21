package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class TexturePreparedPixelLoaderColorPlanTest {
    private static final String COLOR = "Ljava/awt/Color;";

    @Test
    void transformedWrapperWritesPreparedColorsToLoaderFields() throws Exception {
        byte[] fixture = fixture();
        byte[] transformed = TexturePreparedPixelPlan.transform(ClassSignature.parse(fixture), fixture);
        assertNotNull(transformed);

        ClassNode owner = new ClassNode(Opcodes.ASM9);
        new ClassReader(transformed).accept(owner, ClassReader.EXPAND_FRAMES);
        MethodNode wrapper = method(
                owner,
                TexturePreparedPixelPlan.CONVERT_METHOD,
                TexturePreparedPixelPlan.CONVERT_DESCRIPTOR);
        assertNotNull(wrapper);

        List<FieldInsnNode> writes = new ArrayList<>();
        for (AbstractInsnNode instruction = wrapper.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTFIELD
                    && COLOR.equals(field.desc)) {
                writes.add(field);
            }
        }

        assertEquals(3, writes.size());
        assertEquals(List.of("derived0", "derived1", "derived2"),
                writes.stream().map(field -> field.name).toList());
        assertTrue(writes.stream().allMatch(field ->
                TexturePreparedPixelPlan.TARGET_CLASS.equals(field.owner)
                        && receiverLoad(field) instanceof VarInsnNode receiver
                        && receiver.getOpcode() == Opcodes.ALOAD
                        && receiver.var == 0));

        MethodNode transfer = method(
                owner,
                "transferColors",
                "(L" + TexturePreparedPixelPlan.TEXTURE_OBJECT + ";)V");
        assertNotNull(transfer);
        assertEquals(3, setterCalls(transfer));
    }

    private static AbstractInsnNode receiverLoad(FieldInsnNode write) {
        AbstractInsnNode colorCall = previousOpcode(write);
        AbstractInsnNode preparedLoad = previousOpcode(colorCall);
        return previousOpcode(preparedLoad);
    }

    private static int setterCalls(MethodNode method) {
        int calls = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && TexturePreparedPixelPlan.TEXTURE_OBJECT.equals(call.owner)
                    && "(Ljava/awt/Color;)V".equals(call.desc)) {
                calls++;
            }
        }
        return calls;
    }

    private static byte[] fixture() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                TexturePreparedPixelPlan.TARGET_CLASS,
                null,
                "java/lang/Object",
                null);
        for (int i = 0; i < 3; i++) {
            writer.visitField(
                    Opcodes.ACC_PRIVATE,
                    "derived" + i,
                    COLOR,
                    null,
                    null).visitEnd();
        }

        MethodNode decode = new MethodNode(
                Opcodes.ASM9,
                Opcodes.ACC_PUBLIC,
                TexturePreparedPixelPlan.DECODE_METHOD,
                TexturePreparedPixelPlan.DECODE_DESCRIPTOR,
                null,
                null);
        LabelNode direct = new LabelNode();
        decode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        decode.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/fs/graphics/L",
                "class",
                TexturePreparedPixelPlan.DECODE_DESCRIPTOR,
                false));
        decode.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        decode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        decode.instructions.add(new JumpInsnNode(Opcodes.IFNULL, direct));
        decode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        decode.instructions.add(new InsnNode(Opcodes.ARETURN));
        decode.instructions.add(direct);
        decode.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        decode.instructions.add(new InsnNode(Opcodes.ARETURN));
        decode.accept(writer);

        MethodNode convert = new MethodNode(
                Opcodes.ASM9,
                Opcodes.ACC_PUBLIC,
                TexturePreparedPixelPlan.CONVERT_METHOD,
                TexturePreparedPixelPlan.CONVERT_DESCRIPTOR,
                null,
                null);
        convert.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        convert.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/awt/image/BufferedImage",
                "getRaster",
                "()Ljava/awt/image/WritableRaster;",
                false));
        convert.instructions.add(new InsnNode(Opcodes.POP));
        for (int i = 0; i < 3; i++) {
            convert.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            convert.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            convert.instructions.add(new FieldInsnNode(
                    Opcodes.PUTFIELD,
                    TexturePreparedPixelPlan.TARGET_CLASS,
                    "derived" + i,
                    COLOR));
        }
        convert.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        convert.instructions.add(new InsnNode(Opcodes.ARETURN));
        convert.accept(writer);

        MethodNode cleanup = new MethodNode(
                Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                TexturePreparedPixelPlan.CLEANUP_METHOD,
                TexturePreparedPixelPlan.CLEANUP_DESCRIPTOR,
                null,
                null);
        cleanup.instructions.add(new InsnNode(Opcodes.RETURN));
        cleanup.accept(writer);

        MethodNode transfer = new MethodNode(
                Opcodes.ASM9,
                Opcodes.ACC_PUBLIC,
                "transferColors",
                "(L" + TexturePreparedPixelPlan.TEXTURE_OBJECT + ";)V",
                null,
                null);
        for (int i = 0; i < 3; i++) {
            transfer.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            transfer.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            transfer.instructions.add(new FieldInsnNode(
                    Opcodes.GETFIELD,
                    TexturePreparedPixelPlan.TARGET_CLASS,
                    "derived" + i,
                    COLOR));
            transfer.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    TexturePreparedPixelPlan.TEXTURE_OBJECT,
                    "setColor" + i,
                    "(Ljava/awt/Color;)V",
                    false));
        }
        transfer.instructions.add(new InsnNode(Opcodes.RETURN));
        transfer.accept(writer);

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .findFirst()
                .orElse(null);
    }

    private static AbstractInsnNode previousOpcode(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction == null ? null : instruction.getPrevious();
        while (current instanceof LabelNode
                || current instanceof LineNumberNode
                || current instanceof FrameNode) {
            current = current.getPrevious();
        }
        return current;
    }
}
