package dev.starsector.preflight.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded evidence for constructing one exact generated-bytecode cache context.
 *
 * <p>Names, compiler settings, and character encodings are retained only as length/hash tokens.
 * Source and resource contents are never retained. An exact cache context is available only when
 * every declared completeness gate passes for the same requested root class.</p>
 */
public final class GeneratedBytecodeContextEvidence {
    public static final int MAX_CLASSPATH_ENTRIES = 512;
    public static final int MAX_LOOKUPS = 4_096;
    public static final int MAX_COMPILER_SETTINGS = 256;
    public static final int MAX_OBSERVED_TEXT_CHARS = 4_096;

    private static final String SCHEMA = "starsector-preflight-generated-bytecode-context-evidence-v1";

    private final Token requestedClass;
    private final String starsectorBuildSha256;
    private final String janinoImplementationSha256;
    private final List<ClasspathEntry> orderedClasspath;
    private final List<Lookup> orderedLookups;
    private final List<CompilerSetting> orderedCompilerSettings;
    private final Token characterEncoding;
    private final String parentLoaderIdentitySha256;
    private final String protectionDomainPolicySha256;
    private final Completion completion;

    public GeneratedBytecodeContextEvidence(
            String requestedClassName,
            String starsectorBuildSha256,
            String janinoImplementationSha256,
            List<ClasspathEntry> orderedClasspath,
            List<Lookup> orderedLookups,
            List<CompilerSetting> orderedCompilerSettings,
            String characterEncoding,
            String parentLoaderIdentitySha256,
            String protectionDomainPolicySha256,
            Completion completion) {
        this.requestedClass = Token.observed(requestedClassName, "requestedClassName");
        this.starsectorBuildSha256 = hash(starsectorBuildSha256, "starsectorBuildSha256");
        this.janinoImplementationSha256 = hash(janinoImplementationSha256, "janinoImplementationSha256");
        this.orderedClasspath = boundedCopy(
                orderedClasspath, MAX_CLASSPATH_ENTRIES, "orderedClasspath");
        this.orderedLookups = boundedCopy(orderedLookups, MAX_LOOKUPS, "orderedLookups");
        this.orderedCompilerSettings = boundedCopy(
                orderedCompilerSettings, MAX_COMPILER_SETTINGS, "orderedCompilerSettings");
        this.characterEncoding = Token.observed(characterEncoding, "characterEncoding");
        this.parentLoaderIdentitySha256 = hash(
                parentLoaderIdentitySha256, "parentLoaderIdentitySha256");
        this.protectionDomainPolicySha256 = hash(
                protectionDomainPolicySha256, "protectionDomainPolicySha256");
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    /** Returns an exact SPJB context only for the same request and complete evidence. */
    public Optional<GeneratedBytecodeContext> exactContextFor(String requestedClassName) {
        if (!incompleteReasonsFor(requestedClassName).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GeneratedBytecodeContext(
                starsectorBuildSha256,
                janinoImplementationSha256,
                orderedClasspathSha256(),
                orderedSourceGraphSha256(),
                compilerOptionsSha256(),
                parentLoaderIdentitySha256,
                protectionDomainPolicySha256));
    }

    /** Deterministic reasons why this evidence cannot participate in cache reuse. */
    public List<String> incompleteReasonsFor(String requestedClassName) {
        Token actual = Token.observed(requestedClassName, "requestedClassName");
        List<String> reasons = new ArrayList<>();
        if (!requestedClass.equals(actual)) reasons.add("requested-class-mismatch");
        if (orderedClasspath.isEmpty()) reasons.add("classpath-empty");
        if (orderedLookups.isEmpty()) reasons.add("lookup-trace-empty");
        if (!completion.lookupInterceptionComplete()) reasons.add("lookup-interception-incomplete");
        if (!completion.sourceGraphClosed()) reasons.add("source-graph-open");
        if (!completion.classpathOrderComplete()) reasons.add("classpath-order-incomplete");
        if (!completion.compilerSettingsComplete()) reasons.add("compiler-settings-incomplete");
        if (!completion.parentLoaderComplete()) reasons.add("parent-loader-incomplete");
        if (!completion.protectionDomainComplete()) reasons.add("protection-domain-incomplete");
        if (!completion.duplicateBehaviorProven()) reasons.add("duplicate-behavior-unproven");
        if (!completion.generatedClassMapComplete()) reasons.add("generated-class-map-incomplete");
        return List.copyOf(reasons);
    }

    public String evidenceSha256() {
        String canonical = SCHEMA + "\n"
                + "request=" + requestedClass.canonical() + "\n"
                + "starsector=" + starsectorBuildSha256 + "\n"
                + "janino=" + janinoImplementationSha256 + "\n"
                + "classpath=" + orderedClasspathSha256() + "\n"
                + "lookups=" + orderedSourceGraphSha256() + "\n"
                + "compiler=" + compilerOptionsSha256() + "\n"
                + "parent-loader=" + parentLoaderIdentitySha256 + "\n"
                + "protection-domain=" + protectionDomainPolicySha256 + "\n"
                + "completion=" + completion.canonical() + "\n";
        return Hashes.sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    /** Content-safe diagnostic summary. No observed names, options, or source bytes are emitted. */
    public Map<String, Object> summaryFor(String requestedClassName) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("schema", SCHEMA);
        values.put("evidenceSha256", evidenceSha256());
        values.put("requestedClassNameLength", requestedClass.length());
        values.put("requestedClassNameSha256", requestedClass.sha256());
        values.put("starsectorBuildSha256", starsectorBuildSha256);
        values.put("janinoImplementationSha256", janinoImplementationSha256);
        values.put("orderedClasspathSha256", orderedClasspathSha256());
        values.put("orderedSourceGraphSha256", orderedSourceGraphSha256());
        values.put("compilerOptionsSha256", compilerOptionsSha256());
        values.put("parentLoaderIdentitySha256", parentLoaderIdentitySha256);
        values.put("protectionDomainPolicySha256", protectionDomainPolicySha256);
        values.put("classpathEntryCount", orderedClasspath.size());
        values.put("lookupCount", orderedLookups.size());
        values.put("compilerSettingCount", orderedCompilerSettings.size());
        values.put("completion", completion.toMap());
        List<String> reasons = incompleteReasonsFor(requestedClassName);
        values.put("exactContextAvailable", reasons.isEmpty());
        values.put("incompleteReasons", reasons);
        exactContextFor(requestedClassName).ifPresent(context -> values.put("contextKeySha256", context.keySha256()));
        values.put("sourceContentsIncluded", false);
        values.put("resourceContentsIncluded", false);
        values.put("observedNamesIncluded", false);
        values.put("compilerSettingValuesIncluded", false);
        return Map.copyOf(values);
    }

    private String orderedClasspathSha256() {
        StringBuilder canonical = new StringBuilder(SCHEMA).append("\nclasspath\n");
        for (int index = 0; index < orderedClasspath.size(); index++) {
            canonical.append(index).append('=').append(orderedClasspath.get(index).canonical()).append('\n');
        }
        return Hashes.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String orderedSourceGraphSha256() {
        StringBuilder canonical = new StringBuilder(SCHEMA).append("\nlookups\n");
        for (int index = 0; index < orderedLookups.size(); index++) {
            canonical.append(index).append('=').append(orderedLookups.get(index).canonical()).append('\n');
        }
        return Hashes.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String compilerOptionsSha256() {
        StringBuilder canonical = new StringBuilder(SCHEMA)
                .append("\ncompiler-settings\nencoding=")
                .append(characterEncoding.canonical())
                .append('\n');
        for (int index = 0; index < orderedCompilerSettings.size(); index++) {
            canonical.append(index).append('=').append(orderedCompilerSettings.get(index).canonical()).append('\n');
        }
        return Hashes.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static <T> List<T> boundedCopy(List<T> values, int limit, String name) {
        Objects.requireNonNull(values, name);
        if (values.size() > limit) {
            throw new IllegalArgumentException(name + " exceeds " + limit + " entries");
        }
        List<T> copy = List.copyOf(values);
        for (T value : copy) Objects.requireNonNull(value, name + " entry");
        return copy;
    }

    private static String hash(String value, String name) {
        Objects.requireNonNull(value, name);
        Hashes.decodeSha256(value);
        return value.toLowerCase(Locale.ROOT);
    }

    public enum ProviderKind {
        ARCHIVE,
        DIRECTORY,
        MEMORY,
        PARENT_LOADER,
        OTHER
    }

    public enum LookupKind {
        SOURCE,
        RESOURCE,
        CLASS
    }

    public enum LookupOutcome {
        FOUND,
        MISS
    }

    /** One exact provider in classpath order. */
    public record ClasspathEntry(ProviderKind kind, String providerIdentitySha256) {
        public ClasspathEntry {
            kind = Objects.requireNonNull(kind, "kind");
            providerIdentitySha256 = hash(providerIdentitySha256, "providerIdentitySha256");
        }

        String canonical() {
            return kind.name() + ':' + providerIdentitySha256;
        }
    }

    /** One ordered source/resource/class lookup without retaining the logical name or content. */
    public record Lookup(
            LookupKind kind,
            Token logicalName,
            String providerIdentitySha256,
            LookupOutcome outcome,
            String contentSha256) {
        public Lookup {
            kind = Objects.requireNonNull(kind, "kind");
            logicalName = Objects.requireNonNull(logicalName, "logicalName");
            providerIdentitySha256 = hash(providerIdentitySha256, "providerIdentitySha256");
            outcome = Objects.requireNonNull(outcome, "outcome");
            if (outcome == LookupOutcome.FOUND) {
                contentSha256 = hash(contentSha256, "contentSha256");
            } else if (contentSha256 != null && !contentSha256.isBlank()) {
                throw new IllegalArgumentException("A MISS lookup may not include a content hash");
            } else {
                contentSha256 = "";
            }
        }

        public static Lookup observed(
                LookupKind kind,
                String logicalName,
                String providerIdentitySha256,
                LookupOutcome outcome,
                String contentSha256) {
            return new Lookup(
                    kind,
                    Token.observed(logicalName, "logicalName"),
                    providerIdentitySha256,
                    outcome,
                    contentSha256);
        }

        String canonical() {
            return kind.name() + ':' + logicalName.canonical() + ':' + providerIdentitySha256 + ':'
                    + outcome.name() + ':' + contentSha256;
        }
    }

    /** One ordered compiler setting retained only as key/value tokens. */
    public record CompilerSetting(Token key, Token value) {
        public CompilerSetting {
            key = Objects.requireNonNull(key, "key");
            value = Objects.requireNonNull(value, "value");
        }

        public static CompilerSetting observed(String key, String value) {
            return new CompilerSetting(
                    Token.observed(key, "compiler setting key"),
                    Token.observed(value, "compiler setting value"));
        }

        String canonical() {
            return key.canonical() + ':' + value.canonical();
        }
    }

    /** Length/hash representation of observed text. */
    public record Token(int length, String sha256) {
        public Token {
            if (length < 0 || length > MAX_OBSERVED_TEXT_CHARS) {
                throw new IllegalArgumentException(
                        "observed text length is outside 0.." + MAX_OBSERVED_TEXT_CHARS);
            }
            sha256 = hash(sha256, "sha256");
        }

        public static Token observed(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.length() > MAX_OBSERVED_TEXT_CHARS) {
                throw new IllegalArgumentException(name + " exceeds " + MAX_OBSERVED_TEXT_CHARS + " characters");
            }
            return new Token(value.length(), Hashes.sha256(value.getBytes(StandardCharsets.UTF_8)));
        }

        String canonical() {
            return length + ":" + sha256;
        }
    }

    /** Completeness gates required before any SPJB lookup or write is allowed. */
    public record Completion(
            boolean lookupInterceptionComplete,
            boolean sourceGraphClosed,
            boolean classpathOrderComplete,
            boolean compilerSettingsComplete,
            boolean parentLoaderComplete,
            boolean protectionDomainComplete,
            boolean duplicateBehaviorProven,
            boolean generatedClassMapComplete) {
        public static Completion complete() {
            return new Completion(true, true, true, true, true, true, true, true);
        }

        String canonical() {
            return lookupInterceptionComplete + ":" + sourceGraphClosed + ":" + classpathOrderComplete + ':'
                    + compilerSettingsComplete + ':' + parentLoaderComplete + ':' + protectionDomainComplete + ':'
                    + duplicateBehaviorProven + ':' + generatedClassMapComplete;
        }

        Map<String, Boolean> toMap() {
            Map<String, Boolean> values = new LinkedHashMap<>();
            values.put("lookupInterceptionComplete", lookupInterceptionComplete);
            values.put("sourceGraphClosed", sourceGraphClosed);
            values.put("classpathOrderComplete", classpathOrderComplete);
            values.put("compilerSettingsComplete", compilerSettingsComplete);
            values.put("parentLoaderComplete", parentLoaderComplete);
            values.put("protectionDomainComplete", protectionDomainComplete);
            values.put("duplicateBehaviorProven", duplicateBehaviorProven);
            values.put("generatedClassMapComplete", generatedClassMapComplete);
            return Map.copyOf(values);
        }
    }
}
