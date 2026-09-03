package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierProductionWorkSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.ProductionJob;
import io.farfrontier.palemirror.frontier.v3.model.ProductionWorkProgress;
import io.farfrontier.palemirror.frontier.v3.model.ProductionWorkProgressed;
import io.farfrontier.palemirror.frontier.v3.model.ProductionWorkSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.ProductionWorkSceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.ProductionWorkTraversalAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * One loaded-worker proof for production work.  The fixture retains only canonical production
 * facts; the test itself never loads the remote settlement chunk or creates a second route.
 */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ProductionWorkGameTests {
    private FrontierV3ProductionWorkGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-production-work", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void exactWorkerRetainsStationsThenDeathDrainsAndFinalizesWork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bodyPosition = helper.absolutePos(new BlockPos(0, 8, 0));
        String suffix = bodyPosition.getX() + "-" + bodyPosition.getZ();
        WorldId world = new WorldId("frontier:production-work-game-test-" + suffix);
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.productionWorkConfiguration(world, 41L), new EphemeralStore(), 20_000);
        try {
            FrontierWorldState initial = state(runtime);
            ProductionJob initialJob = initial.productionJobs().get(new SubjectId("job:production-development-input-theft"));
            helper.assertTrue(initialJob != null, "the fixture must retain one exact materialized production job");
            SubjectId jobId = initialJob.id();
            FrontierProductionWorkSceneSupport.Candidate candidate = FrontierProductionWorkSceneSupport.nextCandidate(initial).orElseThrow();
            SceneLeaseId leaseId = new SceneLeaseId("lease:production-work-game-test-" + suffix);
            SceneMember member = new SceneMember(initialJob.workerId(), SceneLease.deterministicEntityId(world, initialJob.workerId()));
            SceneLease lease = SceneLease.forCause(leaseId, world, new ProductionWorkSceneCause(initialJob.id()), candidate.handoffPosition(),
                    runtime.canonicalState().orElseThrow().instant(), runtime.canonicalState().orElseThrow().revision().value(), SceneLeaseStatus.PREPARED,
                    List.of(member), SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), java.util.Set.of(), Optional.empty());
            FrontierV3CommandSubmission.submit(runtime, "production-work-game-test-prepare", suffix, new ProductionWorkSceneLeasePrepared(lease));
            FrontierV3CommandSubmission.submit(runtime, "production-work-game-test-hot", suffix, new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));

            ProductionJob job = state(runtime).productionJobs().get(jobId);
            int inputCursor = job.workTraversal().linearCorridorSurfaces().size() - 2;
            while (job.traversalCursor() < inputCursor) {
                int next = job.traversalCursor() + 1;
                BodyPosition observed = job.workTraversal().linearCorridorSurfaces().get(next).standingBody();
                FrontierV3CommandSubmission.submit(runtime, "production-work-game-test-approach", suffix + "-" + next,
                        new ProductionWorkTraversalAdvanced(job.id(), leaseId, observed, next));
                job = state(runtime).productionJobs().get(job.id());
                helper.assertValueEqual(state(runtime).sceneLeases().get(leaseId).memberPosition(job.workerId()), observed,
                        "each observed approach arrival must persist the same worker position as its retained cursor");
            }
            BodyPosition input = job.workTraversal().linearCorridorSurfaces().get(inputCursor).standingBody();
            FrontierV3CommandSubmission.submit(runtime, "production-work-game-test-input", suffix,
                    new ProductionWorkProgressed(job.id(), leaseId, input, ProductionWorkProgress.inputReady()));
            int workCursor = inputCursor + 1;
            BodyPosition work = job.workTraversal().linearCorridorSurfaces().get(workCursor).standingBody();
            FrontierV3CommandSubmission.submit(runtime, "production-work-game-test-work-arrival", suffix,
                    new ProductionWorkTraversalAdvanced(job.id(), leaseId, work, workCursor));
            FrontierV3CommandSubmission.submit(runtime, "production-work-game-test-processing", suffix,
                    new ProductionWorkProgressed(job.id(), leaseId, work, ProductionWorkProgress.processing(0)));
            helper.assertValueEqual(state(runtime).productionJobs().get(job.id()).workProgress(), ProductionWorkProgress.processing(0),
                    "the exact HOT worker must reach the declared work station before processing starts");
            helper.assertValueEqual(state(runtime).sceneLeases().get(leaseId).memberPosition(job.workerId()), work,
                    "the persisted HOT lease must retain the processing station for restart/reclaim");

            level.setBlock(bodyPosition.below(), Blocks.STONE.defaultBlockState(), 3);
            Villager body = EntityType.VILLAGER.create(level);
            helper.assertTrue(body != null, "the exact production worker body must be constructible");
            body.setUUID(member.entityId()); body.setNoAi(true); body.setPos(bodyPosition.getX() + 0.5D, bodyPosition.getY(), bodyPosition.getZ() + 0.5D);
            body.getPersistentData().putString(FrontierV3SceneExecutor.LEASE_KEY, leaseId.value());
            body.getPersistentData().putString(FrontierV3SceneExecutor.ACTOR_KEY, job.workerId().value());
            body.getPersistentData().putLong(FrontierV3SceneExecutor.REVISION_KEY, lease.revision());
            helper.assertTrue(level.addFreshEntity(body), "the exact worker body must enter the already-loaded GameTest cell");
            helper.runAfterDelay(2L, () -> {
                try {
                    // The GameTest template deliberately lives outside the bounded Frontier map.
                    // Keep body admission local, then present its already-retained canonical work
                    // position to the observer; setPos has no chunk-loading or world-write authority.
                    body.setPos(work.x() + 0.5D, work.y(), work.z() + 0.5D);
                    helper.assertTrue(FrontierV3SceneExecutor.observeDeath(runtime, body, null),
                            "the physical scene-death observer must accept only the exact leased production worker");
                    FrontierWorldState afterDeath = state(runtime);
                    helper.assertValueEqual(afterDeath.sceneLeases().get(leaseId).status(), SceneLeaseStatus.DRAINING,
                            "worker death must drain its same production scene rather than starting a replacement worker");
                    helper.assertTrue(afterDeath.productionJobs().containsKey(jobId),
                            "the blocked job remains owned until the physical worker release is durable");
                    FrontierV3CommandSubmission.submit(runtime, "production-work-game-test-release", suffix,
                            new SceneLeaseReleased(leaseId, List.of()));
                    FrontierWorldState finalized = state(runtime);
                    helper.assertFalse(finalized.productionJobs().containsKey(jobId),
                            "release must run the typed production finalizer and free no zombie job or reservation");
                    body.discard(); runtime.shutdown(); helper.succeed();
                } catch (RuntimeException failure) {
                    body.discard(); runtime.shutdown(); throw failure;
                }
            });
        } catch (RuntimeException failure) {
            runtime.shutdown(); throw failure;
        }
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.decodedState().orElseThrow(() -> new IllegalStateException("production work fixture runtime is inactive"));
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
