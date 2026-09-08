package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Loaded-world negative evidence and bounded multi-scope scheduling for the F0.2B adapter. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ReferenceContainerCustodyGameTests {
    private FrontierV3ReferenceContainerCustodyGameTests() { }

    @GameTest(batch = "pm-frontier-v3-reference-conflict-restart", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void foreignAndMissingProvenanceRemainActualEvidence(GameTestHelper helper) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-observation"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position);
        chest.getPersistentData().putString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY, "container:foreign-depot");
        chest.getPersistentData().putString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY, "foreign:player");
        FrontierV3ReferenceContainerCustodyExecutor.Observed foreign = FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, chest);
        helper.assertTrue(foreign.provenance().contains("foreign:container-owner=container:foreign-depot") && foreign.provenance().contains("foreign:player"),
                "a wrong owner and its actual provenance must remain typed evidence, never canonical adoption");

        chest.getPersistentData().putString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY, depot.value());
        chest.getPersistentData().remove(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY);
        FrontierV3ReferenceContainerCustodyExecutor.Observed missingProvenance = FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, chest);
        helper.assertTrue(missingProvenance.provenance().equals("missing:replica-provenance:" + depot.value()),
                "a legacy owner marker alone cannot manufacture canonical replica provenance");
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, null).provenance().equals("missing:" + depot.value()),
                "an absent chest remains missing evidence");
        helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor.initialDeclarationReady(ContainerSurfaceStatus.PREPARED, chest)
                        && FrontierV3ReferenceContainerCustodyExecutor.initialDeclarationReady(ContainerSurfaceStatus.ACTIVE, chest),
                "a tagged chest cannot become a replica boundary until its surface transition is durably ACTIVE");
        helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor.readyForReplicaObservation(ContainerSurfaceStatus.PREPARED)
                        && FrontierV3ReferenceContainerCustodyExecutor.readyForReplicaObservation(ContainerSurfaceStatus.ACTIVE),
                "a retained replica must not classify a generic socket's durable PREPARED recovery window as missing evidence");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-depot-never-visited", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void roundRobinAdvancesDepotAndHiveWhileAConflictIsLocal(GameTestHelper helper) {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-fairness"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        SubjectId hiveStore = baseline.bootstrap().hive().organs().stream().flatMap(organ -> organ.containerId().stream()).findFirst().orElseThrow();
        List<ContainerSurface> loaded = List.of(baseline.inventory().surfaces().get(depot), baseline.inventory().surfaces().get(hiveStore));
        List<ContainerSurface> eligible = FrontierV3ReferenceContainerCustodyExecutor.eligibleReferenceSurfaces(baseline, loaded);
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.selectRoundRobin(eligible, 0L).containerId()
                        != FrontierV3ReferenceContainerCustodyExecutor.selectRoundRobin(eligible, 1L).containerId(),
                "a stable first held scope cannot starve the next naturally loaded depot/store");

        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(baseline, depot), 1L,
                ReferenceContainerCustody.canonicalFingerprint(baseline, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState conflicted = PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(depot, 1L, 1L, "sha256:foreign", "foreign:player", 1L);
        FrontierWorldState localConflict = baseline.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(conflicted));
        List<ContainerSurface> remaining = FrontierV3ReferenceContainerCustodyExecutor.eligibleReferenceSurfaces(localConflict, loaded);
        helper.assertTrue(remaining.size() == 1 && remaining.getFirst().containerId().equals(hiveStore),
                "one conflicted depot is excluded locally while the hive store continues");
        helper.succeed();
    }

    /** A formerly visited unloaded depot remains cold until fresh matching evidence exists. */
    @GameTest(batch = "pm-frontier-v3-reference-depot-visited-unloaded", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void releasedDepotDoesNotReemitBeforeRetainedEvidenceMatches(GameTestHelper helper) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-released"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(state, depot), 7L,
                ReferenceContainerCustody.canonicalFingerprint(state, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState retained = PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(depot, 7L, 1L, expected.fingerprint(), expected.provenance(), 7L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(depot), depot, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 7L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null))
                .checkpoint(ReferenceContainerCustody.scopeId(depot), 1L, 7L, 2L)
                .release(ReferenceContainerCustody.scopeId(depot), 1L, 7L, 2L);
        helper.assertTrue(!ReferenceContainerCustody.hasLiveCustody(state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(retained)), depot),
                "a released unloaded visit has no write authority before a fresh matching observation");
        helper.succeed();
    }

    /** A naturally loaded world with no player still schedules the independent hive store. */
    @GameTest(batch = "pm-frontier-v3-reference-hive-zero-player", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void zeroPlayerHiveStoreIsAReferenceScopeWithoutDepotAuthority(GameTestHelper helper) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-hive"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        SubjectId hiveStore = state.bootstrap().hive().organs().stream().flatMap(organ -> organ.containerId().stream()).findFirst().orElseThrow();
        helper.assertTrue(ReferenceContainerCustody.isReferenceContainer(state, hiveStore) && !ReferenceContainerCustody.hasLiveCustody(state, depot),
                "the zero-player hive store is independent from an unheld settlement depot");
        helper.succeed();
    }
}
