package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProductionResumedHandoffTest {
    @Test
    void progressedWorkerTransfersThroughRealCommandWithoutRecompilingOrResettingWork() {
        for (boolean processing : List.of(false, true)) {
            FrontierWorldState initial = FrontierDevelopmentScenarios.materializedProductionInputTheftFixture(
                    new WorldId("frontier:production-resume-" + processing), 41L).state();
            ProductionJob original = initial.productionJobs().get(new SubjectId("job:production-development-input-theft"));
            int cursor = processing ? original.workTraversal().linearCorridorSurfaces().size() - 1 : 1;
            ProductionJob job = original.withWorkTraversal(original.workTraversal(), cursor);
            if (processing) job = job.withWorkProgress(ProductionWorkProgress.processing(17));
            BodyPosition body = job.workTraversal().linearCorridorSurfaces().get(cursor).standingBody();
            Map<SubjectId, ActorLocation> locations = new LinkedHashMap<>(initial.actorLocations());
            locations.put(job.workerId(), new ActorLocation(body, locations.get(job.workerId()).condition(), locations.get(job.workerId()).kind()));
            var now = new SimInstant(100L);
            AmbientActorLease ambient = new AmbientActorLease(job.workerId(), body, now, 1L,
                    AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, body);
            FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations)
                    .productionJobs(Map.of(job.id(), job)).ambientLeases(Map.of(job.workerId(), ambient)));
            var leaseId = new SceneLeaseId("lease:production-resume");
            SceneLease lease = SceneLease.forCause(leaseId, state.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()),
                    body.supportingSurface().support(), now, 1L, SceneLeaseStatus.PREPARED,
                    List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), leaseId, job.workerId()))),
                    Map.of(job.workerId(), body), Set.of(job.workerId()), Optional.empty());
            var handoff = new ProductionWorkSceneLeaseHandoff(lease, List.of(new SceneMemberPosition(job.workerId(), body,
                    locations.get(job.workerId()).condition().health())));
            BodyPosition stale = original.workTraversal().linearCorridorSurfaces().getFirst().standingBody();
            assertNotEquals(body, stale, "fixture must distinguish old origin from retained progress");
            var staleCapture = new ProductionWorkSceneLeaseHandoff(lease, List.of(new SceneMemberPosition(job.workerId(), stale,
                    locations.get(job.workerId()).condition().health())));
            SubjectId owner = job.settlementId();
            assertThrows(IllegalArgumentException.class, () -> ProductionProcess.rebaseForAmbientHandoff(state, owner, staleCapture));
            Map<SubjectId, ActorLocation> staleLocations = new LinkedHashMap<>(locations);
            staleLocations.put(job.workerId(), new ActorLocation(stale, locations.get(job.workerId()).condition(), locations.get(job.workerId()).kind()));
            FrontierWorldState staleCanonical = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(staleLocations));
            assertThrows(IllegalArgumentException.class, () -> ProductionProcess.rebaseForAmbientHandoff(staleCanonical, owner, handoff));
            var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 41L);
            var due = ProductionProcess.complete(job, 120L);
            var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(state.bootstrap().worldId(), state, now,
                    base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(),
                    base.limits(), List.of(due), base.transactionCommitter()));
            var id = new CommandId("command:production-resume");
            assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, id, state.bootstrap().worldId(),
                    engine.checkpoint().revision(), now, FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), handoff)));
            FrontierWorldState accepted = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            assertEquals(job, accepted.productionJobs().get(job.id()), "the complete job, topology, cursor and partial labor survive ingress");
            assertEquals(body, accepted.sceneLeases().get(leaseId).memberPosition(job.workerId()));
            assertEquals(AmbientLeaseStatus.CLOSED, accepted.ambientLeases().get(job.workerId()).status());
            assertEquals(state.inventory(), accepted.inventory(), "worker transfer does not manufacture or consume resources");
            assertEquals(List.of(due), engine.checkpoint().schedules());
        }
    }
}
