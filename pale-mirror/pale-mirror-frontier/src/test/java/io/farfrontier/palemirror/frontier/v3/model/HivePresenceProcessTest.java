package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HivePresenceProcessTest {
    @Test void budgetDeferredPresenceUsesActualCommitInstantAndRetainsOneInitialization() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:hive-presence-deferred"), 93L);
        var state = configuration.initialState();
        var action = HivePresenceProcess.initialize(state.bootstrap().hive().id());
        var planned = FrontierWorldProcessCatalog.planScheduled(FrontierWorldRuntimeDefinition.processRegistry(), state, action, new SimInstant(5L));
        assertTrue(planned.stream().map(ProposedEvent::payload).filter(ActorPresenceStarted.class::isInstance)
                .map(ActorPresenceStarted.class::cast).allMatch(value -> value.atTick() == 5L));
        applyPresence(state, planned);
        var review = StrategicObjectiveProcess.review(state.bootstrap().hive().id(), 1, 2L);
        var reviewed = FrontierWorldProcessCatalog.planScheduled(FrontierWorldRuntimeDefinition.processRegistry(), state, review, new SimInstant(5L));
        assertTrue(reviewed.stream().map(ProposedEvent::payload).filter(ActorPresenceStarted.class::isInstance)
                .map(ActorPresenceStarted.class::cast).allMatch(value -> value.atTick() == 5L));
        applyPresence(state, reviewed);
        var engine = FrontierEngines.create(configuration);
        for (long tick = 1L; tick <= 8L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
            assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind(), engine.status().toString());
        }
        assertTrue(engine.checkpoint().schedules().stream().noneMatch(value -> value.kind().equals(action.kind())));
        var committed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(4, committed.actorExecutions().current(ActorActivityKind.PRESENCE).keySet().stream()
                .filter(actor -> committed.actorLocations().get(actor).kind() == ActorKind.BIOFORM).count());
    }

    @Test void oneRegisteredBootstrapTurnAdmitsOnlyReleasedBioformsWithoutBodiesOrTravel() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:hive-presence"), 91L);
        var before = configuration.initialState();
        var action = HivePresenceProcess.initialize(before.bootstrap().hive().id());
        assertEquals(1L, configuration.initialSchedules().stream().filter(value -> value.kind().equals(action.kind())).count());
        var registry = FrontierWorldRuntimeDefinition.processRegistry();
        assertEquals("hive", registry.requireScheduledOwner(action.kind()));
        var planned = FrontierWorldProcessCatalog.planScheduled(registry, before, action);
        var keys = planned.stream().map(ProposedEvent::payload).filter(ActorPresenceStarted.class::isInstance)
                .map(ActorPresenceStarted.class::cast).map(value -> value.execution().actorId()).sorted().toList();
        var expected = before.bootstrap().hive().bioforms().stream()
                .filter(value -> HivePhysiologySupport.initiallyDeployed(before.bootstrap().hive(), value))
                .map(Bioform::id).sorted().toList();
        assertEquals(expected, keys);
        assertEquals(4, keys.size());
        assertInstanceOf(ScheduleEffect.Consumed.class, planned.getLast().payload());
        var after = applyPresence(before, planned);
        assertSame(before.actorLocations(), after.actorLocations());
        assertSame(before.fencedRecovery(), after.fencedRecovery());
        assertSame(before.inventory(), after.inventory());
        assertSame(before.hiveColony(), after.hiveColony());
        assertSame(before.ambientLeases(), after.ambientLeases());
        assertSame(before.sceneLeases(), after.sceneLeases());
        var guard = before.bootstrap().hive().bioforms().stream().filter(Bioform::isDefender)
                .filter(value -> keys.contains(value.id())).findFirst().orElseThrow().id();
        var guarded = AmbientLeaseStateProcess.prepare(after, AmbientActorProcess.nextLease(after, guard, new SimInstant(1L)));
        var location = guarded.actorLocations().get(guard);
        guarded = ActorBodyAuthority.present(guarded, new ActorBodyPresent(ActorBodyAuthority.current(guarded, guard),
                location.body(), location.condition().health(), location.body(), location.condition().health()));
        guarded = AmbientLeaseStateProcess.transition(guarded, guard, AmbientLeaseStatus.HOT);
        ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(guarded,
                guarded.actorExecutions().actors().get(guard).current().orElseThrow(), guarded.ambientLeases().get(guard));
        assertEquals(AmbientGoalKind.GUARD, guarded.ambientLeases().get(guard).goal());
        var codec = new FrontierWorldStateCodec();
        var restored = codec.decode(codec.encode(after));
        assertEquals(after.actorExecutions(), restored.actorExecutions());
        assertTrue(HivePresenceProcess.planFree(restored, restored.bootstrap().hive().id(), 2L).isEmpty());
        assertEquals(List.of(), HivePresenceProcess.planInitialization(after, action).stream()
                .filter(value -> value.payload() instanceof ActorPresenceStarted).toList());
        var engine = FrontierEngines.create(configuration);
        engine.advanceTo(new SimInstant(1L), new WorkBudget(1_024, 4_096));
        assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind());
        var committed = codec.decode(engine.checkpoint().canonicalState());
        for (var actor : keys) assertEquals(ActorActivityKind.PRESENCE,
                committed.actorExecutions().actors().get(actor).current().orElseThrow().activityKind());
        assertTrue(engine.checkpoint().schedules().stream().noneMatch(value -> value.kind().equals(action.kind())));
    }

    @Test void dormantAndUnconfirmedWakingPresenceRejectWhileObservedCocoonLossIsEligible() {
        var state = initial();
        var dormant = state.bootstrap().hive().bioforms().stream().filter(value ->
                state.hiveColony().bioformLifecycles().get(value.id()).phase() == BioformLifecyclePhase.DORMANT)
                .findFirst().orElseThrow();
        var id = state.actorExecutions().next(dormant.id(), ActorActivityKind.PRESENCE, dormant.id());
        var presence = new ActorPresenceStarted(id, 1L);
        assertThrows(IllegalArgumentException.class, () -> applyPresence(state,
                List.of(new ProposedEvent(dormant.id(), presence))));
        assertThrows(IllegalArgumentException.class, () -> state.withChanges(FrontierWorldStateUpdate.begin()
                .actorExecutions(state.actorExecutions().begin(id, 0L))), "root closure cannot bypass owner eligibility");
        var lifecycle = state.hiveColony().bioformLifecycles().get(dormant.id());
        var lifecycles = new HashMap<>(state.hiveColony().bioformLifecycles());
        lifecycles.put(dormant.id(), lifecycle.waking());
        var unconfirmed = state.withChanges(FrontierWorldStateUpdate.begin()
                .hiveColony(state.hiveColony().withBioformLifecycles(lifecycles)));
        assertFalse(HivePresencePolicy.permits(unconfirmed, dormant.id()));
        assertThrows(IllegalArgumentException.class, () -> applyPresence(unconfirmed,
                List.of(new ProposedEvent(dormant.id(), presence))));
        var slot = lifecycle.homeSlot().orElseThrow();
        var organ = state.bootstrap().hive().organs().stream()
                .filter(value -> value.id().equals(slot.hibernaculumId())).findFirst().orElseThrow();
        var released = state.recordPhysicalDelta(new PhysicalDelta(HiveCocoonPlan.cocoonCell(organ, slot),
                PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_COCOON, dormant.id())),
                Optional.of(GrayboxSemanticPart.COCOON), "player:test"));
        assertTrue(HivePresencePolicy.permits(released, dormant.id()));
        var admitted = applyPresence(released, List.of(new ProposedEvent(dormant.id(), presence)));
        assertSame(released.actorLocations(), admitted.actorLocations());
        assertSame(released.fencedRecovery(), admitted.fencedRecovery());
        assertThrows(IllegalArgumentException.class, () -> admitted.withChanges(FrontierWorldStateUpdate.begin()
                .hiveColony(state.hiveColony()).actorLocations(state.actorLocations())),
                "physiology cannot put an independently executing body back into its cocoon");
    }

    @Test void scoutPolicyReplacesPresenceExactlyAndHiveReviewDoesNotStealThatExecution() {
        var before = initial();
        var state = applyPresence(before, HivePresenceProcess.planFree(before, before.bootstrap().hive().id(), 1L));
        var scout = state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout)
                .filter(value -> HivePhysiologySupport.initiallyDeployed(state.bootstrap().hive(), value))
                .sorted(Comparator.comparing(Bioform::id)).findFirst().orElseThrow();
        var presence = state.actorExecutions().actors().get(scout.id()).current().orElseThrow();
        var started = HiveScoutPatrolProcess.start(state, scout.id());
        var active = HiveScoutPatrolProcess.reduceStarted(state, state.bootstrap().hive().id(), started);
        var current = active.actorExecutions().actors().get(scout.id()).current().orElseThrow();
        assertEquals(ActorActivityKind.SCOUT_PATROL, current.activityKind());
        assertEquals(presence.generation() + 1L, current.generation());
        assertTrue(HivePresenceProcess.planFree(active, active.bootstrap().hive().id(), 2L).isEmpty());
        var review = StrategicObjectiveProcess.plan(active, StrategicObjectiveProcess.review(active.bootstrap().hive().id(), 1, 2L));
        assertTrue(review.stream().noneMatch(value -> value.payload() instanceof ActorPresenceStarted));
        assertThrows(IllegalArgumentException.class, () -> applyPresence(active,
                List.of(new ProposedEvent(scout.id(), new ActorPresenceStarted(presence, 1L)))));
        var guard = before.bootstrap().hive().bioforms().stream().filter(Bioform::isDefender)
                .filter(value -> HivePhysiologySupport.initiallyDeployed(before.bootstrap().hive(), value))
                .findFirst().orElseThrow();
        assertTrue(StrategicObjectiveProcess.plan(before, StrategicObjectiveProcess.review(before.bootstrap().hive().id(), 1, 2L)).stream()
                .anyMatch(value -> value.payload() instanceof ActorPresenceStarted candidate
                        && candidate.execution().actorId().equals(guard.id())));
    }

    @Test void presencePolicyCompositionRejectsMissingDuplicateAndUnregisteredDeclaredKinds() {
        var resident = new ActorPresencePolicies.Registration(ActorKind.RESIDENT, (state, actor) -> true);
        var required = Set.of(ActorKind.RESIDENT, ActorKind.BIOFORM);
        assertThrows(IllegalArgumentException.class, () -> new ActorPresencePolicies(required, List.of(resident)));
        assertThrows(IllegalArgumentException.class, () -> new ActorPresencePolicies(required, List.of(resident, resident)));
        var residentsOnly = new ActorPresencePolicies(Set.of(ActorKind.RESIDENT), List.of(resident));
        var state = initial();
        var bioform = state.bootstrap().hive().bioforms().getFirst().id();
        assertThrows(IllegalArgumentException.class, () -> residentsOnly.requireEligible(state, bioform));
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.PRESENCE_POLICIES
                .requireEligible(state, new SubjectId("actor:absent-presence")));
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-passive-eligibility"), 91L));
    }
    private static FrontierWorldState applyPresence(FrontierWorldState state, List<ProposedEvent> proposed) {
        long revision = 0L;
        for (var value : proposed) {
            if (!(value.payload() instanceof ActorPresenceStarted presence)) continue;
            var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
            assertEquals(presence, codecs.decode(presence.type(), codecs.encode(presence)));
            assertEquals("actor-execution", FrontierWorldRuntimeDefinition.processRegistry().requireReducedEventOwner(presence.type()));
            var event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION, new EventId("event:hive-presence-" + ++revision),
                    new TransactionId("transaction:hive-presence-" + revision), state.bootstrap().worldId(), new Revision(revision),
                    new SimInstant(presence.atTick()), value.subject(), CauseChain.root(new CommandId("command:hive-presence")), presence);
            state = FrontierWorldProcessCatalog.reduce("actor-execution", state, event);
        }
        return state;
    }
}
