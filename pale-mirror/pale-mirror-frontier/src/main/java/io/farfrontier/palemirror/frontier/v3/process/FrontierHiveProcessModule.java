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
        if (command.payload() instanceof HiveMobilizationAssemblyAdvanced advanced) {
            try { HiveMobilizationProcess.reduceAssemblyAdvanced(state, state.bootstrap().hive().id(), advanced); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            HiveMobilization mobilization = state.hiveColony().mobilizations().get(advanced.mobilizationId());
            HiveTaskAssembly next = mobilization.assembly().orElseThrow().advance(advanced.bioformId());
            List<ProposedEvent> events = new java.util.ArrayList<>(List.of(new ProposedEvent(state.bootstrap().hive().id(), advanced)));
            if (next.complete()) events.addAll(HiveSettlementAssaultProcess.planAssemblyDeparture(state, mobilization, next, command.submittedAt().ticks()));
            return new CommandPlan.Accepted(List.copyOf(events));
        }
        if (command.payload() instanceof HiveMobilizationReturnAdvanced advanced) {
            try { HiveMobilizationProcess.reduceReturnAdvanced(state, state.bootstrap().hive().id(), advanced); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), advanced)));
        }
        if (command.payload() instanceof HiveMobilizationConflicted conflicted) {
            try { HiveMobilizationProcess.reduceConflicted(state, state.bootstrap().hive().id(), conflicted); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), conflicted)));
        }
        if (command.payload() instanceof DeferredAftermathResolved resolved) {
            DeferredAftermath aftermath = state.deferredAftermath().entries().get(resolved.aftermathId());
            if (aftermath == null || !aftermath.ownerId().equals(state.bootstrap().hive().id())) {
                return FrontierWorldCommandPlanner.rejected("deferred aftermath has no exact hive owner");
            }
            try { state.deferredAftermath().resolve(resolved.aftermathId(), resolved.expectedEpoch(), resolved.observationAt(), resolved.expectedCursor(), resolved.authorityRevision(), resolved.result()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(aftermath.ownerId(), resolved)));
        }
        if (command.payload() instanceof SettlementAssaultFormationObserved observed) {
            SettlementAssault assault = state.strategicPlans().settlementAssaults().get(observed.assaultId());
            if (assault == null) return FrontierWorldCommandPlanner.rejected("expedition formation observation has no assault");
            try { HiveSettlementAssaultProcess.reduceFormationObserved(state, assault.hiveId(), observed); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(assault.hiveId(), observed)));
        }
        if (command.payload() instanceof SettlementAssaultMarchIssueObserved observed) {
            SettlementAssault assault = state.strategicPlans().settlementAssaults().get(observed.assaultId());
            if (assault == null) return FrontierWorldCommandPlanner.rejected("expedition march issue has no assault");
            try { HiveSettlementAssaultProcess.reduceMarchIssueObserved(state, assault.hiveId(), observed); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(assault.hiveId(), observed)));
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
            case HiveMobilizationReturnAdvanced advanced -> HiveMobilizationProcess.reduceReturnAdvanced(state, event.subject(), advanced);
            case HiveMobilizationDeparted departed -> HiveMobilizationProcess.reduceDeparted(state, event.subject(), departed);
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
            case RouteEngagementCommandAuthorityChanged changed -> HiveRouteEngagementProcess.reduceCommandAuthorityChanged(state, event.subject(), changed);
            case SettlementAssaultStarted started -> HiveSettlementAssaultProcess.reduceStarted(state, event.subject(), started);
            case SettlementAssaultAttackerAdvanced advanced -> HiveSettlementAssaultProcess.reduceAdvanced(state, event.subject(), advanced);
            case SettlementAssaultFormationObserved observed -> HiveSettlementAssaultProcess.reduceFormationObserved(state, event.subject(), observed);
            case SettlementAssaultMarchIssueObserved observed -> HiveSettlementAssaultProcess.reduceMarchIssueObserved(state, event.subject(), observed);
            case SettlementAssaultTransition transition -> HiveSettlementAssaultProcess.reduceTransition(state, event.subject(), transition);
            case SettlementAssaultStrike strike -> HiveSettlementAssaultProcess.reduceStrike(state, event.subject(), strike);
            case SettlementAssaultResolved resolved -> HiveSettlementAssaultProcess.reduceResolved(state, event.subject(), resolved);
            case DeferredAftermathPrepared prepared -> reduceAftermathPrepared(state, event.subject(), prepared);
            case DeferredAftermathResolved resolved -> reduceAftermathResolved(state, event.subject(), resolved);
            default -> throw new IllegalArgumentException("hive process does not own event: " + event.payload().type());
        };
    }

    private static FrontierWorldState reduceAftermathPrepared(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                               DeferredAftermathPrepared prepared) {
        if (!subject.equals(state.bootstrap().hive().id()) || !prepared.aftermath().ownerId().equals(subject)) {
            throw new IllegalArgumentException("deferred aftermath preparation lacks hive ownership");
        }
        DeferredAftermath aftermath = prepared.aftermath();
        String assaultId = aftermath.provenance().startsWith("captive-bomber-strike:")
                ? aftermath.provenance().substring("captive-bomber-strike:".length()) : "";
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(assaultId));
        // The preparation event follows the exact strike in the same canonical transaction.
        // A same-actor or same-provenance substitute cannot cross this epoch/cause fence.
        if (assault == null || assault.nextStrikeEpoch() != aftermath.expectedEpoch() + 1L
                || aftermath.causeId().value().isBlank()
                || aftermath.cells().stream().anyMatch(cell -> cell.expectedMaterial() == null)) {
            throw new IllegalArgumentException("deferred aftermath preparation has stale or substituted causal authority");
        }
        boolean exactCause = assault.combatantAttackerIds().stream()
                .anyMatch(attacker -> aftermath.causeId().equals(SettlementAssaultCauseIdentity.strike(assault.id(), attacker, aftermath.expectedEpoch())));
        if (!exactCause) throw new IllegalArgumentException("deferred aftermath preparation has stale or substituted causal authority");
        FrontierWorldState preparedState = state.withChanges(FrontierWorldStateUpdate.begin()
                .deferredAftermath(state.deferredAftermath().prepare(aftermath)));
        // The COLD strike's semantic loss is committed with its exact cause.  Later natural
        // loading only reconciles the retained physical footprint; it must never decide whether
        // the causal consequence happened.
        return FrontierWorldPhysicalDeltaSupport.recordAll(preparedState, aftermath.cells().stream().map(cell ->
                new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                        java.util.Optional.of(cell.expectedOwner()), java.util.Optional.of(cell.expectedPart()), aftermath.causeId().value())).toList());
    }
    private static FrontierWorldState reduceAftermathResolved(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                               DeferredAftermathResolved resolved) {
        DeferredAftermath current = state.deferredAftermath().entries().get(resolved.aftermathId());
        if (current == null || !subject.equals(current.ownerId())) throw new IllegalArgumentException("deferred aftermath resolution lacks exact owner");
        DeferredAftermathState next = state.deferredAftermath().resolve(resolved.aftermathId(), resolved.expectedEpoch(), resolved.observationAt(), resolved.expectedCursor(), resolved.authorityRevision(), resolved.result());
        DeferredAftermathCell cell = current.cellAt(resolved.expectedCursor());
        if (resolved.result() == DeferredAftermathCellStatus.REALIZED && (cell == null || cell.status() != DeferredAftermathCellStatus.RUNNING)) {
            throw new IllegalArgumentException("realized aftermath has no running exact cell");
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().deferredAftermath(next));
    }
}
