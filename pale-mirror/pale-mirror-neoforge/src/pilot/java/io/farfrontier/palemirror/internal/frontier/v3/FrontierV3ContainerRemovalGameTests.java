package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Native palette/onRemove boundary with an explicit canonical initial fixture, not a restart claim. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ContainerRemovalGameTests {
    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void emptyProviderHasDurableIntentBeforePaletteWriteAndRetiresOnlyAfterNativeReceipt(GameTestHelper helper) {
        var level = helper.getLevel(); var point = helper.absolutePos(new BlockPos(1, 1, 1));
        var seed = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:container-removal-operation"), 94L).initialState().bootstrap();
        var base = FrontierWorldRuntimeDefinition.configuration(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(
                seed, point.getX(), point.getY() - 65, point.getZ()));
        var owner = base.initialState().bootstrap().settlements().getFirst().id();
        var id = new SubjectId("container:native-removal"); var support = SurfaceAnchor.at(point.getX() - 1, point.getY() - 1, point.getZ());
        var endpoint = new ShipmentEndpoint.Storage(owner, id, support, Optional.empty(), support);
        var record = new ContainerRecord(id, owner, 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(), Optional.of(
                new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE)));
        var initial = new ResourceStorageDeclared(record, new BlockPosition(point.getX(), point.getY(), point.getZ())).apply(base.initialState(), owner);
        for (var status : List.of(ContainerSurfaceStatus.PREPARED, ContainerSurfaceStatus.ACTIVE)) {
            var recovery = FencedRecoveryContainerSupport.transition(initial.fencedRecovery(), record, initial.inventory().surfaces().get(id), status);
            initial = initial.withChanges(FrontierWorldStateUpdate.begin().inventory(initial.inventory().withSurfaceStatus(id, status)).fencedRecovery(recovery));
        }
        initial = ReferenceContainerCustodyFixtures.observedAndHeld(initial, id);
        var config = new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var runtime = FrontierV3ServerRuntime.start(config, new FrontierV3AmbientActorGameTests.EphemeralStore(), 10_000);
        level.setBlockAndUpdate(point.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(point, Blocks.AIR.defaultBlockState());
        var chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, point, id);
        var chunk = level.getChunkSource().getChunkNow(point.getX() >> 4, point.getZ() >> 4);
        var operations = FrontierV3OperationLedger.get(level); var journal = FrontierV3ResourceHandoffLedger.get(level);
        try {
            helper.assertTrue(chest != null && chest.isEmpty(), "empty native source needs no item-drop callback");
            var changed = FrontierV3ContainerBlockOperation.replace(level, runtime, chunk, point, Blocks.AIR.defaultBlockState(), () -> {
                var witness = journal.removals().stream().filter(w -> w.container().equals(id)).findFirst().orElseThrow();
                helper.assertTrue(!journal.isDirty() && !operations.isDirty() && journal.durableReady() && operations.durableReady()
                        && chunk.getBlockEntities().get(point) == chest && chunk.getBlockState(point).is(Blocks.CHEST),
                        "exact outer intent is forced before vanilla changes the palette or calls onRemove");
                FrontierV3ContainerRemovalExecutor.tick(level, runtime);
                helper.assertTrue(runtime.decodedState().orElseThrow().inventory().surfaces().get(id).status() == ContainerSurfaceStatus.ACTIVE
                        && operations.require(witness.operation().ticket()).applied().isEmpty(), "prepared removal is not native completion");
                return chunk.setBlockState(point, Blocks.AIR.defaultBlockState(), false);
            });
            helper.assertTrue(changed != null && changed.is(Blocks.CHEST) && level.getBlockEntity(point) == null, "actual native removal ran");
            var witness = journal.removals().stream().filter(w -> w.container().equals(id)).findFirst().orElseThrow();
            helper.assertTrue(operations.nativeOutcomeAvailable(witness.operation().ticket()), "common native receipt confirms the actual terrain write");
            // Component fault injection: the independently persisted family index is
            // absent. The retained common owner must reconstruct it, never the block.
            journal.retireRemoval(witness); journal.persist(level);
            FrontierV3ContainerRemovalExecutor.tick(level, runtime);
            helper.assertTrue(runtime.decodedState().orElseThrow().inventory().surfaces().get(id).status() == ContainerSurfaceStatus.RETIRED
                    && !journal.removes(id), "canonical retirement and owner outbox close without a phantom item drop");
            helper.assertTrue(operations.require(witness.operation().ticket()).ownerClosed()
                    && operations.require(witness.operation().ticket()).saved().isEmpty(), "canonical closure is not an invented native disk save");
        } finally { runtime.shutdown(); }
        helper.succeed();
    }
}
