package dev.starsector.preflight.agent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Bounded read-only evidence for the exact Starsector sound-loader wrapper classes.
 *
 * <p>The report preserves the original class bytes and excludes bytecode listings, class bytes,
 * decoded audio, asset contents, and literal strings. Every retained collection and string is
 * bounded, deterministic, and explicit about truncation.</p>
 */
final class SoundLoaderContractReport {
    static final int IDENTITY_LIMIT = 64;
    static final int METHOD_LIMIT = 1_024;
    static final int STRUCTURAL_METHOD_LIMIT = 512;
    static final int FIELD_LIMIT = 512;
    static final int CALL_LIMIT = 1_024;
    static final int TRY_CATCH_LIMIT = 256;
    static final int STRING_CONSTANT_LIMIT = 256;
    static final int FLOW_POINT_LIMIT = 512;
    static final int FRAME_VALUE_LIMIT = 128;
    static final int DIAGNOSTIC_LIMIT = 100;
    static final int TEXT_LIMIT = 4_096;
    private static final int SOURCE_SUFFIX_LIMIT = 1_024;

    private static final Set<String> TARGETS = Set.of(
            "sound/J",
            "sound/F",
            "sound/ooOO",
            "sound/D",
            "sound/Sound",
            "sound/void",
            "com/fs/starfarer/loading/A");
    private static final MethodKey PRIMARY_SEAM = new MethodKey(
            "sound/J", "o00000", "(Ljava/io/InputStream;)Lsound/F;");

    private final Path destination;
    private final Instant startedAt = Instant.now();
    private final TreeMap<IdentityKey, Entry> entries = new TreeMap<>();
    private final List<String> diagnostics = new ArrayList<>();
    private boolean entriesTruncated;
    private boolean diagnosticsTruncated;

    SoundLoaderContractReport(Path destination) {
        this.destination = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
    }

    boolean interested(String internalClassName) {
        return internalClassName != null && TARGETS.contains(internalClassName.replace('.', '/'));
    }

    synchronized void observed(
            ClassSignature signature,
            AdapterSourceIdentity source,
            byte[] classfileBuffer) {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(classfileBuffer, "classfileBuffer");
        if (!interested(signature.internalName())) return;

        IdentityKey key = IdentityKey.of(signature, source);
        if (entries.containsKey(key)) return;

        List<MethodIdentity> methods = signature.methods().stream()
                .sorted(Comparator.comparing(ClassSignature.Method::name)
                        .thenComparing(ClassSignature.Method::descriptor)
                        .thenComparingInt(ClassSignature.Method::access))
                .limit(METHOD_LIMIT)
                .map(method -> new MethodIdentity(method.name(), method.descriptor(), method.access()))
                .toList();

        StructuralAnalysis structural;
        try {
            structural = analyze(signature.internalName(), classfileBuffer);
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            String detail = "Could not analyze sound-loader class " + signature.internalName() + ": " + message(error);
            diagnostic(detail);
            structural = StructuralAnalysis.failed(detail);
        }

        SourceSuffix suffix = sourceSuffix(source.normalizedSource());
        Entry entry = new Entry(
                key.typedKey(),
                signature.internalName(),
                signature.sha256(),
                signature.majorVersion(),
                signature.methods().size(),
                methods,
                signature.methods().size() > METHOD_LIMIT,
                bounded(source.codeSource()),
                bounded(source.normalizedSource()),
                source.sourceKind(),
                suffix.value(),
                suffix.truncated(),
                bounded(source.sourceSha256()),
                bounded(source.sourceHashProblem()),
                bounded(source.loaderClass()),
                bounded(source.loaderName()),
                structural.methods(),
                structural.methodsTruncated(),
                structural.analysisProblem());
        retainIdentity(key, entry);
    }

    synchronized void contained(String detail, Throwable error) {
        diagnostic(detail + ": " + message(error));
    }

    synchronized void write() throws IOException {
        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAt", Instant.now());
        root.put("startedAt", startedAt);
        root.put("destination", destination);
        root.put("format", "starsector-preflight-sound-loader-contract-v1");
        root.put("observationScope", "loaded exact target classes only");
        root.put("primarySeam", PRIMARY_SEAM.toMap());
        root.put("targetClassNames", TARGETS.stream().sorted().toList());
        root.put("identityKeyFormat", "typed-length-prefixed-sha256-v1");
        root.put("originalClassBytesRetained", true);
        root.put("classBytesIncluded", false);
        root.put("bytecodeListingsIncluded", false);
        root.put("literalStringsIncluded", false);
        root.put("decodedAudioIncluded", false);
        root.put("automaticAllowlistGenerated", false);
        root.put("transformationPlanGenerated", false);
        root.put("transformRegistered", false);
        root.put("cacheReadsEnabled", false);
        root.put("cacheWritesEnabled", false);
        root.put("preparedAudioWritesEligible", false);
        root.put("requiresHumanReview", true);
        root.put("identityLimit", IDENTITY_LIMIT);
        root.put("methodLimitPerIdentity", METHOD_LIMIT);
        root.put("structuralMethodLimitPerIdentity", STRUCTURAL_METHOD_LIMIT);
        root.put("fieldLimitPerMethod", FIELD_LIMIT);
        root.put("callLimitPerMethod", CALL_LIMIT);
        root.put("tryCatchLimitPerMethod", TRY_CATCH_LIMIT);
        root.put("stringConstantLimitPerMethod", STRING_CONSTANT_LIMIT);
        root.put("flowPointLimitPerMethod", FLOW_POINT_LIMIT);
        root.put("frameValueLimit", FRAME_VALUE_LIMIT);
        root.put("retainedIdentities", entries.size());
        root.put("entriesTruncated", entriesTruncated);
        root.put("entries", entries.values().stream().map(Entry::toMap).toList());
        root.put("diagnosticLimit", DIAGNOSTIC_LIMIT);
        root.put("diagnosticsTruncated", diagnosticsTruncated);
        root.put("diagnostics", List.copyOf(diagnostics));
        writeAtomic(destination, AgentJson.object(root) + System.lineSeparator());
    }

    static String typedIdentityKey(
            String className,
            String classSha256,
            String normalizedSource,
            String loaderClass,
            String loaderName) {
        MessageDigest digest = newDigest();
        updateTyped(digest, "identity-format", "sound-loader-source-loader-v1");
        updateTyped(digest, "class-name", text(className).replace('.', '/'));
        updateTyped(digest, "class-sha256", text(classSha256).toLowerCase(Locale.ROOT));
        updateTyped(digest, "normalized-source", text(normalizedSource).replace('\\', '/'));
        updateTyped(digest, "loader-class", text(loaderClass).replace('.', '/'));
        updateTyped(digest, "loader-name", text(loaderName));
        return "sound-loader-source-loader-v1:" + HexFormat.of().formatHex(digest.digest());
    }

    private static StructuralAnalysis analyze(String owner, byte[] classfileBuffer) throws AnalyzerException {
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        new ClassReader(classfileBuffer).accept(classNode, ClassReader.SKIP_DEBUG);
        List<MethodNode> ordered = new ArrayList<>(classNode.methods);
        ordered.sort(Comparator.comparing((MethodNode method) -> method.name)
                .thenComparing(method -> method.desc)
                .thenComparingInt(method -> method.access));

        List<MethodStructure> methods = new ArrayList<>();
        boolean truncated = false;
        for (MethodNode method : ordered) {
            if (methods.size() >= STRUCTURAL_METHOD_LIMIT) {
                truncated = true;
                break;
            }
            methods.add(analyzeMethod(owner, method));
        }
        return new StructuralAnalysis(List.copyOf(methods), truncated, "");
    }

    private static MethodStructure analyzeMethod(String owner, MethodNode method) {
        IdentityHashMap<AbstractInsnNode, Integer> indexes = instructionIndexes(method);
        TreeMap<String, FieldAccess> fields = new TreeMap<>();
        TreeMap<String, CallEdge> calls = new TreeMap<>();
        TreeMap<String, CallEdge> sameClassCalls = new TreeMap<>();
        TreeMap<String, CallEdge> constructors = new TreeMap<>();
        TreeMap<String, RedactedString> strings = new TreeMap<>();
        boolean fieldsTruncated = false;
        boolean callsTruncated = false;
        boolean stringsTruncated = false;
        boolean callsPrimarySeam = false;
        boolean consumesSoundF = Type.getReturnType(method.desc).getDescriptor().equals("Lsound/F;");

        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode field) {
                FieldAccess access = new FieldAccess(
                        field.getOpcode(),
                        field.owner,
                        field.name,
                        field.desc,
                        field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC);
                String key = access.key();
                if (fields.size() < FIELD_LIMIT || fields.containsKey(key)) {
                    fields.putIfAbsent(key, access);
                } else {
                    fieldsTruncated = true;
                }
                if (field.owner.equals("sound/F") || field.desc.contains("Lsound/F;")) consumesSoundF = true;
            } else if (instruction instanceof MethodInsnNode call) {
                CallEdge edge = new CallEdge(
                        call.getOpcode(), call.owner, call.name, call.desc, call.itf,
                        call.name.equals("<init>"));
                String key = edge.key();
                if (calls.size() < CALL_LIMIT || calls.containsKey(key)) {
                    calls.putIfAbsent(key, edge);
                } else {
                    callsTruncated = true;
                }
                if (owner.equals(call.owner)) sameClassCalls.putIfAbsent(key, edge);
                if (call.name.equals("<init>")) constructors.putIfAbsent(key, edge);
                if (PRIMARY_SEAM.matches(call.owner, call.name, call.desc)) callsPrimarySeam = true;
                if (descriptorMentionsSoundF(call.desc) || call.owner.equals("sound/F")) consumesSoundF = true;
            } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                CallEdge edge = new CallEdge(
                        Opcodes.INVOKEDYNAMIC,
                        "<dynamic>",
                        dynamic.name,
                        dynamic.desc,
                        false,
                        false);
                String key = edge.key() + "@" + handle(dynamic.bsm);
                if (calls.size() < CALL_LIMIT || calls.containsKey(key)) {
                    calls.putIfAbsent(key, edge);
                } else {
                    callsTruncated = true;
                }
                if (descriptorMentionsSoundF(dynamic.desc)) consumesSoundF = true;
            } else if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String literal) {
                RedactedString redacted = new RedactedString(
                        literal.length(),
                        sha256(literal.getBytes(StandardCharsets.UTF_8)));
                String key = redacted.length() + "@" + redacted.sha256();
                if (strings.size() < STRING_CONSTANT_LIMIT || strings.containsKey(key)) {
                    strings.putIfAbsent(key, redacted);
                } else {
                    stringsTruncated = true;
                }
            }
        }

        TryCatchResult tryCatches = tryCatchRegions(method, indexes);
        FlowResult flow = analyzeFlow(owner, method, indexes);
        boolean primary = PRIMARY_SEAM.matches(owner, method.name, method.desc);
        boolean consumerCandidate = callsPrimarySeam || consumesSoundF;
        return new MethodStructure(
                method.name,
                method.desc,
                method.access,
                primary,
                consumerCandidate,
                method.maxStack,
                method.maxLocals,
                executableInstructionCount(method),
                List.copyOf(fields.values()),
                List.copyOf(calls.values()),
                List.copyOf(sameClassCalls.values()),
                List.copyOf(constructors.values()),
                tryCatches.regions(),
                List.copyOf(strings.values()),
                flow.points(),
                flow.error(),
                fieldsTruncated,
                callsTruncated,
                tryCatches.truncated(),
                stringsTruncated,
                flow.truncated());
    }

    private static IdentityHashMap<AbstractInsnNode, Integer> instructionIndexes(MethodNode method) {
        IdentityHashMap<AbstractInsnNode, Integer> indexes = new IdentityHashMap<>();
        int index = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            indexes.put(instruction, index++);
        }
        return indexes;
    }

    private static int executableInstructionCount(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) count++;
        }
        return count;
    }

    private static TryCatchResult tryCatchRegions(
            MethodNode method,
            IdentityHashMap<AbstractInsnNode, Integer> indexes) {
        if (method.tryCatchBlocks == null || method.tryCatchBlocks.isEmpty()) {
            return new TryCatchResult(List.of(), false);
        }
        List<TryCatchRegion> regions = new ArrayList<>();
        boolean truncated = false;
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            if (regions.size() >= TRY_CATCH_LIMIT) {
                truncated = true;
                break;
            }
            regions.add(new TryCatchRegion(
                    index(indexes, block.start),
                    index(indexes, block.end),
                    index(indexes, block.handler),
                    block.type == null ? "<all>" : block.type));
        }
        regions.sort(Comparator.comparingInt(TryCatchRegion::startInstructionIndex)
                .thenComparingInt(TryCatchRegion::endInstructionIndex)
                .thenComparingInt(TryCatchRegion::handlerInstructionIndex)
                .thenComparing(TryCatchRegion::caughtType));
        return new TryCatchResult(List.copyOf(regions), truncated);
    }

    private static FlowResult analyzeFlow(
            String owner,
            MethodNode method,
            IdentityHashMap<AbstractInsnNode, Integer> indexes) {
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return new FlowResult(List.of(), "Method has no bytecode body", false);
        }
        try {
            Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
            Frame<BasicValue>[] frames = analyzer.analyze(owner, method);
            List<FlowPoint> points = new ArrayList<>();
            boolean truncated = false;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null;
                    instruction = instruction.getNext()) {
                if (!selectedFlowPoint(instruction)) continue;
                if (points.size() >= FLOW_POINT_LIMIT) {
                    truncated = true;
                    break;
                }
                int index = index(indexes, instruction);
                Frame<BasicValue> frame = index >= 0 && index < frames.length ? frames[index] : null;
                points.add(new FlowPoint(
                        index,
                        instruction.getOpcode(),
                        flowKind(instruction),
                        flowTarget(instruction),
                        frameValues(frame, true),
                        frameValues(frame, false)));
            }
            return new FlowResult(List.copyOf(points), "", truncated);
        } catch (AnalyzerException | RuntimeException error) {
            return new FlowResult(List.of(), message(error), false);
        }
    }

    private static boolean selectedFlowPoint(AbstractInsnNode instruction) {
        if (instruction instanceof FieldInsnNode field) {
            return field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC;
        }
        if (instruction instanceof MethodInsnNode call) {
            if (call.owner.startsWith("com/jcraft/jogg/") || call.owner.startsWith("com/jcraft/jorbis/")) {
                return true;
            }
            Type returnType = Type.getReturnType(call.desc);
            if (returnType.getDescriptor().equals("Lsound/F;")) return true;
            return call.name.equals("<init>") && argumentsContainSoundF(call.desc);
        }
        int opcode = instruction.getOpcode();
        return opcode == Opcodes.IRETURN
                || opcode == Opcodes.LRETURN
                || opcode == Opcodes.FRETURN
                || opcode == Opcodes.DRETURN
                || opcode == Opcodes.ARETURN
                || opcode == Opcodes.RETURN
                || opcode == Opcodes.ATHROW;
    }

    private static String flowKind(AbstractInsnNode instruction) {
        if (instruction instanceof FieldInsnNode) return "field-write";
        if (instruction instanceof MethodInsnNode call) {
            if (call.owner.startsWith("com/jcraft/jogg/") || call.owner.startsWith("com/jcraft/jorbis/")) {
                return "jogg-jorbis-call";
            }
            if (call.name.equals("<init>") && argumentsContainSoundF(call.desc)) {
                return "constructor-consuming-sound-f";
            }
            return "call-returning-sound-f";
        }
        return instruction.getOpcode() == Opcodes.ATHROW ? "throw" : "return";
    }

    private static String flowTarget(AbstractInsnNode instruction) {
        if (instruction instanceof FieldInsnNode field) {
            return field.owner + "." + field.name + field.desc;
        }
        if (instruction instanceof MethodInsnNode call) {
            return call.owner + "." + call.name + call.desc;
        }
        return "";
    }

    private static List<String> frameValues(Frame<BasicValue> frame, boolean locals) {
        if (frame == null) return List.of();
        int count = locals ? frame.getLocals() : frame.getStackSize();
        int retained = Math.min(count, FRAME_VALUE_LIMIT);
        List<String> result = new ArrayList<>(retained);
        for (int i = 0; i < retained; i++) {
            BasicValue value = locals ? frame.getLocal(i) : frame.getStack(i);
            result.add(frameValue(value));
        }
        return List.copyOf(result);
    }

    private static String frameValue(BasicValue value) {
        if (value == null) return "<null>";
        if (value == BasicValue.UNINITIALIZED_VALUE) return "<uninitialized>";
        if (value == BasicValue.RETURNADDRESS_VALUE) return "<return-address>";
        Type type = value.getType();
        return type == null ? bounded(value.toString()) : bounded(type.getDescriptor());
    }

    private static boolean descriptorMentionsSoundF(String descriptor) {
        return Type.getReturnType(descriptor).getDescriptor().equals("Lsound/F;")
                || argumentsContainSoundF(descriptor);
    }

    private static boolean argumentsContainSoundF(String descriptor) {
        for (Type type : Type.getArgumentTypes(descriptor)) {
            if (type.getDescriptor().equals("Lsound/F;")) return true;
        }
        return false;
    }

    private void retainIdentity(IdentityKey key, Entry entry) {
        if (entries.size() < IDENTITY_LIMIT) {
            entries.put(key, entry);
            return;
        }
        entriesTruncated = true;
        IdentityKey largest = entries.lastKey();
        if (key.compareTo(largest) < 0) {
            entries.pollLastEntry();
            entries.put(key, entry);
        }
    }

    private synchronized void diagnostic(String detail) {
        String value = bounded(detail);
        if (diagnostics.size() >= DIAGNOSTIC_LIMIT) {
            diagnosticsTruncated = true;
        } else {
            diagnostics.add(value);
        }
    }

    private static SourceSuffix sourceSuffix(String normalizedSource) {
        String source = text(normalizedSource).replace('\\', '/');
        String lower = source.toLowerCase(Locale.ROOT);
        String[] markers = {"contents/resources/java/", "starsector-core/", "mods/"};
        String value = "";
        for (String marker : markers) {
            int index = lower.lastIndexOf(marker);
            if (index >= 0) {
                value = source.substring(index);
                break;
            }
        }
        if (value.isEmpty()) {
            int slash = source.lastIndexOf('/');
            value = slash >= 0 ? source.substring(slash + 1) : source;
        }
        if (value.length() <= SOURCE_SUFFIX_LIMIT) return new SourceSuffix(value, false);
        return new SourceSuffix(value.substring(value.length() - SOURCE_SUFFIX_LIMIT), true);
    }

    private static String handle(Handle handle) {
        if (handle == null) return "";
        return handle.getTag() + "@" + handle.getOwner() + "." + handle.getName() + handle.getDesc()
                + "@" + handle.isInterface();
    }

    private static int index(Map<AbstractInsnNode, Integer> indexes, AbstractInsnNode instruction) {
        Integer value = indexes.get(instruction);
        return value == null ? -1 : value;
    }

    private static String bounded(String value) {
        String normalized = text(value);
        if (normalized.length() <= TEXT_LIMIT) return normalized;
        return normalized.substring(0, TEXT_LIMIT - 3) + "...";
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String message(Throwable error) {
        if (error == null) return "unknown error";
        String value = error.getMessage();
        return bounded(value == null || value.isBlank() ? error.getClass().getSimpleName() : value);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(newDigest().digest(bytes));
    }

    private static void updateTyped(MessageDigest digest, String type, String value) {
        byte[] typeBytes = type.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(typeBytes.length).array());
        digest.update(typeBytes);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(valueBytes.length).array());
        digest.update(valueBytes);
    }

    private static void writeAtomic(Path destination, String content) throws IOException {
        Path temporary = destination.resolveSibling(
                destination.getFileName() + ".tmp-" + ProcessHandle.current().pid() + "-" + System.nanoTime());
        boolean moved = false;
        try {
            Files.writeString(
                    temporary,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private record IdentityKey(
            String className,
            String classSha256,
            String normalizedSource,
            String loaderClass,
            String loaderName,
            String typedKey) implements Comparable<IdentityKey> {
        static IdentityKey of(ClassSignature signature, AdapterSourceIdentity source) {
            return new IdentityKey(
                    signature.internalName(),
                    signature.sha256(),
                    source.normalizedSource(),
                    source.loaderClass(),
                    source.loaderName(),
                    typedIdentityKey(
                            signature.internalName(),
                            signature.sha256(),
                            source.normalizedSource(),
                            source.loaderClass(),
                            source.loaderName()));
        }

        @Override
        public int compareTo(IdentityKey other) {
            int order = className.compareTo(other.className);
            if (order != 0) return order;
            order = classSha256.compareTo(other.classSha256);
            if (order != 0) return order;
            order = normalizedSource.compareTo(other.normalizedSource);
            if (order != 0) return order;
            order = loaderClass.compareTo(other.loaderClass);
            if (order != 0) return order;
            order = loaderName.compareTo(other.loaderName);
            if (order != 0) return order;
            return typedKey.compareTo(other.typedKey);
        }
    }

    private record MethodKey(String owner, String name, String descriptor) {
        boolean matches(String candidateOwner, String candidateName, String candidateDescriptor) {
            return owner.equals(candidateOwner)
                    && name.equals(candidateName)
                    && descriptor.equals(candidateDescriptor);
        }

        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("owner", owner);
            values.put("name", name);
            values.put("descriptor", descriptor);
            return values;
        }
    }

    private record Entry(
            String identityKey,
            String className,
            String classSha256,
            int majorVersion,
            int methodCount,
            List<MethodIdentity> methods,
            boolean methodsTruncated,
            String codeSource,
            String normalizedSource,
            String sourceKind,
            String sourceSuffix,
            boolean sourceSuffixTruncated,
            String sourceSha256,
            String sourceHashProblem,
            String loaderClass,
            String loaderName,
            List<MethodStructure> structuralMethods,
            boolean structuralMethodsTruncated,
            String structuralAnalysisProblem) {
        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("identityKey", identityKey);
            values.put("className", className);
            values.put("classSha256", classSha256);
            values.put("majorVersion", majorVersion);
            values.put("methodCount", methodCount);
            values.put("methods", methods.stream().map(MethodIdentity::toMap).toList());
            values.put("methodsTruncated", methodsTruncated);
            values.put("codeSource", codeSource);
            values.put("normalizedSource", normalizedSource);
            values.put("sourceKind", sourceKind);
            values.put("sourceSuffix", sourceSuffix);
            values.put("sourceSuffixTruncated", sourceSuffixTruncated);
            values.put("sourceSha256", sourceSha256.isBlank() ? null : sourceSha256);
            values.put("sourceHashProblem", sourceHashProblem.isBlank() ? null : sourceHashProblem);
            values.put("loaderClass", loaderClass);
            values.put("loaderName", loaderName);
            values.put("structuralMethods", structuralMethods.stream().map(MethodStructure::toMap).toList());
            values.put("structuralMethodsTruncated", structuralMethodsTruncated);
            values.put("structuralAnalysisProblem", structuralAnalysisProblem.isBlank() ? null : structuralAnalysisProblem);
            return values;
        }
    }

    private record MethodIdentity(String name, String descriptor, int access) {
        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("name", name);
            values.put("descriptor", descriptor);
            values.put("access", access);
            return values;
        }
    }

    private record MethodStructure(
            String name,
            String descriptor,
            int access,
            boolean primarySeam,
            boolean consumerCandidate,
            int maxStack,
            int maxLocals,
            int instructionCount,
            List<FieldAccess> fieldAccesses,
            List<CallEdge> calls,
            List<CallEdge> sameClassCalls,
            List<CallEdge> constructorCalls,
            List<TryCatchRegion> tryCatchRegions,
            List<RedactedString> redactedStringConstants,
            List<FlowPoint> flowPoints,
            String flowAnalysisError,
            boolean fieldAccessesTruncated,
            boolean callsTruncated,
            boolean tryCatchRegionsTruncated,
            boolean stringConstantsTruncated,
            boolean flowPointsTruncated) {
        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("name", name);
            values.put("descriptor", descriptor);
            values.put("access", access);
            values.put("primarySeam", primarySeam);
            values.put("consumerCandidate", consumerCandidate);
            values.put("maxStack", maxStack);
            values.put("maxLocals", maxLocals);
            values.put("instructionCount", instructionCount);
            values.put("fieldAccesses", fieldAccesses.stream().map(FieldAccess::toMap).toList());
            values.put("calls", calls.stream().map(CallEdge::toMap).toList());
            values.put("sameClassCalls", sameClassCalls.stream().map(CallEdge::toMap).toList());
            values.put("constructorCalls", constructorCalls.stream().map(CallEdge::toMap).toList());
            values.put("tryCatchRegions", tryCatchRegions.stream().map(TryCatchRegion::toMap).toList());
            values.put("redactedStringConstants", redactedStringConstants.stream().map(RedactedString::toMap).toList());
            values.put("flowPoints", flowPoints.stream().map(FlowPoint::toMap).toList());
            values.put("flowAnalysisError", flowAnalysisError.isBlank() ? null : flowAnalysisError);
            values.put("fieldAccessesTruncated", fieldAccessesTruncated);
            values.put("callsTruncated", callsTruncated);
            values.put("tryCatchRegionsTruncated", tryCatchRegionsTruncated);
            values.put("stringConstantsTruncated", stringConstantsTruncated);
            values.put("flowPointsTruncated", flowPointsTruncated);
            return values;
        }
    }

    private record FieldAccess(int opcode, String owner, String name, String descriptor, boolean write) {
        String key() {
            return opcode + "@" + owner + "." + name + descriptor;
        }

        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("opcode", opcode);
            values.put("owner", owner);
            values.put("name", name);
            values.put("descriptor", descriptor);
            values.put("write", write);
            return values;
        }
    }

    private record CallEdge(
            int opcode,
            String owner,
            String name,
            String descriptor,
            boolean interfaceCall,
            boolean constructor) {
        String key() {
            return opcode + "@" + owner + "." + name + descriptor + "@" + interfaceCall;
        }

        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("opcode", opcode);
            values.put("owner", owner);
            values.put("name", name);
            values.put("descriptor", descriptor);
            values.put("interfaceCall", interfaceCall);
            values.put("constructor", constructor);
            return values;
        }
    }

    private record TryCatchRegion(
            int startInstructionIndex,
            int endInstructionIndex,
            int handlerInstructionIndex,
            String caughtType) {
        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("startInstructionIndex", startInstructionIndex);
            values.put("endInstructionIndex", endInstructionIndex);
            values.put("handlerInstructionIndex", handlerInstructionIndex);
            values.put("caughtType", caughtType);
            return values;
        }
    }

    private record RedactedString(int length, String sha256) {
        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("length", length);
            values.put("sha256", sha256);
            return values;
        }
    }

    private record FlowPoint(
            int instructionIndex,
            int opcode,
            String kind,
            String target,
            List<String> locals,
            List<String> stack) {
        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("instructionIndex", instructionIndex);
            values.put("opcode", opcode);
            values.put("kind", kind);
            values.put("target", target.isBlank() ? null : target);
            values.put("locals", locals);
            values.put("stack", stack);
            return values;
        }
    }

    private record StructuralAnalysis(
            List<MethodStructure> methods,
            boolean methodsTruncated,
            String analysisProblem) {
        static StructuralAnalysis failed(String problem) {
            return new StructuralAnalysis(List.of(), false, bounded(problem));
        }
    }

    private record TryCatchResult(List<TryCatchRegion> regions, boolean truncated) {
    }

    private record FlowResult(List<FlowPoint> points, String error, boolean truncated) {
    }

    private record SourceSuffix(String value, boolean truncated) {
    }
}
