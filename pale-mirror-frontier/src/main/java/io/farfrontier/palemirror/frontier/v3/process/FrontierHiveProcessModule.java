package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;

/** Exact owner for hive perception, doctrine, growth and combat facts. */
final class FrontierHiveProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof ScoutPatrolAdvanced advanced) {
            try { HiveScoutPatrolProcess.reduce(state, state.bootstrap().hive().id(), advanced); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), advanced)));
        }
        if (command.payload() instanceof ScoutPatrolLeaseRecovered recovered) {
            try { HiveScoutPatrolProcess.reduceLeaseRecovered(state, state.bootstrap().hive().id(), recovered); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), recovered)));
        }
        if (command.payload() instanceof HotScoutOperationObserved observed) {
            try { HivePerceptionProcess.reduceHot(state, state.bootstrap().hive().id(), observed); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(observed.operationId(), observed.scoutId(),
                    observed.seenCarrierPosition(), observed.observedAt());
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), observed),
                    new ProposedEvent(state.bootstrap().hive().id(), new ScheduleEffect.Created(
                            StrategicObjectiveProcess.interceptOpportunity(state.bootstrap().hive().id(), sighting,
                                    Math.addExact(command.submittedAt().ticks(), 1L))))));
        }
        if (command.payload() instanceof HiveMobilizationReleaseStarted started) {
            try { HiveMobilizationProcess.reduceReleaseStarted(state, state.bootstrap().hive().id(), started); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), started)));
        }
        if (command.payload() instanceof HiveMobilizationCocoonReleased released) {
            try { HiveMobilizationProcess.reduceCocoonReleased(state, state.bootstrap().hive().id(), released); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            HiveMobilization mobilization = state.hiveColony().mobilizations().get(released.mobilizationId());
            boolean finalRelease = mobilization.releasedMemberIds().size() + 1 == mobilization.memberIds().size();
            List<ProposedEvent> events = new java.util.ArrayList<>(List.of(new ProposedEvent(state.bootstrap().hive().id(), released)));
            if (finalRelease) events.add(new ProposedEvent(state.bootstrap().hive().id(), new ScheduleEffect.Created(
                    HiveMobilizationProcess.assemblyProgress(mobilization.id(), Math.addExact(command.submittedAt().ticks(),
                            state.bootstrap().ruleset().cadence().migrationStepInterval())))));
            return new CommandPlan.Accepted(List.copyOf(events));
        }
        if (command.payload() instanceof HiveMobilizationConflicted conflicted) {
            try { HiveMobilizationProcess.reduceConflicted(state, state.bootstrap().hive().id(), conflicted); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), conflicted)));
        }
        return FrontierWorldCommandPlanner.rejected("hive process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case InfectionChanged changed -> state.withInfection(changed.cell(), changed.intensity());
            case HiveGrowthStarted started -> HiveGrowthProcess.reduceStarted(state, event.subject(), started);
            case HiveGrowthBiomassConsumed consumed -> HiveGrowthProcess.reduceConsumed(state, event.subject(), consumed);
            case HiveGrowthCompleted completed -> HiveGrowthProcess.reduceCompleted(state, event.subject(), completed);
            case HiveGrowthBlocked blocked -> HiveGrowthProcess.reduceBlocked(state, event.subject(), blocked);
            case HiveNutrientTransferStarted started -> HiveNutrientTransferProcess.reduceStarted(state, event.subject(), started.transfer());
            case HiveNutrientTransferAdvanced advanced -> HiveNutrientTransferProcess.reduceAdvanced(state, event.subject(), advanced);
            case HiveNutrientTransferCompleted completed -> HiveNutrientTransferProcess.reduceCompleted(state, event.subject(), completed);
            case HiveNutrientTransferBlocked blocked -> HiveNutrientTransferProcess.reduceBlocked(state, event.subject(), blocked);
            case HiveNutrientTransferEndpointPrepared prepared -> HiveNutrientTransferProcess.reduceEndpointPrepared(state, event.subject(), prepared);
            case HiveMobilizationStarted started -> HiveMobilizationProcess.reduceStarted(state, event.subject(), started);
            case HiveMobilizationReleaseStarted started -> HiveMobilizationProcess.reduceReleaseStarted(state, event.subject(), started);
            case HiveMobilizationCocoonReleased released -> HiveMobilizationProcess.reduceCocoonReleased(state, event.subject(), released);
            case HiveMobilizationAssemblyAdvanced advanced -> HiveMobilizationProcess.reduceAssemblyAdvanced(state, event.subject(), advanced);
            case HiveMobilizationConflicted conflicted -> HiveMobilizationProcess.reduceConflicted(state, event.subject(), conflicted);
            case HiveOperationObserved observed -> HivePerceptionProcess.reduce(state, event.subject(), observed);
            case HiveTerritoryObserved observed -> HiveTerritoryPerceptionProcess.reduce(state, event.subject(), observed);
            case HiveSettlementObserved observed -> HiveSettlementPerceptionProcess.reduce(state, event.subject(), observed);
            case HiveDoctrineSelected selected -> HiveDoctrineProcess.reduce(state, event.subject(), selected);
            case HotScoutOperationObserved observed -> HivePerceptionProcess.reduceHot(state, event.subject(), observed);
            case ScoutPatrolAdvanced advanced -> HiveScoutPatrolProcess.reduce(state, event.subject(), advanced);
            case ScoutPatrolLeaseRecovered recovered -> HiveScoutPatrolProcess.reduceLeaseRecovered(state, event.subject(), recovered);
            case RouteEngagementStarted started -> HiveRouteEngagementProcess.reduceStarted(state, event.subject(), started);
            case RouteEngagementAttackerAdvanced advanced -> HiveRouteEngagementProcess.reduceAdvanced(state, event.subject(), advanced);
            case RouteEngagementTransition transition -> HiveRouteEngagementProcess.reduceTransition(state, event.subject(), transition);
            case RouteEngagementStrike strike -> HiveRouteEngagementProcess.reduceStrike(state, event.subject(), strike);
            case RouteEngagementResolved resolved -> HiveRouteEngagementProcess.reduceResolved(state, event.subject(), resolved);
            case SettlementAssaultStarted started -> HiveSettlementAssaultProcess.reduceStarted(state, event.subject(), started);
            case SettlementAssaultAttackerAdvanced advanced -> HiveSettlementAssaultProcess.reduceAdvanced(state, event.subject(), advanced);
            case SettlementAssaultTransition transition -> HiveSettlementAssaultProcess.reduceTransition(state, event.subject(), transition);
            case SettlementAssaultStrike strike -> HiveSettlementAssaultProcess.reduceStrike(state, event.subject(), strike);
            case SettlementAssaultResolved resolved -> HiveSettlementAssaultProcess.reduceResolved(state, event.subject(), resolved);
            default -> throw new IllegalArgumentException("hive process does not own event: " + event.payload().type());
        };
    }
}
