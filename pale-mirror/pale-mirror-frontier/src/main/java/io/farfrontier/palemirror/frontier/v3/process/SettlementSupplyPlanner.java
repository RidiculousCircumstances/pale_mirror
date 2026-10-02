package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Comparator;
import java.util.Optional;

/** Profile owner proposes operations; it never commits goals or takes a resident body. */
final class SettlementSupplyPlanner implements SettlementOperationPlanner {
    @Override public String id() { return "frontier:supply"; }
    @Override public Assessment assess(FrontierWorldState state, Settlement settlement) {
        boolean constructionActive = state.routeConstructions().values().stream().anyMatch(project -> project.settlementId().equals(settlement.id()));
        boolean alreadyConfirmed = state.strategicPlans().routePatrols().values().stream().anyMatch(patrol -> patrol.settlementId().equals(settlement.id())
                && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED && patrol.obstruction().stream().anyMatch(state.physicalDeltas()::containsKey));
        Optional<RouteLoss> causalLoss = failedRouteLoss(state, settlement.id());
        boolean blockedRoute = !state.routeTopology().supplyPassable(state.bootstrap(), settlement.id());
        boolean operationOwnsRouteLoss = state.operations().values().stream()
                .filter(operation -> operation.stage() == OperationStage.ASSEMBLING || operation.stage() == OperationStage.EN_ROUTE
                        || operation.stage() == OperationStage.RETURNING || operation.stage() == OperationStage.ARRIVED
                        || operation.stage() == OperationStage.FAILED || operation.stage() == OperationStage.INTERRUPTED)
                .anyMatch(operation -> state.physicalDeltas().values().stream().anyMatch(delta -> isOwnedRouteLoss(delta)
                        && FrontierRouteNetwork.containsOperationSurfaceCell(operation.route(), delta.position())));
        // A confirmed physical logistics failure outranks ordinary production and containment
        // selection.  It does not cancel an already active task; lane ownership remains the
        // sole authority for that decision.
        // A patrol proves one exact physical loss.  Its independent maintenance owner
        // repairs that retained cell; it is not authorization to invent a replacement
        // corridor.  A future re-route policy must be an explicit graph decision with
        // its own evidence, never an accidental consequence of inspection completion.
        if (!constructionActive && alreadyConfirmed) return Assessment.held(Reason.ROUTE_RECOVERY_ALREADY_OWNED);
        // An active convoy has the narrower retained cause and will publish its
        // operation-backed inspection on terminal failure. A periodic review
        // must not race that source with duplicate generic settlement patrols.
        if (!constructionActive && !alreadyConfirmed && causalLoss.isEmpty() && operationOwnsRouteLoss) return Assessment.held(Reason.ROUTE_OBSERVATION_ALREADY_OWNED);
        if (!constructionActive && !alreadyConfirmed && (blockedRoute || causalLoss.isPresent())) {
            return Assessment.offer(settlement.id(), causalLoss.map(loss -> new StrategicOperationProposal(StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE,
                    Optional.empty(), Optional.empty(), Optional.of(loss.operationId()), Optional.of(loss.position()), Long.MAX_VALUE))
                    .orElseGet(() -> new StrategicOperationProposal(StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE, Optional.empty(), Long.MAX_VALUE)), Priority.CRITICAL);
        }
        return Assessment.empty();
    }
    private static Optional<RouteLoss> failedRouteLoss(FrontierWorldState state, SubjectId settlementId) {
        return state.operations().values().stream().filter(operation -> operation.settlementId().equals(settlementId))
                .filter(operation -> operation.stage() == OperationStage.FAILED || operation.stage() == OperationStage.INTERRUPTED)
                .sorted(Comparator.comparing(RouteOperation::id)).flatMap(operation -> state.physicalDeltas().values().stream()
                        .filter(SettlementSupplyPlanner::isOwnedRouteLoss)
                        .map(PhysicalDelta::position).filter(position -> FrontierRouteNetwork.containsOperationSurfaceCell(operation.route(), position))
                        .sorted(Comparator.comparingInt(BlockPosition::x).thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z))
                        .map(position -> new RouteLoss(operation.id(), position))).findFirst();
    }
    private static boolean isOwnedRouteLoss(PhysicalDelta delta) {
        return delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS
                && delta.semanticTarget().filter(target -> target.kind() == PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK
                && FrontierRouteNetwork.OWNER.equals(target.subjectId())).isPresent()
                && delta.semanticPart().filter(part -> part == GrayboxSemanticPart.ROUTE_SURFACE
                || part == GrayboxSemanticPart.ROUTE_FOUNDATION).isPresent();
    }
    private record RouteLoss(SubjectId operationId, BlockPosition position) { }
}
