package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestState;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryWork;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWork;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecution;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableReferenceIndex.Source;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableReferenceIndex.Read;
import java.util.List;
import java.util.Map;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState;
import java.util.Objects;

/** Immutable aggregate inputs during hydration; never an alternative movement authority. */
public record ActorMovementReferences(HumanPopulation people, ExactInventory inventory, ShipmentState shipments,
                                     UnitGroupState groups, ExtractionSiteState extractionSites, StrategicPlanState strategicPlans,
                                     ForestState forest, Map<SubjectId, ProductionJob> productionJobs,
                                     Map<SubjectId, ActorLocation> actors,
                                     ActorExecutionState executions) {
    public ActorMovementReferences {
        Objects.requireNonNull(people); Objects.requireNonNull(inventory); Objects.requireNonNull(shipments);
        Objects.requireNonNull(groups); Objects.requireNonNull(extractionSites);
        Objects.requireNonNull(strategicPlans);
        Objects.requireNonNull(forest); Objects.requireNonNull(productionJobs);
        Objects.requireNonNull(actors); Objects.requireNonNull(executions);
    }
    public static final Source<ActorMovementReferences, SubjectId, ProductionJob>
            PRODUCTION = new Source<>(ActorMovementReferences::productionJobs);
    public static final Source<ActorMovementReferences, SubjectId, ForestryWork>
            FORESTRY = new Source<>(r -> r.forest().work());
    public static final Source<ActorMovementReferences, SubjectId, SettlementAssault>
            ASSAULTS = new Source<>(r -> r.strategicPlans().settlementAssaults());
    public static final Source<ActorMovementReferences, SubjectId, ContainerRecord>
            CONTAINERS = new Source<>(r -> r.inventory().containers());
    public static final Source<ActorMovementReferences, SubjectId, TransportMission>
            MISSIONS = new Source<>(r -> r.shipments().missions());
    public static final Source<ActorMovementReferences, SubjectId, UnitGroup>
            GROUPS = new Source<>(r -> r.groups().groups());
    public static final Source<ActorMovementReferences, SubjectId, ResidentProfile>
            RESIDENTS = new Source<>(r -> r.people().residents());
    public static final Source<ActorMovementReferences, SubjectId, Shipment>
            SHIPMENTS = new Source<>(r -> r.shipments().shipments());
    public static final Source<ActorMovementReferences, SubjectId, ExtractionWork>
            EXTRACTION = new Source<>(r -> r.extractionSites().work());
    public static final Source<ActorMovementReferences, SubjectId, ExtractionDeposit>
            DEPOSITS = new Source<>(r -> r.extractionSites().deposits());
    public static final Source<ActorMovementReferences, SubjectId, ActorLocation>
            ACTORS = new Source<>(ActorMovementReferences::actors);
    public static final Source<ActorMovementReferences, SubjectId, ActorExecution>
            EXECUTIONS = new Source<>(r -> r.executions().actors());
    public static final Source<ActorMovementReferences, SubjectId, ResidentMeal>
            MEALS = new Source<>(r -> r.people().meals());
    public static final List<Source<ActorMovementReferences, ?, ?>> SOURCES = List.of(
            PRODUCTION, FORESTRY, ASSAULTS, CONTAINERS, MISSIONS, GROUPS, RESIDENTS, SHIPMENTS, EXTRACTION, DEPOSITS, ACTORS, EXECUTIONS, MEALS);
    public List<Read<ActorMovementReferences>> reads(ActorMovement movement) {
        var actor = movement.order().actorId();
        var result = new java.util.ArrayList<Read<ActorMovementReferences>>();
        result.add(ACTORS.at(actor)); result.add(EXECUTIONS.at(actor)); result.add(MEALS.at(actor));
        result.addAll(movement.context().validationReads(this, movement));
        return List.copyOf(result);
    }

}
