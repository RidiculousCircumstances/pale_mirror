package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the physical-carrier provenance boundary for HOT hive knowledge. */
class HotScoutOperationObservationTest {
    @Test
    void hotScoutKnowledgeRequiresBothLiveLeasesAndRetainsExactCarrierPosition() {
        WorldId world = new WorldId("frontier:hot-scout-observation");
        FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 41L));
        SubjectId scout = new SubjectId("bioform:west-1");
        RouteOperation operation = state(engine).operations().get(new SubjectId("operation:supply-1-2"));
        SceneLease lease = FrontierTestSceneLeases.exact(state(engine), new SceneLeaseId("lease:hot-scout-observation"), operation.id(), operation.cargoId(),
                operation.currentPosition(), engine.checkpoint().instant(), engine.checkpoint().revision().value(), Optional.empty(), operation.participantIds());

        // A test fixture may only capture the ordinary unleased actor position first; the next
        // two commands are the production PREPARED -> HOT hand-off, not a test-only lease edit.
        submit(engine, world, new AmbientActorObserved(scout, FrontierSceneBehaviors.logistics(lease).cargoPosition(), state(engine).actorLocations().get(scout).condition().health()));
        AmbientActorLease scoutLease = AmbientActorProcess.nextLease(state(engine), scout, engine.checkpoint().instant());
        submit(engine, world, new AmbientLeasePrepared(scoutLease));
        submit(engine, world, new AmbientLeaseTransition(scout, AmbientLeaseStatus.HOT));
        submit(engine, world, new SceneLeasePrepared(lease));
        submit(engine, world, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));

        HotScoutOperationObserved forged = new HotScoutOperationObserved(lease.id(), operation.id(), scout,
                FrontierSceneBehaviors.logistics(lease).cargoPosition().offset(1, 0, 0), engine.checkpoint().instant().ticks());
        assertInstanceOf(CommandResult.Rejected.class, submitResult(engine, world, forged),
                "a tagged operation and Scout cannot claim an unobserved carrier coordinate");
        assertTrue(state(engine).strategicPlans().hiveOperationKnowledge().entries().isEmpty(),
                "a rejected physical observation must not leak strategic knowledge");

        HotScoutOperationObserved observed = new HotScoutOperationObserved(lease.id(), operation.id(), scout,
                FrontierSceneBehaviors.logistics(lease).cargoPosition(), engine.checkpoint().instant().ticks());
        submit(engine, world, observed);
        HiveOperationKnowledge.Sighting sighting = state(engine).strategicPlans().hiveOperationKnowledge().entries().get(operation.id());
        assertEquals(FrontierSceneBehaviors.logistics(lease).cargoPosition(), sighting.position(), "the durable knowledge must retain the actually seen carrier anchor");
        assertEquals(scout, sighting.scoutId(), "one exact HOT Scout owns its own sighting");
        assertEquals(observed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(observed.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(observed)), "the WAL codec must retain the HOT provenance fields");
        assertEquals(state(engine), new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state(engine))),
                "the observed knowledge must survive a canonical snapshot round trip");
    }

    @Test
    void hotScoutKnowledgeRejectsClosedScoutLeaseEvenWhenTheCaravanSceneIsStillHot() {
        Fixture fixture = fixture();
        submit(fixture.engine(), fixture.world(), new AmbientLeaseTransition(fixture.scout(), AmbientLeaseStatus.DRAINING));
        assertInstanceOf(CommandResult.Rejected.class, submitResult(fixture.engine(), fixture.world(), fixture.observation()),
                "a former physical Scout cannot retain strategic sight after its HOT lease stops");
        assertTrue(state(fixture.engine()).strategicPlans().hiveOperationKnowledge().entries().isEmpty());
    }

    @Test
    void verifiedHotSightingSurvivesRestartThenCreatesOnePinnedInterceptTask() {
        Fixture fixture = fixture();
        submit(fixture.engine(), fixture.world(), fixture.observation());
        assertInstanceOf(CommandResult.Rejected.class, submitResult(fixture.engine(), fixture.world(), fixture.observation()),
                "the same HOT observation must not fork a second queued intercept wake-up");
        assertEquals(1L, fixture.engine().checkpoint().schedules().stream()
                .filter(action -> action.kind().equals("frontier.objective.interrupt")).count());

        var beforeRestart = fixture.engine().checkpoint();
        var recovered = FrontierEngines.recover(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(fixture.world(), 41L),
                new RecoveryImage(fixture.world(), Optional.of(new SnapshotRecord(beforeRestart, beforeRestart.revision().value())), java.util.List.of()));
        assertEquals(beforeRestart.schedules(), recovered.checkpoint().schedules(), "the one-shot planner wake-up must be durable before execution");

        recovered.advanceTo(new SimInstant(beforeRestart.instant().ticks() + 1L), new WorkBudget(64, 512));
        StrategicTask task = state(recovered).strategicPlans().tasks().values().stream()
                .filter(value -> value.kind() == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION).findFirst().orElseThrow();
        assertEquals(fixture.observation().operationId(), task.operationTarget().orElseThrow());
        assertEquals(fixture.observation().seenCarrierPosition(), task.operationObservationPosition().orElseThrow(),
                "the task must execute the observed position, not re-query the current caravan coordinate");
        assertEquals(1L, state(recovered).strategicPlans().tasks().values().stream()
                .filter(value -> value.kind() == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION).count());
    }

    @Test
    void interceptFixtureKeepsOnlyTheThreeRequiredAttackersAtDistinctApproachPositions() {
        FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(
                FrontierV3FixtureCatalog.hotScoutInterceptConfiguration(new WorldId("frontier:hot-scout-intercept-fixture"), 41L));
        FrontierWorldState state = state(engine);
        RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));
        var selected = java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(value -> value.role() == BioformRole.BOMBER || value.role() == BioformRole.GUARD)
                .sorted(java.util.Comparator.comparingLong((Bioform value) -> distance(state.actorLocations().get(value.id()).position(), operation.currentPosition()))
                        .thenComparing(Bioform::id)).limit(3).toList();
        assertEquals(3, selected.size());
        assertEquals(3L, selected.stream().map(value -> state.actorLocations().get(value.id()).position()).distinct().count(),
                "the pilot fixture must not make vanilla entity cramming into an invented combat outcome");
        assertEquals(1L, selected.stream().filter(value -> value.role() == BioformRole.BOMBER).count());
        assertEquals(2L, selected.stream().filter(value -> value.role() == BioformRole.GUARD).count());
    }

    private static long distance(BlockPosition left, BlockPosition right) {
        long x = (long) left.x() - right.x(), y = (long) left.y() - right.y(), z = (long) left.z() - right.z();
        return x * x + y * y + z * z;
    }

    private static Fixture fixture() {
        WorldId world = new WorldId("frontier:hot-scout-observation-negative");
        FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 41L));
        SubjectId scout = new SubjectId("bioform:west-1");
        RouteOperation operation = state(engine).operations().get(new SubjectId("operation:supply-1-2"));
        SceneLease lease = FrontierTestSceneLeases.exact(state(engine), new SceneLeaseId("lease:hot-scout-observation-negative"), operation.id(), operation.cargoId(),
                operation.currentPosition(), engine.checkpoint().instant(), engine.checkpoint().revision().value(), Optional.empty(), operation.participantIds());
        submit(engine, world, new AmbientActorObserved(scout, FrontierSceneBehaviors.logistics(lease).cargoPosition(), state(engine).actorLocations().get(scout).condition().health()));
        submit(engine, world, new AmbientLeasePrepared(AmbientActorProcess.nextLease(state(engine), scout, engine.checkpoint().instant())));
        submit(engine, world, new AmbientLeaseTransition(scout, AmbientLeaseStatus.HOT));
        submit(engine, world, new SceneLeasePrepared(lease));
        submit(engine, world, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        return new Fixture(world, engine, scout, new HotScoutOperationObserved(lease.id(), operation.id(), scout, FrontierSceneBehaviors.logistics(lease).cargoPosition(), engine.checkpoint().instant().ticks()));
    }

    private static void submit(FrontierEngine<FrontierWorldProjection> engine, WorldId world, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        CommandResult result = submitResult(engine, world, payload);
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);
    }

    private static CommandResult submitResult(FrontierEngine<FrontierWorldProjection> engine, WorldId world, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        var checkpoint = engine.checkpoint();
        CommandId id = new CommandId("command:hot-scout-observation-" + checkpoint.revision().value());
        return engine.submit(new FrontierCommand(1, id, world, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(id), payload));
    }

    private static FrontierWorldState state(FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }

    private record Fixture(WorldId world, FrontierEngine<FrontierWorldProjection> engine, SubjectId scout, HotScoutOperationObserved observation) { }
}
