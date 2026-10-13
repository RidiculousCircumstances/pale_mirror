package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** Read-only process views; lookup never selects mutation or ownership authority. */
final class FrontierV3ProcessDiagnosticJson {
    static String render(String id, FrontierScheduleView checkpoint, FrontierWorldState state) {
        return render(id, checkpoint, state, io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView.canonical(state,
                checkpoint.instant().ticks()), "CANONICAL", movement -> java.util.Optional.empty(), movement -> java.util.Optional.empty());
    }
    static String render(String id, FrontierScheduleView checkpoint, FrontierWorldState state,
                         io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView positions, String positionSource,
                         java.util.function.Function<io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement, java.util.Optional<String>> nativeWait,
                         java.util.function.Function<io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement,
                                 java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPermission>> steering) {
        var subject = FrontierV3DiagnosticJson.subject(id).orElse(null);
        var machine = subject == null ? null : state.productionStations().machines().get(subject);
        if (machine != null) return FrontierV3ProductionStationDiagnosticJson.render(checkpoint, state, machine);
        var extraction = subject == null ? null : state.extractionSites().deposits().get(subject);
        if (extraction != null) return FrontierV3ExtractionDiagnosticJson.site(checkpoint, state, extraction);
        var mining = subject == null ? null : state.extractionSites().work().get(subject);
        if (mining != null) return FrontierV3ExtractionDiagnosticJson.work(checkpoint, state, mining);
        var group = subject == null ? null : state.unitGroups().groups().get(subject);
        if (group != null) return FrontierV3GroupDiagnosticJson.render(checkpoint, state, group, positions, positionSource, nativeWait, steering);
        var mission = subject == null ? null : state.shipments().missions().get(subject);
        if (mission != null) return FrontierV3GroupDiagnosticJson.render(checkpoint, state, state.unitGroups().groups().get(mission.groupId()), positions, positionSource, nativeWait, steering);
        var shipment = subject == null ? null : state.shipments().shipments().get(subject);
        if (shipment != null) return FrontierV3ShipmentDiagnosticJson.render(checkpoint, state, shipment);
        var participant = subject == null ? null : state.companies().goodsTrade().participants().participants().get(subject);
        if (participant != null) return FrontierV3GoodsParticipantDiagnosticJson.render(checkpoint, state, participant);
        var harvest = subject == null ? null : state.resourceSites().sites().values().stream()
                .flatMap(site -> site.harvestJobs().values().stream())
                .filter(value -> value.id().equals(subject)).findFirst().orElse(null);
        if (harvest != null) return FrontierV3HarvestDiagnosticJson.harvestProcess(checkpoint, state, harvest);
        var production = subject == null ? null : state.productionJobs().get(subject);
        if (production != null) return FrontierV3ProductionProcessDiagnosticJson.render(checkpoint, state, production);
        // A removed job is not a completion receipt. Report its retained exact
        // task separately so a diagnostic can distinguish success from cancellation.
        var task = subject == null ? null : state.strategicPlans().tasks().get(subject);
        if (task == null) return FrontierV3DiagnosticJson.unavailable("process", id, checkpoint, "not_found");
        return FrontierV3DiagnosticJson.base("process", id, checkpoint)
                + ",\"status\":\"ok\",\"family\":\"frontier.strategic-task\",\"identity\":{\"task\":\""
                + FrontierV3DiagnosticJson.quote(task.id().value()) + "\",\"owner\":\""
                + FrontierV3DiagnosticJson.quote(task.ownerId().value()) + "\",\"objective\":\""
                + FrontierV3DiagnosticJson.quote(task.objectiveId().value()) + "\"},\"result\":{\"kind\":\""
                + task.kind().name() + "\",\"taskStatus\":\"" + task.status().name() + "\"}}";
    }
    private FrontierV3ProcessDiagnosticJson() { }
}
