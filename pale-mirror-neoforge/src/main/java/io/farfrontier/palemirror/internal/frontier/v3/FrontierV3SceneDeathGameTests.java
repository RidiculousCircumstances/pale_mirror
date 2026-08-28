package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** Materialized observer coverage for several same-effect HOT scene deaths. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3SceneDeathGameTests {
    private FrontierV3SceneDeathGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-deaths", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void multipleExactDeathsRemainObservedAfterTheSceneBeginsDraining(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(0, 8, 0));
        WorldId world = new WorldId("frontier:scene-death-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.developmentHotSceneStrikeConfiguration(world, 91L), new EphemeralStore(), 20_000);
        SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst();
        SceneLeaseId leaseId = new SceneLeaseId("lease:scene-death-game-test");
        var checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = new SceneLease(leaseId, world, candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(), checkpoint.instant(), checkpoint.revision().value(),
                SceneLeaseStatus.PREPARED, Optional.of(candidate.engagementId()), candidate.actorIds().stream()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))).toList());
        FrontierV3CommandSubmission.submit(runtime, "scene-deaths-prepare", leaseId.value(), new SceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-deaths-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        for (int index = 0; index < lease.members().size(); index++) addOwnedBody(helper, level, lease, lease.members().get(index), origin.offset(index & 1, 0, index / 2));

        helper.runAfterDelay(1L, () -> {
            List<Entity> bodies = lease.members().stream().map(member -> level.getEntity(member.entityId())).toList();
            try {
                SceneMember first = lease.members().getFirst(), second = lease.members().get(1);
                Entity firstBody = bodies.getFirst(), secondBody = bodies.get(1);
                helper.assertTrue(firstBody != null && secondBody != null, "the exact scene victims must remain indexed before their observation");
                bodies.forEach(body -> {
                    helper.assertTrue(body != null, "the exact scene body must remain loaded until its death observation");
                    body.setPos(candidate.handoffPosition().x() + 0.5D, candidate.handoffPosition().y(), candidate.handoffPosition().z() + 0.5D);
                });
                helper.assertTrue(FrontierV3SceneExecutor.observeDeath(runtime, firstBody, null),
                        "first exact death must drain the HOT scene through the actual body observer");
                helper.assertTrue(FrontierV3SceneExecutor.observeDeath(runtime, secondBody, null),
                        "a later same-effect death must remain accepted after the lease is DRAINING");
                FrontierWorldState afterDeaths = state(runtime);
                helper.assertValueEqual(afterDeaths.actorLocations().get(first.actorId()).condition().status(), ActorLifeStatus.DEAD, "first death must be canonical");
                helper.assertValueEqual(afterDeaths.actorLocations().get(second.actorId()).condition().status(), ActorLifeStatus.DEAD, "second death must be canonical");
                helper.assertValueEqual(afterDeaths.sceneLeases().get(leaseId).status(), SceneLeaseStatus.DRAINING, "no invalid second transition may occur");
                List<SceneMemberPosition> survivors = lease.members().stream().filter(member -> !member.equals(first) && !member.equals(second))
                        .map(member -> new SceneMemberPosition(member.actorId(), candidate.handoffPosition())).toList();
                FrontierV3CommandSubmission.submit(runtime, "scene-deaths-release", leaseId.value(), new SceneLeaseReleased(leaseId, survivors));
                helper.assertValueEqual(state(runtime).sceneLeases().get(leaseId).status(), SceneLeaseStatus.CLOSED,
                        "release must capture only canonical survivors without quarantining the runtime");
                cleanup(bodies); runtime.shutdown(); helper.succeed();
            } catch (RuntimeException failure) {
                cleanup(bodies); runtime.shutdown(); throw failure;
            }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-deaths", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void releaseRefreshesAfterARealDeathChangesTheCanonicalScene(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldId world = new WorldId("frontier:scene-death-release-refresh-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.developmentHotSceneStrikeConfiguration(world, 91L), new EphemeralStore(), 20_000);
        SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst();
        SceneLeaseId leaseId = new SceneLeaseId("lease:scene-death-release-refresh-game-test");
        var checkpoint = runtime.checkpointImage().orElseThrow();
        SceneLease lease = new SceneLease(leaseId, world, candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(), checkpoint.instant(), checkpoint.revision().value(),
                SceneLeaseStatus.PREPARED, Optional.of(candidate.engagementId()), candidate.actorIds().stream()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))).toList());
        FrontierV3CommandSubmission.submit(runtime, "scene-death-release-refresh-prepare", leaseId.value(), new SceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-death-release-refresh-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        BlockPos handoff = new BlockPos(candidate.handoffPosition().x(), candidate.handoffPosition().y(), candidate.handoffPosition().z());
        level.getChunkAt(handoff);
        for (int index = 0; index < lease.members().size(); index++) {
            addOwnedBody(helper, level, lease, lease.members().get(index), handoff.offset(index & 1, 0, index / 2));
        }

        helper.runAfterDelay(1L, () -> {
            List<Entity> bodies = lease.members().stream().map(member -> level.getEntity(member.entityId())).toList();
            try {
                bodies.forEach(body -> helper.assertTrue(body != null, "the exact scene body must remain indexed before release refresh"));
                FrontierWorldState stale = state(runtime);
                Entity victim = bodies.getFirst();
                helper.assertTrue(victim != null && FrontierV3SceneExecutor.observeDeath(runtime, victim, null),
                        "a real exact death must durably drain its HOT scene");
                victim.discard();
                lease.members().stream().skip(1).forEach(member -> helper.assertTrue(level.getEntity(member.entityId()) != null,
                        "the surviving exact HOT body must remain indexed before executor release: " + member.actorId().value()));
                SceneMember survivor = lease.members().get(1);
                Entity survivorBody = level.getEntity(survivor.entityId());
                helper.assertTrue(survivorBody != null, "one surviving exact HOT body must remain available for its position capture");
                BlockPosition survivorPosition = new BlockPosition(survivorBody.getBlockX(), survivorBody.getBlockY(), survivorBody.getBlockZ());
                FrontierV3SceneExecutor.release(level, runtime, stale.sceneLeases().get(leaseId));
                helper.assertValueEqual(state(runtime).sceneLeases().get(leaseId).status(), SceneLeaseStatus.CLOSED,
                        "release must refresh canonical death evidence and capture only survivors");
                helper.assertValueEqual(state(runtime).actorLocations().get(survivor.actorId()).position(), survivorPosition,
                        "COLD continuation must retain the exact surviving HOT position rather than its old approach endpoint");
                cleanup(bodies); runtime.shutdown(); helper.succeed();
            } catch (RuntimeException failure) {
                cleanup(bodies); runtime.shutdown(); throw failure;
            }
        });
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
    }

    private static void addOwnedBody(GameTestHelper helper, ServerLevel level, SceneLease lease, SceneMember member, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        Mob body = member.actorId().value().startsWith("bioform:") ? EntityType.ZOMBIE.create(level) : EntityType.VILLAGER.create(level);
        helper.assertTrue(body != null, "the exact HOT body fixture must be constructible");
        body.setUUID(member.entityId()); body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D); body.setNoAi(true);
        body.getPersistentData().putString(FrontierV3SceneExecutor.LEASE_KEY, lease.id().value());
        body.getPersistentData().putString(FrontierV3SceneExecutor.ACTOR_KEY, member.actorId().value());
        body.getPersistentData().putLong(FrontierV3SceneExecutor.REVISION_KEY, lease.revision());
        helper.assertTrue(level.addFreshEntity(body), "the exact HOT body fixture must enter the loaded world");
    }

    private static void cleanup(List<Entity> bodies) {
        bodies.forEach(body -> { if (body != null) body.discard(); });
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
