package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class TexturePreparedPixelColorSinkTest {
    private static final String COLOR = "Ljava/awt/Color;";
    private static final String TEXTURE_ARGUMENT =
            "(L" + TexturePreparedPixelPlan.TEXTURE_OBJECT + ";)V";

    @Test
    void acceptsThreeDirectTextureObjectColorFields() {
        ClassNode owner = owner();
        MethodNode convert = convertWithRasterRead();
        addColorWrites(convert, TexturePreparedPixelPlan.TEXTURE_OBJECT, "color");
        owner.methods.add(convert);

        TexturePreparedPixelColorSink.Review review =
                TexturePreparedPixelColorSink.inspect(owner, convert);
        List<TexturePreparedPixelColorSink.SinkField> fields =
                TexturePreparedPixelColorSink.reviewed(owner, convert);

        assertTrue(review.eligible(), review.problems().toString());
        assertEquals(TexturePreparedPixelColorSink.MODEL_DIRECT_TEXTURE_FIELDS, review.model());
        assertEquals(3, review.directFields().size());
        assertTrue(review.stagedFields().isEmpty());
        assertEquals(3, fields.size());
        assertTrue(fields.stream().allMatch(field ->
                TexturePreparedPixelPlan.TEXTURE_OBJECT.equals(field.owner())
                        && field.receiverLocal() == 2));
        assertEquals(3, ((List<?>) review.toMap().get("reviewedFields")).size());
    }

    @Test
    void acceptsLoaderFieldsTransferredToThreeDistinctTextureSetters() {
        ClassNode owner = owner();
        declareLoaderColors(owner, "derived");
        MethodNode convert = convertWithRasterRead();
        addColorWrites(convert, TexturePreparedPixelPlan.TARGET_CLASS, "derived");
        owner.methods.add(convert);
        owner.methods.add(transferMethod("derived", false, TEXTURE_ARGUMENT, 1));

        TexturePreparedPixelColorSink.Review review =
                TexturePreparedPixelColorSink.inspect(owner, convert);
        List<TexturePreparedPixelColorSink.SinkField> fields =
                TexturePreparedPixelColorSink.reviewed(owner, convert);

        assertTrue(review.eligible(), review.problems().toString());
        assertEquals(TexturePreparedPixelColorSink.MODEL_STAGED_LOADER_SETTERS, review.model());
        assertTrue(review.stagedFieldsDeclared());
        assertTrue(review.stagedSetterFlowExact());
        assertEquals(3, fields.size());
        assertTrue(fields.stream().allMatch(field ->
                TexturePreparedPixelPlan.TARGET_CLASS.equals(field.owner())
                        && field.receiverLocal() == 0));
    }

    @Test
    void acceptsTextureArgumentAfterWideLocalSlots() {
        ClassNode owner = owner();
        declareLoaderColors(owner, "derived");
        MethodNode convert = convertWithRasterRead();
        addColorWrites(convert, TexturePreparedPixelPlan.TARGET_CLASS, "derived");
        owner.methods.add(convert);
        owner.methods.add(transferMethod(
                "derived",
                false,
                "(JL" + TexturePreparedPixelPlan.TEXTURE_OBJECT + ";)V",
                3));

        assertEquals(3, TexturePreparedPixelColorSink.reviewed(owner, convert).size());
    }

    @Test
    void rejectsLoaderFieldsWithoutReviewedSetterTransfers() {
        ClassNode owner = owner();
        declareLoaderColors(owner, "derived");
        MethodNode convert = convertWithRasterRead();
        addColorWrites(convert, TexturePreparedPixelPlan.TARGET_CLASS, "derived");
        owner.methods.add(convert);

        assertTrue(TexturePreparedPixelColorSink.reviewed(owner, convert).isEmpty());
    }

    @Test
    void rejectsLoaderFieldsTransferredToOneAmbiguousSetter() {
        ClassNode owner = owner();
        declareLoaderColors(owner, "derived");
        MethodNode convert = convertWithRasterRead();
        addColorWrites(convert, TexturePreparedPixelPlan.TARGET_CLASS, "derived");
        owner.methods.add(convert);
        owner.methods.add(transferMethod("derived", true, TEXTURE_ARGUMENT, 1));

        assertTrue(TexturePreparedPixelColorSink.reviewed(owner, convert).isEmpty());
    }

    @Test
    void rejectsSetterReceiverWhoseLocalTypeIsNotTextureObject() {
        ClassNode owner = owner();
        declareLoaderColors(owner, "derived");
        MethodNode convert = convertWithRasterRead();
        addColorWrites(convert, TexturePreparedPixelPlan.TARGET_CLASS, "derived");
        owner.methods.add(convert);
        owner.methods.add(transferMethod("derived", false, "(Ljava/lang/String;)V", 1));

        assertTrue(TexturePreparedPixelColorSink.reviewed(owner, convert).isEmpty());
    }

    @Test
    void rejectsStaticTransferMethodWithTypedArguments() {
        ClassNode owner = owner();
        declareLoaderColors(owner, "derived");
        MethodNode convert = convertWithRasterRead();
        addColorWrites(convert, TexturePreparedPixelPlan.TARGET_CLASS, "derived");
        owner.methods.add(convert);
        owner.methods.add(staticTransferMethod("derived"));

        TexturePreparedPixelColorSink.Review review =
                TexturePreparedPixelColorSink.inspect(owner, convert);

        assertFalse(review.eligible());
        assertEquals(TexturePreparedPixelColorSink.MODEL_UNSUPPORTED, review.model());
        assertFalse(review.stagedSetterFlowExact());
        assertTrue(review.problems().stream().anyMatch(problem -> problem.contains("static")));
    }

    @Test
    void rejectsMixedDirectAndStagedSinkModels() {
        ClassNode owner = owner();
        declareLoaderColors(owner, "derived");
        MethodNode convert = convertWithRasterRead();
        addColorWrites(convert, TexturePreparedPixelPlan.TEXTURE_OBJECT, "color");
        addColorWrites(convert, TexturePreparedPixelPlan.TARGET_CLASS, "derived");
        owner.methods.add(convert);
        owner.methods.add(transferMethod("derived", false, TEXTURE_ARGUMENT, 1));

        assertTrue(TexturePreparedPixelColorSink.reviewed(owner, convert).isEmpty());
    }

    @Test
    void rejectsSinkWithoutRasterEvidence() {
        ClassNode owner = owner();
        MethodNode convert = new MethodNode(Opcodes.ASM9);
        addColorWrites(convert, TexturePreparedPixelPlan.TEXTURE_OBJECT, "color");
        owner.methods.add(convert);

        assertTrue(TexturePreparedPixelColorSink.reviewed(owner, convert).isEmpty());
    }

    private static ClassNode owner() {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.name = TexturePreparedPixelPlan.TARGET_CLASS;
        return owner;
    }

    private static MethodNode convertWithRasterRead() {
        MethodNode convert = new MethodNode(Opcodes.ASM9);
        convert.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        convert.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/awt/image/BufferedImage",
                "getRaster",
                "()Ljava/awt/image/WritableRaster;",
                false));
        convert.instructions.add(new InsnNode(Opcodes.POP));
        return convert;
    }

    private static void declareLoaderColors(ClassNode owner, String prefix) {
        for (int i = 0; i < 3; i++) {
            owner.fields.add(new FieldNode(
                    Opcodes.ACC_PRIVATE,
                    prefix + i,
                    COLOR,
                    null,
                    null));
        }
    }

    private static void addColorWrites(MethodNode convert, String owner, String prefix) {
        for (int i = 0; i < 3; i++) {
            convert.instructions.add(new FieldInsnNode(
                    Opcodes.PUTFIELD,
                    owner,
                    prefix + i,
                    COLOR));
        }
    }

    private static MethodNode transferMethod(
            String fieldPrefix,
            boolean oneSetter,
            String descriptor,
            int receiverLocal) {
        MethodNode transfer = new MethodNode(
                Opcodes.ASM9,
                Opcodes.ACC_PUBLIC,
                "transferColors",
                descriptor,
                null,
                null);
        addTransfers(transfer, fieldPrefix, oneSetter, receiverLocal, 0);
        return transfer;
    }

    private static MethodNode staticTransferMethod(String fieldPrefix) {
        MethodNode transfer = new MethodNode(
                Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "transferColorsStatic",
                "(L" + TexturePreparedPixelPlan.TARGET_CLASS
                        + ";L" + TexturePreparedPixelPlan.TEXTURE_OBJECT + ";)V",
                null,
                null);
        addTransfers(transfer, fieldPrefix, false, 1, 0);
        return transfer;
    }

    private static void addTransfers(
            MethodNode transfer,
            String fieldPrefix,
            boolean oneSetter,
            int receiverLocal,
            int loaderLocal) {
        for (int i = 0; i < 3; i++) {
            transfer.instructions.add(new VarInsnNode(Opcodes.ALOAD, receiverLocal));
            transfer.instructions.add(new VarInsnNode(Opcodes.ALOAD, loaderLocal));
            transfer.instructions.add(new FieldInsnNode(
                    Opcodes.GETFIELD,
                    TexturePreparedPixelPlan.TARGET_CLASS,
                    fieldPrefix + i,
                    COLOR));
            transfer.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    TexturePreparedPixelPlan.TEXTURE_OBJECT,
                    oneSetter ? "setColor" : "setColor" + i,
                    "(Ljava/awt/Color;)V",
                    false));
        }
        transfer.instructions.add(new InsnNode(Opcodes.RETURN));
    }
}
