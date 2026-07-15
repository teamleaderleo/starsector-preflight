package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void mainCliWritesRequestedProbeAnalysis() throws Exception {
        Path adapter = temporaryDirectory.resolve("adapter.json");
        Path summary = temporaryDirectory.resolve("summary.json");
        Path output = temporaryDirectory.resolve("joined report.json");
        Files.writeString(adapter, """
                {"mode":"PROBE","rankedCandidates":[
                  {"className":"com/fs/starfarer/A","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                   "relevanceScore":5,"sourceKind":"STARSECTOR_CORE","codeSource":null,"evidence":[]}
                ]}
                """);
        Files.writeString(summary, """
                {"imageReadStackAttribution":{"topMethods":[
                  {"className":"com/fs/starfarer/A","methodName":"a","descriptor":"()V",
                   "behavioralScore":10,"events":1,"bytes":100,"durationMs":0.1,"minimumDepth":1,
                   "depthWeight":10,"pathSamples":["graphics/a.png"],"pathsTruncated":false}
                ]}}
                """);

        int status = PreflightCli.run(new String[] {
                "analyze", "probe", adapter.toString(), summary.toString(), "--json", output.toString()
        });

        assertEquals(0, status);
        assertTrue(Files.isRegularFile(output));
        assertTrue(Files.readString(output).contains("\"combinedScore\":15"));
    }
}
