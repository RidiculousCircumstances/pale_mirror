package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.SettlementManagementComposition;
import java.util.stream.Collectors;

/** Read-only bounded decision explanation, derived from the same management policy as execution. */
final class FrontierV3SettlementManagementDiagnostic {
    private FrontierV3SettlementManagementDiagnostic() { }
    static String render(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId owner = FrontierV3DiagnosticJson.subject(id).orElse(null);
        Settlement settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(owner)).findFirst().orElse(null);
        if (settlement == null) return FrontierV3DiagnosticJson.unavailable("settlement_management", id, checkpoint, "not_found");
        var decision = SettlementManagementComposition.MANAGEMENT.decide(state, settlement);
        var authority = state.strategicPlans().requireDecisionAuthority(owner);
        String offers = decision.considerations().stream().limit(32).map(value -> "{\"planner\":\""
                + FrontierV3DiagnosticJson.quote(value.planner()) + "\",\"operation\":\"" + value.offer().proposal().kind()
                + "\",\"priority\":\"" + value.offer().priority() + "\",\"utility\":" + value.offer().proposal().utility()
                + ",\"laneAvailable\":" + value.admitted() + ",\"requirements\":"
                + FrontierV3DiagnosticJson.strings(value.offer().requirements().stream().map(Enum::name).toList())
                + "}").collect(Collectors.joining(",", "[", "]"));
        String holds = decision.holds().stream().map(value -> "{\"planner\":\""
                + FrontierV3DiagnosticJson.quote(value.planner()) + "\",\"reason\":\"" + value.scope().reason()
                + "\",\"lane\":\"" + value.scope().lane() + "\"}").collect(Collectors.joining(",", "[", "]"));
        return "{\"schema\":1,\"kind\":\"settlement_management\",\"id\":\"" + FrontierV3DiagnosticJson.quote(id)
                + "\",\"revision\":" + checkpoint.revision().value() + ",\"instant\":" + checkpoint.instant().ticks()
                + ",\"authorityEpoch\":" + authority.reconsiderationEpoch() + ",\"selected\":\""
                + decision.selected().map(value -> value.kind().name()).orElse("") + "\",\"commitments\":"
                + FrontierV3DiagnosticJson.strings(authority.commitmentIds().stream().map(SubjectId::value).toList())
                + ",\"offers\":" + offers + ",\"holds\":" + holds + ",\"replacePendingTasks\":"
                + FrontierV3DiagnosticJson.strings(decision.replacePendingTasks().stream().map(SubjectId::value).toList()) + "}";
    }
}
