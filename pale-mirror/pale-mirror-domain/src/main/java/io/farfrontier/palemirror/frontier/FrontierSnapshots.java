package io.farfrontier.palemirror.frontier;

import java.util.Comparator;

/** The only read path from the mutable aggregate to presentation code. */
public final class FrontierSnapshots {
    private FrontierSnapshots() { }

    public static FrontierSnapshot snapshot(FrontierWorldState state) {
        return new FrontierSnapshot(state.profile().id(), state.seed(), state.day(),
                state.settlements().stream().sorted(Comparator.comparing(FrontierSettlement::id))
                        .map(value -> new FrontierSnapshot.SettlementView(value.id(), value.name(), value.focus(), value.center(),
                                state.alivePopulation(value.id()), value.stocks(), value.netCredit(), value.civicState(),
                                value.threatPermille(), value.foodReserveDaysMilli(), value.rationPermille(),
                                value.civicRevision())).toList(),
                state.residents().stream().sorted(Comparator.comparing(FrontierResident::id))
                        .map(value -> new FrontierSnapshot.ResidentView(value.id(), value.settlementId(), value.role(),
                                value.alive(), value.materializationId(), value.revision())).toList(),
                state.facilities().stream().sorted(Comparator.comparing(FrontierFacility::id))
                        .map(value -> new FrontierSnapshot.FacilityView(value.id(), value.settlementId(), value.kind(),
                                value.position(), value.state(), value.revision())).toList(),
                state.operations().stream().sorted(Comparator.comparing(FrontierOperation::id))
                        .map(value -> new FrontierSnapshot.OperationView(value.id(), value.settlementId(), value.facilityId(),
                                value.kind(), value.state(), value.materializationId(), value.revision())).toList(),
                state.cargo().stream().sorted(Comparator.comparing(FrontierCargo::id))
                        .map(value -> new FrontierSnapshot.CargoView(value.id(), value.routeId(), value.sourceSettlementId(),
                                value.destinationSettlementId(), value.resource(), value.amount(), value.creditValue(), value.dispatchedDay(),
                                value.materializationId(), value.revision())).toList(),
                state.hives().stream().sorted(Comparator.comparing(FrontierHive::id))
                        .map(value -> new FrontierSnapshot.HiveView(value.id(), value.anchor(), value.biomass(), value.geneticMaterial(), state.broodSignal(value.id()), value.state(),
                                value.materializationId(), value.revision())).toList(),
                state.hiveOrgans().stream().sorted(Comparator.comparing(FrontierHiveOrgan::id))
                        .map(value -> new FrontierSnapshot.HiveOrganView(value.id(), value.hiveId(), value.kind(), value.position(),
                                value.state(), value.materializationId(), value.revision())).toList(),
                state.bioforms().stream().sorted(Comparator.comparing(FrontierBioform::id))
                        .map(value -> new FrontierSnapshot.BioformView(value.id(), value.hiveId(), value.kind(), value.bornDay(),
                                value.birthOrdinal(), value.alive(), value.materializationId(), value.revision())).toList(),
                state.harvesterRuns().stream().sorted(Comparator.comparing(FrontierHarvesterRun::id))
                        .map(value -> new FrontierSnapshot.HarvesterRunView(value.id(), value.hiveId(), value.sourceOrganId(),
                                value.bioformId(), value.foragePosition(), value.position(), value.receiverOrganId(), value.state(),
                                value.startedDay(), value.outboundDays(), value.transitProgress(), value.returnDays(), value.cargo(),
                                value.geneticCargo(), value.finishedDay(), value.materializationId(), value.revision())).toList(),
                state.assaults().stream().sorted(Comparator.comparing(FrontierAssault::id))
                        .map(value -> new FrontierSnapshot.AssaultView(value.id(), value.hiveId(), value.targetSettlementId(),
                                value.kind(), value.state(), value.position(), value.participantIds(), value.startedDay(),
                                value.transitDays(), value.engagementDays(), value.finishedDay(), value.materializationId(),
                                value.revision())).toList(),
                state.fieldOperations().stream().sorted(Comparator.comparing(FrontierFieldOperation::id))
                        .map(value -> new FrontierSnapshot.FieldOperationView(value.id(), value.settlementId(),
                                value.targetAssaultId(), value.kind(), value.state(), value.position(), value.participantIds(),
                                state.livingFieldParticipants(value), value.startedDay(), value.transitDays(), value.finishedDay(),
                                value.materializationId(), value.revision())).toList(),
                state.ecology().cells().stream().map(value -> new FrontierSnapshot.EcologyCellView(value.x(), value.z(),
                        value.flora(), value.fauna(), value.detritus(), value.nutrients(), value.moisture(), value.scar())).toList(),
                state.morphogenesisProjects().stream().sorted(Comparator.comparing(FrontierMorphogenesisProject::id))
                        .map(value -> new FrontierSnapshot.MorphogenesisProjectView(value.id(), value.hiveId(), value.sourceOrganId(),
                                value.kind(), value.position(), value.startedDay(), value.remainingDays(), value.requiredDays(),
                                value.materializationId(), value.revision())).toList(),
                state.propagationRuns().stream().sorted(Comparator.comparing(FrontierPropagationRun::id))
                        .map(value -> new FrontierSnapshot.PropagationRunView(value.id(), value.hiveId(), value.sourceOrganId(), value.bioformId(),
                                value.target(), value.position(), value.state(), value.startedDay(), value.transitDays(), value.transitProgress(),
                                value.latentColonyId(), value.finishedDay(), value.materializationId(), value.revision())).toList(),
                state.latentColonies().stream().sorted(Comparator.comparing(FrontierLatentColony::id))
                        .map(value -> new FrontierSnapshot.LatentColonyView(value.id(), value.hiveId(), value.sourceOrganId(), value.position(),
                                value.depositedDay(), value.propagules(), value.strength(), value.materializationId(), value.revision())).toList(),
                state.campaigns().stream().sorted(Comparator.comparing(FrontierCampaign::id))
                        .map(value -> new FrontierSnapshot.CampaignView(value.id(), value.leaderSettlementId(), value.targetHiveId(),
                                value.targetOrganId(), value.kind(), value.phase(), value.position(), value.contributorSettlementIds(),
                                value.participantIds(), state.campaignRegistry().livingParticipants(state, value), value.startedDay(),
                                value.transitDays(), value.phaseDays(), value.supplyRiskPermille(), value.supplyReadinessPermille(),
                                value.finishedDay(), value.materializationId(), value.revision())).toList(),
                state.adaptations());
    }
}
