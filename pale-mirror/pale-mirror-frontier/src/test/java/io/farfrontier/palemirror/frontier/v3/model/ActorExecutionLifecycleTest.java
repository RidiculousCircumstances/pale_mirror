package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResidentActivityProcess;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ActorExecutionLifecycleTest {
    @Test void coordinatedDeathRetirementRequiresItsExplicitSameOwnerKindAndCasualtyGroup() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var id = new ActorExecutionId(actor, ActorActivityKind.HIVE_TASK_RETURN, new SubjectId("mobilization:death-group"), 1L);
        assertThrows(IllegalArgumentException.class, () -> new ActorActivityDeath.Acknowledgement(state, id,
                FrontierWorldStateUpdate.begin(), ActorActivityDeath.Disposition.RETIRE_DECLARED_GROUP));
        assertThrows(IllegalArgumentException.class, () -> new ActorActivityDeath.Acknowledgement(state, id,
                FrontierWorldStateUpdate.begin(), ActorActivityDeath.Disposition.RETIRE_EXACT_EXECUTION,
                java.util.Optional.of(new ActorExecutionGroup(List.of(id)))));
        var foreignActor = new SubjectId("bioform:foreign-death-group");
        for (var foreign : List.of(
                new ActorExecutionId(foreignActor, id.activityKind(), new SubjectId("mobilization:foreign"), 1L),
                new ActorExecutionId(foreignActor, ActorActivityKind.HIVE_TASK_ASSEMBLY, id.activityOwnerId(), 1L))) {
            assertThrows(IllegalArgumentException.class, () -> new ActorActivityDeath.Acknowledgement(state, id,
                    FrontierWorldStateUpdate.begin(), ActorActivityDeath.Disposition.RETIRE_DECLARED_GROUP,
                    java.util.Optional.of(new ActorExecutionGroup(List.of(id, foreign)))));
        }
        assertThrows(IllegalArgumentException.class, () -> new ActorActivityDeath.Acknowledgement(state, id,
                FrontierWorldStateUpdate.begin(), ActorActivityDeath.Disposition.RETIRE_DECLARED_GROUP,
                java.util.Optional.of(new ActorExecutionGroup(List.of(new ActorExecutionId(foreignActor,
                        id.activityKind(), id.activityOwnerId(), 1L))))));
    }
    @Test void retainedWorkCannotRegisterWithoutItsOwnDeathAcknowledgement() {
        ActorActivityCapability incomplete = new ActorActivityCapability() {
            @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId id) { }
            @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return ActorActivityBodyCheckpoint.usesActorLocation(); }
            @Override public ActorActivityKind kind() { return ActorActivityKind.FIELD_HARVEST; }
            @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
            @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) { }
            @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId id, AmbientActorLease lease) { return false; }
            @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) {
                return new ActorActivityCheckpoint(state, id, java.util.Optional.empty());
            }
            @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long tick) { return state; }
            @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long tick) { return state; }
            @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) { return state; }
        };
        assertThrows(IllegalArgumentException.class, () -> new ActorActivityCapabilities(
                Set.of(ActorActivityKind.FIELD_HARVEST), List.of(incomplete)));
    }
    @Test void ambientPresentationCannotManufactureAnExecutionOrAuthorizeAStaleGoal() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var presence = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, presence).commit(state, FrontierWorldStateUpdate.begin());
        var prepared = io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess.nextLease(state, actor, new SimInstant(0L));
        state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.prepare(state, prepared);
        var location = state.actorLocations().get(actor);
        state = ActorBodyAuthority.present(state, new ActorBodyPresent(ActorBodyAuthority.current(state, actor),
                location.body(), location.condition().health(), location.body(), location.condition().health()));
        state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(state, actor, AmbientLeaseStatus.HOT);
        var basis = state;
        var lease = state.ambientLeases().get(actor);
        assertDoesNotThrow(() -> ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(basis, presence, lease));
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(basis,
                new ActorExecutionId(actor, ActorActivityKind.MEAL, actor, presence.generation()), lease));
        var workPresentation = ActorExecutionCoordinator.retargetAmbient(state, actor, AmbientGoalKind.WORK, lease.goalBody());
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(
                workPresentation, presence, workPresentation.ambientLeases().get(actor)), "WORK presentation grants no field owner");
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(
                workPresentation, presence, lease), "the same generation cannot revive a replaced presentation goal");
        var successor = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        var replaced = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, successor, 1L).commit(state, FrontierWorldStateUpdate.begin());
        var codec = new FrontierWorldStateCodec();
        var restored = codec.decode(codec.encode(replaced));
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(restored, presence, lease));
        assertDoesNotThrow(() -> ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(restored, successor, lease));
    }

    @Test void passivePresenceReleasesWithoutInventingWorkOrChangingBodyAndResources() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var presence = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, presence)
                .commit(state, FrontierWorldStateUpdate.begin());
        var next = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        var changed = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, next, 1L)
                .commit(state, FrontierWorldStateUpdate.begin());
        assertEquals(next, changed.actorExecutions().actors().get(actor).current().orElseThrow());
        assertTrue(changed.actorExecutions().actors().get(actor).suspended().isEmpty());
        assertSame(state.actorLocations(), changed.actorLocations());
        assertSame(state.inventory(), changed.inventory());
        assertSame(state.ambientLeases(), changed.ambientLeases());
        assertSame(state.sceneLeases(), changed.sceneLeases());
        var workAdmission = ActorExecutionComposition.LIFECYCLE.prepareVacant(changed,
                changed.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor));
        var admitted = workAdmission.commit(changed, FrontierWorldStateUpdate.begin());
        assertEquals(3L, admitted.actorExecutions().generation(actor));
        assertTrue(admitted.actorExecutions().actors().get(actor).suspended().isEmpty());
        var stale = state;
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.LIFECYCLE.prepareVacant(stale, presence));
        assertThrows(IllegalArgumentException.class, () -> admitted.withChanges(FrontierWorldStateUpdate.begin()
                .actorExecutions(admitted.actorExecutions().finish(admitted.actorExecutions().actors().get(actor).current().orElseThrow())
                        .begin(new ActorExecutionId(actor, ActorActivityKind.PRESENCE, new SubjectId("actor:foreign-owner"), 4L), 3L))));
    }
    @Test void declaredRegistryRejectsMissingDuplicateAndForeignFamilies() {
        var capability = new HarvestActivityCapability();
        assertThrows(IllegalArgumentException.class, () -> new ActorActivityCapabilities(
                Set.of(ActorActivityKind.FIELD_HARVEST, ActorActivityKind.PRODUCTION), List.of(capability)));
        assertThrows(IllegalArgumentException.class, () -> new ActorActivityCapabilities(
                Set.of(ActorActivityKind.FIELD_HARVEST), List.of(capability, capability)));
        assertThrows(IllegalArgumentException.class, () -> new ActorActivityCapabilities(
                Set.of(ActorActivityKind.PRODUCTION), List.of(capability)));
        var harvestOnly = new ActorActivityCapabilities(Set.of(ActorActivityKind.FIELD_HARVEST), List.of(capability));
        assertThrows(IllegalArgumentException.class, () -> harvestOnly.require(ActorActivityKind.LOGISTICS));
    }

    @Test void farmerMealKeepsTheJobAndRecoveryContinuationAndSelectionAloneResumesIt() {
        var field = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = field.job();
        FrontierWorldState state = field.state();
        var execution = state.actorExecutions().actors().get(job.workerId()).current().orElseThrow();
        assertEquals(ActorActivityKind.FIELD_HARVEST, execution.activityKind());
        SubjectId settlement = state.humanPopulation().resident(job.workerId()).settlementId();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        var food = state.inventory().fungibleResources().transformCold(ReferenceContainerCustody.scopeId(depot),
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:execution-meal-bread"), settlement,
                        ResidentMeal.BREAD_KIND, 64, "test", List.of()));
        state = state.withInventory(state.inventory().withFungibleResources(food));
        var started = ResidentMealProcess.selectSourceAtYield(state, job.workerId(), 27_000L).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, job.workerId(), started);
        var interrupted = state.actorExecutions().actors().get(job.workerId());
        assertEquals(started.meal().executionId(), interrupted.current().orElseThrow());
        assertEquals(execution, interrupted.suspended().orElseThrow());
        assertEquals(job, state.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow());
        assertEquals(1L, execution.generation());
        assertEquals(2L, interrupted.generation());
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(interrupted, state.actorExecutions().actors().get(job.workerId()));
        long due = 27_001L;
        for (int step = 0; step < 32 && state.humanPopulation().meals().containsKey(job.workerId()); step++) {
            var meal = state.humanPopulation().meals().get(job.workerId());
            var events = ResidentMealProcess.planProgress(state, ResidentMealProcess.progress(meal, due));
            for (var event : events) {
                if (event.payload() instanceof ResidentMealColdStep advanced)
                    state = ResidentMealProcess.reduceColdStep(state, job.workerId(), advanced);
                if (event.payload() instanceof ScheduleEffect.Rescheduled rescheduled
                        && rescheduled.replacement().kind().equals(ResidentMealProcess.PROGRESS))
                    due = rescheduled.replacement().dueAt().ticks();
            }
        }
        assertFalse(state.humanPopulation().meals().containsKey(job.workerId()), "the actual food owner must reach confirmed consumption");
        var completedMeal = state.actorExecutions().actors().get(job.workerId());
        assertTrue(completedMeal.current().isEmpty(), "meal completion cannot choose or start a work successor");
        assertEquals(execution, completedMeal.suspended().orElseThrow());
        assertEquals(job, state.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow());
        // Passive presence can coexist with the exact paused claim. It is not a new job
        // and cannot silently transfer body/cargo or erase the work to be resumed.
        var presence = state.actorExecutions().next(job.workerId(), ActorActivityKind.PRESENCE, job.workerId());
        state = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, presence, due)
                .commit(state, FrontierWorldStateUpdate.begin());
        assertEquals(execution, state.actorExecutions().actors().get(job.workerId()).suspended().orElseThrow());
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        var review = ResidentActivityProcess.plan(state, ResidentActivityProcess.review(job.workerId(), due + 1L));
        var resumed = review.stream().map(ProposedEvent::payload).filter(ActorExecutionResumed.class::isInstance)
                .map(ActorExecutionResumed.class::cast).findFirst().orElseThrow();
        assertEquals(execution, resumed.suspended());
        assertEquals(4L, resumed.successor().generation());
        assertEquals(java.util.Optional.of(presence), resumed.releasing());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(resumed, codecs.decode(resumed.type(), codecs.encode(resumed)));
        var bytes = codecs.encode(resumed);
        assertThrows(RuntimeException.class, () -> codecs.decode(resumed.type(), java.util.Arrays.copyOf(bytes, bytes.length - 1)));
        var stalePredecessor = state;
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.LIFECYCLE.prepareResume(
                stalePredecessor, execution, resumed.successor(), java.util.Optional.empty(), resumed.atTick()),
                "an older vacancy-based resume cannot displace the current presence");
        var newerPresence = state.actorExecutions().next(job.workerId(), ActorActivityKind.PRESENCE, job.workerId());
        var superseded = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, newerPresence, resumed.atTick())
                .commit(state, FrontierWorldStateUpdate.begin());
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.LIFECYCLE.prepareResume(
                superseded, execution, resumed.successor(), resumed.releasing(), resumed.atTick()),
                "the full captured predecessor prevents a late resume from stealing its successor");
        var event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION, new EventId("event:execution-resume"),
                new TransactionId("transaction:execution-resume"), state.bootstrap().worldId(), new Revision(1),
                new SimInstant(resumed.atTick()), job.workerId(),
                CauseChain.root(new CommandId("command:execution-resume")), resumed);
        assertEquals("actor-execution", FrontierWorldRuntimeDefinition.processRegistry().requireReducedEventOwner(resumed.type()));
        state = FrontierWorldProcessCatalog.reduce("actor-execution", state, event);
        assertEquals(resumed.successor(), state.actorExecutions().actors().get(job.workerId()).current().orElseThrow());
        assertTrue(state.actorExecutions().actors().get(job.workerId()).suspended().isEmpty());
        FrontierWorldState current = state;
        assertThrows(IllegalArgumentException.class, () -> current.actorExecutions().requireCurrent(execution));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldProcessCatalog.reduce("actor-execution", current, event));
    }

    @Test void bodyCheckpointRejectsStaleOwnerEvidenceAndCompetingPhysicalWriters() {
        var initial = ResourceSiteHarvestProcessTest.initial();
        var actor = initial.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var id = initial.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        var state = ActorExecutionComposition.LIFECYCLE.prepareVacant(initial, id).commit(initial, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        var pending = state;
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.LIFECYCLE.checkpointBodyDeparture(
                pending, body, pending.actorLocations().get(actor).body()));
        var admitted = ActorBodyAuthority.running(state, body);
        var position = admitted.actorLocations().get(actor).body();
        var owner = ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.PRESENCE);
        java.util.List<ActorActivityBodyCheckpoint> invalid = java.util.List.of(
                request -> new ActorActivityBodyCheckpoint.Acknowledgement(request,
                        FrontierWorldStateUpdate.begin().actorLocations(admitted.actorLocations())),
                request -> new ActorActivityBodyCheckpoint.Acknowledgement(request,
                        FrontierWorldStateUpdate.begin().fencedRecovery(admitted.fencedRecovery())),
                request -> new ActorActivityBodyCheckpoint.Acknowledgement(request,
                        FrontierWorldStateUpdate.begin().actorExecutions(admitted.actorExecutions())),
                request -> new ActorActivityBodyCheckpoint.Acknowledgement(new ActorActivityBodyCheckpoint.Request(
                        admitted.withChanges(FrontierWorldStateUpdate.begin()), id, body, position), FrontierWorldStateUpdate.begin()),
                request -> new ActorActivityBodyCheckpoint.Acknowledgement(new ActorActivityBodyCheckpoint.Request(
                        admitted, id, new ActorBodyId(actor, body.physicalEpoch() + 1), position), FrontierWorldStateUpdate.begin()),
                request -> new ActorActivityBodyCheckpoint.Acknowledgement(new ActorActivityBodyCheckpoint.Request(
                        admitted, new ActorExecutionId(actor, id.activityKind(), id.activityOwnerId(), id.generation() + 1), body, position),
                        FrontierWorldStateUpdate.begin()),
                request -> new ActorActivityBodyCheckpoint.Acknowledgement(new ActorActivityBodyCheckpoint.Request(
                        admitted, id, body, position.offset(1, 0, 0)), FrontierWorldStateUpdate.begin()));
        for (var port : invalid) {
            var lifecycle = new ActorExecutionLifecycle(new ActorActivityCapabilities(Set.of(ActorActivityKind.PRESENCE),
                    List.of(checkpointCapability(owner, port))));
            assertThrows(IllegalArgumentException.class, () -> lifecycle.checkpointBodyDeparture(admitted, body, position));
        }
        assertEquals(body, ActorBodyAuthority.current(admitted, actor));
        assertEquals(id, admitted.actorExecutions().actors().get(actor).current().orElseThrow());
    }

    private static ActorActivityCapability checkpointCapability(ActorActivityCapability owner, ActorActivityBodyCheckpoint port) {
        return new ActorActivityCapability() {
            @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId id) { owner.validateAmbientRelease(state, id); }
            @Override public ActorActivityKind kind() { return owner.kind(); }
            @Override public Interruption interruption() { return owner.interruption(); }
            @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return port; }
            @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) { owner.validateReference(state, id); }
            @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId id, AmbientActorLease lease) {
                return owner.permitsAmbientMotion(state, id, lease);
            }
            @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) { return owner.checkpoint(state, id); }
            @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long tick) { return owner.pause(state, id, tick); }
            @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long tick) { return owner.resume(state, id, tick); }
            @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) { return owner.release(state, id); }
        };
    }

    @Test void checkpointAndPreparedTransitionCannotCrossImmutableBasisOrExactOwner() {
        var field = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var state = field.state();
        var current = state.actorExecutions().actors().get(field.job().workerId()).current().orElseThrow();
        var checkpoint = new HarvestActivityCapability().checkpoint(state, current);
        checkpoint.validate(state, current);
        var changed = state.withActorBody(current.actorId(), state.actorLocations().get(current.actorId()).body());
        assertThrows(IllegalArgumentException.class, () -> checkpoint.validate(changed, current));
        var forged = new ActorExecutionId(current.actorId(), current.activityKind(), new SubjectId("job:foreign"), current.generation());
        assertThrows(IllegalArgumentException.class, () -> checkpoint.validate(state, forged));
        var successor = state.actorExecutions().next(current.actorId(), ActorActivityKind.MEAL, new SubjectId("claim:transition"));
        var transition = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, successor, 27_000L);
        assertThrows(IllegalArgumentException.class, () -> transition.commit(changed, FrontierWorldStateUpdate.begin()));
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.LIFECYCLE.retire(
                state, current.actorId(), current.activityKind(), forged.activityOwnerId()));
    }
}
