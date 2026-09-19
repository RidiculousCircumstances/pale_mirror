package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import java.util.Objects;

/** Bounded first-boundary evidence. Unavailable values are explicit, never inferred later. */
public record DiagnosticIncidentContext(String world, String runtime, String sourceTree, String jar, String ruleset,
        String restartIdentity, String causalWindow, String physical, String claim, String intent, String observation,
        String reconciliation, String projection, boolean complete, String degradation) {
    private static final int MAX = 512;
    public DiagnosticIncidentContext {
        world = field(world); runtime = field(runtime); sourceTree = field(sourceTree); jar = field(jar); ruleset = field(ruleset); restartIdentity = field(restartIdentity); causalWindow = field(causalWindow); physical = field(physical); claim = field(claim); intent = field(intent); observation = field(observation); reconciliation = field(reconciliation); projection = field(projection); degradation = field(degradation);
        if (complete != degradation.equals("complete")) throw new IllegalArgumentException("context completeness disagrees with degradation");
    }
    public static DiagnosticIncidentContext capture(FrontierWorldState state, FrontierEvent event) {
        String causes = event.causes().commands().stream().map(value -> value.value()).collect(java.util.stream.Collectors.joining("->"));
        return new DiagnosticIncidentContext(state.bootstrap().worldId().value(), "frontier-v3", "unavailable", "unavailable", state.bootstrap().ruleset().id() + ":" + state.bootstrap().ruleset().schemaVersion() + ":" + state.bootstrap().ruleset().contentSha256(), "unavailable", causes.isBlank() ? "root" : causes, event.payload().type(), "not_applicable", "not_applicable", "not_applicable", "not_applicable", state.bootstrap().canonicalSha256(), false, "runtime_source_tree_jar_restart_identity_unavailable");
    }
    public static DiagnosticIncidentContext unavailable() { return new DiagnosticIncidentContext("unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", false, "not_captured"); }
    private static String field(String value) { value = Objects.requireNonNull(value, "context field"); if (value.isBlank() || value.length() > MAX) throw new IllegalArgumentException("invalid bounded context field"); return value; }
}
