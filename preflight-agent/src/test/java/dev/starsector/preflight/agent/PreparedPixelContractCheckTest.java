package dev.starsector.preflight.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.starsector.preflight.core.Hashes;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class PreparedPixelContractCheckTest {
    private static final String COLOR = "Ljava/awt/Color;";

    @TempDir
    Path temporaryDirectory;

    @Test
    void inspectsEligibleInstalledStyleClassFile() throws Exception {
        byte[] classBytes = fixture(TransferModel.TYPED_INSTANCE);
        Path classFile = temporaryDirectory.resolve("TextureLoader.class");
        Files.write(classFile, classBytes);

        PreparedPixelContractCheck.Result result = PreparedPixelContractCheck.inspect(classFile, null);

        assertTrue(result.eligible(), result.toJson());
        assertEquals(TexturePreparedPixelPlan.TARGET_CLASS, result.internalClassName());
        assertTrue(result.classMatches());
        assertTrue(result.transformationSucceeded());
        assertNotNull(result.transformed());
        assertEquals(64, result.transformed().get("sha256").toString().length());
        assertTrue(result.problems().isEmpty(), result.toJson());
        assertEquals(classBytes.length, ((Number) result.source().get("inputBytes")).intValue());
        assertEquals(classBytes.length, result.source().get("classBytes"));
        assertEquals(Hashes.sha256(classBytes), result.source().get("inputSha256"));
        assertEquals(Hashes.sha256(classBytes), result.source().get("classSha256"));
        assertEquals(
                AdapterTargetRegistry.texturePreparedPixelTarget().requiredMethods().size(),
                result.requiredMethods().size());
        assertTrue(result.requiredMethods().stream()
                .allMatch(method -> Boolean.TRUE.equals(method.get("present"))));
        assertEquals(
                TexturePreparedPixelColorSink.MODEL_STAGED_LOADER_SETTERS,
                result.preparedPixelColorSink().get("model"));
        assertEquals(true, result.preparedPixelColorSink().get("eligible"));
        assertEquals(3, result.preparedPixelColorSink().get("stagedFieldCount"));
        assertEquals(3, ((List<?>) result.preparedPixelColorSink().get("reviewedFields")).size());
    }

    @Test
    void readsDefaultTextureLoaderEntryFromJar() throws Exception {
        byte[] classBytes = fixture(TransferModel.TYPED_INSTANCE);
        Path archive = temporaryDirectory.resolve("starsector-obf.jar");
        writeArchive(archive, classBytes, true);

        PreparedPixelContractCheck.Result inspected = PreparedPixelContractCheck.inspect(
                archive,
                PreparedPixelContractCheck.DEFAULT_ARCHIVE_ENTRY);
        assertTrue(inspected.eligible(), inspected.toJson());
        assertEquals(Files.size(archive), ((Number) inspected.source().get("inputBytes")).longValue());
        assertEquals(Hashes.sha256(archive), inspected.source().get("inputSha256"));
        assertEquals(Hashes.sha256(classBytes), inspected.source().get("classSha256"));
        assertNotEquals(inspected.source().get("inputSha256"), inspected.source().get("classSha256"));

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int status = PreparedPixelContractCheck.run(
                new String[] {archive.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        String json = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, status, stderr.toString(StandardCharsets.UTF_8));
        assertTrue(json.contains("\"eligible\":true"), json);
        assertTrue(json.contains("\"archiveEntry\":\""
                + PreparedPixelContractCheck.DEFAULT_ARCHIVE_ENTRY + "\""), json);
        assertTrue(json.contains("\"model\":\"staged-loader-setters\""), json);
        assertTrue(json.contains("\"transformationSucceeded\":true"), json);
        assertTrue(json.contains("\"transformed\":{\"bytes\":"), json);
    }

    @Test
    void declinesUntypedSetterReceiverAndReturnsDiagnosticStatus() throws Exception {
        Path classFile = temporaryDirectory.resolve("TextureLoader.class");
        Files.write(classFile, fixture(TransferModel.WRONG_RECEIVER_TYPE));

        PreparedPixelContractCheck.Result result = PreparedPixelContractCheck.inspect(classFile, null);
        assertFalse(result.eligible(), result.toJson());
        assertEquals(
                TexturePreparedPixelColorSink.MODEL_UNSUPPORTED,
                result.preparedPixelColorSink().get("model"));
        assertEquals(3, result.preparedPixelColorSink().get("stagedFieldCount"));
        assertEquals(false, result.preparedPixelColorSink().get("stagedSetterFlowExact"));
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("setter targets")),
                result.toJson());
        assertBounded(result.problems());
        assertFalse(result.transformationSucceeded());
        assertNull(result.transformed());

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int status = PreparedPixelContractCheck.run(
                new String[] {classFile.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        assertEquals(6, status);
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("\"eligible\":false"));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("transformation declined"));
    }

    @Test
    void declinesStaticTransferMethodEvenWithTypedArguments() throws Exception {
        Path classFile = temporaryDirectory.resolve("TextureLoader.class");
        Files.write(classFile, fixture(TransferModel.STATIC_TYPED_ARGUMENTS));

        PreparedPixelContractCheck.Result result = PreparedPixelContractCheck.inspect(classFile, null);

        assertFalse(result.eligible(), result.toJson());
        assertEquals(
                TexturePreparedPixelColorSink.MODEL_UNSUPPORTED,
                result.preparedPixelColorSink().get("model"));
        assertEquals(false, result.preparedPixelColorSink().get("stagedSetterFlowExact"));
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("static")), result.toJson());
        assertFalse(result.transformationSucceeded());
        assertBounded(result.problems());
    }

    @Test
    void reportsWrongClassIdentityWithoutAttemptingTransform() throws Exception {
        Path classFile = temporaryDirectory.resolve("Other.class");
        Files.write(classFile, wrongClass());

        PreparedPixelContractCheck.Result result = PreparedPixelContractCheck.inspect(classFile, null);

        assertFalse(result.eligible(), result.toJson());
        assertFalse(result.classMatches());
        assertFalse(result.transformationSucceeded());
        assertNull(result.transformed());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("expected")), result.toJson());
        assertTrue(result.requiredMethods().stream()
                .noneMatch(method -> Boolean.TRUE.equals(method.get("present"))));
        assertBounded(result.problems());
    }

    @Test
    void rejectsUnsafeArchiveEntrySegments() throws Exception {
        Path archive = temporaryDirectory.resolve("target.jar");
        writeArchive(archive, fixture(TransferModel.TYPED_INSTANCE), false);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PreparedPixelContractCheck.run(
                        new String[] {archive.toString(), "--entry", "com/fs/../TextureLoader.class"},
                        new PrintStream(stdout, true, StandardCharsets.UTF_8),
                        new PrintStream(stderr, true, StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("Invalid archive entry"));
    }

    private static void writeArchive(Path archive, byte[] classBytes, boolean extraEntry) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry(PreparedPixelContractCheck.DEFAULT_ARCHIVE_ENTRY));
            output.write(classBytes);
            output.closeEntry();
            if (extraEntry) {
                output.putNextEntry(new ZipEntry("identity.txt"));
                output.write("archive identity".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static void assertBounded(List<String> problems) {
        assertTrue(problems.size() <= 8, problems.toString());
        assertTrue(problems.stream().allMatch(problem -> problem.length() <= 240), problems.toString());
    }

    private static byte[] fixture(TransferModel transferModel) {
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

        decodeMethod().accept(writer);
        convertMethod().accept(writer);
        cleanupMethod().accept(writer);
        addRequiredMethodStubs(writer);
        transferMethod(transferModel).accept(writer);

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static MethodNode decodeMethod() {
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
        return decode;
    }

    private static MethodNode convertMethod() {
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
        return convert;
    }

    private static MethodNode cleanupMethod() {
        MethodNode cleanup = new MethodNode(
                Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                TexturePreparedPixelPlan.CLEANUP_METHOD,
                TexturePreparedPixelPlan.CLEANUP_DESCRIPTOR,
                null,
                null);
        cleanup.instructions.add(new InsnNode(Opcodes.RETURN));
        return cleanup;
    }

    private static void addRequiredMethodStubs(ClassWriter writer) {
        for (AdapterTarget.RequiredMethod required
                : AdapterTargetRegistry.texturePreparedPixelTarget().requiredMethods()) {
            if (same(required, TexturePreparedPixelPlan.DECODE_METHOD, TexturePreparedPixelPlan.DECODE_DESCRIPTOR)
                    || same(required, TexturePreparedPixelPlan.CONVERT_METHOD,
                            TexturePreparedPixelPlan.CONVERT_DESCRIPTOR)
                    || same(required, TexturePreparedPixelPlan.CLEANUP_METHOD,
                            TexturePreparedPixelPlan.CLEANUP_DESCRIPTOR)) {
                continue;
            }
            MethodNode method = new MethodNode(
                    Opcodes.ASM9,
                    Opcodes.ACC_PUBLIC,
                    required.name(),
                    required.descriptor(),
                    null,
                    null);
            Type returnType = Type.getReturnType(required.descriptor());
            if (returnType.getSort() == Type.VOID) {
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            } else {
                method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
            }
            method.accept(writer);
        }
    }

    private static boolean same(AdapterTarget.RequiredMethod required, String name, String descriptor) {
        return name.equals(required.name()) && descriptor.equals(required.descriptor());
    }

    private static MethodNode transferMethod(TransferModel transferModel) {
        int access = Opcodes.ACC_PUBLIC;
        String descriptor;
        int loaderLocal;
        int textureLocal;
        switch (transferModel) {
            case TYPED_INSTANCE -> {
                descriptor = "(L" + TexturePreparedPixelPlan.TEXTURE_OBJECT + ";)V";
                loaderLocal = 0;
                textureLocal = 1;
            }
            case WRONG_RECEIVER_TYPE -> {
                descriptor = "(Ljava/lang/String;)V";
                loaderLocal = 0;
                textureLocal = 1;
            }
            case STATIC_TYPED_ARGUMENTS -> {
                access |= Opcodes.ACC_STATIC;
                descriptor = "(L" + TexturePreparedPixelPlan.TARGET_CLASS
                        + ";L" + TexturePreparedPixelPlan.TEXTURE_OBJECT + ";)V";
                loaderLocal = 0;
                textureLocal = 1;
            }
            default -> throw new IllegalStateException("Unexpected transfer model: " + transferModel);
        }

        MethodNode transfer = new MethodNode(
                Opcodes.ASM9,
                access,
                "transferColors",
                descriptor,
                null,
                null);
        for (int i = 0; i < 3; i++) {
            transfer.instructions.add(new VarInsnNode(Opcodes.ALOAD, textureLocal));
            transfer.instructions.add(new VarInsnNode(Opcodes.ALOAD, loaderLocal));
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
        return transfer;
    }

    private static byte[] wrongClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Other", null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private enum TransferModel {
        TYPED_INSTANCE,
        WRONG_RECEIVER_TYPE,
        STATIC_TYPED_ARGUMENTS
    }
}
