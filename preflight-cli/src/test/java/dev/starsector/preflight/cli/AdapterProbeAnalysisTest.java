package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdapterProbeAnalysisTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void joinsExactClassNamesAcrossFullRetainedAndDetailedCandidateLists() throws Exception {
        Path adapter = temporaryDirectory.resolve("adapter.json");
        Path summary = temporaryDirectory.resolve("summary.json");
        Path output = temporaryDirectory.resolve("adapter-analysis.json");
        Files.writeString(adapter, """
                {"mode":"PROBE",
                 "candidates":[
                  {"className":"com/fs/starfarer/A","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                   "relevanceScore":40,"sourceKind":"STARSECTOR_CORE","codeSource":"file:/game/core.jar"},
                  {"className":"com/fs/starfarer/B","sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                   "relevanceScore":100,"sourceKind":"STARSECTOR_CORE","codeSource":null}
                 ],
                 "rankedCandidates":[
                  {"className":"com/fs/starfarer/B","sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                   "relevanceScore":100,"sourceKind":"STARSECTOR_CORE","codeSource":null,
                   "evidence":["class name contains texture"]}
                 ]}
                """);
        Files.writeString(summary, """
                {"imageReadStackAttribution":{"topMethods":[
                  {"className":"com/fs/starfarer/A","methodName":"a","descriptor":"(Ljava/lang/String;)[B",
                   "behavioralScore":500,"events":10,"bytes":20480,"durationMs":4.5,"minimumDepth":1,
                   "depthWeight":200,"pathSamples":["graphics/a.png"],"pathsTruncated":false},
                  {"className":"com/fs/starfarer/Obf","methodName":"x","descriptor":"()V",
                   "behavioralScore":300,"events":6,"bytes":8192,"durationMs":2.0,"minimumDepth":2,
                   "depthWeight":80,"pathSamples":["graphics/b.png"],"pathsTruncated":false}
                ]}}
                """);

        AdapterProbeAnalysis.Result result = AdapterProbeAnalysis.analyze(adapter, summary, output);

        assertEquals(2, result.candidateClasses());
        assertEquals(2, result.behavioralClasses());
        assertEquals(1, result.matchedClasses());
        assertEquals(1, result.behaviorOnlyClasses());
        assertTrue(Files.isRegularFile(output));
        Map<String, Object> report = StrictJson.object(Files.readString(output));
        assertEquals(Boolean.FALSE, report.get("automaticAllowlistGenerated"));
        assertEquals(Boolean.FALSE, report.get("liveTransformationEligible"));
        assertEquals(Boolean.TRUE, report.get("requiresHumanReview"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranked = (List<Map<String, Object>>) report.get("rankedCandidates");
        assertEquals("com/fs/starfarer/A", ranked.get(0).get("className"));
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                ranked.get(0).get("sha256"));
        assertEquals(540L, ranked.get(0).get("combinedScore"));
        assertEquals(Boolean.TRUE, ranked.get(0).get("hasBehavioralEvidence"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> methods = (List<Map<String, Object>>) ranked.get(0).get("behavioralMethods");
        assertEquals("(Ljava/lang/String;)[B", methods.get(0).get("descriptor"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> behaviorOnly = (List<Map<String, Object>>) report.get("behaviorOnly");
        assertEquals("com/fs/starfarer/Obf", behaviorOnly.get(0).get("className"));
    }

    @Test
    void emptyEvidenceProducesDiagnosticsWithoutInventingMatches() throws Exception {
        Path adapter = temporaryDirectory.resolve("empty-adapter.json");
        Path summary = temporaryDirectory.resolve("empty-summary.json");
        Path output = temporaryDirectory.resolve("empty-analysis.json");
        Files.writeString(adapter, "{\"mode\":\"PROBE\",\"rankedCandidates\":[]}");
        Files.writeString(summary, "{\"imageReadStackAttribution\":{\"topMethods\":[]}}");

        AdapterProbeAnalysis.analyze(adapter, summary, output);
        Map<String, Object> report = StrictJson.object(Files.readString(output));
        assertEquals(0L, report.get("matchedClasses"));
        assertFalse(((List<?>) report.get("diagnostics")).isEmpty());
    }
}
