package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
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
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(32, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-test");
        FrontierWorldState state = state(runtime); SceneLease lease = lease(state, origin, "lease:frontier-v3-cargo-test");
        prepareFloor(level, cargoPosition(origin, lease));
        var expected = state.inventory().cargo().get(lease.cargoId()).itemIds().stream().map(state.inventory().items()::get)
                .sorted(java.util.Comparator.comparing(value -> value.id())).toList();
        helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materialize(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a prepared loaded scene must create one exact cargo carrier");
        helper.runAfterDelay(1L, () -> {
            try {
                Entity carrier = level.getEntity(FrontierV3CargoCarrierExecutor.id(lease));
                helper.assertTrue(carrier instanceof MinecartChest, "the graybox carrier must be a visible chest minecart");
                helper.assertTrue(FrontierV3CargoCarrierExecutor.owned(carrier, lease, expected), "the carrier must hold only canonical tagged cargo");
                helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materialize(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                        "recovery must reuse the exact carrier rather than duplicate cargo");
                helper.assertValueEqual(level.getEntitiesOfClass(MinecartChest.class, carrier.getBoundingBox().inflate(8.0D)).size(), 1,
                        "one HOT lease must retain exactly one nearby cargo carrier");
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
        ServerLevel level = helper.getLevel();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = runtime("frontier:scene-cargo-recovery-test");
        FrontierWorldState state = state(runtime); SceneEngagementCandidate candidate = state.coldEngagementSceneCandidates().getFirst();
        var checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = new SceneLease(new SceneLeaseId("lease:frontier-v3-cargo-recovery-test"), state.bootstrap().worldId(), candidate.operationId(), candidate.cargoId(),
                candidate.handoffPosition(), checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, Optional.of(candidate.engagementId()),
                candidate.actorIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor))).toList());
        BlockPos handoff = new BlockPos(candidate.handoffPosition().x(), candidate.handoffPosition().y(), candidate.handoffPosition().z());
        level.getChunkAt(handoff);
        for (int index = 0; index < lease.members().size(); index++) {
            BlockPos position = handoff.offset(index & 1, 0, index / 2); prepareFloor(level, position);
            addOwnedBody(helper, level, lease, lease.members().get(index), position);
        }
        prepareFloor(level, cargoPosition(handoff, lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-recovery-prepare", lease.id().value(), new SceneLeasePrepared(lease));
        helper.assertValueEqual(FrontierV3CargoCarrierExecutor.materialize(level, state(runtime), lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a recovery fixture requires its exact cargo carrier");
        FrontierV3CommandSubmission.submit(runtime, "scene-cargo-recovery-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        helper.runAfterDelay(1L, () -> {
            try {
                helper.assertValueEqual(FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(runtime), 1,
                        "restart must first retain the active scene as UNKNOWN");
                Entity carrier = level.getEntity(FrontierV3CargoCarrierExecutor.id(lease));
                helper.assertTrue(carrier != null, "fixture must retain the exact cargo carrier before its loss");
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

    private static FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime(String world) {
        return FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.developmentHotSceneStrikeConfiguration(new WorldId(world), 91L), new EphemeralStore(), 20_000);
    }
    private static SceneLease lease(FrontierWorldState state, BlockPos origin, String id) {
        SceneEngagementCandidate candidate = state.coldEngagementSceneCandidates().getFirst();
        return new SceneLease(new SceneLeaseId(id), state.bootstrap().worldId(), candidate.operationId(), candidate.cargoId(),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED,
                candidate.actorIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor))).toList());
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
