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
        DiagnosticRuntimeIdentity identity = DiagnosticCaptureScope.current().orElse(DiagnosticRuntimeIdentity.unavailable());
        String causes = event.causes().commands().stream().map(value -> value.value()).collect(java.util.stream.Collectors.joining("->"));
        String payload = event.payload().type();
        java.util.Optional<DiagnosticTuple> tuple = DiagnosticIncidentExtractor.tuple(event.payload());
        String claim = tuple.map(value -> "expected_owner=" + value.owner().kind() + ":" + value.owner().id().value()
                + ";observed_subject=" + value.subject().kind() + ":" + value.subject().id().value()).orElse("not_applicable");
        String reconciliation = tuple.map(value -> "category=" + value.category() + ";disposition=" + value.disposition())
                .orElse("not_applicable");
        String physical = "expected=canonical_projection:" + state.bootstrap().canonicalSha256() + ";observed_event=" + payload;
        String intent = "not_applicable";
        String observation = "not_observed";
        if (event.payload() instanceof PhysicalIntentTransition transition) {
            intent = "intent=" + transition.intentId().value() + ";status=" + transition.status();
            observation = transition.observation().map(value -> "observed=" + value.getClass().getSimpleName()).orElse("not_observed");
        }
        boolean complete = identity.complete();
        String degradation = complete ? "complete" : "runtime_source_tree_jar_restart_identity_unavailable";
        return new DiagnosticIncidentContext(state.bootstrap().worldId().value(), identity.runtime(), identity.sourceTree(), identity.jar(), state.bootstrap().ruleset().id() + ":" + state.bootstrap().ruleset().schemaVersion() + ":" + state.bootstrap().ruleset().contentSha256(), identity.restartIdentity(), causes.isBlank() ? "root" : causes, physical, claim, intent, observation, reconciliation, state.bootstrap().canonicalSha256(), complete, degradation);
    }
    public static DiagnosticIncidentContext unavailable() { return new DiagnosticIncidentContext("unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", false, "not_captured"); }
    private static String field(String value) { value = Objects.requireNonNull(value, "context field"); if (value.isBlank() || value.length() > MAX) throw new IllegalArgumentException("invalid bounded context field"); return value; }
}
