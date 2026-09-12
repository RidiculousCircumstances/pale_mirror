package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPayloads.*;
import static org.junit.jupiter.api.Assertions.*;

class FencedRecoveryStateTest {
    private static final SubjectId BODY = new SubjectId("body:recovery-a");
    private static final SubjectId CARGO = new SubjectId("cargo:recovery-a");
    private static final SubjectId CONTAINER = new SubjectId("container:recovery-a");
    private static final SubjectId EFFECT = new SubjectId("effect:recovery-a");
    private static final SubjectId OWNER = new SubjectId("operation:recovery-a");

    @Test
    void noVisitRevocationFencesLateBodyWhileUnrelatedCargoRemainsCurrent() {
        FencedRecoveryState state = FencedRecoveryState.empty().prepare(binding(BODY, FencedRecoveryAsset.BODY, 1L, true))
                .running(BODY, 1L).revokeToCold(BODY, 1L)
                .prepare(binding(CARGO, FencedRecoveryAsset.CARGO, 1L, false)).running(CARGO, 1L);

        assertEquals(FencedRecoveryDisposition.REJECT_STALE, state.lateLoad(BODY, FencedRecoveryAsset.BODY, OWNER, 1L));
        assertEquals(FencedRecoveryDisposition.RECLAIM, state.lateLoad(CARGO, FencedRecoveryAsset.CARGO, OWNER, 1L));
        assertEquals(1L, state.tombstones().get(BODY).retiredEpoch());
        assertEquals(FencedRecoveryDisposition.RESUME_COLD, state.tombstones().get(BODY).disposition());
        assertThrows(IllegalArgumentException.class, () -> state.prepare(binding(BODY, FencedRecoveryAsset.BODY, 1L, true)));
        assertEquals(2L, state.prepare(binding(BODY, FencedRecoveryAsset.BODY, 2L, true)).current().get(BODY).authorityEpoch());
    }

    @Test
    void observedExternalCustodyNeverRollsBackAndAmbiguityStaysLocalUntilBoundedAbandonment() {
        FencedRecoveryState state = FencedRecoveryState.empty().prepare(binding(CONTAINER, FencedRecoveryAsset.CONTAINER, 1L, false))
                .running(CONTAINER, 1L).observed(CONTAINER, 1L);
        assertThrows(IllegalArgumentException.class, () -> state.revokeToCold(CONTAINER, 1L), "an external/container observation is not a reversible pose checkpoint");

        FencedRecoveryState ambiguous = state.ambiguous(CONTAINER, 1L, "player-custody-uninspectable", FencedRecoveryDisposition.INSPECT)
                .ambiguous(CONTAINER, 1L, "player-custody-uninspectable", FencedRecoveryDisposition.RETRY)
                .ambiguous(CONTAINER, 1L, "player-custody-uninspectable", FencedRecoveryDisposition.REPAIR);
        assertEquals(FencedRecoveryPhase.AMBIGUOUS, ambiguous.current().get(CONTAINER).phase());
        assertEquals(FencedRecoveryDisposition.ABANDON, ambiguous.current().get(CONTAINER).nextAction());
        FencedRecoveryState abandoned = ambiguous.abandon(CONTAINER, 1L);
        assertTrue(abandoned.current().isEmpty());
        assertEquals(FencedRecoveryDisposition.ABANDON, abandoned.tombstones().get(CONTAINER).disposition());
    }

    @Test
    void conflictResolutionCanOnlyFenceTheExactAmbiguousAuthorityBeforePreparingItsSuccessor() {
        FencedRecoveryState ambiguous = FencedRecoveryState.empty().prepare(binding(BODY, FencedRecoveryAsset.BODY, 1L, true))
                .running(BODY, 1L).ambiguous(BODY, 1L, "local-obstruction", FencedRecoveryDisposition.INSPECT);
        FencedRecoveryState successor = ambiguous.supersedeAmbiguous(binding(BODY, FencedRecoveryAsset.BODY, 2L, true), "conflict-resolved");

        assertEquals(2L, successor.current().get(BODY).authorityEpoch());
        assertEquals(FencedRecoveryDisposition.REJECT_STALE, successor.lateLoad(BODY, FencedRecoveryAsset.BODY, OWNER, 1L));
        assertThrows(IllegalArgumentException.class, () -> FencedRecoveryState.empty().supersedeAmbiguous(binding(BODY, FencedRecoveryAsset.BODY, 1L, true), "forged"));
    }

    @Test
    void allFourFamiliesRoundTripSnapshotAndWalReducerRejectsStaleEpochsBeforeMutation() {
        FencedRecoveryState recovery = FencedRecoveryState.empty()
                .prepare(binding(BODY, FencedRecoveryAsset.BODY, 1L, true))
                .prepare(binding(CARGO, FencedRecoveryAsset.CARGO, 1L, false))
                .prepare(binding(CONTAINER, FencedRecoveryAsset.CONTAINER, 1L, false))
                .prepare(binding(EFFECT, FencedRecoveryAsset.EFFECT, 1L, false));
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:fenced-recovery"), 91L));
        FrontierWorldState changed = baseline.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(recovery));
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        byte[] encoded = codec.encode(changed);
        assertArrayEquals(encoded, codec.encode(codec.decode(encoded)));
        assertEquals(recovery, codec.decode(encoded).fencedRecovery());

        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:fenced-recovery-wal"), 91L));
        submit(engine, new Prepared(binding(EFFECT, FencedRecoveryAsset.EFFECT, 1L, false)));
        submit(engine, new Running(EFFECT, 1L));
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, new Observed(EFFECT, 2L)));
        assertEquals(FencedRecoveryPhase.RUNNING, new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState()).fencedRecovery().current().get(EFFECT).phase());
        submit(engine, new Observed(EFFECT, 1L));
        submit(engine, new Confirmed(EFFECT, 1L));
        assertEquals(FencedRecoveryDisposition.REJECT_STALE, new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState()).fencedRecovery().tombstones().get(EFFECT).disposition());
    }

    @Test
    void boundedTombstoneRetentionCompactsOnlyForANewDurableAuthorityAndStillRejectsUnknownLateProjection() {
        java.util.Map<SubjectId, FencedRecoveryTombstone> retained = new java.util.LinkedHashMap<>();
        for (int index = 0; index < FencedRecoveryState.MAX_BINDINGS; index++) {
            SubjectId id = new SubjectId("body:retention-" + String.format("%04d", index));
            retained.put(id, new FencedRecoveryTombstone(id, FencedRecoveryAsset.BODY, OWNER, 7L, 1L,
                    FencedRecoveryDisposition.REJECT_STALE, "confirmed"));
        }
        FencedRecoveryState full = new FencedRecoveryState(Map.of(), retained);
        SubjectId successor = new SubjectId("body:retention-successor");
        FencedRecoveryState next = full.prepare(binding(successor, FencedRecoveryAsset.BODY, 1L, true));

        assertEquals(FencedRecoveryState.MAX_BINDINGS, next.current().size() + next.tombstones().size());
        assertTrue(next.current().containsKey(successor));
        assertFalse(next.tombstones().containsKey(new SubjectId("body:retention-0000")),
                "compaction is deterministic rather than depending on map iteration or a later visitor");
        assertEquals(FencedRecoveryDisposition.REJECT_STALE,
                next.lateLoad(new SubjectId("body:retention-0000"), FencedRecoveryAsset.BODY, OWNER, 1L),
                "compaction never converts a forgotten projection into authority");
    }

    private static FencedRecoveryBinding binding(SubjectId id, FencedRecoveryAsset asset, long epoch, boolean reversible) {
        return FencedRecoveryBinding.prepared(id, asset, OWNER, 7L, epoch, reversible);
    }
    private static CommandResult submit(FrontierEngine<FrontierWorldProjection> engine, FrontierPayload payload) {
        var canonical = engine.checkpoint(); CommandId id = new CommandId("command:fenced-recovery-" + canonical.revision().value());
        return engine.submit(new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, canonical.worldId(), canonical.revision(), canonical.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
    }
}
