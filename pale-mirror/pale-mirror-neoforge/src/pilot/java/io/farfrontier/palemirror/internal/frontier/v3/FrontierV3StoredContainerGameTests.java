package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3StoredContainerGameTests {
    private FrontierV3StoredContainerGameTests() { }

    @GameTest(batch = "pm-frontier-v3-reference-projection", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void savedNativeInventoryClosesRecoveredProjectionOnlyOnExactEvidence(GameTestHelper helper) {
        var world = new WorldId("frontier:saved-fixed-projection");
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        var depot = FrontierWorldState.depotId(state.bootstrap().settlements().getFirst().id());
        var local = helper.absolutePos(new BlockPos(2, 2, 2));
        var original = state.inventory().surfaces().get(depot).position();
        state = FrontierWorldState.initial(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(state.bootstrap(),
                local.getX() - original.x(), local.getY() - original.y(), local.getZ() - original.z()));
        var chest = FrontierV3ReferenceContainerCustodyGameTests.chest(helper, local, depot);
        chest.clearContent();
        FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, state, depot);
        var runtime = FrontierV3ReferenceContainerCustodyGameTests.runtime(world, state);
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.prepareInitialProjection(runtime, state, depot), "durable pending projection");
        var pending = runtime.decodedState().orElseThrow();
        var lease = pending.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(depot));
        var saved = chest.saveWithFullMetadata(helper.getLevel().registryAccess());
        var chunk = new CompoundTag(); var entities = new ListTag(); entities.add(saved); chunk.put("block_entities", entities);
        var observed = FrontierV3StoredContainerObservation.read(pending, depot, chunk, helper.getLevel().registryAccess()).orElseThrow();
        helper.assertTrue(observed.equals(FrontierV3ReferenceContainerCustodyExecutor.observed(pending, depot, chest)),
                "detached native decoding must preserve the same slots and ownership evidence as a loaded inventory");
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        var changed = new CompoundTag(); var altered = new ListTag(); altered.add(chest.saveWithFullMetadata(helper.getLevel().registryAccess())); changed.put("block_entities", altered);
        var foreign = FrontierV3StoredContainerObservation.read(pending, depot, changed, helper.getLevel().registryAccess()).orElseThrow();
        helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor.confirmSavedFixedProjection(helper.getLevel(), runtime, pending, lease, foreign)
                && runtime.decodedState().orElseThrow().equals(pending), "different saved contents cannot release or overwrite a pending projection");
        entities.add(saved.copy());
        boolean duplicateRejected = false;
        try { FrontierV3StoredContainerObservation.read(pending, depot, chunk, helper.getLevel().registryAccess()); }
        catch (IllegalArgumentException expected) { duplicateRejected = true; }
        helper.assertTrue(duplicateRejected, "duplicate physical identity cannot certify custody");
        entities.remove(1);
        helper.assertTrue(FrontierV3StoredContainerObservation.read(pending, depot, null, helper.getLevel().registryAccess()).isEmpty(), "absence is not a saved image");
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.confirmSavedFixedProjection(helper.getLevel(), runtime, pending, lease, observed)
                && ReferenceContainerCustody.hasOperationalCustody(runtime.decodedState().orElseThrow(), depot),
                "matching persisted image completes the existing projection without rewriting native slots");
        helper.assertTrue(chest.getItem(0).is(Items.DIAMOND), "saved observation never mutates a loaded container");
        runtime.shutdown(); helper.succeed();
    }
}
