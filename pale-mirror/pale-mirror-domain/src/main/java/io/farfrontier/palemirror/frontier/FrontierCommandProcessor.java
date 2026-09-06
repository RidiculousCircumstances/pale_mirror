package io.farfrontier.palemirror.frontier;

import java.util.ArrayList;
import java.util.List;

/** Atomic command processor for canonical Frontier state. */
public final class FrontierCommandProcessor {
    private final FrontierSimulationEngine simulation = new FrontierSimulationEngine();
    public FrontierCommandOutcome execute(FrontierWorldState state, FrontierCommand command) {
        if (command instanceof FrontierCommand.AdvanceDays advance) return advance(state, advance);
        if (command instanceof FrontierCommand.StartMorphogenesis morphogenesis) return startMorphogenesis(state, morphogenesis);
        if (command instanceof FrontierCommand.LaunchHarvester harvester) return launchHarvester(state, harvester);
        if (command instanceof FrontierCommand.LaunchPropagationRun propagation) return launchPropagationRun(state, propagation);
        return observe(state, ((FrontierCommand.ApplyObservation) command).observation());
    }

    private FrontierCommandOutcome advance(FrontierWorldState state, FrontierCommand.AdvanceDays command) {
        List<FrontierEvent> events = new ArrayList<>();
        for (int day = 0; day < command.days(); day++) events.addAll(simulation.advance(state, command.causationId()));
        return new FrontierCommandOutcome(true, events);
    }

    private FrontierCommandOutcome startMorphogenesis(FrontierWorldState state, FrontierCommand.StartMorphogenesis command) {
        return state.startMorphogenesis(command.hiveId(), command.sourceOrganId(), command.kind(), command.position())
                .<FrontierCommandOutcome>map(project -> new FrontierCommandOutcome(true,
                        List.of(state.event(FrontierEvent.Type.MORPHOGENESIS_STARTED, project.id(), command.causationId()))))
                .orElseGet(() -> new FrontierCommandOutcome(false,
                        List.of(state.event(FrontierEvent.Type.MORPHOGENESIS_REJECTED, command.hiveId(), command.causationId()))));
    }

    private FrontierCommandOutcome launchHarvester(FrontierWorldState state, FrontierCommand.LaunchHarvester command) {
        return state.harvesters().start(state, command.hiveId(), command.sourceOrganId(), command.bioformId(), command.foragePosition())
                .<FrontierCommandOutcome>map(run -> new FrontierCommandOutcome(true,
                        List.of(state.event(FrontierEvent.Type.HARVESTER_LAUNCHED, run.id(), command.causationId()))))
                .orElseGet(() -> new FrontierCommandOutcome(false,
                        List.of(state.event(FrontierEvent.Type.HARVESTER_REJECTED, command.hiveId(), command.causationId()))));
    }

    private FrontierCommandOutcome launchPropagationRun(FrontierWorldState state, FrontierCommand.LaunchPropagationRun command) {
        return state.propagations().start(state, command.hiveId(), command.sourceOrganId(), command.bioformId(), command.target())
                .<FrontierCommandOutcome>map(run -> new FrontierCommandOutcome(true,
                        List.of(state.event(FrontierEvent.Type.PROPAGATION_RUN_LAUNCHED, run.id(), command.causationId()))))
                .orElseGet(() -> new FrontierCommandOutcome(false,
                        List.of(state.event(FrontierEvent.Type.PROPAGATION_RUN_REJECTED, command.hiveId(), command.causationId()))));
    }

    private FrontierCommandOutcome observe(FrontierWorldState state, FrontierPhysicalObservation observation) {
        if (state.seenObservation(observation.observationId())) return new FrontierCommandOutcome(false, List.of());
        state.recordObservation(observation.observationId());
        if (observation instanceof FrontierPhysicalObservation.ResidentDeath death) {
            FrontierResident resident = state.resident(death.residentId()).orElse(null);
            if (resident != null && resident.alive() && resident.materializationId().equals(death.materializationId())
                    && resident.revision() == death.expectedRevision()) {
                resident.kill();
                List<FrontierEvent> events = new ArrayList<>();
                events.add(state.event(FrontierEvent.Type.RESIDENT_DIED, resident.id(), death.causationId()));
                for (FrontierFieldOperation operation : state.abortFieldOperationsWithoutLivingParticipants()) {
                    events.add(state.event(FrontierEvent.Type.FIELD_OPERATION_RESOLVED, operation.id(), death.causationId()));
                }
                for (FrontierCampaign campaign : state.campaignRegistry().failBelowLivingParticipants(state,
                        FrontierCampaign.MINIMUM_PARTICIPANTS)) {
                    events.add(state.event(FrontierEvent.Type.CAMPAIGN_RESOLVED, campaign.id(), death.causationId()));
                }
                return new FrontierCommandOutcome(true, events);
            }
            return rejected(state, death.residentId(), death.causationId());
        }
        if (observation instanceof FrontierPhysicalObservation.CargoLost loss) {
            FrontierCargo cargo = state.cargo(loss.cargoId()).orElse(null);
            if (cargo != null && cargo.materializationId().equals(loss.materializationId())
                    && cargo.revision() == loss.expectedRevision()) {
                FrontierSettlement source = state.settlement(cargo.sourceSettlementId()).orElseThrow();
                FrontierSettlement destination = state.settlement(cargo.destinationSettlementId()).orElseThrow();
                source.changeCredit(-cargo.creditValue());
                destination.changeCredit(cargo.creditValue());
                state.removeCargo(cargo.id());
                state.assertCreditBalanced();
                return new FrontierCommandOutcome(true, List.of(state.event(FrontierEvent.Type.CARGO_LOST,
                        cargo.id(), loss.causationId())));
            }
            return rejected(state, loss.cargoId(), loss.causationId());
        }
        if (observation instanceof FrontierPhysicalObservation.HiveOrganDestroyed destruction) {
            FrontierHiveOrgan organ = state.hiveOrgan(destruction.organId()).orElse(null);
            if (organ != null && organ.materializationId().equals(destruction.materializationId())
                    && organ.revision() == destruction.expectedRevision()) {
                List<FrontierEvent> events = FrontierHiveDestruction.destroy(state, organ, destruction.causationId());
                if (!events.isEmpty()) return new FrontierCommandOutcome(true, events);
            }
            return rejected(state, destruction.organId(), destruction.causationId());
        }
        if (observation instanceof FrontierPhysicalObservation.BioformDeath death) {
            FrontierBioform bioform = state.bioform(death.bioformId()).orElse(null);
            if (bioform != null && bioform.materializationId().equals(death.materializationId())
                    && bioform.revision() == death.expectedRevision() && bioform.kill(state.day())) {
                List<FrontierEvent> events = new ArrayList<>();
                events.add(state.event(FrontierEvent.Type.BIOFORM_DIED, bioform.id(), death.causationId()));
                for (FrontierHarvesterRun run : state.harvesters().abortForBioform(state, bioform.id())) {
                    events.add(state.event(FrontierEvent.Type.HARVESTER_ABORTED, run.id(), death.causationId()));
                }
                for (FrontierPropagationRun run : state.propagations().abortForBioform(state, bioform.id())) {
                    events.add(state.event(FrontierEvent.Type.PROPAGATION_RUN_ABORTED, run.id(), death.causationId()));
                }
                for (FrontierAssault assault : state.abortAssaultsWithoutLivingParticipants()) {
                    events.add(state.event(FrontierEvent.Type.ASSAULT_RESOLVED, assault.id(), death.causationId()));
                    for (FrontierFieldOperation operation : state.beginReturnForAssault(assault.id())) {
                        events.add(state.event(FrontierEvent.Type.FIELD_OPERATION_STATE_CHANGED,
                                operation.id(), death.causationId()));
                    }
                }
                state.adaptationLedger().record(FrontierDamageKind.COMBAT);
                return new FrontierCommandOutcome(true, events);
            }
            return rejected(state, death.bioformId(), death.causationId());
        }
        if (observation instanceof FrontierPhysicalObservation.LatentColonyCleared cleared) {
            FrontierLatentColony colony = state.propagations().colony(cleared.colonyId()).orElse(null);
            if (colony != null && colony.materializationId().equals(cleared.materializationId())
                    && colony.revision() == cleared.expectedRevision() && state.propagations().clear(colony.id())) {
                state.propagations().removeColony(colony.id());
                state.adaptationLedger().record(FrontierDamageKind.CLEANSE);
                return new FrontierCommandOutcome(true, List.of(state.event(FrontierEvent.Type.LATENT_COLONY_CLEARED,
                        colony.id(), cleared.causationId())));
            }
            return rejected(state, cleared.colonyId(), cleared.causationId());
        }
        FrontierPhysicalObservation.FacilityDamage damage = (FrontierPhysicalObservation.FacilityDamage) observation;
        FrontierFacility facility = state.facility(damage.facilityId()).orElse(null);
        String materializationId = "frontier:facility:" + damage.facilityId();
        if (facility != null && materializationId.equals(damage.materializationId())
                && facility.revision() == damage.expectedRevision() && facility.damage()) {
            List<FrontierEvent> events = new ArrayList<>();
            events.add(state.event(FrontierEvent.Type.FACILITY_DAMAGED, facility.id(), damage.causationId()));
            state.operations().stream().filter(value -> value.facilityId().equals(facility.id())).findFirst().ifPresent(operation -> {
                if (state.reconcileOperation(operation)) {
                    events.add(state.event(FrontierEvent.Type.OPERATION_STATE_CHANGED, operation.id(), damage.causationId()));
                }
            });
            return new FrontierCommandOutcome(true, events);
        }
        return rejected(state, damage.facilityId(), damage.causationId());
    }

    private static FrontierCommandOutcome rejected(FrontierWorldState state, String subject, String causation) {
        return new FrontierCommandOutcome(false, List.of(state.event(FrontierEvent.Type.OBSERVATION_REJECTED, subject, causation)));
    }
}
