package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierReleased;
import io.farfrontier.palemirror.frontier.v3.model.ContractStatus;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.OperationStage;
import io.farfrontier.palemirror.frontier.v3.model.SceneEngagementCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** Exact visible cargo custody and conflict coverage for a HOT v3 scene. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3CargoCarrierGameTests {
    private FrontierV3CargoCarrierGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-cargo", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedSceneMaterializesOneExactCargoCarrierWithoutDuplication(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-test");
        FrontierWorldState state = state(runtime); SceneLease lease = lease(state, origin, "lease:frontier-v3-cargo-test");
        prepareFloor(level, cargoPosition(origin, lease));
        var expected = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId()).itemIds().stream().map(state.inventory().items()::get)
                .sorted(java.util.Comparator.comparing(value -> value.id())).toList();
        helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materialize(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a prepared loaded scene must create one exact cargo carrier");
        helper.runAfterDelay(1L, () -> {
            try {
                Entity carrier = level.getEntity(FrontierV3CargoCarrierExecutor.id(lease));
                helper.assertTrue(carrier instanceof MinecartChest, "the graybox carrier must be a visible chest minecart");
                helper.assertTrue(FrontierV3CargoCarrierExecutor.owned(carrier, lease, expected), "the carrier must hold only canonical tagged cargo");
                helper.assertTrue(FrontierV3CargoCarrierPresentation.attached(carrier, lease),
                        "one exact local caption must ride with the real cargo carrier rather than becoming a detached HUD");
                helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materialize(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                        "recovery must reuse the exact carrier rather than duplicate cargo");
                helper.assertValueEqual(level.getEntitiesOfClass(MinecartChest.class, carrier.getBoundingBox().inflate(8.0D),
                                candidate -> FrontierV3CargoCarrierExecutor.owned(candidate, lease, expected)).size(), 1,
                        "one HOT lease must retain exactly one owned cargo carrier, independently of a parallel scene's cart");
                carrier.discard(); runtime.shutdown(); helper.succeed();
            } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedCarrierStandsAboveLoadedRouteDeck(GameTestHelper helper) {
        // Keep the carrier inside this test's own template.  A distant relative coordinate is
        // reclaimed by the parallel GameTest harness before the next-tick identity assertion.
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-deck-test");
        FrontierWorldState state = state(runtime); SceneLease lease = lease(state, origin, "lease:frontier-v3-cargo-deck-test");
        BlockPos deck = cargoPosition(origin, lease); prepareFloor(level, deck); level.setBlock(deck, Blocks.GRAY_CARPET.defaultBlockState(), 3);

        helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materialize(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a loaded thin route surface may occupy strategic hand-off height without blocking the exact cargo carrier");
        helper.runAfterDelay(1L, () -> {
            try {
                Entity carrier = level.getEntity(FrontierV3CargoCarrierExecutor.id(lease));
                helper.assertTrue(carrier != null && carrier.blockPosition().getY() > deck.getY(),
                        "the leased road carrier must stand on the existing route deck rather than inside or replacing it");
                helper.assertValueEqual(level.getBlockState(deck), Blocks.GRAY_CARPET.defaultBlockState(),
                        "carrier placement must preserve the loaded route deck exactly");
                carrier.discard(); runtime.shutdown(); helper.succeed();
            } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedSceneRefusesForeignCargoCarrierWithExpectedUuid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(48, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-conflict-test");
        FrontierWorldState state = state(runtime); SceneLease lease = lease(state, origin, "lease:frontier-v3-cargo-conflict-test");
        prepareFloor(level, cargoPosition(origin, lease));
        MinecartChest foreign = EntityType.CHEST_MINECART.create(level);
        helper.assertTrue(foreign != null, "the foreign carrier fixture must be constructible");
        foreign.setUUID(FrontierV3CargoCarrierExecutor.id(lease)); foreign.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        helper.assertTrue(level.addFreshEntity(foreign), "the foreign carrier fixture must enter the loaded world");
        helper.runAfterDelay(1L, () -> {
            try {
                helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materialize(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.CONFLICT,
                        "an unowned carrier UUID is visible conflict evidence and may never be claimed");
                helper.assertTrue(!foreign.isRemoved(), "the foreign carrier must remain untouched");
                foreign.discard(); runtime.shutdown(); helper.succeed();
            } catch (RuntimeException failure) { foreign.discard(); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void missingCarrierAfterRestartKeepsSceneUnknown(GameTestHelper helper) {
        // The canonical lease remains exact. Only its observed bodies/cart live inside this
        // GameTest's own loaded template, so a parallel template cannot erase the recovery
        // evidence between admission and inspection.
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 4));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-recovery-test");
        FrontierWorldState state = state(runtime); SceneEngagementCandidate candidate = state.coldEngagementSceneCandidates().getFirst();
        var checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(state, checkpoint, candidate, new SceneLeaseId("lease:frontier-v3-cargo-recovery-test"));
        for (int index = 0; index < lease.members().size(); index++) {
            BlockPos position = origin.offset(index & 1, 0, index / 2); prepareFloor(level, position);
            addOwnedBody(helper, level, lease, lease.members().get(index), position);
        }
        prepareFloor(level, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-recovery-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        MinecartChest carrier = addOwnedCarrier(helper, level, state(runtime), lease, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-recovery-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(2L, () -> {
            try {
                helper.assertValueEqual(FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(runtime), 1,
                        "restart must first retain the active scene as UNKNOWN");
                helper.assertTrue(level.getEntity(FrontierV3CargoCarrierExecutor.id(lease)) == carrier,
                        "fixture must retain the exact cargo carrier before its loss");
                carrier.discard();
                helper.assertTrue(!FrontierV3SceneExecutor.reclaimObservedBodies(level, runtime, state(runtime), lease),
                        "a missing exact carrier must prevent HOT recovery even when all bodies remain present");
                helper.assertValueEqual(state(runtime).sceneLeases().get(lease.id()).status(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART,
                        "missing cargo must remain explicit UNKNOWN rather than being recreated or ignored");
                lease.members().forEach(member -> { Entity body = level.getEntity(member.entityId()); if (body != null) body.discard(); });
                runtime.shutdown(); helper.succeed();
            } catch (RuntimeException failure) {
                discard(level, lease); lease.members().forEach(member -> { Entity body = level.getEntity(member.entityId()); if (body != null) body.discard(); });
                runtime.shutdown(); throw failure;
            }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void playerOpeningCargoReleasesItsRouteThenObservesExactWithdrawal(GameTestHelper helper) {
        // This test proves player custody/release, while preparedSceneMaterializes... proves
        // cargo creation. Keep the observed cart in this template instead of force-loading the
        // canonical hand-off chunk shared by parallel GameTests.
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 4));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-player-release");
        FrontierWorldState initial = state(runtime); SceneEngagementCandidate candidate = initial.coldEngagementSceneCandidates().getFirst();
        var checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(initial, checkpoint, candidate, new SceneLeaseId("lease:frontier-v3-cargo-player-release"));
        prepareFloor(level, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-player-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        MinecartChest carrier = addOwnedCarrier(helper, level, state(runtime), lease, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-player-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(2L, () -> {
            try {
                MinecartChest cart = (MinecartChest) level.getEntity(FrontierV3CargoCarrierExecutor.id(lease));
                helper.assertTrue(cart == carrier, "the exact cargo cart must be UUID-indexed before interaction");
                FrontierWorldState hot = state(runtime);
                helper.assertTrue(FrontierV3CargoCarrierExecutor.markReleasedCarrier(hot, lease, cart),
                        "release marks every exact stack with its live cart carrier before the durable fact");
                helper.assertTrue(!FrontierV3CargoCarrierPresentation.attached(cart, lease),
                        "a released player-opened cart must lose its caravan caption before it can become ordinary world custody");
                FrontierV3CommandSubmission.submit(runtime, "scene-cargo-player-release", lease.id().value(),
                        new CargoCarrierReleased(lease.id(), FrontierSceneBehaviors.logistics(lease).cargoId(), cart.getUUID(), java.util.Optional.of(java.util.UUID.fromString("00000000-0000-0000-0000-000000000061"))));
                FrontierWorldState released = state(runtime);
                var itemId = initial.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId()).itemIds().getFirst();
                helper.assertValueEqual(released.inventory().items().get(itemId).custody(), new InventoryCustody.WorldCarrier(cart.getUUID()),
                        "a released shipment becomes the exact cart's physical custody, never an untracked aggregate");
                helper.assertValueEqual(released.contracts().values().stream().filter(contract -> contract.cargoId().equals(FrontierSceneBehaviors.logistics(lease).cargoId())).findFirst().orElseThrow().status(), ContractStatus.INTERRUPTED,
                        "opening the cart interrupts the canonical supply contract");
                helper.assertValueEqual(released.operations().get(FrontierSceneBehaviors.logistics(lease).operationId()).stage(), OperationStage.INTERRUPTED,
                        "the interrupted route cannot continue COLD progression");
                helper.assertValueEqual(released.sceneLeases().get(lease.id()).status(), SceneLeaseStatus.DRAINING,
                        "the HOT bodies must drain rather than keep escorting a released cart");
                var player = helper.makeMockServerPlayerInLevel();
                player.getInventory().setItem(0, cart.removeItemNoUpdate(0)); cart.setChanged();
                FrontierV3CargoCarrierObservationExecutor.tick(level, runtime);
                helper.assertValueEqual(state(runtime).inventory().items().get(itemId).custody(), new InventoryCustody.Player(player.getUUID()),
                        "the first real cart withdrawal must retain its exact stack ID and player UUID");
                cart.discard(); runtime.shutdown(); helper.succeed();
            } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void externalImpactReloadTransfersDestroyedCargoCartToExactDrops(GameTestHelper helper) {
        // Stay inside this test's template footprint. A relative x=64 overlaps a concurrently
        // running fixture, which can legitimately remove its own nearby item entities.
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 4));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-external-impact");
        FrontierWorldState initial = state(runtime); SceneEngagementCandidate candidate = initial.coldEngagementSceneCandidates().getFirst();
        var checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(initial, checkpoint, candidate, new SceneLeaseId("lease:frontier-v3-cargo-external-impact"));
        prepareFloor(level, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-impact-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-impact-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(1L, () -> {
            try {
                MinecartChest cart = EntityType.CHEST_MINECART.create(level);
                helper.assertTrue(cart != null, "the exact cargo cart fixture must be constructible");
                cart.setUUID(FrontierV3CargoCarrierExecutor.id(lease)); BlockPos cartPosition = cargoPosition(origin, lease);
                cart.setPos(cartPosition.getX() + 0.5D, cartPosition.getY(), cartPosition.getZ() + 0.5D);
                cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.LEASE_KEY, lease.id().value());
                cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.CARGO_KEY, FrontierSceneBehaviors.logistics(lease).cargoId().value());
                var cargo = state(runtime).inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
                for (int slot = 0; slot < cargo.itemIds().size(); slot++) cart.setItem(slot, FrontierV3CargoHandoffExecutor.materializedStack(state(runtime).inventory().items().get(cargo.itemIds().get(slot))));
                helper.assertTrue(level.addFreshEntity(cart), "the exact cargo cart fixture must enter the loaded world");
                FrontierV3CargoCarrierImpactLedger ledger = FrontierV3CargoCarrierImpactLedger.get(level);
                boolean rejected = false;
                try { ledger.capture(level.getGameTime(), cart, state(runtime)); }
                catch (IllegalStateException expected) { rejected = true; }
                helper.assertTrue(rejected, "an unreleased HOT cargo batch may never be treated as a physical carrier");
                helper.assertTrue(FrontierV3CargoCarrierExecutor.markReleasedCarrier(state(runtime), lease, cart),
                        "every exact cart stack must retain its old carrier identity before impact");
                FrontierV3CommandSubmission.submit(runtime, "scene-cargo-impact-release", lease.id().value(),
                        new CargoCarrierReleased(lease.id(), FrontierSceneBehaviors.logistics(lease).cargoId(), cart.getUUID(), Optional.empty()));
                FrontierWorldState released = state(runtime); ledger.capture(level.getGameTime(), cart, released);
                FrontierV3CargoCarrierImpactLedger restored = FrontierV3CargoCarrierImpactLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
                BlockPos impactPosition = cart.blockPosition(); java.util.List<net.minecraft.world.item.ItemStack> releasedStacks = new java.util.ArrayList<>();
                for (int slot = 0; slot < cart.getContainerSize(); slot++) {
                    var stack = cart.removeItemNoUpdate(slot); if (stack.isEmpty()) continue;
                    releasedStacks.add(stack);
                }
                cart.setChanged(); cart.discard();
                helper.runAfterDelay(2L, () -> {
                    try {
                        helper.assertTrue(restored.nextReady(Long.MAX_VALUE).isPresent(), "reloaded impact evidence must retain one queued exact stack");
                        // The fixture lives in its own already-loaded template chunk. The production executor
                        // may inspect that chunk but must never make it load.
                        helper.assertTrue(level.hasChunkAt(cargoPosition(origin, lease)), "the impact anchor must remain naturally loaded for inspection");
                        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, java.util.UUID> drops = new java.util.HashMap<>();
                        for (var stack : releasedStacks) {
                            ItemEntity drop = new ItemEntity(level, impactPosition.getX() + 0.5D, impactPosition.getY(), impactPosition.getZ() + 0.5D, stack);
                            helper.assertTrue(level.addFreshEntity(drop), "a real exact cart stack must become one physical drop");
                            drops.put(FrontierV3CargoHandoffExecutor.itemId(stack).orElseThrow(), drop.getUUID());
                        }
                        helper.runAfterDelay(1L, () -> {
                            try {
                                helper.assertTrue(drops.values().stream().allMatch(dropId -> level.getEntity(dropId) instanceof ItemEntity),
                                        "each released cart stack must remain one nearby physical drop before reconciliation");
                                FrontierV3CargoCarrierImpactExecutor.tick(level, runtime, restored, level.getGameTime() + 1L);
                                for (var entry : drops.entrySet()) {
                                    helper.assertValueEqual(state(runtime).inventory().items().get(entry.getKey()).custody(), new InventoryCustody.WorldCarrier(entry.getValue()),
                                            "reloaded impact evidence must transfer each exact stack to its one real surviving drop");
                                }
                                runtime.shutdown(); helper.succeed();
                            } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
                        });
                    } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
                });
            } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void terminalDamageCapturesCargoBeforeVanillaDestroysCart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(8, 8, 8));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-terminal-damage");
        FrontierWorldState initial = state(runtime); SceneEngagementCandidate candidate = initial.coldEngagementSceneCandidates().getFirst();
        var checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(initial, checkpoint, candidate, new SceneLeaseId("lease:frontier-v3-cargo-terminal-damage"));
        prepareFloor(level, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-terminal-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-terminal-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(1L, () -> {
            try {
                MinecartChest cart = EntityType.CHEST_MINECART.create(level);
                helper.assertTrue(cart != null, "the terminal-damage cargo cart fixture must be constructible");
                cart.setUUID(FrontierV3CargoCarrierExecutor.id(lease)); BlockPos cartPosition = cargoPosition(origin, lease);
                cart.setPos(cartPosition.getX() + 0.5D, cartPosition.getY(), cartPosition.getZ() + 0.5D);
                cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.LEASE_KEY, lease.id().value());
                cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.CARGO_KEY, FrontierSceneBehaviors.logistics(lease).cargoId().value());
                var cargo = state(runtime).inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
                for (int slot = 0; slot < cargo.itemIds().size(); slot++) cart.setItem(slot, FrontierV3CargoHandoffExecutor.materializedStack(state(runtime).inventory().items().get(cargo.itemIds().get(slot))));
                helper.assertTrue(level.addFreshEntity(cart), "the terminal-damage cargo cart fixture must enter the loaded world");
                FrontierV3CargoCarrierImpactLedger ledger = FrontierV3CargoCarrierImpactLedger.get(level);
                FrontierV3ServerLifecycle.observeTerminalVehicleDamage(level, runtime, cart, level.damageSources().generic());
                var itemId = cargo.itemIds().getFirst();
                helper.assertValueEqual(state(runtime).inventory().items().get(itemId).custody(), new InventoryCustody.WorldCarrier(cart.getUUID()),
                        "a terminal hit must durably release exact cargo before vanilla destroys the cart");
                helper.assertTrue(ledger.nextReady(Long.MAX_VALUE).isPresent(), "terminal damage must retain restart-safe pre-destruction evidence");
                helper.assertTrue(cart.hurt(level.damageSources().generic(), 5.0F), "vanilla must still apply the unrestricted terminal minecart hit");
                helper.runAfterDelay(2L, () -> {
                    try {
                        helper.assertTrue(cart.isRemoved(), "the test must observe real vanilla cart destruction rather than a protected vehicle");
                        FrontierV3CargoCarrierImpactExecutor.tick(level, runtime, ledger, level.getGameTime() + 1L);
                        var surviving = state(runtime).inventory().items().get(itemId);
                        helper.assertTrue(surviving == null || (surviving.custody() instanceof InventoryCustody.WorldCarrier carrier
                                        && !carrier.carrierId().equals(cart.getUUID())),
                                "the exact cargo must either follow one real vanilla drop or be recorded destroyed, never retain the destroyed cart identity");
                        runtime.shutdown(); helper.succeed();
                    } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
                });
            } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
        });
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime(String world) {
        return FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(new WorldId(world), 91L), new EphemeralStore(), 20_000);
    }
    private static SceneLease lease(FrontierWorldState state, BlockPos origin, String id) {
        SceneEngagementCandidate candidate = state.coldEngagementSceneCandidates().getFirst();
        List<SceneMember> members = candidate.actorIds().stream()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor))).toList();
        BlockPosition handoff = new BlockPosition(origin.getX(), origin.getY(), origin.getZ());
        BlockPos cargo = origin.offset((members.size() % 2) * 2 + 1, 0, (members.size() / 2) * 2);
        return SceneLease.atExactPositions(new SceneLeaseId(id), state.bootstrap().worldId(), candidate.operationId(), candidate.cargoId(), handoff,
                new BlockPosition(cargo.getX(), cargo.getY(), cargo.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED, Optional.empty(), members,
                members.stream().collect(java.util.stream.Collectors.toMap(SceneMember::actorId, ignored -> handoff, (left, right) -> left, java.util.LinkedHashMap::new)));
    }
    private static void prepareFloor(ServerLevel level, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3); level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
    }
    private static BlockPos cargoPosition(BlockPos anchor, SceneLease lease) {
        int ordinal = lease.members().size(); return anchor.offset((ordinal % 2) * 2 + 1, 0, (ordinal / 2) * 2);
    }
    private static void discard(ServerLevel level, SceneLease lease) {
        Entity carrier = level.getEntity(FrontierV3CargoCarrierExecutor.id(lease)); if (carrier != null) carrier.discard();
    }
    private static void addOwnedBody(GameTestHelper helper, ServerLevel level, SceneLease lease, SceneMember member, BlockPos position) {
        Mob body = member.actorId().value().startsWith("bioform:") ? EntityType.ZOMBIE.create(level) : EntityType.VILLAGER.create(level);
        helper.assertTrue(body != null, "the exact recovery body fixture must be constructible");
        body.setUUID(member.entityId()); body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D); body.setNoAi(true);
        body.getPersistentData().putString(FrontierV3SceneExecutor.LEASE_KEY, lease.id().value());
        body.getPersistentData().putString(FrontierV3SceneExecutor.ACTOR_KEY, member.actorId().value());
        body.getPersistentData().putLong(FrontierV3SceneExecutor.REVISION_KEY, lease.revision());
        helper.assertTrue(level.addFreshEntity(body), "the exact recovery body fixture must enter the loaded world");
    }
    private static MinecartChest addOwnedCarrier(GameTestHelper helper, ServerLevel level, FrontierWorldState state, SceneLease lease, BlockPos position) {
        MinecartChest cart = EntityType.CHEST_MINECART.create(level);
        helper.assertTrue(cart != null, "the exact recovery cargo carrier must be constructible");
        cart.setUUID(FrontierV3CargoCarrierExecutor.id(lease)); cart.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.LEASE_KEY, lease.id().value());
        cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.CARGO_KEY, FrontierSceneBehaviors.logistics(lease).cargoId().value());
        var cargo = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
        for (int slot = 0; slot < cargo.itemIds().size(); slot++) {
            cart.setItem(slot, FrontierV3CargoHandoffExecutor.materializedStack(state.inventory().items().get(cargo.itemIds().get(slot))));
        }
        helper.assertTrue(level.addFreshEntity(cart), "the exact recovery cargo carrier must enter the loaded world");
        return cart;
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
    }
    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) {
            throw new UnsupportedOperationException("GameTest does not compact");
        }
    }
}
