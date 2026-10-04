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

import java.util.LinkedHashMap;
import java.util.List;

/** Native scene setup: immutable local genesis, then real common body admission/observation. */
final class FrontierV3SceneBodyGameTestFixture {
    private FrontierV3SceneBodyGameTestFixture() { }

    static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> start(
            GameTestHelper helper, WorldId world, long seed, FrontierStore store) {
        var source = FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(world, seed);
        var handoff = source.initialState().coldEngagementSceneCandidates().getFirst().handoffPosition();
        var support = helper.absolutePos(BlockPos.ZERO);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration =
                FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(FrontierV3CargoLoadingGameTests.translatedBootstrap(
                        source.initialState().bootstrap(), support.getX() - handoff.x(),
                        support.getY() - handoff.y(), support.getZ() - handoff.z()));
        var ledger = FrontierV3AmbientCarrierLedger.get(helper.getLevel(), world);
        FrontierV3ActorFirstAdmissionBootstrap.initialize(ledger, configuration.initialState(), store.recover(world),
                () -> ledger.persist(helper.getLevel(), world));
        return FrontierV3ServerRuntime.start(configuration, store, 20_000);
    }

    static List<Entity> materializeAndObserve(GameTestHelper helper,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        helper.assertTrue(lease.members().size() <= 8, "native cohort must fit this template's interior");
        var positions = new LinkedHashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, BodyPosition>();
        for (int index = 0; index < lease.members().size(); index++) {
            var feet = helper.absolutePos(new BlockPos(index % 3, 1, index / 3));
            FrontierV3AmbientActorGameTests.prepareFloor(helper.getLevel(), feet);
            positions.put(lease.members().get(index).actorId(), new BodyPosition(feet.getX(), feet.getY(), feet.getZ()));
        }
        return materializeAndObserve(helper, runtime, lease, positions);
    }

    static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> assaultConfiguration(
            GameTestHelper helper, WorldId world, long seed) {
        var source = FrontierV3FixtureCatalog.settlementAssaultConfiguration(world, seed);
        var handoff = source.initialState().coldSettlementAssaultSceneCandidates().getFirst().handoffPosition();
        var support = helper.absolutePos(BlockPos.ZERO);
        return FrontierV3FixtureCatalog.settlementAssaultConfiguration(FrontierV3CargoLoadingGameTests.translatedBootstrap(
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

    static List<Entity> materializeAndObserve(GameTestHelper helper,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease,
            java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, BodyPosition> positions) {
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(helper.getLevel(),
                runtime.decodedState().orElseThrow(), lease, positions),
                FrontierV3SceneExecutor.BodyMaterialization.COMPLETE, "common controller admits every exact scene body");
        List<Entity> bodies = lease.members().stream().map(member -> helper.getLevel().getEntity(member.entityId())).toList();
        for (Entity body : bodies) {
            helper.assertTrue(body instanceof Mob, "common admission produced an indexed native mob");
            FrontierV3ActorBodyController.confirmPresent(helper.getLevel(), runtime, body);
        }
        return bodies;
    }

    static void cleanup(List<Entity> bodies) {
        bodies.forEach(body -> { if (body != null) body.discard(); });
    }
}
