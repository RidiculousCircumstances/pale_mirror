package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceOperation;
import io.farfrontier.palemirror.frontier.reference.ReferenceOperationKind;
import io.farfrontier.palemirror.frontier.reference.ReferenceResource;
import io.farfrontier.palemirror.frontier.reference.ReferenceTargetRef;
import io.farfrontier.palemirror.frontier.reference.ReferenceWorld;
import io.farfrontier.palemirror.frontier.reference.ReferenceWorldConfig;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-block proof for the exact operation and field-post cargo hand-off. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxCargoGameTests {
    private SourceGrayboxCargoGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void relocatingCargoMovesOnlyAfterOldCustodyIsReleasedAndLeavesMixedPlayerItems(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        BlockPos oldPosition = anchor.offset(2, 1, 2);
        BlockPos targetPosition = anchor.offset(6, 1, 2);
        helper.getLevel().setBlock(oldPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(targetPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        String id = "cargo-container:operation:991:cargo:food";
        BarrelBlockEntity old = SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), oldPosition, id, ReferenceResource.FOOD);
        helper.assertTrue(old != null, "the old operation pallet must begin with one PM-owned physical custody barrel");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.insert(old, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD), 64), 64,
                "the exact source stack must be present before a move");
        old.setItem(4, new ItemStack(Items.COAL, 3));
        old.setChanged();

        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxCargoLedger.Binding moving = new SourceGrayboxCargoLedger.Binding(id, "operation:991:cargo:food", "operation", 991,
                ReferenceResource.FOOD, oldPosition.getX(), oldPosition.getY(), oldPosition.getZ(), targetPosition.getX(),
                targetPosition.getY(), targetPosition.getZ(), 64, SourceGrayboxCargoLedger.State.RELOCATING);
        helper.assertTrue(data.cargoLedger().put(moving), "the move must retain both old physical custody and the source target");

        SourceGrayboxCargoLedger.Binding arrived = SourceGrayboxCargoRuntime.completeRelocation(helper.getLevel(), data,
                new SourceGrayboxMaterializer(), moving, "0".repeat(64));
        helper.assertTrue(arrived != null && arrived.state() == SourceGrayboxCargoLedger.State.ACTIVE,
                "the ledger may arrive only after the old PM barrel was inspected and released");
        helper.assertValueEqual(arrived.x(), targetPosition.getX(), "arrival must make the source target the only future custody point");
        helper.assertValueEqual(arrived.z(), targetPosition.getZ(), "arrival must retain the exact target z coordinate");
        helper.assertValueEqual(helper.getLevel().getBlockState(oldPosition).getBlock(), Blocks.BARREL,
                "a mixed old barrel must stay in the world for the player instead of being deleted during relocation");
        BarrelBlockEntity released = (BarrelBlockEntity) helper.getLevel().getBlockEntity(oldPosition);
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.count(released, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD)), 0,
                "only the tracked PM resource leaves the old mixed container");
        helper.assertValueEqual(released.getItem(4).getItem(), Items.COAL,
                "a player item must remain in its original mixed container after the source cargo departs");
        helper.assertTrue(SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), oldPosition, id, ReferenceResource.FOOD) == null,
                "the released old barrel must become foreign rather than being silently adopted again");
        helper.assertTrue(helper.getLevel().getBlockState(targetPosition).isAir(),
                "relocation itself must not create a second barrel before normal target materialization owns that step");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void relocatingCargoNeverLoadsOrDuplicatesAnUnavailableOldCustodyChunk(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        BlockPos oldPosition = anchor.offset(2, 1, 2);
        // The GameTest platform is only a few chunks wide. This deliberately
        // lies outside it, so a correct relocation must defer rather than
        // ticketing the target or inventing a second barrel there.
        BlockPos targetPosition = oldPosition.offset(16 * 64, 0, 0);
        helper.getLevel().setBlock(oldPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        String id = "cargo-container:operation:992:cargo:food";
        BarrelBlockEntity old = SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), oldPosition, id, ReferenceResource.FOOD);
        helper.assertTrue(old != null, "the available former location must retain its exact PM custody barrel");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.insert(old, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD), 64), 64,
                "the old physical cargo must be populated before an unavailable move");
        helper.assertTrue(!helper.getLevel().hasChunkAt(targetPosition), "the test target must begin naturally unloaded");

        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxCargoLedger.Binding moving = new SourceGrayboxCargoLedger.Binding(id, "operation:992:cargo:food", "operation", 992,
                ReferenceResource.FOOD, oldPosition.getX(), oldPosition.getY(), oldPosition.getZ(), targetPosition.getX(),
                targetPosition.getY(), targetPosition.getZ(), 64, SourceGrayboxCargoLedger.State.RELOCATING);
        helper.assertTrue(data.cargoLedger().put(moving), "the pending move must be durable before chunk availability changes");

        SourceGrayboxCargoLedger.Binding result = SourceGrayboxCargoRuntime.completeRelocation(helper.getLevel(), data,
                new SourceGrayboxMaterializer(), moving, "0".repeat(64));
        helper.assertTrue(result == null, "a move with an unavailable target must remain pending instead of silently completing");
        helper.assertTrue(!helper.getLevel().hasChunkAt(targetPosition), "the cargo runtime must never ticket or force-load its target chunk");
        helper.assertValueEqual(data.cargoLedger().binding(id), moving,
                "the persisted hand-off must still point to the one old custody barrel while relocation is pending");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.count(old, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD)), 64,
                "the old visible cargo must remain until the target and former custody are both naturally available");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void sourceCargoUsesOneTaggedRealBarrelAndNeverAdoptsAForeignContainer(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        BlockPos sourcePosition = anchor.offset(2, 1, 2);
        helper.getLevel().setBlock(sourcePosition.below(), Blocks.STONE.defaultBlockState(), 3);
        String id = "cargo-container:operation:991:cargo:food";
        BarrelBlockEntity barrel = SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), sourcePosition, id, ReferenceResource.FOOD);
        SourceGrayboxCargoLedger.Binding binding = new SourceGrayboxCargoLedger.Binding(id, "operation:991:cargo:food", "operation", 991,
                ReferenceResource.FOOD, sourcePosition.getX(), sourcePosition.getY(), sourcePosition.getZ(), 0,
                SourceGrayboxCargoLedger.State.ACTIVE);

        helper.assertTrue(barrel != null && SourceGrayboxWarehouseRuntime.matches(barrel, binding.id(), binding.resource()),
                "an operation pallet must receive one exact PM-tagged ordinary barrel, not an adapter-only counter");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.insert(barrel, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD), 127), 127,
                "field cargo must use ordinary item stacks with the shared one-unit-per-stack scale");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.count(barrel, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD)), 127,
                "the exact real item count is the only value eligible to enter a cargo observation");

        BlockPos foreignPosition = anchor.offset(6, 1, 2);
        helper.getLevel().setBlock(foreignPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(foreignPosition, Blocks.BARREL.defaultBlockState(), 3);
        helper.assertTrue(SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), foreignPosition,
                        "cargo-container:operation:991:cargo:medicine", ReferenceResource.MEDICINE) == null,
                "a player barrel at a source cargo slot must remain foreign instead of silently entering operation custody");
        helper.assertValueEqual(helper.getLevel().getBlockState(foreignPosition).getBlock(), Blocks.BARREL,
                "rejecting foreign cargo ownership must not remove or replace the player's barrel");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void retiredCargoNeverDeletesAPlayerItemFromItsFormerContainer(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        BlockPos position = anchor.offset(2, 1, 2);
        helper.getLevel().setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        String id = "cargo-container:operation:991:cargo:food";
        BarrelBlockEntity barrel = SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), position, id, ReferenceResource.FOOD);
        helper.assertTrue(barrel != null, "the PM-owned cargo barrel must exist before its source owner retires");
        SourceGrayboxWarehouseRuntime.insert(barrel, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD), 64);
        barrel.setItem(1, new ItemStack(Items.COAL, 5));

        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxCargoLedger.Binding binding = new SourceGrayboxCargoLedger.Binding(id, "operation:991:cargo:food", "operation", 991,
                ReferenceResource.FOOD, position.getX(), position.getY(), position.getZ(), 64, SourceGrayboxCargoLedger.State.ACTIVE);
        helper.assertTrue(data.cargoLedger().put(binding), "the former physical cargo slot must have exact durable ownership");
        new SourceGrayboxCargoRuntime().materialize(helper.getLevel(), data, new SourceGrayboxMaterializer());

        helper.assertValueEqual(helper.getLevel().getBlockState(position).getBlock(), Blocks.BARREL,
                "retiring a source cargo must retain the container when it contains a player item");
        BarrelBlockEntity retained = (BarrelBlockEntity) helper.getLevel().getBlockEntity(position);
        helper.assertValueEqual(retained.getItem(1).getItem(), Items.COAL,
                "a source materializer may remove only its own retired resource, never a player item in the same container");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.count(retained, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD)), 0,
                "canonical source consumption must still remove only the retired PM resource stack");
        helper.assertValueEqual(data.cargoLedger().binding(id).state(), SourceGrayboxCargoLedger.State.BLOCKED,
                "the mixed container must become a durable visible conflict instead of being retried over the player's item");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void retiredCargoReconcilesBeforeControlledCleanupInsteadOfBecomingAForeignConflict(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        BlockPos position = anchor.offset(2, 1, 2);
        helper.getLevel().setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        String id = "cargo-container:operation:991:cargo:food";
        BarrelBlockEntity barrel = SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), position, id, ReferenceResource.FOOD);
        helper.assertTrue(barrel != null, "the PM-owned cargo barrel must exist before a source-day operation retires it");

        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxCargoLedger.Binding binding = new SourceGrayboxCargoLedger.Binding(id, "operation:991:cargo:food", "operation", 991,
                ReferenceResource.FOOD, position.getX(), position.getY(), position.getZ(), 0, SourceGrayboxCargoLedger.State.ACTIVE);
        helper.assertTrue(data.cargoLedger().put(binding), "the cargo lifecycle starts from a durable active binding");
        SourceGrayboxCargoRuntime runtime = new SourceGrayboxCargoRuntime();
        helper.assertTrue(!runtime.reconcileInbound(helper.getLevel(), data, new SourceGrayboxMaterializer()),
                "an absent source descriptor is controlled retirement, not a false foreign-container receipt");

        runtime.materialize(helper.getLevel(), data, new SourceGrayboxMaterializer());
        helper.assertValueEqual(helper.getLevel().getBlockState(position).getBlock(), Blocks.AIR,
                "an empty PM container may retire after its canonical operation cargo disappears");
        helper.assertTrue(data.cargoLedger().binding(id) == null,
                "controlled retirement must compact the durable hand-off instead of retaining a stale conflict forever");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void v26OperationBarrelConvertsOnlyThroughItsExactFormerCustodyAndKeepsMixedPlayerItems(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        BlockPos position = anchor.offset(2, 1, 2);
        helper.getLevel().setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        ReferenceGrayboxSnapshot.Cargo cargo = new ReferenceGrayboxSnapshot.Cargo("operation:991:cargo:food", "operation", 991,
                "food", 1.0d, new ReferenceGrayboxLayout.Rectangle(position.getX(), position.getZ(), 1, 1), "#ffffff");
        String legacyId = "cargo-container:" + cargo.id();
        BarrelBlockEntity barrel = SourceGrayboxWarehouseRuntime.ensureContainer(helper.getLevel(), position, legacyId, ReferenceResource.FOOD);
        helper.assertTrue(barrel != null, "the retained v26 operation custody must begin as one exact tagged barrel");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.insert(barrel, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD), 64), 64,
                "the legacy barrel must carry its exact source stack before conversion");
        barrel.setItem(4, new ItemStack(Items.COAL, 3));

        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        SourceGrayboxCargoLedger.Binding legacy = new SourceGrayboxCargoLedger.Binding(legacyId, cargo.id(), "operation", 991,
                ReferenceResource.FOOD, position.getX(), position.getY(), position.getZ(), 64, SourceGrayboxCargoLedger.State.ACTIVE);
        helper.assertTrue(data.cargoLedger().put(legacy), "v26 must retain the exact old cargo-barrel binding until inspection");

        helper.assertTrue(SourceGrayboxOperationCargoCarrierRuntime.ensureBinding(helper.getLevel(), data, cargo),
                "conversion must find the historical cargo-container identity instead of inventing a second custody point");
        SourceGrayboxOperationCargoCarrierLedger.Binding carrier = data.operationCarrierLedger().binding("operation-carrier:" + cargo.id());
        helper.assertTrue(carrier != null && carrier.mode() == SourceGrayboxOperationCargoCarrierLedger.Mode.COLD,
                "a converted operation must start COLD and never spawn a cart just for migration");
        helper.assertValueEqual(carrier.observedItems(), 64,
                "the carrier ledger must retain the exact old owned stack count without recreating source cargo");
        helper.assertTrue(data.cargoLedger().binding(legacyId) == null,
                "after a safe conversion the old barrel ledger must no longer claim a duplicate custody point");
        BarrelBlockEntity released = (BarrelBlockEntity) helper.getLevel().getBlockEntity(position);
        helper.assertValueEqual(released.getItem(4).getItem(), Items.COAL,
                "mixed player cargo must remain in the released old barrel during migration");
        helper.assertValueEqual(SourceGrayboxWarehouseRuntime.count(released, SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD)), 0,
                "migration may remove only the source-owned matching resource from its old barrel");
        helper.assertTrue(!SourceGrayboxWarehouseRuntime.matches(released, legacyId, ReferenceResource.FOOD),
                "a mixed legacy barrel must be released rather than silently re-adopted after conversion");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void coldOperationCarrierNeverDeletesMixedPlayerItems(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y + 1).offset(2, 0, 2);
        SourceGrayboxOperationCargoCarrierLedger.Binding binding = new SourceGrayboxOperationCargoCarrierLedger.Binding(
                "operation-carrier:operation:991:cargo:food", "operation:991:cargo:food", 991, ReferenceResource.FOOD, 64,
                position.getX() * 16 + 8, position.getZ() * 16 + 8, SourceGrayboxOperationCargoCarrierLedger.Mode.HOT);
        MinecartChest carrier = new MinecartChest(helper.getLevel(), position.getX() + 0.5d, position.getY(), position.getZ() + 0.5d);
        carrier.getPersistentData().putString(SourceGrayboxOperationCargoCarrierRuntime.ENTITY_ID, binding.id());
        carrier.getPersistentData().putString(SourceGrayboxOperationCargoCarrierRuntime.ENTITY_RESOURCE, binding.resource().name());
        carrier.setItem(0, new ItemStack(SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD), 64));
        carrier.setItem(1, new ItemStack(Items.COAL, 3));
        helper.assertTrue(helper.getLevel().addFreshEntity(carrier), "the real chest minecart must be materialized before COLD hand-off");

        SourceGrayboxOperationCargoCarrierRuntime.drainForCold(carrier, binding);

        helper.assertTrue(!carrier.isRemoved(), "a mixed carrier must remain for the player instead of deleting a foreign stack");
        helper.assertValueEqual(carrier.getItem(0).isEmpty(), true,
                "only the PM-owned resource leaves the physical cart for the COLD source hand-off");
        helper.assertValueEqual(carrier.getItem(1).getItem(), Items.COAL,
                "the player-owned stack remains in the released ordinary chest minecart");
        helper.assertValueEqual(carrier.getPersistentData().getString(SourceGrayboxOperationCargoCarrierRuntime.ENTITY_ID), "",
                "a retained mixed cart must lose PM ownership before another operation may materialize nearby");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void coldOperationCarrierReentryRestoresCanonicalCargoBeforeItCanBecomeALossReceipt(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y + 1).offset(2, 0, 2);
        ReferenceGrayboxSnapshot.Cargo cargo = new ReferenceGrayboxSnapshot.Cargo("operation:991:cargo:food", "operation", 991,
                "food", 2.0d, new ReferenceGrayboxLayout.Rectangle(position.getX(), position.getZ(), 1, 1), "cargo.food");
        SourceGrayboxOperationCargoCarrierLedger.Binding cold = new SourceGrayboxOperationCargoCarrierLedger.Binding(
                "operation-carrier:" + cargo.id(), cargo.id(), 991, ReferenceResource.FOOD, 64,
                position.getX() * 16 + 8, position.getZ() * 16 + 8, SourceGrayboxOperationCargoCarrierLedger.Mode.COLD);
        MinecartChest carrier = new MinecartChest(helper.getLevel(), position.getX() + 0.5d, position.getY(), position.getZ() + 0.5d);

        SourceGrayboxOperationCargoCarrierLedger.Binding hot = SourceGrayboxOperationCargoCarrierRuntime.hydrateForHot(carrier, cold, cargo);

        helper.assertValueEqual(hot.mode(), SourceGrayboxOperationCargoCarrierLedger.Mode.HOT,
                "COLD re-entry must explicitly acquire a HOT carrier lease");
        helper.assertValueEqual(hot.observedItems(), 128,
                "the new HOT ledger hand-off must use canonical cargo, not the historical COLD stack count");
        helper.assertValueEqual(carrier.getItem(0).getCount(), 64,
                "the fresh physical cart must receive the first canonical ordinary stack before observation");
        helper.assertValueEqual(carrier.getItem(1).getCount(), 64,
                "the fresh physical cart must receive the complete canonical ordinary quantity before observation");
        helper.assertValueEqual(hot.observedItems(), carrier.getItem(0).getCount() + carrier.getItem(1).getCount(),
                "the first HOT observation must see zero delta rather than misclassifying COLD custody as a withdrawal");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void coldOperationCarrierReentryStartsAtItsRetainedPositionAndCatchesTheAdvancedSourceAnchor(GameTestHelper helper) {
        BlockPos retained = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y + 1).offset(2, 0, 2);
        BlockPos sourceTarget = retained.offset(8, 0, 0);
        for (int x = retained.getX() - 1; x <= sourceTarget.getX() + 1; x++) {
            helper.getLevel().setBlock(new BlockPos(x, retained.getY() - 1, retained.getZ()), Blocks.STONE.defaultBlockState(), 3);
        }
        ReferenceGrayboxSnapshot.Cargo cargo = new ReferenceGrayboxSnapshot.Cargo("operation:993:cargo:food", "operation", 993,
                "food", 1.0d, new ReferenceGrayboxLayout.Rectangle(sourceTarget.getX(), sourceTarget.getZ(), 1, 1), "cargo.food");
        SourceGrayboxOperationCargoCarrierLedger.Binding cold = new SourceGrayboxOperationCargoCarrierLedger.Binding(
                "operation-carrier:" + cargo.id(), cargo.id(), 993, ReferenceResource.FOOD, 64,
                retained.getX() * 16 + 8, retained.getZ() * 16 + 8, SourceGrayboxOperationCargoCarrierLedger.Mode.COLD);

        MinecartChest carrier = SourceGrayboxOperationCargoCarrierRuntime.spawnAtRetainedPosition(helper.getLevel(), cold);
        helper.assertTrue(carrier != null, "COLD re-entry must materialize one retained physical carrier when its chunk is available");
        helper.assertValueEqual(carrier.blockPosition(), retained,
                "a source anchor that advanced while COLD must not teleport the returning carrier to its new position");
        SourceGrayboxOperationCargoCarrierLedger.Binding hot = SourceGrayboxOperationCargoCarrierRuntime.hydrateForHot(carrier, cold, cargo);

        SourceGrayboxOperationCargoCarrierRuntime.advanceToward(carrier, sourceTarget);
        helper.assertTrue(carrier.getX() > retained.getX() + 0.5d && carrier.getX() < sourceTarget.getX() + 0.5d,
                "after exact COLD restoration the HOT carrier must physically catch the current source anchor in bounded steps");
        helper.assertTrue(SourceGrayboxOperationCargoCarrierRuntime.owns(carrier, hot),
                "the retained-to-HOT hand-off must preserve the one exact source carrier identity");
        helper.assertValueEqual(carrier.getItem(0).getCount(), 64,
                "re-entering from the retained position must restore canonical cargo before movement is observed");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hotOperationCarrierMovesPhysicallyAndNeverTeleportsThroughAnObstruction(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y + 1).offset(2, 0, 2);
        BlockPos wall = start.offset(3, 0, 0);
        BlockPos target = start.offset(8, 0, 0);
        for (int x = start.getX() - 1; x <= target.getX() + 1; x++) {
            helper.getLevel().setBlock(new BlockPos(x, start.getY() - 1, start.getZ()), Blocks.STONE.defaultBlockState(), 3);
        }
        helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        SourceGrayboxOperationCargoCarrierLedger.Binding binding = new SourceGrayboxOperationCargoCarrierLedger.Binding(
                "operation-carrier:operation:991:cargo:food", "operation:991:cargo:food", 991, ReferenceResource.FOOD, 64,
                start.getX() * 16 + 8, start.getZ() * 16 + 8, SourceGrayboxOperationCargoCarrierLedger.Mode.HOT);
        MinecartChest carrier = new MinecartChest(helper.getLevel(), start.getX() + 0.5d, start.getY(), start.getZ() + 0.5d);
        carrier.setNoGravity(true);
        carrier.setUUID(SourceGrayboxOperationCargoCarrierRuntime.carrierUuid(binding.id()));
        carrier.getPersistentData().putString(SourceGrayboxOperationCargoCarrierRuntime.ENTITY_ID, binding.id());
        carrier.getPersistentData().putString(SourceGrayboxOperationCargoCarrierRuntime.ENTITY_RESOURCE, binding.resource().name());
        carrier.setItem(0, new ItemStack(SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD), 64));
        helper.assertTrue(helper.getLevel().addFreshEntity(carrier), "the HOT operation carrier must first be a real minecart body");

        double originalX = carrier.getX();
        SourceGrayboxOperationCargoCarrierRuntime.advanceToward(carrier, target);
        helper.assertTrue(carrier.getX() > originalX && carrier.getX() < target.getX() + 0.5d,
                "one carrier step must make partial physical progress instead of assigning the source destination");
        for (int step = 0; step < 64; step++) SourceGrayboxOperationCargoCarrierRuntime.advanceToward(carrier, target);

        helper.assertTrue(carrier.getX() < wall.getX() - 0.45d,
                "a source target beyond a real wall must leave the carrier blocked before the wall, never teleport it through");
        helper.assertTrue(SourceGrayboxOperationCargoCarrierRuntime.owns(carrier, binding),
                "a physical collision must not silently release the exact operation carrier identity");
        helper.assertValueEqual(carrier.getItem(0).getCount(), 64,
                "pure movement must preserve the exact physical source cargo until a typed item observation occurs");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void realCarrierWithdrawalImmediatelyChangesItsCanonicalOperationCargo(GameTestHelper helper) {
        SourceGrayboxSavedData data = suppliedOperationData();
        ReferenceGrayboxSnapshot.Cargo cargo = data.snapshot().cargoes().stream()
                .filter(candidate -> candidate.ownerKind().equals("operation"))
                .filter(candidate -> candidate.resource().equals("food"))
                .findFirst().orElseThrow(() -> new IllegalStateException("canonical supplied operation must project food cargo"));
        helper.assertValueEqual(cargo.quantity(), 1.0d,
                "the source operation fixture must begin with exactly one canonical food unit (one real Minecraft stack)");

        BlockPos position = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y + 1).offset(2, 0, 2);
        SourceGrayboxOperationCargoCarrierLedger.Binding binding = new SourceGrayboxOperationCargoCarrierLedger.Binding(
                "operation-carrier:" + cargo.id(), cargo.id(), cargo.ownerId(), ReferenceResource.FOOD, 64,
                position.getX() * 16 + 8, position.getZ() * 16 + 8, SourceGrayboxOperationCargoCarrierLedger.Mode.HOT);
        helper.assertTrue(data.operationCarrierLedger().put(binding),
                "the persisted executor must own one exact HOT physical carrier before it observes a player withdrawal");
        MinecartChest carrier = new MinecartChest(helper.getLevel(), position.getX() + 0.5d, position.getY(), position.getZ() + 0.5d);
        carrier.setNoGravity(true);
        carrier.setUUID(SourceGrayboxOperationCargoCarrierRuntime.carrierUuid(binding.id()));
        carrier.getPersistentData().putString(SourceGrayboxOperationCargoCarrierRuntime.ENTITY_ID, binding.id());
        carrier.getPersistentData().putString(SourceGrayboxOperationCargoCarrierRuntime.ENTITY_RESOURCE, binding.resource().name());
        carrier.setItem(0, new ItemStack(SourceGrayboxWarehouseRuntime.item(ReferenceResource.FOOD), 63));
        helper.assertTrue(helper.getLevel().addFreshEntity(carrier),
                "the observed source operation cargo must be a real chest minecart, not an adapter-side count");

        helper.assertTrue(new SourceGrayboxOperationCargoCarrierRuntime().tick(helper.getLevel(), data, new SourceGrayboxMaterializer()),
                "the changed owned stack must create one canonical receipt before any movement or COLD hand-off");
        ReferenceGrayboxSnapshot.Cargo after = data.snapshot().cargoes().stream().filter(candidate -> candidate.id().equals(cargo.id()))
                .findFirst().orElseThrow(() -> new IllegalStateException("one item withdrawal must not retire the live source operation cargo"));
        helper.assertValueEqual(after.quantity(), 63.0d / 64.0d,
                "one physical item removed from the carrier must immediately reduce only its exact source operation cargo by one sixty-fourth");
        helper.assertValueEqual(data.operationCarrierLedger().binding(binding.id()).observedItems(), 63,
                "the durable carrier receipt must acknowledge the exact observed Minecraft count after source acceptance");
        helper.succeed();
    }

    private static SourceGrayboxSavedData suppliedOperationData() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        ReferenceOperation operation = world.operations().launchHuman(world, ReferenceOperationKind.RECON, 1,
                ReferenceTargetRef.cell(10, 10), null, null, Map.of(ReferenceResource.FOOD, 1.0d), null, Map.of());
        if (operation == null) throw new IllegalStateException("source operation fixture must pass normal canonical launch admission");
        ReferenceGrayboxSimulation source = ReferenceGrayboxSimulation.capture(world);

        SourceGrayboxSavedData fresh = SourceGrayboxSavedData.fresh(42L);
        CompoundTag saved = fresh.save(new CompoundTag(), null);
        saved.put("sourceState", SourceGrayboxStateNbt.write(source));
        saved.put("actorExecution", SourceGrayboxActorExecutionNbt.write(ReferenceGrayboxActorExecutionState.bootstrap(source.snapshot())));
        return SourceGrayboxSavedData.load(saved, null);
    }
}
