package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OfflineFirstAdmissionRecoveryPublicationTest {
    @TempDir Path directory;
    private static final SubjectId ACTOR = new SubjectId("resident:1-1");

    private static FrontierV3AmbientCarrierLedger pending(FrontierWorldState state) {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var body = FrontierV3AmbientActorExecutor.carrierDeclaration(state, ACTOR, FrontierV3AmbientActorExecutor.entityId(state, ACTOR), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1L);
        assertTrue(ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(ACTOR, body.kind(), body.entityId()))));
        assertTrue(ledger.beginFirstAdmission(FrontierV3ActorOwnerBinding.body(body)));
        return ledger;
    }

    private static FrontierV3OfflineActorAbsence.Proof proof(Path world, FrontierWorldState state, String digest) {
        return new FrontierV3OfflineActorAbsence.Proof(world,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), ACTOR), 1,
                List.of(new FrontierV3OfflineActorAbsence.FileProof("entities/r.0.0.mca", digest)));
    }

    @Test void receiptThenRearmResumeAcrossBothDurableBoundariesWithoutCanonicalMutation() throws Exception {
        for (var boundary : FrontierV3OfflineFirstAdmissionRecoveryPublication.Boundary.values()) {
            Path world = Files.createDirectory(directory.resolve(boundary.name())).toRealPath();
            Path ledgerFile = world.resolve("carriers.dat");
            var state = FrontierV3OfflineActorRecoveryPlanTest.prepared();
            var original = pending(state);
            var root = new CompoundTag(); root.putInt("DataVersion", 3955);
            root.put("data", original.save(new CompoundTag(), null));
            net.minecraft.SharedConstants.tryDetectVersion();
            original.save(ledgerFile.toFile(), null);
            var absence = proof(world, state, "first-region-proof");
            var config = FrontierV3OfflineActorRecoveryPlanTest.configuration(state);
            var runtime = FrontierV3ServerRuntime.start(config,
                    new FrontierFileStore(world, FrontierWorldRuntimeDefinition.payloadCodecs()), 1000);
            runtime.checkpoint().orElseThrow();
            var head = runtime.checkpointImage().orElseThrow().revision();
            assertThrows(IOException.class, () -> FrontierV3OfflineFirstAdmissionRecoveryPublication.publish(
                    runtime, absence, ACTOR, ledgerFile, true, reached -> {
                        if (reached == boundary) throw new IOException("simulated crash after " + reached);
                    }));
            var afterCrash = FrontierV3AmbientCarrierLedger.readFile(ledgerFile, null);
            assertEquals(boundary == FrontierV3OfflineFirstAdmissionRecoveryPublication.Boundary.RECEIPT_DURABLE
                            ? FrontierV3ActorFirstAdmission.Phase.PENDING : FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT,
                    afterCrash.firstAdmission(ACTOR).orElseThrow().phase());
            var recovered = FrontierV3ServerRuntime.start(config,
                    new FrontierFileStore(world, FrontierWorldRuntimeDefinition.payloadCodecs()), 1000);
            assertThrows(IOException.class, () -> FrontierV3OfflineFirstAdmissionRecoveryPublication.publish(
                    recovered, proof(world, state, "changed-entity-input"), ACTOR, ledgerFile, true));
            assertEquals(head, recovered.checkpointImage().orElseThrow().revision());
            FrontierV3OfflineFirstAdmissionRecoveryPublication.publish(recovered, absence, ACTOR, ledgerFile, true);
            var published = Files.readAllBytes(ledgerFile);
            var rearmed = FrontierV3AmbientCarrierLedger.readFile(ledgerFile, null);
            var permit = rearmed.firstAdmission(ACTOR).orElseThrow();
            assertEquals(FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT, permit.phase());
            assertEquals(permit.identity().entityId(), absence.actorUuid());
            assertTrue(FrontierV3AmbientActorExecutor.hasUnusedFirstAdmission(rearmed,
                    permit.attempt().orElseThrow().declaration()));
            assertFalse(rearmed.permitsRecordedOwner(permit.attempt().orElseThrow()));
            assertEquals(head, recovered.checkpointImage().orElseThrow().revision());
            FrontierV3OfflineFirstAdmissionRecoveryPublication.publish(recovered, absence, ACTOR, ledgerFile, true);
            assertArrayEquals(published, Files.readAllBytes(ledgerFile));
            try (var files = Files.list(world)) {
                assertEquals(1L, files.filter(path -> path.getFileName().toString()
                        .startsWith("offline-first-admission-")).count());
            }
        }
    }

    @Test void onlyExactPreparedUncommittedSoleOwnerMayBeRearmed() {
        var state = FrontierV3OfflineActorRecoveryPlanTest.prepared();
        var ledger = pending(state);
        var absence = proof(directory, state, "exact");
        assertNotNull(FrontierV3OfflineFirstAdmissionRecoveryPlan.create(state, ACTOR, absence, ledger));
        var hot = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(
                io.farfrontier.palemirror.frontier.v3.model.ModeledActorBodyFacts.present(state, ACTOR), ACTOR, AmbientLeaseStatus.HOT);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3OfflineFirstAdmissionRecoveryPlan.create(hot, ACTOR, absence, ledger));
        var foreign = new FrontierV3OfflineActorAbsence.Proof(directory, java.util.UUID.randomUUID(), 1, absence.files());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3OfflineFirstAdmissionRecoveryPlan.create(state, ACTOR, foreign, ledger));
        assertThrows(IllegalArgumentException.class, () -> ledger.rearmFirstAdmissionAfterAbsence(
                ledger.firstAdmission(ACTOR).orElseThrow(), "not-a-proof"));
    }
    @Test void awakeBioformUsesTheSameExactAmbientRecoveryBoundary() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:offline-bioform"), 91L));
        var scout = initial.bootstrap().hive().bioforms().stream()
                .filter(bioform -> initial.hiveColony().bioformLifecycles().get(bioform.id()).phase()
                        == BioformLifecyclePhase.ACTIVE).findFirst().orElseThrow();
        var actor = scout.id();
        var state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.prepare(initial,
                io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess.nextLease(initial, actor,
                        io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO));
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actor, FrontierV3AmbientActorExecutor.entityId(state, actor), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1L);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(actor, declaration.kind(), declaration.entityId()))));
        assertTrue(ledger.beginFirstAdmission(FrontierV3ActorOwnerBinding.body(declaration)));
        var proof = new FrontierV3OfflineActorAbsence.Proof(directory, declaration.entityId(), 1,
                List.of(new FrontierV3OfflineActorAbsence.FileProof("entities/r.0.0.mca", "bioform-proof")));
        assertEquals(declaration, FrontierV3OfflineFirstAdmissionRecoveryPlan.create(state, actor, proof, ledger)
                .exactOwner().declaration());
    }
}
