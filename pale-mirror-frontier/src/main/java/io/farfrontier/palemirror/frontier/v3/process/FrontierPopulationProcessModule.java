package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;

/** Exact owner for resident demography, migration, provisioning and health facts. */
final class FrontierPopulationProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof ResidentBorn) {
            return FrontierWorldCommandPlanner.rejected("resident birth is emitted only by a confirmed population permit");
        }
        if (command.payload() instanceof ResidentMigrated migration) {
            try { state.recordResidentMigration(migration); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(migration.destinationSettlementId(), migration)));
        }
        if (command.payload() instanceof ResidentTransitAdvanced advanced) {
            ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
            if (journey == null) return FrontierWorldCommandPlanner.rejected("HOT transit observation has no active migration journey");
            try { PopulationMigrationProcess.reduceHotAdvance(state, advanced); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(journey.originSettlementId(), advanced)));
        }
        return FrontierWorldCommandPlanner.rejected("population process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case ResidentBorn birth -> PopulationBirthProcess.reduceBorn(state, event.subject(), birth);
            case ResidentMigrated migration -> reduceMigrated(state, event.subject(), migration);
            case ResidentMigrationStarted started -> reduceStarted(state, event.subject(), started);
            case ResidentMigrationAdvanced advanced -> reduceAdvanced(state, event.subject(), advanced);
            case ResidentTransitAdvanced advanced -> reduceTransit(state, event.subject(), advanced);
            case ResidentMigrationBlocked blocked -> reduceBlocked(state, event.subject(), blocked);
            case ResidentMigrationResumed resumed -> reduceResumed(state, event.subject(), resumed);
            case ResidentBirthStarted started -> PopulationBirthProcess.reduceStarted(state, event.subject(), started);
            case ResidentBirthCancelled cancelled -> PopulationBirthProcess.reduceCancelled(state, event.subject(), cancelled);
            case LegacySettlementProvisionStarted started -> SettlementProvisionProcess.reduceLegacyStarted(state, event.subject(), started);
            case SettlementProvisionStarted started -> SettlementProvisionProcess.reduceStarted(state, event.subject(), started);
            case SettlementProvisionConsumed consumed -> SettlementProvisionProcess.reduceConsumed(state, event.subject(), consumed);
            case SettlementProvisionResolved resolved -> SettlementProvisionProcess.reduceResolved(state, event.subject(), resolved);
            case ResidentHealthTransition transition -> HumanHealthProcess.reduceResidentTransition(state, event.subject(), event.instant().ticks(), transition);
            case SettlementQuarantineTransition transition -> HumanHealthProcess.reduceQuarantineTransition(state, event.subject(), event.instant().ticks(), transition);
            default -> throw new IllegalArgumentException("population process does not own event: " + event.payload().type());
        };
    }

    private static FrontierWorldState reduceMigrated(FrontierWorldState state, SubjectId subject, ResidentMigrated migration) {
        if (!subject.equals(migration.destinationSettlementId())) throw new IllegalArgumentException("resident migration lacks destination settlement owner");
        return state.recordResidentMigration(migration);
    }

    private static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, ResidentMigrationStarted started) {
        if (!subject.equals(started.journey().originSettlementId())) throw new IllegalArgumentException("migration start lacks its origin settlement owner");
        return HumanPopulationStateSupport.startMigration(state, started.journey());
    }

    private static FrontierWorldState reduceAdvanced(FrontierWorldState state, SubjectId subject, ResidentMigrationAdvanced advanced) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration advance lacks its origin settlement owner");
        return HumanPopulationStateSupport.advanceMigration(state, advanced);
    }

    private static FrontierWorldState reduceTransit(FrontierWorldState state, SubjectId subject, ResidentTransitAdvanced advanced) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("HOT transit observation lacks its origin settlement owner");
        return PopulationMigrationProcess.reduceHotAdvance(state, advanced);
    }

    private static FrontierWorldState reduceBlocked(FrontierWorldState state, SubjectId subject, ResidentMigrationBlocked blocked) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(blocked.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration block lacks its origin settlement owner");
        return HumanPopulationStateSupport.blockMigration(state, blocked);
    }

    private static FrontierWorldState reduceResumed(FrontierWorldState state, SubjectId subject, ResidentMigrationResumed resumed) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(resumed.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration resume lacks its origin settlement owner");
        return HumanPopulationStateSupport.resumeMigration(state, resumed);
    }
}
