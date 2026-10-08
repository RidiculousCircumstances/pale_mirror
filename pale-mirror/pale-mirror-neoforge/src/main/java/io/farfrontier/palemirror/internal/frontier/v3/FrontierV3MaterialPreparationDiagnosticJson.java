package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;

/** Read-only explanation of the shared resource boundary, never a request to bind stock. */
final class FrontierV3MaterialPreparationDiagnosticJson {
    private FrontierV3MaterialPreparationDiagnosticJson() { }
    static String render(FrontierWorldState state, ActorContainerItemOrder order) {
        var portion = (ActorContainerItemOrder.Portion.Fungible) order.portion();
        String status; long epoch = 0; String reason = "";
        try {
            var review = MaterialSourcePreparation.review(state, order);
            status = review.status().name(); epoch = review.expectedEpoch();
        } catch (IllegalStateException | IllegalArgumentException contradiction) {
            status = "CANONICAL_CONTRADICTION"; reason = contradiction.getMessage();
        }
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(portion.sourceAccountId())).toList();
        return "{\"status\":" + string(status) + ",\"account\":" + string(portion.sourceAccountId().value())
                + ",\"kind\":" + string(portion.itemKind()) + ",\"quantity\":" + portion.quantity()
                + ",\"claim\":" + portion.claimId().map(id -> string(id.value())).orElse("null")
                + ",\"expectedEpoch\":" + epoch + ",\"bindingCount\":" + bindings.size()
                + ",\"bindingEpochs\":" + bindings.stream().map(PhysicalStackBinding::authorityEpoch).distinct().sorted().toList()
                + ",\"reason\":" + string(reason) + "}";
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
}
