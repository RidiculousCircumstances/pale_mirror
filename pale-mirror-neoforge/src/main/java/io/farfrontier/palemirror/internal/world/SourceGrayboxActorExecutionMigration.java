package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;

/** Strict, one-time SavedData migrations for actor execution source bindings. */
final class SourceGrayboxActorExecutionMigration {
    private SourceGrayboxActorExecutionMigration() { }

    /** A bad execution ledger is not allowed to fabricate or forget a source body during hydration. */
    static void assertMatchesSource(ReferenceGrayboxActorExecutionState execution, ReferenceGrayboxSnapshot snapshot) {
        Map<String, ReferenceGrayboxActorExecutionState.ActorDescriptor> expected = expected(snapshot);
        for (ReferenceGrayboxActorExecutionState.ActorState actor : execution.actors()) {
            ReferenceGrayboxActorExecutionState.ActorDescriptor descriptor = expected.remove(actor.id());
            if (descriptor == null) {
                if (actor.mode() != ReferenceGrayboxActorExecutionState.Mode.RETIRED) {
                    throw new IllegalStateException("source graybox execution has a non-retired actor absent from source: " + actor.id());
                }
                continue;
            }
            if (actor.mode() == ReferenceGrayboxActorExecutionState.Mode.RETIRED || actor.kind() != descriptor.kind()
                    || !actor.sourceRevision().equals(descriptor.sourceRevision())) {
                throw new IllegalStateException("source graybox actor execution disagrees with source: " + actor.id());
            }
        }
        if (!expected.isEmpty()) {
            throw new IllegalStateException("source graybox actor execution is missing source actors: " + expected.keySet().iterator().next());
        }
    }

    /** v23/v24 had a global snapshot revision, not an exact actor semantic revision. */
    static void rebindLegacyRevision(ReferenceGrayboxActorExecutionState execution, ReferenceGrayboxSnapshot snapshot, int schema) {
        requireExactIdentityAndKind(execution, snapshot, "v" + schema + " source graybox actor execution");
        execution.reconcile(snapshot);
        assertMatchesSource(execution, snapshot);
    }

    /**
     * v27 projected bodies into source-visible geometry. v28 changes only their
     * deterministic admission slots, retaining every leased body's actual hand-off.
     */
    static void rebindV27AdmissionSlots(ReferenceGrayboxActorExecutionState execution, ReferenceGrayboxSnapshot snapshot) {
        requireExactIdentityAndKind(execution, snapshot, "v27 source graybox actor admission migration");
        execution.reconcile(snapshot);
        assertMatchesSource(execution, snapshot);
    }

    private static void requireExactIdentityAndKind(ReferenceGrayboxActorExecutionState execution,
                                                    ReferenceGrayboxSnapshot snapshot, String context) {
        Map<String, ReferenceGrayboxActorExecutionState.ActorDescriptor> expected = expected(snapshot);
        for (ReferenceGrayboxActorExecutionState.ActorState actor : execution.actors()) {
            ReferenceGrayboxActorExecutionState.ActorDescriptor descriptor = expected.remove(actor.id());
            if (descriptor == null || actor.mode() == ReferenceGrayboxActorExecutionState.Mode.RETIRED
                    || actor.kind() != descriptor.kind()) {
                throw new IllegalStateException(context + " cannot be safely rebound: " + actor.id());
            }
        }
        if (!expected.isEmpty()) {
            throw new IllegalStateException(context + " is missing source actors: " + expected.keySet().iterator().next());
        }
    }

    private static Map<String, ReferenceGrayboxActorExecutionState.ActorDescriptor> expected(ReferenceGrayboxSnapshot snapshot) {
        Map<String, ReferenceGrayboxActorExecutionState.ActorDescriptor> result = new LinkedHashMap<>();
        for (ReferenceGrayboxActorExecutionState.ActorDescriptor descriptor : ReferenceGrayboxActorExecutionState.descriptors(snapshot)) {
            result.put(descriptor.id(), descriptor);
        }
        return result;
    }
}
