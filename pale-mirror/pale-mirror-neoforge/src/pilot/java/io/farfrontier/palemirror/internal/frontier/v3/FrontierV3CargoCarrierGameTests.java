package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLeaseStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierReleased;
import io.farfrontier.palemirror.frontier.v3.model.ContractStatus;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.OperationStage;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.SceneEngagementCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
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

    @GameTest(batch = "pm-frontier-v3-scene-cargo-authority", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedSceneMaterializesOneExactCargoCarrierWithoutDuplication(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-test");
        SceneLease lease = lease(runtime, origin, "lease:frontier-v3-cargo-test"); FrontierWorldState state = state(runtime);
        prepareSupport(level, cargoPosition(origin, lease));
        var cargo = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
        var expected = cargo.itemIds().stream().map(state.inventory().items()::get)
                .sorted(java.util.Comparator.comparing(value -> value.id())).toList();
        helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materializeForFixture(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a prepared loaded scene must create one exact cargo carrier");
        helper.runAfterDelay(1L, () -> {
            try {
                Entity carrier = level.getEntity(FrontierV3CargoCarrierExecutor.id(lease));
                helper.assertTrue(carrier instanceof MinecartChest, "the graybox carrier must be a visible chest minecart");
                helper.assertTrue(FrontierV3CargoCarrierExecutor.owned(state, carrier, lease, cargo, expected), "the carrier must hold only canonical tagged cargo");
                carrier.getPersistentData().remove(FrontierV3CargoCarrierExecutor.REVISION_KEY);
                helper.assertFalse(FrontierV3CargoCarrierExecutor.owned(state, carrier, lease, cargo, expected),
                        "a stable UUID cannot supply a missing scene revision, including revision zero");
                carrier.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.REVISION_KEY, lease.revision() + 1);
                helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materializeForFixture(level, state, lease),
                        FrontierV3SceneExecutor.BodyMaterialization.CONFLICT,
                        "a different-generation cart must conflict instead of being reused or replaced");
                carrier.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.REVISION_KEY, lease.revision());
                long epoch = carrier.getPersistentData().getLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY);
                carrier.getPersistentData().remove(FrontierV3CargoCarrierExecutor.EPOCH_KEY);
                helper.assertFalse(FrontierV3CargoCarrierExecutor.owned(state, carrier, lease, cargo, expected),
                        "scene revision cannot supply a missing recovery attempt epoch");
                carrier.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY, epoch);
                helper.assertTrue(FrontierV3CargoCarrierPresentation.attached(carrier, lease),
                        "one exact local caption must ride with the real cargo carrier rather than becoming a detached HUD");
                helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materializeForFixture(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                        "recovery must reuse the exact carrier rather than duplicate cargo");
                helper.assertValueEqual(level.getEntitiesOfClass(MinecartChest.class, carrier.getBoundingBox().inflate(8.0D),
                                candidate -> FrontierV3CargoCarrierExecutor.owned(state, candidate, lease, cargo, expected)).size(), 1,
                        "one HOT lease must retain exactly one owned cargo carrier, independently of a parallel scene's cart");
                FrontierV3CommandSubmission.submit(runtime, "cargo-fixture-conflict", lease.id().value(),
                        new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT));
                FrontierV3CommandSubmission.submit(runtime, "cargo-fixture-reprepare", lease.id().value(),
                        new SceneLeaseTransition(lease.id(), SceneLeaseStatus.PREPARED));
                var retry = state(runtime);
                helper.assertValueEqual(retry.sceneLeases().get(lease.id()).revision(), lease.revision(), "reattempt retains the scene revision");
                helper.assertTrue(FrontierV3CargoCarrierAuthority.currentEpoch(retry.fencedRecovery(), lease).orElseThrow() > epoch,
                        "the canonical reattempt must advance physical authority");
                helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materializeForFixture(level, retry, lease),
                        FrontierV3SceneExecutor.BodyMaterialization.CONFLICT, "the old cart cannot be restamped or adopted into the next attempt");
                helper.assertTrue(!carrier.isRemoved(), "stale-attempt inspection must preserve the conflicting physical evidence");
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
        SceneLease lease = lease(runtime, origin, "lease:frontier-v3-cargo-deck-test"); FrontierWorldState state = state(runtime);
        BlockPos deck = cargoPosition(origin, lease); prepareSupport(level, deck); level.setBlock(deck, Blocks.GRAY_CARPET.defaultBlockState(), 3);

        helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materializeForFixture(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
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
    public static void cargoCarrierFollowsTheExactRaisedTransportSupportRatherThanFlatteningItsGrade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-raised-grade-test");
        SceneLease lease = lease(runtime, origin, "lease:frontier-v3-cargo-raised-grade-test"); FrontierWorldState state = state(runtime);
        BlockPos start = cargoPosition(origin, lease); BlockPos raised = start.offset(1, 1, 0);
        prepareSupport(level, start); prepareSupport(level, raised);
        helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materializeForFixture(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "one exact cargo carrier must materialize above its retained start support");
        helper.runAfterDelay(1L, () -> {
            try {
                for (int tick = 0; tick < 80; tick++) helper.assertTrue(FrontierV3CargoCarrierExecutor.move(level, state, lease,
                        new BlockPosition(raised.getX(), raised.getY(), raised.getZ())), "a loaded raised support must remain available to its exact carrier");
                Entity carrier = level.getEntity(FrontierV3CargoCarrierExecutor.id(lease));
                helper.assertTrue(carrier != null && FrontierV3CargoCarrierExecutor.atDestination(level, state, lease,
                                new BlockPosition(raised.getX(), raised.getY(), raised.getZ()))
                                && Math.abs(carrier.getY() - (raised.getY() + 1.0D)) < 0.36D,
                        "the carrier must reach the one-block raised support's exact standing datum rather than flattening Y: "
                                + (carrier == null ? "missing" : carrier.position()) + " support=" + raised
                                + " expectedFeet=" + raised.above());
                discard(level, lease); runtime.shutdown(); helper.succeed();
            } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedSceneRefusesForeignCargoCarrierWithExpectedUuid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(48, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-conflict-test");
        SceneLease lease = lease(runtime, origin, "lease:frontier-v3-cargo-conflict-test"); FrontierWorldState state = state(runtime);
        prepareSupport(level, cargoPosition(origin, lease));
        MinecartChest foreign = EntityType.CHEST_MINECART.create(level);
        helper.assertTrue(foreign != null, "the foreign carrier fixture must be constructible");
        foreign.setUUID(FrontierV3CargoCarrierExecutor.id(lease)); foreign.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        helper.assertTrue(level.addFreshEntity(foreign), "the foreign carrier fixture must enter the loaded world");
        helper.runAfterDelay(1L, () -> {
            try {
                helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materializeForFixture(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.CONFLICT,
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
            addOwnedBody(helper, level, state, lease, lease.members().get(index), position);
        }
        prepareSupport(level, cargoPosition(origin, lease));
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

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void closedCargoProjectionRequiresItsExactRecoveryFenceBeforeStaleCleanup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 4));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-stale-fence");
        FrontierWorldState initial = state(runtime); SceneEngagementCandidate candidate = initial.coldEngagementSceneCandidates().getFirst();
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(initial, runtime.checkpointImage().orElseThrow(), candidate,
                new SceneLeaseId("lease:frontier-v3-cargo-stale-fence"));
        prepareSupport(level, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-stale-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        MinecartChest cart = addOwnedCarrier(helper, level, state(runtime), lease, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-stale-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(2L, () -> {
            try {
                FrontierV3CommandSubmission.submit(runtime, "scene-cargo-stale-drain", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
                helper.assertTrue(FrontierV3CargoDepartureObserver.prepareRelease(level, state(runtime), state(runtime).sceneLeases().get(lease.id())),
                        "physical cleanup witness must be durable before canonical release");
                FrontierV3CommandSubmission.submit(runtime, "scene-cargo-stale-release", lease.id().value(), new SceneLeaseReleased(lease.id(),
                        lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(), lease.memberPosition(member.actorId()))).toList()));
                FrontierWorldState closed = state(runtime);
                FrontierWorldState unfenced = closed.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(FencedRecoveryState.empty()));
                FrontierV3SceneExecutor.cleanClosedBodies(level, unfenced);
                helper.assertTrue(level.getEntity(cart.getUUID()) == cart && !cart.isRemoved(),
                        "canonical cargo alone must not delete a naturally returned cart without its exact retired recovery fence");
                var history = new java.util.LinkedHashMap<>(closed.sceneLeases()); history.remove(lease.id());
                FrontierV3SceneExecutor.cleanClosedBodies(level, closed.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(history)));
                helper.assertTrue(level.getEntity(cart.getUUID()) == null,
                        "the same closed carrier must be removed only after its exact durable tombstone rejects the old projection");
                runtime.shutdown(); helper.succeed();
            } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void playerOpeningCargoReleasesItsRouteThenObservesPartialFungibleWithdrawal(GameTestHelper helper) {
        // This test proves player custody/release, while preparedSceneMaterializes... proves
        // cargo creation. Keep the observed cart in this template instead of force-loading the
        // canonical hand-off chunk shared by parallel GameTests.
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 4));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-player-release");
        FrontierWorldState initial = state(runtime); SceneEngagementCandidate candidate = initial.coldEngagementSceneCandidates().getFirst();
        var checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(initial, checkpoint, candidate, new SceneLeaseId("lease:frontier-v3-cargo-player-release"));
        prepareSupport(level, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-player-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        MinecartChest carrier = addOwnedCarrier(helper, level, state(runtime), lease, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-player-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(2L, () -> {
            try {
                MinecartChest cart = (MinecartChest) level.getEntity(FrontierV3CargoCarrierExecutor.id(lease));
                helper.assertTrue(cart == carrier, "the exact cargo cart must be UUID-indexed before interaction");
                MinecartChest ordinary = EntityType.CHEST_MINECART.create(level);
                helper.assertTrue(ordinary != null, "ordinary cart fixture must be constructible");
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, ordinary, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.NOT_MANAGED, "ordinary carts do not acquire shipment custody");
                FrontierV3ServerLifecycle.observeTerminalVehicleDamage(level, runtime, ordinary, level.damageSources().generic());
                helper.assertValueEqual(runtime.status().kind(), FrontierV3RuntimeStatus.Kind.ACTIVE,
                        "damage to an ordinary cart must not quarantine the simulation for absent cargo custody");
                cart.getPersistentData().remove(FrontierV3CargoCarrierExecutor.REVISION_KEY);
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, cart, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.REJECTED,
                        "a malformed managed cart must not bypass the release boundary as ordinary vanilla storage");
                helper.assertValueEqual(state(runtime).sceneLeases().get(lease.id()).status(), SceneLeaseStatus.HOT,
                        "rejected interaction cannot consume scene authority");
                cart.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.REVISION_KEY, lease.revision());
                var declarationBeforeRelease = cart.getPersistentData().copy();
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, cart,
                                Optional.of(java.util.UUID.fromString("00000000-0000-0000-0000-000000000061"))),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.RELEASED,
                        "the actual interaction boundary verifies and durably releases the exact cargo carrier");
                helper.assertFalse(FrontierV3CargoCarrierExecutor.hasDeclaration(cart),
                        "accepted world custody retires the old scene declaration before ordinary container interaction");
                helper.assertTrue(!FrontierV3CargoCarrierPresentation.attached(cart, lease),
                        "a released player-opened cart must lose its caravan caption before it can become ordinary world custody");
                FrontierWorldState released = state(runtime);
                var source = fungibleWorldCarrierAccount(released, cart.getUUID());
                helper.assertValueEqual(source.custody(), new ResourceCustody.WorldCarrier(cart.getUUID()),
                        "a released shipment becomes the cart's one HOT fungible custody account");
                helper.assertValueEqual(released.contracts().values().stream().filter(contract -> contract.cargoId().equals(FrontierSceneBehaviors.logistics(lease).cargoId())).findFirst().orElseThrow().status(), ContractStatus.INTERRUPTED,
                        "opening the cart interrupts the canonical supply contract");
                helper.assertValueEqual(released.operations().get(FrontierSceneBehaviors.logistics(lease).operationId()).stage(), OperationStage.INTERRUPTED,
                        "the interrupted route cannot continue COLD progression");
                helper.assertValueEqual(released.sceneLeases().get(lease.id()).status(), SceneLeaseStatus.DRAINING,
                        "the HOT bodies must drain rather than keep escorting a released cart");
                var releaseRevision = runtime.checkpointImage().orElseThrow().revision();
                // Fault injection at the save boundary: canonical handoff is committed,
                // but the cart still carries its prior serialized scene declaration.
                cart.getPersistentData().merge(declarationBeforeRelease);
                long releasedEpoch = declarationBeforeRelease.getLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY);
                cart.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY, releasedEpoch + 1);
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, cart, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.REJECTED,
                        "world custody cannot authorize a mismatched scene-attempt declaration");
                helper.assertValueEqual(cart.getPersistentData().getLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY), releasedEpoch + 1,
                        "rejected evidence must not be erased or restamped");
                cart.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY, releasedEpoch);
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, cart, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.NOT_MANAGED,
                        "exact already-committed world handoff retires only its own old declaration");
                helper.assertFalse(FrontierV3CargoCarrierExecutor.hasDeclaration(cart), "successful reconciliation removes all scene authority fields");
                helper.assertValueEqual(runtime.checkpointImage().orElseThrow().revision(), releaseRevision,
                        "declaration reconciliation must not release or credit the same cargo twice");
                var player = helper.makeMockServerPlayerInLevel();
                var withdrawn = cart.getItem(0).split(cart.getItem(0).getCount() / 2);
                player.getInventory().setItem(0, withdrawn); cart.setChanged();
                FrontierV3FungibleResourceObservationExecutor.tick(level, runtime);
                FrontierWorldState observed = state(runtime);
                var retained = observed.inventory().fungibleResources().accounts().get(source.id());
                var playerAccount = observed.inventory().fungibleResources().accounts().values().stream()
                        .filter(account -> account.custody().equals(new ResourceCustody.Player(player.getUUID()))).findFirst().orElseThrow();
                helper.assertValueEqual(retained.lotQuantities().values().stream().mapToInt(Integer::intValue).sum(), cart.getItem(0).getCount(),
                        "the cart retains exactly its physically remaining partial fungible portion");
                helper.assertValueEqual(playerAccount.lotQuantities().values().stream().mapToInt(Integer::intValue).sum(), withdrawn.getCount(),
                        "the observed player stack owns exactly the withdrawn partial fungible portion");
                runtime.shutdown();
                cart.getPersistentData().merge(declarationBeforeRelease);
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, cart, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.REJECTED, "inactive runtime cannot authorize a declared managed container");
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, ordinary, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.NOT_MANAGED, "inactive runtime must not block unrelated vanilla containers");
                cart.discard(); helper.succeed();
            } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void externalImpactReloadTransfersDestroyedCargoCartToFungibleDrop(GameTestHelper helper) {
        // Stay inside this test's template footprint. A relative x=64 overlaps a concurrently
        // running fixture, which can legitimately remove its own nearby item entities.
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 4));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-external-impact");
        FrontierWorldState initial = state(runtime); SceneEngagementCandidate candidate = initial.coldEngagementSceneCandidates().getFirst();
        var checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(initial, checkpoint, candidate, new SceneLeaseId("lease:frontier-v3-cargo-external-impact"));
        prepareSupport(level, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-impact-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-impact-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(1L, () -> {
            try {
                MinecartChest cart = addOwnedCarrier(helper, level, state(runtime), lease, cargoPosition(origin, lease));
                FrontierV3CargoCarrierImpactLedger ledger = FrontierV3CargoCarrierImpactLedger.get(level);
                boolean rejected = false;
                try { ledger.capture(level.getGameTime(), cart, state(runtime)); }
                catch (IllegalStateException expected) { rejected = true; }
                helper.assertTrue(rejected, "an unreleased HOT cargo batch may never be treated as a physical carrier");
                var originalStack = cart.getItem(0).copy();
                helper.assertFalse(originalStack.isEmpty(), "the cargo fixture must carry a real stack");
                var changedCount = originalStack.copy();
                changedCount.setCount(originalStack.getCount() == 1 ? 2 : originalStack.getCount() - 1);
                cart.setItem(0, changedCount);
                helper.assertFalse(FrontierV3CargoCarrierExecutor.markReleasedCarrier(state(runtime), lease, cart),
                        "matching item/components cannot authorize release when the physical quantity differs");
                cart.setItem(0, originalStack);
                helper.assertTrue(FrontierV3CargoCarrierExecutor.markReleasedCarrier(state(runtime), lease, cart),
                        "the live fungible cart must pass its canonical carrier ownership check before impact");
                FrontierV3CommandSubmission.submit(runtime, "scene-cargo-impact-release", lease.id().value(),
                        new CargoCarrierReleased(lease.id(), FrontierSceneBehaviors.logistics(lease).cargoId(), cart.getUUID(), Optional.empty()));
                FrontierWorldState released = state(runtime); ledger.capture(level.getGameTime(), cart, released);
                FrontierV3CargoCarrierImpactLedger restored = FrontierV3CargoCarrierImpactLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
                var originalReady = ledger.nextReady(Long.MAX_VALUE);
                helper.assertTrue(originalReady.isPresent(), "the live saved-data ledger must retain the released fungible carrier before simulated restart");
                ledger.resolve(originalReady.orElseThrow());
                BlockPos impactPosition = cart.blockPosition(); java.util.List<net.minecraft.world.item.ItemStack> releasedStacks = new java.util.ArrayList<>();
                for (int slot = 0; slot < cart.getContainerSize(); slot++) {
                    var stack = cart.removeItemNoUpdate(slot); if (stack.isEmpty()) continue;
                    releasedStacks.add(stack);
                }
                cart.setChanged(); cart.discard();
                helper.runAfterDelay(2L, () -> {
                    try {
                        helper.assertTrue(restored.nextReady(Long.MAX_VALUE).isPresent(), "reloaded impact evidence must retain one queued fungible carrier");
                        // The fixture lives in its own already-loaded template chunk. The production executor
                        // may inspect that chunk but must never make it load.
                        helper.assertTrue(level.hasChunkAt(cargoPosition(origin, lease)), "the impact anchor must remain naturally loaded for inspection");
                        helper.assertValueEqual(releasedStacks.size(), 1, "one fungible cargo account must have one physical cart stack");
                        ItemEntity drop = new ItemEntity(level, impactPosition.getX() + 0.5D, impactPosition.getY(), impactPosition.getZ() + 0.5D, releasedStacks.getFirst());
                        helper.assertTrue(level.addFreshEntity(drop), "the real fungible cart stack must become one physical drop");
                        helper.runAfterDelay(1L, () -> {
                            try {
                                helper.assertTrue(level.getEntity(drop.getUUID()) instanceof ItemEntity,
                                        "the released fungible cart stack must remain one nearby physical drop before reconciliation");
                                helper.assertTrue(level.hasChunkAt(impactPosition), "the retained carrier impact position must remain ordinarily loaded");
                                helper.assertValueEqual(level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(impactPosition).inflate(4.0D),
                                                candidateDrop -> ItemStack.isSameItemSameComponents(candidateDrop.getItem(), drop.getItem())
                                                        && candidateDrop.getItem().getCount() == drop.getItem().getCount()).size(), 1,
                                        "the restart fixture must expose exactly one physical successor drop in the immediate cart-impact footprint");
                                FrontierV3CargoCarrierImpactExecutor.tick(level, runtime, restored, level.getGameTime() + 1L);
                                var successor = state(runtime).inventory().fungibleResources().accounts().values().stream()
                                        .filter(account -> account.custody().equals(new ResourceCustody.WorldCarrier(drop.getUUID()))).findFirst();
                                helper.assertTrue(successor.isPresent(), "reloaded impact evidence must retain a successor account at the observed drop; accounts="
                                        + state(runtime).inventory().fungibleResources().accounts().values().stream().map(account -> account.custody().toString()).toList());
                                helper.assertValueEqual(successor.orElseThrow().custody(), new ResourceCustody.WorldCarrier(drop.getUUID()),
                                        "reloaded impact evidence must transfer the one fungible account to its real surviving drop");
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
        prepareSupport(level, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-terminal-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-terminal-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(1L, () -> {
            try {
                MinecartChest cart = addOwnedCarrier(helper, level, state(runtime), lease, cargoPosition(origin, lease));
                FrontierV3CargoCarrierImpactLedger ledger = FrontierV3CargoCarrierImpactLedger.get(level);
                FrontierV3ServerLifecycle.observeTerminalVehicleDamage(level, runtime, cart, level.damageSources().generic());
                var carrierAccount = fungibleWorldCarrierAccount(state(runtime), cart.getUUID());
                helper.assertValueEqual(carrierAccount.custody(), new ResourceCustody.WorldCarrier(cart.getUUID()),
                        "a terminal hit must durably release fungible cargo before vanilla destroys the cart");
                helper.assertTrue(ledger.nextReady(Long.MAX_VALUE).isPresent(), "terminal damage must retain restart-safe pre-destruction evidence");
                helper.assertTrue(cart.hurt(level.damageSources().generic(), 5.0F), "vanilla must still apply the unrestricted terminal minecart hit");
                helper.runAfterDelay(2L, () -> {
                    try {
                        helper.assertTrue(cart.isRemoved(), "the test must observe real vanilla cart destruction rather than a protected vehicle");
                        FrontierV3CargoCarrierImpactExecutor.tick(level, runtime, ledger, level.getGameTime() + 1L);
                        boolean retained = state(runtime).inventory().fungibleResources().accounts().values().stream()
                                .anyMatch(account -> account.custody().equals(new ResourceCustody.WorldCarrier(cart.getUUID())));
                        boolean successor = state(runtime).inventory().fungibleResources().accounts().values().stream()
                                .anyMatch(account -> account.custody() instanceof ResourceCustody.WorldCarrier carrier && !carrier.carrierId().equals(cart.getUUID()));
                        helper.assertTrue(successor || retained && ledger.nextReady(Long.MAX_VALUE).isPresent(),
                                "a missing fungible drop must either retain one successor carrier or stay fenced by restart-safe impact evidence");
                        runtime.shutdown(); helper.succeed();
                    } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
                });
            } catch (RuntimeException failure) { discard(level, lease); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void departedSceneBodyRetainsFinalHealthAndBlocksDivergentReturn(GameTestHelper helper) {
        // Native body/NBT/callback boundary only. Fixture positions are outside the domain
        // world and are deliberately not submitted as a canonical release or restart proof.
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 4));
        var runtime = runtime("frontier:scene-body-departure");
        FrontierWorldState initial = state(runtime);
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(initial, runtime.checkpointImage().orElseThrow(),
                initial.coldEngagementSceneCandidates().getFirst(), new SceneLeaseId("lease:scene-body-departure"));
        FrontierV3CommandSubmission.submit(runtime, "departure-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "departure-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        SceneMember member = lease.members().getFirst();
        prepareFloor(level, origin);
        Mob body = addOwnedBody(helper, level, state(runtime), lease, member, origin);
        helper.runAfterDelay(1L, () -> {
            try {
                helper.assertTrue(FrontierV3SceneExecutor.owned(body, state(runtime), lease, member),
                        "initial exact body may perform HOT work; removed=" + body.getRemovalReason() + " tags=" + body.getPersistentData());
                body.setHealth(9.0F);
                net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
                helper.assertTrue(body.save(saved), "capture the actual Minecraft entity serialization before departure");
                body.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                helper.assertTrue(FrontierV3SceneDepartureObserver.observeLeave(level, runtime, body), "chunk departure must capture the final physical body");
                var ledger = FrontierV3AmbientCarrierLedger.get(level, initial.bootstrap().worldId());
                helper.assertValueEqual(ledger.departure(member.actorId()).orElseThrow().observed().health(),
                        io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(9), "departure must retain changed health, not the initial sample");
                helper.runAfterDelay(1L, () -> {
                    Mob returned = (Mob) EntityType.loadEntityRecursive(saved, level, entity -> entity);
                    try {
                        helper.assertTrue(returned != null && level.addFreshEntity(returned), "the actual saved body must return with its original declaration");
                        returned.setHealth(8.0F); // Deliberately divergent physical input, not an automatic repair.
                        FrontierV3SceneDepartureObserver.observeJoin(level, runtime, returned);
                        helper.assertTrue(FrontierV3SceneExecutor.recognizesDeclaration(runtime, returned), "divergence must not erase managed provenance");
                        helper.assertFalse(FrontierV3SceneExecutor.recognizes(runtime, returned), "family-level execution lookup must also reject divergent return");
                        helper.assertFalse(FrontierV3SceneExecutor.owned(returned, state(runtime), lease, member), "divergent HOT return cannot resume physical work");
                        returned.setHealth(0.0F); // Separate death-boundary input; no tick or canonical death is injected.
                        FrontierV3SceneDepartureObserver.observeJoin(level, runtime, returned);
                        helper.assertTrue(ledger.departure(member.actorId()).isPresent(), "a dead returned body must retain evidence for the death owner without throwing");
                        returned.setHealth(9.0F); // Separate exact-return input to the same boundary.
                        FrontierV3SceneDepartureObserver.observeJoin(level, runtime, returned);
                        helper.assertTrue(FrontierV3SceneExecutor.owned(returned, state(runtime), lease, member), "only the matching snapshot can resume the retained scene");
                        helper.assertTrue(ledger.departure(member.actorId()).isEmpty(), "exact return consumes its departure receipt");
                        returned.discard(); runtime.shutdown(); helper.succeed();
                    } catch (RuntimeException failure) {
                        if (returned != null) returned.discard(); runtime.shutdown(); throw failure;
                    }
                });
            } catch (RuntimeException failure) { body.discard(); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void cargoDepartureRequiresActualUnloadExactAttemptAndUnchangedContents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 0));
        var runtime = runtime("frontier:cargo-departure-capture");
        SceneLease lease = lease(runtime, origin, "lease:cargo-departure-capture");
        var state = state(runtime);
        prepareSupport(level, cargoPosition(origin, lease));
        helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materializeForFixture(level, state, lease),
                FrontierV3SceneExecutor.BodyMaterialization.COMPLETE, "exact cargo must materialize");
        helper.runAfterDelay(1L, () -> {
            var cart = (MinecartChest) level.getEntity(FrontierV3CargoCarrierExecutor.id(lease));
            try {
                helper.assertTrue(FrontierV3CargoCarrierExecutor.captureDeparture(state, cart, lease).isEmpty(), "periodic sample is not departure");
                var saved = new net.minecraft.nbt.CompoundTag();
                helper.assertTrue(cart.save(saved), "retain the actual cart for the separate natural-return boundary");
                cart.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                var receipt = FrontierV3CargoCarrierExecutor.captureDeparture(state, cart, lease).orElseThrow();
                helper.assertValueEqual(FrontierV3CargoDeparture.load(receipt.save()), receipt, "exact final inventory survives serialization");
                helper.assertTrue(FrontierV3CargoCarrierExecutor.currentDeparture(state, lease, receipt, level.registryAccess()),
                        "release can revalidate the exact inventory and attempt without a loaded cart");
                cart.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY, receipt.authorityEpoch() + 1);
                helper.assertTrue(FrontierV3CargoCarrierExecutor.captureDeparture(state, cart, lease).isEmpty(), "foreign attempt cannot supply release evidence");
                cart.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY, receipt.authorityEpoch());
                cart.getItem(0).shrink(1);
                helper.assertTrue(FrontierV3CargoCarrierExecutor.captureDeparture(state, cart, lease).isEmpty(), "changed contents cannot be accepted as intact canonical cargo");
                var ledger = FrontierV3CargoDepartureLedger.get(level, state.bootstrap().worldId());
                helper.assertTrue(ledger.record(receipt), "persist exact final departure");
                helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materializeForFixture(level, state, lease),
                        FrontierV3SceneExecutor.BodyMaterialization.DEFERRED, "departure evidence forbids replacement while original cart is stored");
                helper.runAfterDelay(1L, () -> {
                    var returned = (MinecartChest) EntityType.loadEntityRecursive(saved, level, entity -> entity);
                    try {
                        helper.assertTrue(returned != null && level.addFreshEntity(returned), "original cart returns from its own NBT");
                        returned.getItem(0).shrink(1);
                        FrontierV3CargoDepartureObserver.observeJoin(level, runtime, returned);
                        helper.assertTrue(ledger.observation(returned.getUUID()).isPresent(), "divergent return cannot erase retained evidence");
                        helper.assertFalse(FrontierV3CargoCarrierExecutor.intact(level, state, lease), "divergent return cannot regain HOT work permission");
                        returned.getItem(0).grow(1); // Separate matching input, not production reconciliation.
                        FrontierV3CargoDepartureObserver.observeJoin(level, runtime, returned);
                        helper.assertTrue(ledger.observation(returned.getUUID()).isEmpty(), "exact current return resolves only its own receipt");
                        helper.assertTrue(FrontierV3CargoCarrierExecutor.intact(level, state, lease), "same current cart resumes ordinary ownership");
                        returned.clearContent(); returned.discard(); runtime.shutdown(); helper.succeed();
                    } catch (RuntimeException failure) { if (returned != null) returned.discard(); runtime.shutdown(); throw failure; }
                });
            } catch (RuntimeException failure) { if (cart != null) cart.discard(); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void retiredCargoReturnRemovesOnlyTheExactObsoleteProjection(GameTestHelper helper) {
        lateCargoReturn(helper, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-departure", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void retiredCargoReturnPreservesAcceptedWorldCustody(GameTestHelper helper) {
        lateCargoReturn(helper, true);
    }

    private static void lateCargoReturn(GameTestHelper helper, boolean worldCustody) {
        // Isolated return/deletion boundary: registered domain commands establish the
        // retired precondition. This is not physical actor release or process-crash proof.
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 4));
        var runtime = runtime("frontier:late-cargo-" + worldCustody);
        var initial = state(runtime);
        var lease = FrontierV3GameTestSceneLeases.exact(initial, runtime.checkpointImage().orElseThrow(),
                initial.coldEngagementSceneCandidates().getFirst(), new SceneLeaseId("lease:late-cargo-" + worldCustody));
        FrontierV3CommandSubmission.submit(runtime, "late-cargo-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        prepareSupport(level, cargoPosition(origin, lease));
        var cart = addOwnedCarrier(helper, level, state(runtime), lease, cargoPosition(origin, lease));
        FrontierV3CommandSubmission.submit(runtime, "late-cargo-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(1L, () -> {
            try {
                var saved = new net.minecraft.nbt.CompoundTag(); helper.assertTrue(cart.save(saved), "serialize actual cart");
                cart.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                helper.assertTrue(FrontierV3CargoDepartureObserver.observeLeave(level, runtime, cart), "production departure observer retains final cart");
                var ledger = FrontierV3CargoDepartureLedger.get(level, initial.bootstrap().worldId());
                var receipt = ledger.observation(cart.getUUID()).orElseThrow();
                if (worldCustody) {
                    FrontierV3CommandSubmission.submit(runtime, "late-cargo-world", lease.id().value(),
                            new CargoCarrierReleased(lease.id(), receipt.cargoId(), cart.getUUID(), Optional.empty()));
                } else {
                    FrontierV3CommandSubmission.submit(runtime, "late-cargo-drain", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
                }
                FrontierV3CommandSubmission.submit(runtime, "late-cargo-release", lease.id().value(), new SceneLeaseReleased(lease.id(),
                        lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(), lease.memberPosition(member.actorId()))).toList()));
                var closed = state(runtime);
                helper.assertValueEqual(closed.sceneLeases().get(lease.id()).status(), SceneLeaseStatus.CLOSED, "registered commands must establish retired scene");
                helper.assertTrue(FrontierV3CargoDepartureObserver.retired(closed, lease, receipt), "exact retirement must be retained");
                helper.assertFalse(FrontierV3CargoDepartureObserver.retired(
                        closed.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(FencedRecoveryState.empty())), lease, receipt),
                        "missing retirement cannot authorize deletion");
                var compactedScenes = new java.util.LinkedHashMap<>(closed.sceneLeases());
                compactedScenes.remove(lease.id());
                var compacted = closed.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(compactedScenes)
                        .fencedRecovery(closed.fencedRecovery().compactTombstones(java.util.Set.of(
                                FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(receipt.cargoId())))));
                helper.assertTrue(FrontierV3CargoDepartureObserver.retirement(compacted, receipt) != null,
                        "pending cleanup survives both historical scene and tombstone compaction");
                helper.runAfterDelay(1L, () -> {
                    var returned = (MinecartChest) EntityType.loadEntityRecursive(saved, level, entity -> entity);
                    try {
                        helper.assertTrue(returned != null && level.addFreshEntity(returned), "late cart returns from actual NBT");
                        returned.getItem(0).shrink(1);
                        FrontierV3CargoDepartureObserver.observeJoin(level, compacted, returned);
                        helper.assertFalse(returned.isRemoved(), "foreign changed contents cannot be deleted");
                        if (worldCustody) {
                            helper.assertFalse(FrontierV3CargoCarrierExecutor.hasDeclaration(returned),
                                    "exact world-retained retirement clears old declaration despite changed player contents");
                            var releasedNbt = new net.minecraft.nbt.CompoundTag();
                            helper.assertTrue(returned.save(releasedNbt), "serialize actual retained cart after declaration retirement");
                            helper.assertTrue(FrontierV3CargoCleanupPersistence.savedWithoutSceneDeclaration(releasedNbt),
                                    "real Minecraft entity serialization supplies the retained-carrier postcondition");
                            helper.assertValueEqual(returned.getItem(0).getCount(), cart.getItem(0).getCount() - 1,
                                    "declaration retirement must not restore the old contents");
                        }
                        helper.assertTrue(ledger.observation(cart.getUUID()).isPresent(), "foreign payload preserves original witness");
                        returned.getItem(0).grow(1); // Separate exact input to the boundary.
                        helper.assertTrue(FrontierV3CargoDepartureObserver.retainCleanupWitness(level, compacted, receipt),
                                "cleanup must publish its durable witness before physical mutation");
                        helper.assertTrue(ledger.resolveExact(receipt), "simulate loss of the independent SavedData observation");
                        FrontierV3CargoDepartureObserver.observeJoin(level, compacted, returned);
                        helper.assertValueEqual(returned.isRemoved(), !worldCustody, "only obsolete projection is removed, never accepted world custody");
                        helper.assertValueEqual(returned.getItem(0).isEmpty(), !worldCustody, "discard cannot duplicate contents as item drops");
                        helper.assertTrue(ledger.observation(cart.getUUID()).isEmpty(),
                                "return must be handled from the durable archive, not an in-memory SavedData receipt");
                        helper.assertTrue(compacted.fencedRecovery().cargoRetirements().pending().containsKey(cart.getUUID()),
                                "physical discard must not acknowledge durable entity removal");
                        if (!returned.isRemoved()) { returned.clearContent(); returned.discard(); }
                        runtime.shutdown(); helper.succeed();
                    } catch (RuntimeException failure) { if (returned != null) returned.discard(); runtime.shutdown(); throw failure; }
                });
            } catch (RuntimeException failure) { cart.discard(); runtime.shutdown(); throw failure; }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void failedDurableCargoReleasePreservesPhysicalCarrier(GameTestHelper helper) {
        var store = new EphemeralStore();
        var runtime = FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(
                new WorldId("frontier:cargo-release-write-failure"), 91L), store, 20_000);
        var level = helper.getLevel();
        var initial = state(runtime);
        var lease = FrontierV3GameTestSceneLeases.exact(initial, runtime.checkpointImage().orElseThrow(),
                initial.coldEngagementSceneCandidates().getFirst(), new SceneLeaseId("lease:cargo-release-write-failure"));
        var position = helper.absolutePos(new BlockPos(1, 2, 1));
        prepareSupport(level, position.below());
        FrontierV3CommandSubmission.submit(runtime, "failed-release-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        var cart = addOwnedCarrier(helper, level, state(runtime), lease, position);
        FrontierV3CommandSubmission.submit(runtime, "failed-release-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        FrontierV3CargoCarrierPresentation.ensure(level, state(runtime), lease, cart);
        helper.runAfterDelay(2L, () -> {
            try {
                helper.assertTrue(FrontierV3CargoCarrierPresentation.attached(cart, lease), "fixture has a real attached cargo caption");
                var declaration = cart.getPersistentData().copy();
                var contents = FrontierV3CargoCarrierExecutor.observedInventory(cart);
                int committed = store.appended;
                store.rejectAppend = true;
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, cart, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.REJECTED, "failed durable write must reject interaction");
                helper.assertValueEqual(store.appended, committed, "no durable handoff committed");
                helper.assertValueEqual(runtime.status().kind(), FrontierV3RuntimeStatus.Kind.QUARANTINED,
                        "durable write failure is reported, not silently accepted");
                helper.assertValueEqual(cart.getPersistentData(), declaration, "failed handoff preserves complete declaration");
                helper.assertValueEqual(FrontierV3CargoCarrierExecutor.observedInventory(cart), contents, "failed handoff preserves exact contents");
                helper.assertTrue(FrontierV3CargoCarrierPresentation.attached(cart, lease), "failed handoff must not remove caption before commit");
                helper.succeed();
            } finally {
                FrontierV3CargoCarrierPresentation.discard(cart, lease);
                cart.discard(); runtime.shutdown();
            }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void committedExactCargoRepairsOnlyMissingProvenance(GameTestHelper helper) {
        // Provider-level recovery inputs, not a fabricated canonical handoff/restart receipt.
        var carrier = java.util.UUID.randomUUID();
        var other = java.util.UUID.randomUUID();
        var first = new io.farfrontier.palemirror.frontier.v3.model.ExactItemStack(new SubjectId("item:recovered-a"),
                new SubjectId("owner:recovered"), "minecraft:bread", 3,
                new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.WorldCarrier(carrier));
        var second = new io.farfrontier.palemirror.frontier.v3.model.ExactItemStack(new SubjectId("item:recovered-b"),
                first.economicOwnerId(), "minecraft:wheat", 2, first.custody());
        var items = java.util.Map.of(first.id(), first, second.id(), second);
        var cart = EntityType.CHEST_MINECART.create(helper.getLevel());
        helper.assertTrue(cart != null, "real cart inventory is available");
        cart.setUUID(carrier);
        cart.setItem(8, FrontierV3CargoHandoffExecutor.materializedStack(first));
        cart.setItem(19, new ItemStack(net.minecraft.world.item.Items.STONE, 7));
        helper.assertTrue(FrontierV3CargoCarrierProvenance.restore(items, carrier, cart), "missing tag repaired after committed custody");
        helper.assertValueEqual(FrontierV3CargoHandoffExecutor.worldCarrierId(cart.getItem(8)), Optional.of(carrier), "exact provenance restored at moved slot");
        helper.assertTrue(cart.getItem(0).isEmpty(), "absent second item is never recreated");
        helper.assertValueEqual(cart.getItem(19).getCount(), 7, "foreign player contents preserved");
        var repaired = FrontierV3CargoCarrierExecutor.observedInventory(cart);
        helper.assertTrue(FrontierV3CargoCarrierProvenance.restore(items, carrier, cart), "repair is idempotent");
        helper.assertValueEqual(FrontierV3CargoCarrierExecutor.observedInventory(cart), repaired, "second repair changes nothing");
        cart.setItem(8, FrontierV3CargoHandoffExecutor.materializedStack(first));
        cart.setItem(20, FrontierV3CargoHandoffExecutor.materializedStack(second));
        FrontierV3CargoHandoffExecutor.bindWorldCarrier(cart.getItem(20), other);
        var foreign = FrontierV3CargoCarrierExecutor.observedInventory(cart);
        helper.assertFalse(FrontierV3CargoCarrierProvenance.restore(items, carrier, cart), "foreign declared carrier cannot be restamped");
        helper.assertValueEqual(FrontierV3CargoCarrierExecutor.observedInventory(cart), foreign, "late conflict leaves even earlier slot untouched");
        cart.setItem(20, FrontierV3CargoHandoffExecutor.materializedStack(first));
        helper.assertFalse(FrontierV3CargoCarrierProvenance.restore(items, carrier, cart), "duplicate exact identity is not repaired");
        helper.assertTrue(FrontierV3CargoHandoffExecutor.worldCarrierId(cart.getItem(8)).isEmpty(), "duplicate rejection does not partly repair");
        cart.setItem(20, ItemStack.EMPTY);
        cart.getItem(8).setCount(1);
        helper.assertFalse(FrontierV3CargoCarrierProvenance.restore(items, carrier, cart), "changed exact quantity remains unresolved");
        helper.assertValueEqual(cart.getItem(8).getCount(), 1, "reconciliation never restores prior quantity");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void retainedCartCleanupWaitsForSuccessfulSave(GameTestHelper helper) {
        footprintAcknowledgement(helper, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void destroyedReleasedCartCleanupWaitsForSuccessfulSave(GameTestHelper helper) {
        footprintAcknowledgement(helper, true);
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void returnedCartAfterUnsavedRemovalCanRetireCleanly(GameTestHelper helper) {
        footprintAcknowledgement(helper, false, true);
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void failedRemovalObservationRetriesBeforeEntityWrite(GameTestHelper helper) {
        footprintAcknowledgement(helper, true, false, true);
    }

    private static void footprintAcknowledgement(GameTestHelper helper, boolean destroy) {
        footprintAcknowledgement(helper, destroy, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void storedAbsenceReadBeforeRetirementStillClosesTheLaterObligation(GameTestHelper helper) {
        earlyCargoRead(helper, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void storedAbsenceCompletingAfterSavePassResumesWithoutAnotherSave(GameTestHelper helper) {
        earlyCargoRead(helper, true);
    }

    private static void earlyCargoRead(GameTestHelper helper, boolean delayed) {
        earlyCargoRead(helper, delayed, false);
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void newReadDuringSyncRebuildsCleanupProofWithoutAnotherSave(GameTestHelper helper) {
        earlyCargoRead(helper, false, true);
    }

    private static void earlyCargoRead(GameTestHelper helper, boolean delayed, boolean superseded) {
        var level = helper.getLevel(); var runtime = runtime("frontier:early-cargo-read-" + delayed + "-" + superseded);
        var initial = state(runtime);
        var lease = FrontierV3GameTestSceneLeases.exact(initial, runtime.checkpointImage().orElseThrow(),
                initial.coldEngagementSceneCandidates().getFirst(), new SceneLeaseId("lease:early-cargo-read-" + delayed + "-" + superseded));
        FrontierV3CommandSubmission.submit(runtime, "early-read-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        var position = helper.absolutePos(new BlockPos(1, 2, 1)); prepareSupport(level, position.below());
        var cart = addOwnedCarrier(helper, level, state(runtime), lease, position);
        helper.assertTrue(FrontierV3CargoFootprintObserver.prepareBirth(level, lease,
                FrontierV3CargoCarrierAuthority.currentEpoch(state(runtime).fencedRecovery(), lease).orElseThrow(), cart), "durable birth");
        FrontierV3CommandSubmission.submit(runtime, "early-read-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(2L, () -> {
            try {
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, cart, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.RELEASED, "ordinary player handoff");
                var chunk = cart.chunkPosition(); cart.discard();
                FrontierV3CargoFootprintObserver.observeRemoval(level, initial.bootstrap().worldId(), cart, null);
                helper.assertTrue(state(runtime).fencedRecovery().cargoRetirements().pending().isEmpty(), "read precedes terminal obligation");
                // Controlled provider input: actual read callback returns an empty column.
                // No second read/write is supplied after canonical retirement.
                var source = new java.util.concurrent.CompletableFuture<Optional<net.minecraft.nbt.CompoundTag>>();
                FrontierV3CargoCleanupPersistence.observeRead(level, runtime, chunk, source);
                if (!delayed) source.complete(Optional.empty());
                FrontierV3CommandSubmission.submit(runtime, "early-read-close", lease.id().value(), new SceneLeaseReleased(lease.id(),
                        lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(), lease.memberPosition(member.actorId()))).toList()));
                helper.assertTrue(state(runtime).fencedRecovery().cargoRetirements().pending().containsKey(cart.getUUID()), "retirement created after read");
                var sync = new java.util.concurrent.CompletableFuture<Void>();
                var syncCalls = new java.util.concurrent.atomic.AtomicInteger();
                FrontierV3CargoCleanupPersistence.completeSavePass(level, runtime, true, () ->
                        superseded && syncCalls.getAndIncrement() == 0 ? sync : java.util.concurrent.CompletableFuture.completedFuture(null));
                if (delayed) source.complete(Optional.empty());
                if (superseded) {
                    FrontierV3CargoCleanupPersistence.observeRead(level, runtime, chunk,
                            java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()));
                    sync.complete(null);
                }
                helper.runAfterDelay(1L, () -> {
                    try {
                        helper.assertFalse(state(runtime).fencedRecovery().cargoRetirements().pending().containsKey(cart.getUUID()),
                                "earlier stored absence plus successful sync satisfies later retirement");
                        helper.succeed();
                    } finally { FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown(); }
                });
            } catch (RuntimeException failure) {
                if (!cart.isRemoved()) cart.discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown(); throw failure;
            }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-cargo-interaction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void successfulSaveWithoutBirthHistoryCannotAcknowledgeRetainedCart(GameTestHelper helper) {
        var level = helper.getLevel(); var runtime = runtime("frontier:missing-birth-ack");
        var initial = state(runtime);
        var lease = FrontierV3GameTestSceneLeases.exact(initial, runtime.checkpointImage().orElseThrow(),
                initial.coldEngagementSceneCandidates().getFirst(), new SceneLeaseId("lease:missing-birth-ack"));
        FrontierV3CommandSubmission.submit(runtime, "missing-birth-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        var position = helper.absolutePos(new BlockPos(1, 2, 1)); prepareSupport(level, position.below());
        var cart = addOwnedCarrier(helper, level, state(runtime), lease, position);
        helper.assertTrue(FrontierV3CargoFootprintObserver.prepareBirth(level, lease,
                FrontierV3CargoCarrierAuthority.currentEpoch(state(runtime).fencedRecovery(), lease).orElseThrow(), cart), "birth is initially durable");
        FrontierV3CommandSubmission.submit(runtime, "missing-birth-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(2L, () -> {
            try {
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, cart, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.RELEASED, "ordinary player handoff");
                FrontierV3CommandSubmission.submit(runtime, "missing-birth-close", lease.id().value(), new SceneLeaseReleased(lease.id(),
                        lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(), lease.memberPosition(member.actorId()))).toList()));
                var closed = state(runtime); var retirement = closed.fencedRecovery().cargoRetirements().pending().get(cart.getUUID());
                FrontierV3CargoCarrierExecutor.cleanRetired(level, closed, retirement);
                var entity = new net.minecraft.nbt.CompoundTag(); helper.assertTrue(cart.save(entity), "serialize real retained cart");
                helper.assertTrue(FrontierV3CargoCleanupPersistence.savedWithoutSceneDeclaration(entity), "cart is clean");
                var chunk = cart.chunkPosition(); var entities = new net.minecraft.nbt.ListTag(); entities.add(entity);
                var stored = new net.minecraft.nbt.CompoundTag(); stored.put("Entities", entities);
                stored.putIntArray("Position", new int[]{chunk.x, chunk.z});
                // Inject missing historical evidence, not a domain outcome. A successful
                // current-column save cannot establish anything about unknown old columns.
                var archive = FrontierV3CargoFootprintArchive.at(level, closed.bootstrap().worldId());
                for (var footprint : archive.inventory()) archive.forgetExact(footprint);
                FrontierV3CargoCleanupPersistence.observeWrite(level, runtime, chunk, stored, java.util.concurrent.CompletableFuture.completedFuture(null));
                FrontierV3CargoCleanupPersistence.completeSavePass(level, runtime, true, () -> java.util.concurrent.CompletableFuture.completedFuture(null));
                helper.runAfterDelay(1L, () -> {
                    try {
                        helper.assertValueEqual(state(runtime).fencedRecovery().cargoRetirements().pending().get(cart.getUUID()),
                                retirement, "missing history retains the exact terminal obligation despite successful save");
                        helper.assertValueEqual(state(runtime).inventory(), closed.inventory(), "no inventory reconstruction or transfer");
                        helper.assertFalse(cart.isRemoved(), "player cart is preserved");
                        helper.succeed();
                    } finally { cart.discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown(); }
                });
            } catch (java.io.IOException | RuntimeException failure) {
                cart.discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown();
                throw new IllegalStateException(failure);
            }
        });
    }

    private static void footprintAcknowledgement(GameTestHelper helper, boolean destroy, boolean restore) {
        footprintAcknowledgement(helper, destroy, restore, false);
    }

    private static void footprintAcknowledgement(GameTestHelper helper, boolean destroy, boolean restore, boolean retryRemoval) {
        // Registered canonical commands + real entity/archive, with controlled provider IO
        // futures. This tests composition/failed-write retry, NOT a real region-file crash.
        var level = helper.getLevel(); var runtime = runtime("frontier:footprint-ack-" + destroy + "-" + restore + "-" + retryRemoval);
        var initial = state(runtime);
        var lease = FrontierV3GameTestSceneLeases.exact(initial, runtime.checkpointImage().orElseThrow(),
                initial.coldEngagementSceneCandidates().getFirst(), new SceneLeaseId("lease:footprint-ack-" + destroy + "-" + restore + "-" + retryRemoval));
        FrontierV3CommandSubmission.submit(runtime, "footprint-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        var position = helper.absolutePos(new BlockPos(1, 2, 1)); prepareSupport(level, position.below());
        var original = addOwnedCarrier(helper, level, state(runtime), lease, position);
        var carrier = new java.util.concurrent.atomic.AtomicReference<MinecartChest>(original);
        helper.assertTrue(FrontierV3CargoFootprintObserver.prepareBirth(level, lease,
                FrontierV3CargoCarrierAuthority.currentEpoch(state(runtime).fencedRecovery(), lease).orElseThrow(), original), "birth must be durable");
        FrontierV3CommandSubmission.submit(runtime, "footprint-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        Runnable exercise = () -> {
            var cart = carrier.get();
            try {
                if (!restore) {
                    FrontierV3CommandSubmission.submit(runtime, "footprint-staged-drain", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
                    helper.assertTrue(FrontierV3CargoDepartureObserver.prepareRelease(level, state(runtime), state(runtime).sceneLeases().get(lease.id())),
                            "prepare original REMOVE witness before intervening player handoff");
                    // Component input: closure did not commit, then recovery returns this
                    // same attempt to HOT. Player interaction itself only admits HOT custody.
                    FrontierV3CommandSubmission.submit(runtime, "footprint-staged-unknown", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
                    FrontierV3CommandSubmission.submit(runtime, "footprint-staged-return", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
                }
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, cart, Optional.empty()),
                        restore ? FrontierV3ServerLifecycle.CargoCarrierInteraction.NOT_MANAGED
                                : FrontierV3ServerLifecycle.CargoCarrierInteraction.RELEASED, "handoff commits exactly once");
                FrontierV3CommandSubmission.submit(runtime, "footprint-close", lease.id().value(), new SceneLeaseReleased(lease.id(),
                        lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(), lease.memberPosition(member.actorId()))).toList()));
                var closed = state(runtime); var retirement = closed.fencedRecovery().cargoRetirements().pending().get(cart.getUUID());
                helper.assertTrue(retirement != null, "closed scene retains exact cleanup obligation");
                helper.assertTrue(FrontierV3SceneDiagnosticJson.render(FrontierSceneBehaviors.logistics(lease).operationId().value(), runtime.checkpointImage().orElseThrow(),
                        closed, Optional.empty()).contains("\"cargoCleanupPending\":true"), "diagnostic exposes pending cleanup, not just CLOSED history");
                var inventory = closed.inventory();
                var history = new java.util.LinkedHashMap<>(closed.sceneLeases()); history.remove(lease.id());
                var withoutHistory = closed.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(history));
                FrontierV3CargoCarrierExecutor.cleanRetired(level, withoutHistory, retirement);
                helper.assertFalse(cart.getPersistentData().contains(FrontierV3CargoFootprintObserver.KEY), "terminal owner strips cleanup identity before clean save");
                var chunk = cart.chunkPosition();
                net.minecraft.nbt.CompoundTag stored;
                if (destroy) {
                    var removalArchive = FrontierV3CargoFootprintArchive.at(level, closed.bootstrap().worldId());
                    var exactHistory = removalArchive.read(cart.getUUID(), retirement.authorization().retiredEpoch()).orElseThrow();
                    if (retryRemoval) removalArchive.forgetExact(exactHistory);
                    cart.discard();
                    FrontierV3CargoFootprintObserver.observeRemoval(level, closed.bootstrap().worldId(), cart, retirement);
                    if (retryRemoval) {
                        boolean fenced = false;
                        try { FrontierV3CargoFootprintObserver.beforeWrite(level, chunk, null); }
                        catch (java.io.IOException expected) { fenced = true; }
                        helper.assertTrue(fenced, "missing removal evidence blocks even an empty entity-column write");
                        removalArchive.retain(exactHistory); // Restore original fixture bytes, never inferred history.
                        FrontierV3CargoFootprintObserver.beforeWrite(level, chunk, null);
                        helper.assertTrue(removalArchive.read(cart.getUUID(), retirement.authorization().retiredEpoch()).orElseThrow().removalChunk().isPresent(),
                                "retained event retries before the deletion write is permitted");
                    }
                    stored = null;
                } else {
                    var entity = new net.minecraft.nbt.CompoundTag(); helper.assertTrue(cart.save(entity), "serialize retained cart");
                    helper.assertTrue(FrontierV3CargoCleanupPersistence.savedWithoutSceneDeclaration(entity), "clean entity has no obsolete metadata");
                    var entities = new net.minecraft.nbt.ListTag(); entities.add(entity);
                    stored = new net.minecraft.nbt.CompoundTag(); stored.put("Entities", entities); stored.putIntArray("Position", new int[]{chunk.x, chunk.z});
                }
                var failed = new java.util.concurrent.CompletableFuture<Void>();
                FrontierV3CargoCleanupPersistence.observeWrite(level, runtime, chunk, stored, failed);
                FrontierV3CargoCleanupPersistence.completeSavePass(level, runtime, true, () -> java.util.concurrent.CompletableFuture.completedFuture(null));
                helper.assertTrue(state(runtime).fencedRecovery().cargoRetirements().pending().containsKey(cart.getUUID()), "pending write cannot acknowledge cleanup");
                failed.completeExceptionally(new IllegalStateException("injected entity write failure"));
                helper.assertTrue(state(runtime).fencedRecovery().cargoRetirements().pending().containsKey(cart.getUUID()), "failed write cannot acknowledge cleanup");
                FrontierV3CargoCleanupPersistence.observeWrite(level, runtime, chunk, stored, java.util.concurrent.CompletableFuture.completedFuture(null));
                FrontierV3CargoCleanupPersistence.completeSavePass(level, runtime, true, () -> java.util.concurrent.CompletableFuture.completedFuture(null));
                helper.runAfterDelay(1L, () -> {
                    try {
                        helper.assertFalse(state(runtime).fencedRecovery().cargoRetirements().pending().containsKey(cart.getUUID()), "successful exact retry acknowledges cleanup");
                        helper.assertTrue(FrontierV3SceneDiagnosticJson.render(FrontierSceneBehaviors.logistics(lease).operationId().value(), runtime.checkpointImage().orElseThrow(),
                                state(runtime), Optional.empty()).contains("\"cargoCleanupPending\":false"), "diagnostic reflects acknowledged cleanup");
                        helper.assertValueEqual(state(runtime).inventory(), inventory, "cleanup does not invent item transfers or losses");
                        var archive = FrontierV3CargoFootprintArchive.at(level, closed.bootstrap().worldId());
                        helper.assertTrue(archive.inventory().isEmpty() && archive.savedInventory().isEmpty(), "acknowledged metadata is compacted");
                        // A second ordinary pass compacts the queued, now obsolete pre-handoff witness.
                        // Parallel GameTests share a ServerLevel but not a canonical runtime:
                        // announce this pass's provider write so its exact runtime index is current.
                        FrontierV3CargoCleanupPersistence.observeWrite(level, runtime, chunk, stored, java.util.concurrent.CompletableFuture.completedFuture(null));
                        FrontierV3CargoCleanupPersistence.completeSavePass(level, runtime, true, () -> java.util.concurrent.CompletableFuture.completedFuture(null));
                        var cleanup = FrontierV3CargoCleanupArchive.at(level, closed.bootstrap().worldId());
                        helper.assertTrue(cleanup.observation(cart.getUUID()).isEmpty() && cleanup.savedWitnesses().isEmpty(),
                                "RETAIN acknowledgement also compacts rejected REMOVE preparation without touching inventory");
                        helper.succeed();
                    } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                    finally { if (!cart.isRemoved()) cart.discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown(); }
                });
            } catch (java.io.IOException | RuntimeException failure) {
                if (!cart.isRemoved()) cart.discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown();
                throw new IllegalStateException(failure);
            }
        };
        if (!restore) helper.runAfterDelay(2L, exercise);
        else helper.runAfterDelay(2L, () -> {
            try {
                helper.assertValueEqual(FrontierV3ServerLifecycle.releaseCargoCarrier(level, runtime, original, Optional.empty()),
                        FrontierV3ServerLifecycle.CargoCarrierInteraction.RELEASED, "handoff precedes injected unsaved removal");
                var saved = new net.minecraft.nbt.CompoundTag(); helper.assertTrue(original.save(saved), "retain actual pre-removal entity snapshot");
                original.discard();
                FrontierV3CargoFootprintObserver.observeRemoval(level, initial.bootstrap().worldId(), original, null);
                helper.runAfterDelay(1L, () -> {
                    try {
                        var returned = (MinecartChest) EntityType.loadEntityRecursive(saved, level, entity -> entity);
                        helper.assertTrue(returned != null && level.addFreshEntity(returned), "restore actual prior entity NBT, not inventory reconstruction");
                        carrier.set(returned); exercise.run();
                    } catch (RuntimeException failure) {
                        carrier.get().discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown(); throw failure;
                    }
                });
            } catch (RuntimeException failure) {
                original.discard(); FrontierV3CargoCleanupPersistence.forget(runtime); runtime.shutdown(); throw failure;
            }
        });
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime(String world) {
        return FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(new WorldId(world), 91L), new EphemeralStore(), 20_000);
    }
    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, BlockPos origin, String id) {
        FrontierWorldState state = state(runtime);
        SceneEngagementCandidate candidate = state.coldEngagementSceneCandidates().getFirst();
        SceneLease canonical = FrontierV3GameTestSceneLeases.exact(state, runtime.checkpointImage().orElseThrow(), candidate, new SceneLeaseId(id));
        FrontierV3CommandSubmission.submit(runtime, "cargo-fixture-prepare", id, new SceneLeasePrepared(canonical));
        List<SceneMember> members = canonical.members();
        BlockPosition handoff = new BlockPosition(origin.getX(), origin.getY(), origin.getZ());
        BlockPos cargo = origin.offset((members.size() % 2) * 2 + 1, 0, (members.size() / 2) * 2);
        // Only the read-only physical projection moves into the GameTest cell. Canonical
        // preparation and recovery authority use the real retained scene positions.
        return SceneLease.atExactPositions(new SceneLeaseId(id), state.bootstrap().worldId(), candidate.operationId(), candidate.cargoId(), handoff,
                new BlockPosition(cargo.getX(), cargo.getY(), cargo.getZ()), canonical.handoffInstant(), canonical.revision(), SceneLeaseStatus.PREPARED, Optional.of(candidate.engagementId()), members,
                members.stream().collect(java.util.stream.Collectors.toMap(SceneMember::actorId,
                        ignored -> BodyPosition.aboveSupportCell(handoff), (left, right) -> left, java.util.LinkedHashMap::new)));
    }
    private static void prepareFloor(ServerLevel level, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3); level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
    }
    private static void prepareSupport(ServerLevel level, BlockPos support) {
        level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3); level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
    }
    private static BlockPos cargoPosition(BlockPos anchor, SceneLease lease) {
        int ordinal = lease.members().size(); return anchor.offset((ordinal % 2) * 2 + 1, 0, (ordinal / 2) * 2);
    }
    private static void discard(ServerLevel level, SceneLease lease) {
        Entity carrier = level.getEntity(FrontierV3CargoCarrierExecutor.id(lease)); if (carrier != null) carrier.discard();
    }
    private static Mob addOwnedBody(GameTestHelper helper, ServerLevel level, FrontierWorldState state, SceneLease lease, SceneMember member, BlockPos position) {
        boolean bioform = state.bootstrap().hive().bioforms().stream().anyMatch(value -> value.id().equals(member.actorId()))
                || state.hiveColony().spawnedBioforms().containsKey(member.actorId());
        Mob body = bioform ? EntityType.ZOMBIE.create(level) : EntityType.VILLAGER.create(level);
        helper.assertTrue(body != null, "the exact recovery body fixture must be constructible");
        body.setUUID(member.entityId()); body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D); body.setNoAi(true); body.setPersistenceRequired();
        body.getPersistentData().putString(FrontierV3SceneExecutor.LEASE_KEY, lease.id().value());
        body.getPersistentData().putString(FrontierV3SceneExecutor.ACTOR_KEY, member.actorId().value());
        body.getPersistentData().putLong(FrontierV3SceneExecutor.REVISION_KEY, lease.revision());
        body.getPersistentData().putLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY, 1L);
        FrontierV3ActorCarrierComposition.stamp(body, FrontierV3ActorCarrierComposition.fromCanonical(state, member.actorId(),
                bioform ? FrontierV3ActorCarrierComposition.ActorKind.BIOFORM : FrontierV3ActorCarrierComposition.ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), 1L));
        helper.assertTrue(level.addFreshEntity(body), "the exact recovery body fixture must enter the loaded world");
        return body;
    }
    private static MinecartChest addOwnedCarrier(GameTestHelper helper, ServerLevel level, FrontierWorldState state, SceneLease lease, BlockPos position) {
        MinecartChest cart = EntityType.CHEST_MINECART.create(level);
        helper.assertTrue(cart != null, "the cargo carrier fixture must be constructible");
        cart.setUUID(FrontierV3CargoCarrierExecutor.id(lease)); cart.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.LEASE_KEY, lease.id().value());
        cart.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.REVISION_KEY, lease.revision());
        cart.getPersistentData().putLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY,
                FrontierV3CargoCarrierAuthority.currentEpoch(state.fencedRecovery(), lease).orElseThrow());
        cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.CARGO_KEY, FrontierSceneBehaviors.logistics(lease).cargoId().value());
        CustodyAccount account = fungibleCargoAccount(state, FrontierSceneBehaviors.logistics(lease).cargoId());
        String kind = account.lotQuantities().keySet().stream().map(state.inventory().fungibleResources().lots()::get)
                .map(lot -> lot.itemKind()).distinct().reduce((left, right) -> "").orElse("");
        int quantity = account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(kind)).orElse(null);
        helper.assertTrue(item != null && quantity > 0 && quantity <= 64, "the cargo fixture requires one bounded canonical fungible stack");
        cart.setItem(0, new ItemStack(item, quantity));
        helper.assertTrue(level.addFreshEntity(cart), "the canonical cargo carrier fixture must enter the loaded world");
        return cart;
    }

    private static CustodyAccount fungibleCargoAccount(FrontierWorldState state, SubjectId cargoId) {
        return state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Cargo custody && custody.cargoId().equals(cargoId))
                .findFirst().orElseThrow();
    }
    private static CustodyAccount fungibleWorldCarrierAccount(FrontierWorldState state, java.util.UUID carrierId) {
        return state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody().equals(new ResourceCustody.WorldCarrier(carrierId))).findFirst().orElseThrow();
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
    }
    private static final class EphemeralStore implements FrontierStore {
        private boolean rejectAppend;
        private int appended;
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            if (rejectAppend) throw new IllegalStateException("injected pre-commit cargo release write failure");
            appended++;
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) {
            throw new UnsupportedOperationException("GameTest does not compact");
        }
    }
}
