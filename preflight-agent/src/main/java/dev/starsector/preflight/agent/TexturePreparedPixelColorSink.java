package dev.starsector.preflight.agent;

import java.util.ArrayList;
import java.util.Collections;
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
    static final String MODEL_DIRECT_TEXTURE_FIELDS = "direct-texture-fields";
    static final String MODEL_STAGED_LOADER_SETTERS = "staged-loader-setters";
    static final String MODEL_UNSUPPORTED = "unsupported";

    private static final String TARGET_CLASS = TexturePreparedPixelPlan.TARGET_CLASS;
    private static final String TEXTURE_OBJECT = TexturePreparedPixelPlan.TEXTURE_OBJECT;
    private static final String TEXTURE_OBJECT_DESCRIPTOR = "L" + TEXTURE_OBJECT + ";";
    private static final String COLOR_DESCRIPTOR = "Ljava/awt/Color;";
    private static final String COLOR_SETTER_DESCRIPTOR = "(Ljava/awt/Color;)V";

    private TexturePreparedPixelColorSink() {
    }

    static List<SinkField> reviewed(ClassNode owner, MethodNode convert) {
        Review review = inspect(owner, convert);
        return review.eligible() ? review.reviewedFields() : List.of();
    }

    static Review inspect(ClassNode owner, MethodNode convert) {
        boolean rasterRead = hasRasterRead(convert);
        List<SinkField> direct = colorFields(convert, TEXTURE_OBJECT, 2);
        List<SinkField> staged = colorFields(convert, TARGET_CLASS, 0);
        boolean stagedFieldsDeclared = staged.size() == 3 && fieldsExistOnOwner(owner, staged);
        boolean stagedSetterFlowExact = stagedFieldsDeclared
                && hasReviewedSetterTransfers(owner, convert, staged);
        boolean directEligible = rasterRead && direct.size() == 3;
        boolean stagedEligible = rasterRead
                && staged.size() == 3
                && stagedFieldsDeclared
                && stagedSetterFlowExact;

        String model = MODEL_UNSUPPORTED;
        List<SinkField> reviewed = List.of();
        if (directEligible != stagedEligible) {
            model = directEligible ? MODEL_DIRECT_TEXTURE_FIELDS : MODEL_STAGED_LOADER_SETTERS;
            reviewed = directEligible ? direct : staged;
        }

        List<String> problems = new ArrayList<>();
        if (!rasterRead) {
            problems.add("converter has no reviewed raster read");
        }
        if (!direct.isEmpty() && direct.size() != 3) {
            problems.add("direct texture color sink count is " + direct.size() + ", expected exactly 3");
        }
        if (!staged.isEmpty() && staged.size() != 3) {
            problems.add("staged loader color sink count is " + staged.size() + ", expected exactly 3");
        }
        if (staged.size() == 3 && !stagedFieldsDeclared) {
            problems.add("staged loader color fields are absent, static, or incorrectly typed");
        }
        if (staged.size() == 3 && stagedFieldsDeclared && !stagedSetterFlowExact) {
            problems.add("staged loader color setter targets are incomplete, ambiguous, static, or incorrectly typed");
        }
        if (directEligible && stagedEligible) {
            problems.add("direct and staged color sink models are both eligible");
        }
        if (MODEL_UNSUPPORTED.equals(model) && problems.isEmpty()) {
            problems.add("no exact prepared-pixel color sink model is eligible");
        }

        return new Review(
                model,
                !MODEL_UNSUPPORTED.equals(model),
                rasterRead,
                direct,
                staged,
                stagedFieldsDeclared,
                stagedSetterFlowExact,
                reviewed,
                problems);
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
        return List.copyOf(fields.values());
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
            if (method == convert || (method.access & Opcodes.ACC_STATIC) != 0) {
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
        int cursor = 1;
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

    record Review(
            String model,
            boolean eligible,
            boolean rasterRead,
            List<SinkField> directFields,
            List<SinkField> stagedFields,
            boolean stagedFieldsDeclared,
            boolean stagedSetterFlowExact,
            List<SinkField> reviewedFields,
            List<String> problems) {
        Review {
            directFields = List.copyOf(directFields);
            stagedFields = List.copyOf(stagedFields);
            reviewedFields = List.copyOf(reviewedFields);
            problems = List.copyOf(problems);
        }

        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("model", model);
            values.put("eligible", eligible);
            values.put("rasterRead", rasterRead);
            values.put("directFieldCount", directFields.size());
            values.put("directFields", fieldMaps(directFields));
            values.put("stagedFieldCount", stagedFields.size());
            values.put("stagedFields", fieldMaps(stagedFields));
            values.put("stagedFieldsDeclared", stagedFieldsDeclared);
            values.put("stagedSetterFlowExact", stagedSetterFlowExact);
            values.put("reviewedFields", fieldMaps(reviewedFields));
            values.put("problems", problems);
            return Collections.unmodifiableMap(values);
        }

        private static List<Map<String, Object>> fieldMaps(List<SinkField> fields) {
            return fields.stream().map(SinkField::toMap).toList();
        }
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

        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("owner", owner);
            values.put("name", name);
            values.put("descriptor", descriptor);
            values.put("receiverLocal", receiverLocal);
            return Collections.unmodifiableMap(values);
        }
    }
}
