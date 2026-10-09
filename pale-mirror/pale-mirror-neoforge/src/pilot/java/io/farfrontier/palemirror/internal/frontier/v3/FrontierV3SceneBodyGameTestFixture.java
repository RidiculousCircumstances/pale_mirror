package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/** Native scene setup: immutable local genesis, then real common body admission/observation. */
final class FrontierV3SceneBodyGameTestFixture {
    private FrontierV3SceneBodyGameTestFixture() { }


    static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> assaultConfiguration(
            GameTestHelper helper, WorldId world, long seed) {
        var source = FrontierV3FixtureCatalog.settlementAssaultConfiguration(world, seed);
        var handoff = source.initialState().coldSettlementAssaultSceneCandidates().getFirst().handoffPosition();
        var support = helper.absolutePos(BlockPos.ZERO);
        return FrontierV3FixtureCatalog.settlementAssaultConfiguration(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(
                source.initialState().bootstrap(), support.getX() - handoff.x(),
                support.getY() - handoff.y(), support.getZ() - handoff.z()));
    }

    static void initializeAdmission(GameTestHelper helper,
            FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration, FrontierStore store) {
        var world = configuration.worldId();
        var ledger = FrontierV3AmbientCarrierLedger.get(helper.getLevel(), world);
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, configuration.initialState(), store.recover(world),
                () -> ledger.persist(helper.getLevel(), world));
    }

    static void materializeAndObserve(GameTestHelper helper,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease,
            java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, BodyPosition> positions,
            java.util.function.Consumer<List<Entity>> complete) {
        // GameTest's terrain ticket is not necessarily an entity-ticking ticket.
        // Supply real local observation before requesting native body admission.
        var observer = helper.makeMockServerPlayerInLevel();
        var anchor = positions.values().iterator().next();
        observer.setPos(anchor.x() + 12.5D, anchor.y(), anchor.z() + 12.5D);
        // Other fixtures in this batch may still be constructing their worlds on
        // this thread. Start the I/O watchdog only after yielding to the test loop.
        helper.runAfterDelay(1, () -> awaitBodies(helper, runtime, lease, positions, observer,
                System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10), 0, complete));
    }

    private static void awaitBodies(GameTestHelper helper,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease,
            java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, BodyPosition> positions,
            net.minecraft.server.level.ServerPlayer observer, long deadline, int indexed,
            java.util.function.Consumer<List<Entity>> complete) {
        var result = FrontierV3SceneExecutor.materializeBodiesForFixture(helper.getLevel(), runtime.decodedState().orElseThrow(), lease, positions);
        // Never block the server thread or bypass the insertion journal. The enclosing
        // GameTest owns a finite hang bound; the next stage needs actual indexed bodies.
        if (result == FrontierV3SceneExecutor.BodyMaterialization.DEFERRED) {
            int current = (int) lease.members().stream().filter(member -> helper.getLevel().getEntity(member.entityId()) != null).count();
            long progressDeadline = current > indexed
                    ? System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10) : deadline;
            if (System.nanoTime() >= progressDeadline) {
                observer.connection.disconnect(net.minecraft.network.chat.Component.literal("scene admission timeout"));
                observer.discard();
                helper.fail("native body admission did not complete within its real I/O hang bound: "
                        + admissionDetail(helper, runtime, lease, positions));
                return;
            }
            helper.runAfterDelay(1, () -> awaitBodies(helper, runtime, lease, positions, observer, progressDeadline, current, complete));
            return;
        }
        helper.assertValueEqual(result,
                FrontierV3SceneExecutor.BodyMaterialization.COMPLETE, "common controller admits every exact scene body");
        List<Entity> bodies = lease.members().stream().map(member -> helper.getLevel().getEntity(member.entityId())).toList();
        for (Entity body : bodies) {
            helper.assertTrue(body instanceof Mob, "common admission produced an indexed native mob");
            FrontierV3ActorBodyController.confirmPresent(helper.getLevel(), runtime, body);
        }
        observer.connection.disconnect(net.minecraft.network.chat.Component.literal("scene fixture admission complete"));
        observer.discard();
        complete.accept(bodies);
    }

    private static String admissionDetail(GameTestHelper helper, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            SceneLease lease, java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, BodyPosition> positions) {
        var state = runtime.decodedState().orElseThrow();
        for (var member : lease.members()) {
            if (helper.getLevel().getEntity(member.entityId()) != null) continue;
            var support = positions.get(member.actorId()).supportingSurface();
            var point = new BlockPos(support.x(), support.y(), support.z());
            var ticket = FrontierV3BodyInsertionJournal.get(helper.getLevel(), state.bootstrap().worldId(), member.actorId());
            return "actor=" + member.actorId() + " support=" + support
                    + " residenceReady=" + FrontierV3NativeBodyResidence.admissionReady(helper.getLevel(), support)
                    + " block=" + helper.getLevel().getBlockState(point)
                    + " standing=" + FrontierV3StandingPosition.aboveExactFloor(helper.getLevel(), point)
                    + " ticket=" + (ticket != null) + " durable=" + (ticket != null && ticket.ready());
        }
        return "all members indexed but completion deferred";
    }
}
