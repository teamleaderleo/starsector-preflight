package dev.starsector.preflight.agent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Exact color sink evidence for the prepared-pixel conversion bypass. */
final class TexturePreparedPixelColorSink {
    private static final String TARGET_CLASS = TexturePreparedPixelPlan.TARGET_CLASS;
    private static final String TEXTURE_OBJECT = TexturePreparedPixelPlan.TEXTURE_OBJECT;
    private static final String TEXTURE_OBJECT_DESCRIPTOR = "L" + TEXTURE_OBJECT + ";";
    private static final String COLOR_DESCRIPTOR = "Ljava/awt/Color;";
    private static final String COLOR_SETTER_DESCRIPTOR = "(Ljava/awt/Color;)V";

    private TexturePreparedPixelColorSink() {
    }

    static List<SinkField> reviewed(ClassNode owner, MethodNode convert) {
        if (!hasRasterRead(convert)) {
            return List.of();
        }

        List<SinkField> direct = colorFields(convert, TEXTURE_OBJECT, 2);
        List<SinkField> staged = colorFields(convert, TARGET_CLASS, 0);
        boolean directEligible = direct.size() == 3;
        boolean stagedEligible = staged.size() == 3
                && fieldsExistOnOwner(owner, staged)
                && hasReviewedSetterTransfers(owner, convert, staged);

        if (directEligible == stagedEligible) {
            return List.of();
        }
        return directEligible ? direct : staged;
    }

    private static boolean hasRasterRead(MethodNode convert) {
        for (AbstractInsnNode instruction = convert.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call
                    && "java/awt/image/BufferedImage".equals(call.owner)
                    && ("getData".equals(call.name) || "getRaster".equals(call.name))) {
                return true;
            }
        }
        return false;
    }

    private static List<SinkField> colorFields(
            MethodNode convert,
            String fieldOwner,
            int receiverLocal) {
        Map<String, SinkField> fields = new LinkedHashMap<>();
        for (AbstractInsnNode instruction = convert.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTFIELD
                    && fieldOwner.equals(field.owner)
                    && COLOR_DESCRIPTOR.equals(field.desc)) {
                fields.putIfAbsent(
                        field.name + field.desc,
                        new SinkField(field.owner, field.name, field.desc, receiverLocal));
            }
        }
        return fields.size() == 3 ? List.copyOf(fields.values()) : List.of();
    }

    private static boolean fieldsExistOnOwner(ClassNode owner, List<SinkField> fields) {
        Set<String> declared = new LinkedHashSet<>();
        for (FieldNode field : owner.fields) {
            if ((field.access & Opcodes.ACC_STATIC) == 0 && COLOR_DESCRIPTOR.equals(field.desc)) {
                declared.add(field.name + field.desc);
            }
        }
        return fields.stream().allMatch(field -> declared.contains(field.name() + field.descriptor()));
    }

    private static boolean hasReviewedSetterTransfers(
            ClassNode owner,
            MethodNode convert,
            List<SinkField> fields) {
        Map<String, Set<String>> settersByField = new LinkedHashMap<>();
        for (SinkField field : fields) {
            settersByField.put(field.name() + field.descriptor(), new LinkedHashSet<>());
        }

        for (MethodNode method : owner.methods) {
            if (method == convert) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null;
                    instruction = instruction.getNext()) {
                if (!(instruction instanceof FieldInsnNode field)
                        || field.getOpcode() != Opcodes.GETFIELD
                        || !TARGET_CLASS.equals(field.owner)
                        || !COLOR_DESCRIPTOR.equals(field.desc)) {
                    continue;
                }
                Set<String> setters = settersByField.get(field.name + field.desc);
                if (setters == null) {
                    continue;
                }

                AbstractInsnNode thisLoad = previousOpcode(field);
                AbstractInsnNode receiverLoad = previousOpcode(thisLoad);
                AbstractInsnNode setterInstruction = nextOpcode(field);
                if (!(thisLoad instanceof VarInsnNode loadedThis)
                        || loadedThis.getOpcode() != Opcodes.ALOAD
                        || loadedThis.var != 0
                        || !(receiverLoad instanceof VarInsnNode receiver)
                        || receiver.getOpcode() != Opcodes.ALOAD
                        || !isTextureObjectLocal(method, receiver.var)
                        || !(setterInstruction instanceof MethodInsnNode setter)
                        || setter.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !TEXTURE_OBJECT.equals(setter.owner)
                        || !COLOR_SETTER_DESCRIPTOR.equals(setter.desc)) {
                    continue;
                }
                setters.add(setter.name + setter.desc);
            }
        }

        Set<String> distinctSetters = new LinkedHashSet<>();
        for (Set<String> setters : settersByField.values()) {
            if (setters.size() != 1) {
                return false;
            }
            distinctSetters.add(setters.iterator().next());
        }
        return distinctSetters.size() == 3;
    }

    private static boolean isTextureObjectLocal(MethodNode method, int local) {
        int cursor = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            if (cursor == local) {
                return TEXTURE_OBJECT_DESCRIPTOR.equals(argument.getDescriptor());
            }
            cursor += argument.getSize();
        }
        return false;
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

    private static AbstractInsnNode nextOpcode(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction == null ? null : instruction.getNext();
        while (current instanceof LabelNode
                || current instanceof LineNumberNode
                || current instanceof FrameNode) {
            current = current.getNext();
        }
        return current;
    }

    record SinkField(String owner, String name, String descriptor, int receiverLocal) {
        SinkField {
            if ((!TARGET_CLASS.equals(owner) && !TEXTURE_OBJECT.equals(owner))
                    || name == null
                    || name.isBlank()
                    || !COLOR_DESCRIPTOR.equals(descriptor)
                    || (receiverLocal != 0 && receiverLocal != 2)) {
                throw new IllegalArgumentException("Invalid prepared-pixel color sink field");
            }
        }
    }
}
