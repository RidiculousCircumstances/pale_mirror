package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Explicit runtime/artifact identity supplied by an owning host; no reducer may invent it. */
public record DiagnosticRuntimeIdentity(String runtime, String sourceTree, String jar, String restartIdentity) {
    public DiagnosticRuntimeIdentity {
        runtime = field(runtime); sourceTree = field(sourceTree); jar = field(jar); restartIdentity = field(restartIdentity);
    }
    public static DiagnosticRuntimeIdentity unavailable() { return new DiagnosticRuntimeIdentity("frontier-v3", "unavailable", "unavailable", "unavailable"); }
    public boolean complete() { return !sourceTree.equals("unavailable") && !jar.equals("unavailable") && !restartIdentity.equals("unavailable"); }
    private static String field(String value) { value = Objects.requireNonNull(value, "diagnostic runtime identity"); if (value.isBlank() || value.length() > 512) throw new IllegalArgumentException("diagnostic runtime identity"); return value; }
}
