package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.FrontierSnapshot;

/** NeoForge-side adapter: the public visual SPI sees immutable data and no Frontier internals. */
public final class FrontierProjectionMapper {
    private FrontierProjectionMapper() { }

    public static FrontierProjection map(FrontierSnapshot snapshot) {
        return new FrontierProjection(snapshot.profileId(), snapshot.seed(), snapshot.day(),
                snapshot.settlements().stream().map(value -> new FrontierProjection.Settlement(value.id(), value.name(),
                        value.focus().name(), value.center().x(), value.center().z(), value.alivePopulation(),
                        value.stocks().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                                entry -> entry.getKey().name(), java.util.Map.Entry::getValue)), value.netCredit(),
                        value.civicState().name(), value.threatPermille(), value.foodReserveDaysMilli(),
                        value.rationPermille(), value.civicRevision())).toList(),
                snapshot.residents().stream().map(value -> new FrontierProjection.Resident(value.id(), value.settlementId(),
                        value.role().name(), value.alive(), value.materializationId(), value.revision())).toList(),
                snapshot.facilities().stream().map(value -> new FrontierProjection.Facility(value.id(), value.settlementId(),
                        value.kind().name(), value.position().x(), value.position().z(), value.state().name(),
                        value.revision())).toList(),
                snapshot.operations().stream().map(value -> new FrontierProjection.Operation(value.id(), value.settlementId(),
                        value.facilityId(), value.kind().name(), value.state().name(), value.materializationId(),
                        value.revision())).toList(),
                snapshot.cargo().stream().map(value -> new FrontierProjection.Cargo(value.id(), value.routeId(),
                        value.sourceSettlementId(), value.destinationSettlementId(), value.resource().name(), value.amount(), value.creditValue(),
                        value.dispatchedDay(), value.materializationId(), value.revision())).toList(),
                snapshot.hives().stream().map(value -> new FrontierProjection.Hive(value.id(), value.anchor().x(),
                        value.anchor().z(), value.biomass(), value.geneticMaterial(), value.broodSignal(), value.state().name(),
                        value.materializationId(), value.revision())).toList(),
                snapshot.hiveOrgans().stream().map(value -> new FrontierProjection.HiveOrgan(value.id(), value.hiveId(),
                        value.kind().name(), value.position().x(), value.position().z(), value.state().name(),
                        value.materializationId(), value.revision())).toList(),
                snapshot.bioforms().stream().map(value -> new FrontierProjection.Bioform(value.id(), value.hiveId(),
                        value.kind().name(), value.bornDay(), value.birthOrdinal(), value.alive(),
                        value.materializationId(), value.revision())).toList(),
                snapshot.harvesterRuns().stream().map(value -> new FrontierProjection.HarvesterRun(value.id(), value.hiveId(),
                        value.sourceOrganId(), value.bioformId(), value.foragePosition().x(), value.foragePosition().z(),
                        value.position().x(), value.position().z(), value.receiverOrganId(), value.state().name(), value.startedDay(),
                        value.outboundDays(), value.transitProgress(), value.returnDays(), value.cargo(), value.geneticCargo(),
                        value.finishedDay(), value.materializationId(), value.revision())).toList(),
                snapshot.assaults().stream().map(value -> new FrontierProjection.Assault(value.id(), value.hiveId(),
                        value.targetSettlementId(), value.kind().name(), value.state().name(), value.position().x(),
                        value.position().z(), value.participantIds(), value.startedDay(), value.transitDays(),
                        value.engagementDays(), value.finishedDay(), value.materializationId(), value.revision())).toList(),
                snapshot.fieldOperations().stream().map(value -> new FrontierProjection.FieldOperation(value.id(),
                        value.settlementId(), value.targetAssaultId(), value.kind().name(), value.state().name(),
                        value.position().x(), value.position().z(), value.participantIds(), value.livingParticipants(),
                        value.startedDay(), value.transitDays(), value.finishedDay(), value.materializationId(),
                        value.revision())).toList(),
                snapshot.ecology().stream().map(value -> new FrontierProjection.EcologyCell(value.x(), value.z(), value.flora(),
                        value.fauna(), value.detritus(), value.nutrients(), value.moisture(), value.scar())).toList(),
                snapshot.morphogenesisProjects().stream().map(value -> new FrontierProjection.MorphogenesisProject(value.id(),
                        value.hiveId(), value.sourceOrganId(), value.kind().name(), value.position().x(), value.position().z(),
                        "GROWING", value.startedDay(), value.remainingDays(), value.requiredDays(), value.materializationId(),
                        value.revision())).toList(),
                snapshot.propagationRuns().stream().map(value -> new FrontierProjection.PropagationRun(value.id(), value.hiveId(),
                        value.sourceOrganId(), value.bioformId(), value.target().x(), value.target().z(), value.position().x(),
                        value.position().z(), value.state().name(), value.startedDay(), value.transitDays(), value.transitProgress(),
                        value.latentColonyId(), value.finishedDay(), value.materializationId(), value.revision())).toList(),
                snapshot.latentColonies().stream().map(value -> new FrontierProjection.LatentColony(value.id(), value.hiveId(),
                        value.sourceOrganId(), value.position().x(), value.position().z(), value.depositedDay(), value.propagules(),
                        value.strength(), value.materializationId(), value.revision())).toList(),
                snapshot.campaigns().stream().map(value -> new FrontierProjection.Campaign(value.id(), value.leaderSettlementId(),
                        value.targetHiveId(), value.targetOrganId(), value.kind().name(), value.phase().name(), value.position().x(),
                        value.position().z(), value.contributorSettlementIds(), value.participantIds(), value.livingParticipants(),
                        value.startedDay(), value.transitDays(), value.phaseDays(), value.supplyRiskPermille(),
                        value.supplyReadinessPermille(), value.finishedDay(), value.materializationId(), value.revision())).toList(),
                snapshot.adaptations().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().name(), java.util.Map.Entry::getValue)));
    }
}
