package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** Read-only process views; lookup never selects mutation or ownership authority. */
final class FrontierV3ProcessDiagnosticJson {
    static String render(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        return render(id, checkpoint, state, io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView.canonical(state,
                checkpoint.instant().ticks()), "CANONICAL", movement -> java.util.Optional.empty());
    }
    static String render(String id, CheckpointImage checkpoint, FrontierWorldState state,
                         io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView positions, String positionSource,
                         java.util.function.Function<io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement, java.util.Optional<String>> nativeWait) {
        var subject = FrontierV3DiagnosticJson.subject(id).orElse(null);
        var group = subject == null ? null : state.unitGroups().groups().get(subject);
        if (group != null) return FrontierV3GroupDiagnosticJson.render(checkpoint, state, group, positions, positionSource, nativeWait);
        var mission = subject == null ? null : state.shipments().missions().get(subject);
        if (mission != null) return FrontierV3GroupDiagnosticJson.render(checkpoint, state, state.unitGroups().groups().get(mission.groupId()), positions, positionSource, nativeWait);
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
