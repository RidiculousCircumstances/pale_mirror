package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.PopulationBirthProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Real canonical scheduled birth + file-store recovery; not a Minecraft body acceptance test. */
class FrontierV3ActorBirthRuntimeTest {
    @TempDir Path directory;
    private static final WorldId WORLD = new WorldId("frontier:birth-runtime");

    @Test void birthAndFirstAdmissionRemainExactAcrossWalReplayAndCheckpointCompaction() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var base = FrontierWorldRuntimeDefinition.configuration(WORLD, 91L);
        var initial = base.initialState();
        var settlement = initial.bootstrap().settlements().getFirst().id();
        var food = new SubjectId("item:birth-runtime-bread");
        var stocked = initial.withInventory(initial.inventory()
                .store(new ExactItemStack(food, settlement, PopulationBirthProcess.BREAD, 64,
                        new InventoryCustody.ContainerSlot(FrontierWorldState.depotId(settlement), 1)))
                .store(new ExactItemStack(new SubjectId("item:birth-runtime-reserve"), settlement,
                        PopulationBirthProcess.BREAD, 64,
                        new InventoryCustody.ContainerSlot(FrontierWorldState.depotId(settlement), 2))));
        var configuration = new FrontierEngineConfiguration<>(WORLD, stocked, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(PopulationBirthProcess.review(settlement, 1, 100L)), base.transactionCommitter(),
                FrontierWorldStateTransitionValidator.INSTANCE);
        var store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        Path ledgerFile = directory.resolve("birth-ledger.dat");
        var writer = new FrontierV3ActorBirthCommitter(WORLD, ledger, () -> ledger.save(ledgerFile.toFile(), null),
                new FrontierStoreTransactionCommitter(store));
        var runtime = FrontierV3ServerRuntime.startRecovered(configuration, store, store.recover(WORLD), 10000,
                DiagnosticRuntimeIdentity.unavailable(), writer);
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind());
        assertTrue(runtime.advance(100, new WorkBudget(16, 64)).isPresent());
        var job = runtime.decodedState().orElseThrow().humanPopulation().birthJobs().values().iterator().next();
        assertTrue(ledger.firstAdmission(job.resident().id()).isEmpty(), "conception is not birth");
        assertTrue(runtime.advance(200, new WorkBudget(16, 64)).isPresent());
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind(), () -> runtime.status().toString());
        var born = runtime.decodedState().orElseThrow();
        assertEquals(job.resident(), born.humanPopulation().resident(job.resident().id()));
        assertEquals(63, born.inventory().items().get(food).count());
        var disk = readLedger(ledgerFile);
        var permit = disk.firstAdmission(job.resident().id()).orElseThrow();
        assertEquals(FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED, permit.phase());
        assertEquals(ActorKind.RESIDENT, permit.identity().kind());
        assertEquals(SceneLease.deterministicEntityId(WORLD, job.resident().id()), permit.identity().entityId());

        // Open the real durable WAL without checkpointing the first runtime. No physical body exists.
        var recoveredStore = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var image = recoveredStore.recover(WORLD);
        assertFalse(image.walTail().isEmpty());
        var recovered = FrontierV3ServerRuntime.startRecovered(configuration, recoveredStore, image, 10000,
                DiagnosticRuntimeIdentity.unavailable(), new FrontierV3ActorBirthCommitter(WORLD, disk,
                        () -> fail("WAL replay must not reissue birth permission"), new FrontierStoreTransactionCommitter(recoveredStore)));
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, recovered.status().kind());
        assertEquals(born, recovered.decodedState().orElseThrow());
        assertTrue(recovered.checkpoint().isPresent());
        assertTrue(recoveredStore.recover(WORLD).walTail().isEmpty());

        // Exercise only the durable first-admission boundary, not a fake physical evidence claim.
        var target = FrontierV3ActorOwnerBinding.ambient(new FrontierV3ActorCarrierComposition.Declaration(
                permit.identity().actorId(), permit.identity().kind(), FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                permit.identity().entityId(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1, 1));
        assertTrue(FrontierV3ActorFirstAdmissionBoundary.admit(disk, target, () -> disk.save(ledgerFile.toFile(), null), () -> true));
        var afterAdmission = readLedger(ledgerFile);
        var snapshotImage = recoveredStore.recover(WORLD);
        var snapRuntime = FrontierV3ServerRuntime.startRecovered(configuration, recoveredStore, snapshotImage, 10000,
                DiagnosticRuntimeIdentity.unavailable(), new FrontierV3ActorBirthCommitter(WORLD, afterAdmission,
                        () -> fail("snapshot recovery must not reissue permission"), new FrontierStoreTransactionCommitter(recoveredStore)));
        assertEquals(born, snapRuntime.decodedState().orElseThrow());
        assertEquals(FrontierV3ActorFirstAdmission.Phase.PENDING, afterAdmission.firstAdmission(job.resident().id()).orElseThrow().phase());
        assertFalse(FrontierV3ActorFirstAdmissionBoundary.admit(afterAdmission, target,
                () -> fail("no second save"), () -> { fail("no second creation"); return true; }));
        snapRuntime.shutdown();
    }
    @Test void hiveBirthPublishesExactBioformAndRecoversAfterItsGrowthJobWasRetired() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var base = FrontierWorldRuntimeDefinition.configuration(WORLD, 93L);
        var initial = base.initialState(); var hive = initial.bootstrap().hive().id();
        var objective = new StrategicObjective(new SubjectId("objective:birth-runtime"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, java.util.Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        var task = new StrategicTask(new SubjectId("task:birth-runtime"), objective.id(), hive,
                StrategicTaskKind.GROW_HIVE_ORGANISM, java.util.Optional.empty(),
                List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS), List.of(), StrategicTaskStatus.PENDING);
        var lot = new ResourceLot(new SubjectId("lot:birth-runtime"), hive, "minecraft:rotten_flesh", 64, "bootstrap", List.of());
        var account = new CustodyAccount(new SubjectId("custody:birth-runtime"),
                new ResourceCustody.Container(new SubjectId("container:hive-west-store")), java.util.Map.of(lot.id(), 64), java.util.Map.of());
        var stocked = initial.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task))
                .withInventory(initial.inventory().withFungibleResources(FungibleResourceLedger.empty().issue(lot, account)));
        var configuration = new FrontierEngineConfiguration<>(WORLD, stocked, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(io.farfrontier.palemirror.frontier.v3.process.HiveGrowthProcess.start(task, 100L)),
                base.transactionCommitter(), FrontierWorldStateTransitionValidator.INSTANCE);
        var store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); var ledgerFile = directory.resolve("bioform-ledger.dat");
        var runtime = FrontierV3ServerRuntime.startRecovered(configuration, store, store.recover(WORLD), 10000,
                DiagnosticRuntimeIdentity.unavailable(), new FrontierV3ActorBirthCommitter(WORLD, ledger,
                        () -> ledger.save(ledgerFile.toFile(), null), new FrontierStoreTransactionCommitter(store)));
        assertTrue(runtime.advance(100, new WorkBudget(16, 64)).isPresent());
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind(), () -> runtime.status().toString());
        var growing = runtime.decodedState().orElseThrow();
        var job = growing.hiveColony().growthJobs().values().iterator().next();
        assertTrue(ledger.firstAdmission(job.bioform().id()).isEmpty());
        assertFalse(growing.inventory().fungibleResources().lots().containsKey(lot.id()));
        var forged = new HiveGrowthCompleted(job.id(), new ActorBirthIdentity(new SubjectId("bioform:foreign"), ActorBirthIdentity.Kind.BIOFORM));
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.HiveGrowthProcess.reduceCompleted(growing, hive, forged));
        assertTrue(runtime.advance(200, new WorkBudget(16, 64)).isPresent());
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind(), () -> runtime.status().toString());
        var born = runtime.decodedState().orElseThrow();
        assertEquals(job.bioform(), born.hiveColony().spawnedBioforms().get(job.bioform().id()));
        assertFalse(born.hiveColony().growthJobs().containsKey(job.id()));
        assertEquals(StrategicTaskStatus.COMPLETED, born.strategicPlans().tasks().get(task.id()).status());
        var disk = readLedger(ledgerFile); var permission = disk.firstAdmission(job.bioform().id()).orElseThrow();
        assertEquals(ActorKind.BIOFORM, permission.identity().kind());
        assertEquals(SceneLease.deterministicEntityId(WORLD, job.bioform().id()), permission.identity().entityId());
        assertEquals(FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED, permission.phase());
        var reopened = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var recovered = FrontierV3ServerRuntime.startRecovered(configuration, reopened, reopened.recover(WORLD), 10000,
                DiagnosticRuntimeIdentity.unavailable(), new FrontierV3ActorBirthCommitter(WORLD, disk,
                        () -> fail("recovery cannot reconstruct permission from an already retired job"), new FrontierStoreTransactionCommitter(reopened)));
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, recovered.status().kind(), () -> recovered.status().toString());
        assertEquals(born, recovered.decodedState().orElseThrow());
        assertEquals(permission, readLedger(ledgerFile).firstAdmission(job.bioform().id()).orElseThrow());
        recovered.shutdown();
    }

    private static FrontierV3AmbientCarrierLedger readLedger(Path file) throws Exception {
        return FrontierV3AmbientCarrierLedger.load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data"), null);
    }
    @Test void failedBirthPublicationRecoversAndReissuesOnlyWhenScheduledBirthCommits() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var base = FrontierWorldRuntimeDefinition.configuration(WORLD, 91L);
        var initial = base.initialState(); var settlement = initial.bootstrap().settlements().getFirst().id();
        var stocked = initial.withInventory(initial.inventory()
                .store(new ExactItemStack(new SubjectId("item:interrupted-birth"), settlement,
                        PopulationBirthProcess.BREAD, 64,
                        new InventoryCustody.ContainerSlot(FrontierWorldState.depotId(settlement), 1)))
                .store(new ExactItemStack(new SubjectId("item:interrupted-birth-reserve"), settlement,
                        PopulationBirthProcess.BREAD, 64,
                        new InventoryCustody.ContainerSlot(FrontierWorldState.depotId(settlement), 2))));
        var configuration = new FrontierEngineConfiguration<>(WORLD, stocked, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), List.of(PopulationBirthProcess.review(settlement, 1, 100L)), base.transactionCommitter(),
                FrontierWorldStateTransitionValidator.INSTANCE);
        var store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var canonical = new FrontierStoreTransactionCommitter(store);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); var file = directory.resolve("interrupted-birth.dat");
        var runtime = FrontierV3ServerRuntime.startRecovered(configuration, store, store.recover(WORLD), 10000,
                DiagnosticRuntimeIdentity.unavailable(), new FrontierV3ActorBirthCommitter(WORLD, ledger,
                        () -> ledger.save(file.toFile(), null), (transaction, durability) -> {
                            if (transaction.events().stream().anyMatch(event -> event.payload().actorBirth().isPresent()))
                                throw new IllegalStateException("injected before canonical birth WAL append");
                            canonical.commit(transaction, durability);
                        }));
        runtime.advance(100, new WorkBudget(16, 64));
        var job = runtime.decodedState().orElseThrow().humanPopulation().birthJobs().values().iterator().next();
        runtime.advance(200, new WorkBudget(16, 64));
        assertEquals(FrontierV3RuntimeStatus.Kind.QUARANTINED, runtime.status().kind());
        var saved = readLedger(file);
        assertEquals(FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED, saved.firstAdmission(job.resident().id()).orElseThrow().phase());
        var reopened = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var recovered = FrontierV3ServerRuntime.startRecovered(configuration, reopened, reopened.recover(WORLD), 10000,
                DiagnosticRuntimeIdentity.unavailable(), new FrontierV3ActorBirthCommitter(WORLD, saved,
                        () -> saved.save(file.toFile(), null), new FrontierStoreTransactionCommitter(reopened)));
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, recovered.status().kind(), () -> recovered.status().toString());
        assertFalse(recovered.decodedState().orElseThrow().actorLocations().containsKey(job.resident().id()));
        assertEquals(1, FrontierV3ActorBirthRecovery.retireUnpublished(recovered.decodedState().orElseThrow(), saved,
                () -> saved.save(file.toFile(), null)));
        assertTrue(readLedger(file).firstAdmission(job.resident().id()).isEmpty());
        recovered.advance(200, new WorkBudget(16, 64));
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, recovered.status().kind(), () -> recovered.status().toString());
        assertEquals(job.resident(), recovered.decodedState().orElseThrow().humanPopulation().resident(job.resident().id()));
        assertEquals(FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED, readLedger(file).firstAdmission(job.resident().id()).orElseThrow().phase());
        assertEquals(1, saved.firstAdmissions().size());
        recovered.shutdown();
    }
}
