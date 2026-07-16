package dev.starsector.preflight.synthetic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SyntheticJdkSourceCompilerTest {
    @Test
    void compilerReturnsTheCompleteGeneratedClassMap() throws Exception {
        String source = "package synthetic.generated;\n"
                + "public final class Source00000 {\n"
                + "  public int value() { Runnable r = new Runnable(){ public void run(){} }; "
                + "r.run(); return Inner.VALUE; }\n"
                + "  static final class Inner { static final int VALUE = 7; }\n"
                + "}\n";
        AtomicInteger calls = new AtomicInteger();

        Map<String, byte[]> classes = SyntheticJdkSourceCompiler.compile(
                Map.of(
                        "data/scripts/generated/Source00000.java",
                        source.getBytes(StandardCharsets.UTF_8)),
                calls);

        assertEquals(1, calls.get());
        assertEquals(
                Set.of(
                        "synthetic.generated.Source00000",
                        "synthetic.generated.Source00000$1",
                        "synthetic.generated.Source00000$Inner"),
                classes.keySet());
        classes.values().forEach(bytes -> assertTrue(bytes.length > 0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> classes.put("synthetic.generated.Other", new byte[] {1}));
    }

    @Test
    void outputBudgetsRejectPerClassAndAggregateOverflowBeforeWriting() throws Exception {
        SyntheticJdkSourceCompiler.OutputBudget budget =
                new SyntheticJdkSourceCompiler.OutputBudget(5);
        SyntheticJdkSourceCompiler.BoundedOutput first =
                new SyntheticJdkSourceCompiler.BoundedOutput(4, budget);
        first.write(new byte[] {1, 2, 3, 4});
        assertThrows(IOException.class, () -> first.write(5));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, first.toByteArray());
        assertEquals(4, budget.totalBytes());

        SyntheticJdkSourceCompiler.BoundedOutput second =
                new SyntheticJdkSourceCompiler.BoundedOutput(4, budget);
        second.write(6);
        assertThrows(IOException.class, () -> second.write(7));
        assertArrayEquals(new byte[] {6}, second.toByteArray());
        assertEquals(5, budget.totalBytes());

        second.close();
        assertThrows(IOException.class, () -> second.write(8));
    }

    @Test
    void compilerDiagnosticTextRemainsBounded() {
        LinkedHashMap<String, byte[]> sources = new LinkedHashMap<>();
        for (int i = 0; i < 300; i++) {
            String className = String.format("Source%05d", i);
            String source = "package synthetic.generated; public final class "
                    + className
                    + " { int broken = ; }";
            sources.put(
                    "data/scripts/generated/" + className + ".java",
                    source.getBytes(StandardCharsets.UTF_8));
        }

        IOException error = assertThrows(
                IOException.class,
                () -> SyntheticJdkSourceCompiler.compile(sources, new AtomicInteger()));

        assertTrue(error.getMessage().startsWith("Synthetic Java compilation failed:"));
        assertTrue(error.getMessage().length()
                <= SyntheticJdkSourceCompiler.MAX_DIAGNOSTIC_CHARS + 64);
    }
}
