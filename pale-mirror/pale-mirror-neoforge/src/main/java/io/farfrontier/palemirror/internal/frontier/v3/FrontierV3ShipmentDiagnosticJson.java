package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.process.ShipmentProgressObligation;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute;

/** Read-only exact transport/receiver view, with no dispatch or reconciliation authority. */
final class FrontierV3ShipmentDiagnosticJson {
    static String render(CheckpointImage checkpoint, FrontierWorldState state, Shipment shipment) {
        var actor = shipment.execution().actorId();
        var movement = state.actorMovements().get(actor); var lease = state.ambientLeases().get(actor);
        var body = ActorMovementProcess.bodyAt(state, actor, checkpoint.instant().ticks());
        var contract = state.companies().goodsTrade().contracts().get(shipment.authorization().claimantId());
        var obligation = ShipmentProgressObligation.describe(state, shipment);
        var mission = shipment.transportMissionId().map(state.shipments().missions()::get);
        var admission = lease != null && lease.status() == AmbientLeaseStatus.HOT
                && !shipment.terminal() && shipment.reception().isEmpty() && movement == null
                ? ShipmentPhysicalStateSupport.preparationAdmission(state, shipment) : null;
        String phase = shipment.status().name();
        String wait = shipment.terminal() ? "TERMINAL" : shipment.reception().isPresent() ? "RECEIVER_ACKNOWLEDGEMENT"
                : shipment.pendingPhysicalStep().isPresent() ? "PHYSICAL_EFFECT_RECEIPT"
                : state.actorLocations().get(actor).condition().status() != ActorLifeStatus.ALIVE ? "COURIER_CASUALTY"
                : state.actorExecutions().actors().get(actor).suspended().filter(shipment.execution()::equals).isPresent() ? "HIGHER_PRIORITY_ACTIVITY"
                : movement != null ? "JOURNEY" : mission.filter(value -> value.stage() == TransportMission.Stage.OUTBOUND).isPresent()
                    ? "GROUP_COORDINATION" : admission != null && admission != ShipmentPhysicalStateSupport.PreparationAdmission.READY
                    ? admission.name() : shipment.status() == Shipment.Status.CARRYING
                    && ShipmentStateSupport.coldTransferLots(state, shipment).isEmpty() ? "RECEIVER_CAPACITY" : "ENDPOINT_HANDOFF";
        String schedules = checkpoint.schedules().stream().filter(s -> s.subject().equals(shipment.id()) || s.subject().equals(actor))
                .sorted().limit(8).map(s -> "{\"id\":" + string(s.id().value()) + ",\"kind\":" + string(s.kind())
                        + ",\"dueAt\":" + s.dueAt().ticks() + "}").collect(java.util.stream.Collectors.joining(","));
        return FrontierV3DiagnosticJson.base("process", shipment.id().value(), checkpoint)
                + ",\"status\":\"ok\",\"family\":\"frontier.shipment\",\"identity\":{\"shipment\":" + string(shipment.id().value())
                + ",\"worker\":" + string(actor.value()) + ",\"entityId\":" + string(ActorBodyId.entityId(state.bootstrap().worldId(), actor).toString())
                + ",\"workerPresentation\":" + string(FrontierSceneLabels.actor(state, actor, false))
                + ",\"transportMission\":" + shipment.transportMissionId().map(value -> string(value.value())).orElse("null")
                + ",\"executionGeneration\":" + shipment.execution().generation() + "},\"shipmentRevision\":" + shipment.revision()
                + ",\"result\":{\"stage\":" + string(phase) + ",\"remainingQuantity\":" + (shipment.terminal() ? 0 : shipment.quantity())
                + ",\"receptionQuantity\":" + shipment.reception().map(ShipmentReception::quantity).orElse(0)
                + ",\"acceptedQuantity\":" + (contract == null ? -1 : contract.acceptedQuantity())
                + ",\"fulfilled\":" + (contract != null && contract.fulfilled()) + ",\"disposition\":" + string(wait)
                + ",\"dutyPhase\":" + string(movement == null ? phase : "TRAVELLING")
                + ",\"pendingPhysicalEffect\":" + shipment.pendingPhysicalStep().isPresent() + "},\"actorBody\":" + FrontierV3DiagnosticJson.position(body)
                + ",\"materialPreparation\":" + (shipment.terminal() || shipment.reception().isPresent()
                    || shipment.pendingPhysicalStep().isPresent() ? "null"
                    : FrontierV3MaterialPreparationDiagnosticJson.render(state, shipment.itemOrder()))
                + ",\"goal\":" + (shipment.terminal() ? "null" : FrontierV3DiagnosticJson.position(
                    (movement == null ? shipment.movementOrder() : movement.order()).legalStations().getFirst().standingBody()))
                + ",\"lease\":" + (lease == null || lease.status() == AmbientLeaseStatus.CLOSED ? "null" : string(lease.status().name()))
                + ",\"coldArrivalTick\":" + (movement == null ? -1 : movement.coldTravel().map(TimedKnownRoute::arrivalTick).orElse(-1L))
                + ",\"progressObligation\":{\"schema\":1,\"rule\":\"frontier.shipment.progress.v1\",\"subject\":" + string(shipment.id().value())
                + ",\"worker\":" + string(actor.value()) + ",\"generation\":" + shipment.execution().generation()
                + ",\"revision\":" + shipment.revision() + ",\"fingerprint\":" + string(obligation.fingerprint())
                + ",\"budgetTicks\":" + obligation.budgetTicks() + ",\"disposition\":" + string(obligation.disposition().name())
                + ",\"next\":" + string(obligation.next().name()) + "},\"schedule\":[" + schedules + "]}";
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
    private FrontierV3ShipmentDiagnosticJson() { }
}
