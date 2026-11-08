package io.farfrontier.palemirror.frontier.v3.model.seating;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecution;
import java.util.*;

/** Sparse exact cross-owner closure. Unsupported activities fail closed through their registered capability. */
public final class SeatRelationships {
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ActorLocation> ACTORS =
            new ImmutableReferenceIndex.Source<>(FrontierWorldState::actorLocations);
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ActorExecution> EXECUTIONS =
            new ImmutableReferenceIndex.Source<>(s -> s.actorExecutions().actors());
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, io.farfrontier.palemirror.frontier.v3.model.routine.ResidentRoutine> ROUTINES =
            new ImmutableReferenceIndex.Source<>(s -> s.routines().rows());
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ResidentMeal> MEALS =
            new ImmutableReferenceIndex.Source<>(s -> s.humanPopulation().meals());
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ResidentMealResourceObligation> OBLIGATIONS =
            new ImmutableReferenceIndex.Source<>(s -> s.humanPopulation().mealResourceObligations());
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ResidentProfile> PEOPLE =
            new ImmutableReferenceIndex.Source<>(s -> s.humanPopulation().residents());
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, FencedRecoveryBinding> BODIES =
            new ImmutableReferenceIndex.Source<>(s -> s.fencedRecovery().current());
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, FencedRecoveryTombstone> RETIRED =
            new ImmutableReferenceIndex.Source<>(s -> s.fencedRecovery().tombstones());
    private static final ImmutableReferenceIndex<FrontierWorldState, SubjectId, SeatUse> REFERENCES = new ImmutableReferenceIndex<>(
            SimulationCapacity.MAX_RESIDENTS, 8, List.of(ACTORS, EXECUTIONS, ROUTINES, MEALS, OBLIGATIONS, PEOPLE, BODIES, RETIRED),
            (state, use) -> {
                var reads = new ArrayList<ImmutableReferenceIndex.Read<FrontierWorldState>>(List.of(ACTORS.at(use.execution().actorId()),
                        EXECUTIONS.at(use.execution().actorId()), ROUTINES.at(use.execution().actorId()), MEALS.at(use.execution().actorId()),
                        OBLIGATIONS.at(use.execution().actorId()), PEOPLE.at(use.execution().actorId())));
                use.pendingBody().ifPresent(body -> {
                    var key = io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(body.body().actorId());
                    reads.add(BODIES.at(key)); reads.add(RETIRED.at(key));
                });
                return List.copyOf(reads);
            },
            (state, actor, use) -> {
                if (!SeatContacts.present(state, use.place())) throw new IllegalArgumentException("seat reservation lost authored identity/geometry");
                var person = state.humanPopulation().residents().get(actor);
                if (person == null || !person.settlementId().equals(use.place().home())) throw new IllegalArgumentException("seat has a foreign resident/home");
                boolean alive = state.actorLocations().get(actor).condition().status() == ActorLifeStatus.ALIVE;
                if (alive) state.actorExecutions().requireRetained(use.execution());
                ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind()).validateSeatReference(state, use.execution(), use);
                use.pendingBody().ifPresent(body -> ActorBodyAuthority.requireObservation(state, body.body()));
                if (alive && use.phase() == SeatUse.Phase.SEATED && !state.actorExecutions().actors().get(actor).current().equals(Optional.of(use.execution())))
                    throw new IllegalArgumentException("seated body cannot pass to a successor before exit");
            });
    public static void collect(FrontierWorldState state, List<FrontierDomainRelationships.Edge> target) {
        for (var use : state.humanPopulation().seats().uses().values()) {
            var owner = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.SEAT_USE, use.execution().actorId());
            target.add(FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.SEAT_ACTOR, owner, owner,
                    new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.RESIDENT, use.execution().actorId()),
                    FrontierDomainRelationships.Lifecycle.ACTIVE, "seat"));
            target.add(FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.SEAT_BUILDING, owner, owner,
                    new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.STRUCTURE, use.place().id().buildingId()),
                    FrontierDomainRelationships.Lifecycle.ACTIVE, use.place().id().localId()));
        }
    }
    public static void validate(FrontierWorldState state) {
        REFERENCES.validate(state.bootstrap(), state.humanPopulation().seats().uses(), state);
    }
    private SeatRelationships() { }
}
