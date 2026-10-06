package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** Read-only process views; lookup never selects mutation or ownership authority. */
final class FrontierV3ProcessDiagnosticJson {
    static String render(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        var subject = FrontierV3DiagnosticJson.subject(id).orElse(null);
        var shipment = subject == null ? null : state.shipments().shipments().get(subject);
        if (shipment != null) return FrontierV3ShipmentDiagnosticJson.render(checkpoint, state, shipment);
        var participant = subject == null ? null : state.companies().goodsTrade().participants().participants().get(subject);
        if (participant != null) return FrontierV3GoodsParticipantDiagnosticJson.render(checkpoint, state, participant);
        var harvest = subject == null ? null : state.resourceSites().sites().values().stream()
                .flatMap(site -> site.harvestJobs().values().stream())
                .filter(value -> value.id().equals(subject)).findFirst().orElse(null);
        if (harvest != null) return FrontierV3HarvestDiagnosticJson.harvestProcess(checkpoint, state, harvest);
        var production = subject == null ? null : state.productionJobs().get(subject);
        return production == null ? FrontierV3DiagnosticJson.unavailable("process", id, checkpoint, "not_found")
                : FrontierV3ProductionProcessDiagnosticJson.render(checkpoint, state, production);
    }
    private FrontierV3ProcessDiagnosticJson() { }
}
