from pathlib import Path


def idx(lines: list[str], target: str, start: int = 0, end: int | None = None) -> int:
    if end is None:
        end = len(lines)
    matches = [i for i in range(start, end) if lines[i] == target]
    if len(matches) != 1:
        raise RuntimeError(f"Expected one line {target!r}, found {len(matches)} in range {start}:{end}")
    return matches[0]


def insert_after(lines: list[str], target: str, additions: list[str], start: int = 0, end: int | None = None) -> None:
    i = idx(lines, target, start, end)
    lines[i + 1:i + 1] = additions


def update_sound_report() -> None:
    path = Path("preflight-agent/src/main/java/dev/starsector/preflight/agent/SoundLoaderContractReport.java")
    lines = path.read_text().splitlines()

    insert_after(lines, '        root.put("callLimitPerMethod", CALL_LIMIT);', [
        '        root.put("sameClassCallLimitPerMethod", CALL_LIMIT);',
        '        root.put("constructorCallLimitPerMethod", CALL_LIMIT);',
    ])
    insert_after(lines, '        boolean callsTruncated = false;', [
        '        boolean sameClassCallsTruncated = false;',
        '        boolean constructorCallsTruncated = false;',
    ])

    i = idx(lines, '                if (owner.equals(call.owner)) sameClassCalls.putIfAbsent(key, edge);')
    if lines[i + 1] != '                if (call.name.equals("<init>")) constructors.putIfAbsent(key, edge);':
        raise RuntimeError("Constructor edge anchor changed")
    lines[i:i + 2] = [
        '                if (owner.equals(call.owner)) {',
        '                    if (sameClassCalls.size() < CALL_LIMIT || sameClassCalls.containsKey(key)) {',
        '                        sameClassCalls.putIfAbsent(key, edge);',
        '                    } else {',
        '                        sameClassCallsTruncated = true;',
        '                    }',
        '                }',
        '                if (call.name.equals("<init>")) {',
        '                    if (constructors.size() < CALL_LIMIT || constructors.containsKey(key)) {',
        '                        constructors.putIfAbsent(key, edge);',
        '                    } else {',
        '                        constructorCallsTruncated = true;',
        '                    }',
        '                }',
    ]

    method_return = idx(lines, '        return new MethodStructure(')
    calls_arg = idx(lines, '                callsTruncated,', method_return)
    lines[calls_arg + 1:calls_arg + 1] = [
        '                sameClassCallsTruncated,',
        '                constructorCallsTruncated,',
    ]

    frame_line = idx(lines, '                Frame<BasicValue> frame = index >= 0 && index < frames.length ? frames[index] : null;')
    points_start = idx(lines, '                points.add(new FlowPoint(', frame_line)
    points_end = idx(lines, '                        frameValues(frame, false)));', points_start)
    lines[frame_line + 1:points_end + 1] = [
        '                FrameValues locals = frameValues(frame, true);',
        '                FrameValues stack = frameValues(frame, false);',
        '                points.add(new FlowPoint(',
        '                        index,',
        '                        instruction.getOpcode(),',
        '                        flowKind(instruction),',
        '                        flowTarget(instruction),',
        '                        locals.values(),',
        '                        locals.truncated(),',
        '                        stack.values(),',
        '                        stack.truncated()));',
    ]

    frame_method_start = idx(lines, '    private static List<String> frameValues(Frame<BasicValue> frame, boolean locals) {')
    frame_method_end = idx(lines, '    private static String frameValue(BasicValue value) {', frame_method_start)
    lines[frame_method_start:frame_method_end] = [
        '    private static FrameValues frameValues(Frame<BasicValue> frame, boolean locals) {',
        '        if (frame == null) return new FrameValues(List.of(), false);',
        '        int count = locals ? frame.getLocals() : frame.getStackSize();',
        '        int retained = Math.min(count, FRAME_VALUE_LIMIT);',
        '        List<String> result = new ArrayList<>(retained);',
        '        for (int i = 0; i < retained; i++) {',
        '            BasicValue value = locals ? frame.getLocal(i) : frame.getStack(i);',
        '            result.add(frameValue(value));',
        '        }',
        '        return new FrameValues(List.copyOf(result), count > retained);',
        '    }',
        '',
    ]

    method_record = idx(lines, '    private record MethodStructure(')
    method_json = idx(lines, '        Map<String, Object> toMap() {', method_record)
    calls_field = idx(lines, '            boolean callsTruncated,', method_record, method_json)
    lines[calls_field + 1:calls_field + 1] = [
        '            boolean sameClassCallsTruncated,',
        '            boolean constructorCallsTruncated,',
    ]
    calls_json = idx(lines, '            values.put("callsTruncated", callsTruncated);', method_json)
    lines[calls_json + 1:calls_json + 1] = [
        '            values.put("sameClassCallsTruncated", sameClassCallsTruncated);',
        '            values.put("constructorCallsTruncated", constructorCallsTruncated);',
    ]

    flow_record = idx(lines, '    private record FlowPoint(')
    flow_json = idx(lines, '        Map<String, Object> toMap() {', flow_record)
    locals_field = idx(lines, '            List<String> locals,', flow_record, flow_json)
    lines[locals_field + 1:locals_field + 1] = ['            boolean localsTruncated,']
    stack_field = idx(lines, '            List<String> stack) {', flow_record, flow_json + 1)
    lines[stack_field:stack_field + 1] = [
        '            List<String> stack,',
        '            boolean stackTruncated) {',
    ]
    locals_json = idx(lines, '            values.put("locals", locals);', flow_json)
    lines[locals_json + 1:locals_json + 1] = ['            values.put("localsTruncated", localsTruncated);']
    stack_json = idx(lines, '            values.put("stack", stack);', locals_json)
    lines[stack_json + 1:stack_json + 1] = ['            values.put("stackTruncated", stackTruncated);']

    source_suffix = idx(lines, '    private record SourceSuffix(String value, boolean truncated) {')
    lines[source_suffix:source_suffix] = [
        '    private record FrameValues(List<String> values, boolean truncated) {',
        '    }',
        '',
    ]
    path.write_text("\n".join(lines) + "\n")


def update_sound_report_test() -> None:
    path = Path("preflight-agent/src/test/java/dev/starsector/preflight/agent/SoundLoaderContractReportTest.java")
    lines = path.read_text().splitlines()
    insert_after(lines, 'import org.junit.jupiter.api.io.TempDir;', [
        'import org.objectweb.asm.ClassWriter;',
        'import org.objectweb.asm.MethodVisitor;',
        'import org.objectweb.asm.Opcodes;',
    ])
    insert_after(lines, '        assertTrue(json.contains("\\\"flowPointsTruncated\\\":false"), json);', [
        '        assertTrue(json.contains("\\\"sameClassCallsTruncated\\\":false"), json);',
        '        assertTrue(json.contains("\\\"constructorCallsTruncated\\\":false"), json);',
        '        assertTrue(json.contains("\\\"localsTruncated\\\":false"), json);',
        '        assertTrue(json.contains("\\\"stackTruncated\\\":false"), json);',
    ])
    helper = idx(lines, '    private static AdapterSourceIdentity source(String path, String loaderClass, String loaderName) {')
    block = '''    @Test
    void structuralEdgesAndFrameValuesAreExplicitlyBounded() throws Exception {
        byte[] bytes = oversizedStructuralClass();
        ClassSignature signature = ClassSignature.parse(bytes);
        Path output = temporaryDirectory.resolve("structural-bounds.json");
        SoundLoaderContractReport report = new SoundLoaderContractReport(output);

        report.observed(signature, source("/synthetic/fs.common_obf.jar", "example.Loader", "synthetic"), bytes);
        report.write();

        String json = Files.readString(output);
        assertTrue(json.contains("\\\"sameClassCallLimitPerMethod\\\":" + SoundLoaderContractReport.CALL_LIMIT), json);
        assertTrue(json.contains("\\\"constructorCallLimitPerMethod\\\":" + SoundLoaderContractReport.CALL_LIMIT), json);
        assertTrue(json.contains("\\\"sameClassCallsTruncated\\\":true"), json);
        assertTrue(json.contains("\\\"constructorCallsTruncated\\\":true"), json);
        assertTrue(json.contains("\\\"localsTruncated\\\":true"), json);
    }

    private static byte[] oversizedStructuralClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "sound/J", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "oversized", "()V", null, null);
        method.visitCode();
        for (int index = 0; index < SoundLoaderContractReport.CALL_LIMIT + 5; index++) {
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "sound/J", "same" + index, "()V", false);
            String owner = "example/Constructed" + index;
            method.visitTypeInsn(Opcodes.NEW, owner);
            method.visitInsn(Opcodes.DUP);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
            method.visitInsn(Opcodes.POP);
        }
        for (int index = 0; index < SoundLoaderContractReport.FRAME_VALUE_LIMIT + 5; index++) {
            method.visitInsn(Opcodes.ICONST_0);
            method.visitVarInsn(Opcodes.ISTORE, index);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, SoundLoaderContractReport.FRAME_VALUE_LIMIT + 5);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

'''.splitlines()
    lines[helper:helper] = block
    path.write_text("\n".join(lines) + "\n")


def update_cli_pom() -> None:
    path = Path("preflight-cli/pom.xml")
    lines = path.read_text().splitlines()
    insert_after(lines, '              <createDependencyReducedPom>false</createDependencyReducedPom>', [
        '              <relocations>',
        '                <relocation>',
        '                  <pattern>org.objectweb.asm</pattern>',
        '                  <shadedPattern>dev.starsector.preflight.internal.asm</shadedPattern>',
        '                </relocation>',
        '              </relocations>',
    ])
    path.write_text("\n".join(lines) + "\n")


def update_packaged_test() -> None:
    path = Path("preflight-cli/src/test/java/dev/starsector/preflight/cli/AdapterAgentIT.java")
    lines = path.read_text().splitlines()
    insert_after(lines, 'import java.util.concurrent.TimeUnit;', ['import java.util.jar.JarFile;'])
    first_launch = idx(lines, '        ProcessResult result = launch(agentArguments);')
    lines[first_launch:first_launch] = ['        assertAsmIsRelocated();']
    launch_method = idx(lines, '    private ProcessResult launch(String agentArguments) throws Exception {')
    helper = '''    private static void assertAsmIsRelocated() throws Exception {
        Path agent = Path.of("target", "preflight.jar").toAbsolutePath().normalize();
        try (JarFile jar = new JarFile(agent.toFile())) {
            assertTrue(jar.getEntry("dev/starsector/preflight/internal/asm/ClassReader.class") != null);
            assertTrue(jar.getEntry("org/objectweb/asm/ClassReader.class") == null);
        }
    }

'''.splitlines()
    lines[launch_method:launch_method] = helper
    path.write_text("\n".join(lines) + "\n")


def main() -> None:
    update_sound_report()
    update_sound_report_test()
    update_cli_pom()
    update_packaged_test()
    Path(".github/workflows/run-self-review-script.yml").unlink()
    Path(__file__).unlink()


if __name__ == "__main__":
    main()
