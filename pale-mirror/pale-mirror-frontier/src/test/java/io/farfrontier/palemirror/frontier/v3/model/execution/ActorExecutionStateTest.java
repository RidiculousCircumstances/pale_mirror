package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ActorExecutionStateTest {
    private static final SubjectId ACTOR = new SubjectId("resident:execution-contract");
    private static final SubjectId OWNER = new SubjectId("job:execution-contract");

    @Test void incrementalTransitionsMatchFullIngressIncludingProducerOrderAndSuspensions() {
        var state = ActorExecutionState.empty();
        var actors = java.util.List.of(new SubjectId("resident:z"), new SubjectId("resident:a"), new SubjectId("resident:m"));
        for (var actor : actors) { state = state.begin(state.next(actor, ActorActivityKind.FIELD_HARVEST, actor), 0L); assertFullImage(state); }
        var before = state;
        var first = state.current(ActorActivityKind.FIELD_HARVEST).get(actors.getFirst());
        var last = state.current(ActorActivityKind.FIELD_HARVEST).get(actors.getLast());
        state = state.suspendAndBegin(last, state.next(last.actorId(), ActorActivityKind.MEAL, OWNER)); assertFullImage(state);
        state = state.suspendAndBegin(first, state.next(first.actorId(), ActorActivityKind.MEAL, OWNER)); assertFullImage(state);
        assertEquals(java.util.List.of(first, last), state.suspended(), "continuations follow actor producer order, not interruption order");
        var meal = state.current(ActorActivityKind.MEAL).get(first.actorId());
        state = state.finish(meal); assertFullImage(state);
        state = state.resume(first, state.next(first.actorId(), first.activityKind(), first.activityOwnerId())); assertFullImage(state);
        var lastMeal = state.current(ActorActivityKind.MEAL).get(last.actorId());
        state = state.retireSuspended(last); assertFullImage(state);
        state = state.finish(lastMeal); assertFullImage(state);
        state = state.begin(state.next(last.actorId(), ActorActivityKind.FIELD_HARVEST, last.activityOwnerId()), state.generation(last.actorId())); assertFullImage(state);
        assertEquals(actors, java.util.List.copyOf(state.current(ActorActivityKind.FIELD_HARVEST).keySet()));
        assertEquals(actors, java.util.List.copyOf(before.current(ActorActivityKind.FIELD_HARVEST).keySet()));
        assertTrue(before.suspended().isEmpty(), "published predecessors cannot acquire later continuations");
        var published = state;
        assertThrows(IllegalArgumentException.class, () -> published.finish(first));
        assertFullImage(published);
    }
    @Test void nestedContinuationsResumeInOrderAndCannotDuplicateAnOwner(){
        var state=ActorExecutionState.empty();var work=state.next(ACTOR,ActorActivityKind.FIELD_HARVEST,OWNER);state=state.begin(work,0);
        var meal=state.next(ACTOR,ActorActivityKind.MEAL,new SubjectId("claim:nested-food"));state=state.suspendAndBegin(work,meal);
        var safety=state.next(ACTOR,ActorActivityKind.SAFETY,new SubjectId("safety:nested"));state=state.suspendAndBegin(meal,safety);assertFullImage(state);
        state.requireRetained(work);state.requireRetained(meal);assertEquals(java.util.List.of(meal,work),state.actors().get(ACTOR).continuations());
        var vacant=state.finish(safety);var skipped=vacant.next(ACTOR,ActorActivityKind.FIELD_HARVEST,OWNER);
        assertThrows(IllegalArgumentException.class,()->vacant.resume(work,skipped));
        var resumed=vacant.next(ACTOR,ActorActivityKind.MEAL,meal.activityOwnerId());state=vacant.resume(meal,resumed);assertFullImage(state);
        assertEquals(java.util.List.of(work),state.actors().get(ACTOR).continuations());
        var duplicate=state.next(ACTOR,work.activityKind(),work.activityOwnerId());var current=state;
        assertThrows(IllegalArgumentException.class,()->current.suspendAndBegin(resumed,duplicate));
    }
    private static void assertFullImage(ActorExecutionState state) {
        var recovered = new ActorExecutionState(state.actors());
        assertEquals(recovered, state); assertEquals(recovered.currentKinds(), state.currentKinds());
        for (var kind : ActorActivityKind.values()) {
            assertEquals(recovered.current(kind), state.current(kind));
            assertEquals(java.util.List.copyOf(recovered.current(kind).keySet()), java.util.List.copyOf(state.current(kind).keySet()));
        }
        assertEquals(recovered.suspended(), state.suspended());
    }

    @Test void interruptionRetainsAChainAndResumeNeedsVacantExactNewGeneration() {
        var empty = ActorExecutionState.empty();
        var work = empty.next(ACTOR, ActorActivityKind.FIELD_HARVEST, OWNER);
        var running = empty.begin(work, 0L);
        var meal = running.next(ACTOR, ActorActivityKind.MEAL, new SubjectId("claim:execution-meal"));
        var paused = running.suspendAndBegin(work, meal);
        assertEquals(java.util.Optional.of(work), paused.actors().get(ACTOR).suspended());
        assertThrows(IllegalArgumentException.class, () -> paused.requireCurrent(work));
        var successor = paused.next(ACTOR, work.activityKind(), work.activityOwnerId());
        assertThrows(IllegalArgumentException.class, () -> paused.resume(work, successor));
        var another = paused.next(ACTOR, ActorActivityKind.SERVICE_EXIT, ACTOR);
        assertEquals(2,paused.suspendAndBegin(meal,another).actors().get(ACTOR).continuations().size());
        var vacancy = paused.finish(meal);
        var stale = new ActorExecutionId(ACTOR, work.activityKind(), work.activityOwnerId(), 2L);
        assertThrows(IllegalArgumentException.class, () -> vacancy.resume(work, stale));
        var foreign = new ActorExecutionId(ACTOR, work.activityKind(), ACTOR, successor.generation());
        assertThrows(IllegalArgumentException.class, () -> vacancy.resume(work, foreign));
        var resumed = vacancy.resume(work, successor);
        resumed.requireCurrent(successor);
        assertTrue(resumed.actors().get(ACTOR).suspended().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> resumed.finish(meal));
        var retired = paused.retireSuspended(work);
        retired.requireCurrent(meal);
        assertTrue(retired.actors().get(ACTOR).suspended().isEmpty());
    }

    @Test void finishRetainsGenerationAndCannotReleaseSuccessor() {
        var empty = ActorExecutionState.empty();
        var first = empty.next(ACTOR, ActorActivityKind.FIELD_HARVEST, OWNER);
        var running = empty.begin(first, 0L);
        assertThrows(IllegalArgumentException.class, () -> running.begin(first, 0L));
        var vacant = running.finish(first);
        assertEquals(1L, vacant.generation(ACTOR));
        var second = vacant.next(ACTOR, ActorActivityKind.MEAL, ACTOR);
        var successor = vacant.begin(second, 1L);
        assertEquals(2L, second.generation());
        assertThrows(IllegalArgumentException.class, () -> successor.finish(first));
        successor.requireCurrent(second);
    }

    @Test void everyAuthorityDimensionMustMatchBeforeMutation() {
        var id = new ActorExecutionId(ACTOR, ActorActivityKind.FIELD_HARVEST, OWNER, 1L);
        var running = ActorExecutionState.empty().begin(id, 0L);
        var forged = java.util.List.of(
                new ActorExecutionId(new SubjectId("resident:other"), id.activityKind(), OWNER, 1L),
                new ActorExecutionId(ACTOR, ActorActivityKind.PRODUCTION, OWNER, 1L),
                new ActorExecutionId(ACTOR, id.activityKind(), new SubjectId("job:other"), 1L),
                new ActorExecutionId(ACTOR, id.activityKind(), OWNER, 2L));
        forged.forEach(candidate -> assertThrows(IllegalArgumentException.class, () -> running.finish(candidate)));
        running.requireCurrent(id);
        assertThrows(NullPointerException.class, () -> new ActorExecutionId(ACTOR, null, OWNER, 1L));
        assertThrows(NullPointerException.class, () -> new ActorExecutionId(ACTOR, id.activityKind(), null, 1L));
        assertThrows(IllegalArgumentException.class, () -> new ActorExecutionId(ACTOR, id.activityKind(), OWNER, 0L));
        var group = new ActorExecutionGroup(java.util.List.of(id));
        assertSame(id, group.requireMember(ACTOR), "participant lookup retains the complete declared execution");
        assertThrows(IllegalArgumentException.class, () -> group.requireMember(new SubjectId("resident:other")));
    }

    @Test void startRequiresExactVacantGenerationAndIndexCannotAliasActor() {
        var empty = ActorExecutionState.empty();
        var id = empty.next(ACTOR, ActorActivityKind.PRODUCTION, OWNER);
        assertThrows(IllegalArgumentException.class, () -> empty.begin(id, 1L));
        var running = empty.begin(id, 0L);
        var contender = running.next(ACTOR, ActorActivityKind.MEAL, ACTOR);
        assertThrows(IllegalArgumentException.class, () -> running.begin(contender, 1L));
        assertThrows(IllegalArgumentException.class, () -> new ActorExecutionState(Map.of(
                new SubjectId("resident:other"), running.actors().get(ACTOR))));
    }

    @Test void wireTagsAreExplicitClosedAndRoundTripEveryFamily() {
        var tags = new java.util.HashSet<Integer>();
        for (ActorActivityKind kind : ActorActivityKind.values()) {
            assertTrue(tags.add(FrontierWireTags.tag(kind)));
            assertSame(kind, FrontierWireTags.require(ActorActivityKind.class, FrontierWireTags.tag(kind)));
        }
        assertThrows(IllegalArgumentException.class, () -> FrontierWireTags.require(ActorActivityKind.class, 0));
    }

    @Test void suspensionCannotAliasCurrentGenerationOrItsExactActivityOwner() {
        var paused = new ActorExecutionId(ACTOR, ActorActivityKind.FIELD_HARVEST, OWNER, 1L);
        var sameGeneration = new ActorExecutionId(ACTOR, ActorActivityKind.MEAL, ACTOR, 1L);
        assertThrows(IllegalArgumentException.class, () -> new ActorExecution(ACTOR, 1L,
                java.util.Optional.of(sameGeneration), java.util.Optional.of(paused)));
        var sameOwner = new ActorExecutionId(ACTOR, ActorActivityKind.FIELD_HARVEST, OWNER, 2L);
        assertThrows(IllegalArgumentException.class, () -> new ActorExecution(ACTOR, 2L,
                java.util.Optional.of(sameOwner), java.util.Optional.of(paused)));
    }
}
