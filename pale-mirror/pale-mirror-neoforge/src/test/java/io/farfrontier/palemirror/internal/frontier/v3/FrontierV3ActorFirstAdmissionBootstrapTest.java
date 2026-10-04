package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ActorFirstAdmissionBootstrapTest {
    @Test void freshWorldPublishesAllExplicitIdentitiesBeforeRuntimeAndCanResumeUnusedIssuance() {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:first-issuance"), 91L);
        var state = config.initialState(); var recovery = new RecoveryImage(config.worldId(), Optional.empty(), List.of());
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); var writes = new java.util.concurrent.atomic.AtomicInteger();
        assertTrue(FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, state, recovery, () -> {
            assertEquals(state.actorLocations().size(), ledger.firstAdmissions().size()); writes.incrementAndGet();
        }));
        var restored = FrontierV3AmbientCarrierLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertTrue(FrontierV3ActorFirstAdmissionBootstrap.initialize(restored, state, recovery, writes::incrementAndGet));
        assertEquals(2, writes.get());
        var first = restored.firstAdmissions().getFirst();
        var target = FrontierV3ActorOwnerBinding.body(new FrontierV3ActorCarrierComposition.Declaration(
                first.identity().actorId(), first.identity().kind(), FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                first.identity().entityId(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, 1L));
        assertTrue(restored.beginFirstAdmission(target));
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmissionBootstrap.initialize(restored, state, recovery,
                () -> fail("used first-admission history must not be republished as fresh")));
    }
    @Test void recoveredWorldWithMissingLedgerDoesNotReceiveFreshPermits() {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:recovered-not-fresh"), 91L);
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(config);
        var recovery = new RecoveryImage(config.worldId(), Optional.of(new SnapshotRecord(engine.checkpoint(), 0L)), List.of());
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertFalse(FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, config.initialState(), recovery,
                () -> fail("recovery must not persist invented fresh history")));
        assertTrue(ledger.firstAdmissions().isEmpty());
    }
    @Test void lateUsedPermitCannotLeaveEarlierBootstrapPermissionsPartiallyRegistered() {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:first-batch-rejection"), 91L);
        var state = config.initialState();
        var recovery = new RecoveryImage(config.worldId(), Optional.empty(), List.of());
        var order = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        state.humanPopulation().residents().values().forEach(resident -> order.add(resident.id()));
        state.bootstrap().hive().bioforms().forEach(bioform -> order.add(bioform.id()));
        state.hiveColony().spawnedBioforms().values().forEach(bioform -> order.add(bioform.id()));
        var lastActor = order.getLast();
        var seeded = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(FrontierV3ActorFirstAdmissionBootstrap.initialize(seeded, state, recovery, () -> {}));
        var permit = seeded.firstAdmission(lastActor).orElseThrow();
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.registerFirstAdmission(permit));
        var id = permit.identity();
        var binding = FrontierV3ActorOwnerBinding.body(new FrontierV3ActorCarrierComposition.Declaration(
                id.actorId(), id.kind(), FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                id.entityId(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, 1));
        assertTrue(ledger.beginFirstAdmission(binding));
        var before = ledger.save(new net.minecraft.nbt.CompoundTag(), null);
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, state, recovery,
                () -> fail("rejected bootstrap must not persist a partial roster")));
        assertEquals(before, ledger.save(new net.minecraft.nbt.CompoundTag(), null));
        assertEquals(1, ledger.firstAdmissions().size());
    }
}
