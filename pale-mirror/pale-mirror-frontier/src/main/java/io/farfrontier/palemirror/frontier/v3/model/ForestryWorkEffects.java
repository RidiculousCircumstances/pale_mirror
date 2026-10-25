package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryWork;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import java.util.*;

/** Forestry declares its portion; common equipment, hand and resource owners execute custody. */
public final class ForestryWorkEffects {
    private ForestryWorkEffects() { }
    public static ActorContainerItemOrder equipment(FrontierWorldState state, ForestryWork job) {
        return equipment(state, job, Optional.empty());
    }
    public static ActorContainerItemOrder equipment(FrontierWorldState state, ForestryWork job,
            Optional<InventoryCustody.ContainerSlot> retainedSlot) {
        boolean take = job.phase() == ForestryWork.Phase.TAKE_TOOL;
        if (!take && job.phase() != ForestryWork.Phase.RETURN_TOOL)
            throw new IllegalArgumentException("forestry phase has no tool transfer");
        var tool = Objects.requireNonNull(state.inventory().items().get(job.toolId()), "retained forestry tool");
        if (!tool.economicOwnerId().equals(job.home()) || tool.count() != 1
                || state.bootstrap().ruleset().tools().find(tool.itemKind()).isEmpty()
                || take && !state.bootstrap().ruleset().tools().serviceable(tool, state.bootstrap().ruleset().forestry().tool()))
            throw new IllegalArgumentException("forestry tool changed its admitted ownership or ability");
        return WorkEquipmentOrders.create(state, job.id(), job.execution().actorId(), tool,
                take ? job.toolSource() : job.storageTarget(),
                take ? ActorContainerItemOrder.Direction.TAKE : ActorContainerItemOrder.Direction.PLACE,
                retainedSlot, job.phase().wireTag(), job.revision());
    }
    public static ActorContainerItemOrder cargo(FrontierWorldState state, ForestryWork job) {
        if (job.phase() != ForestryWork.Phase.STORE) throw new IllegalArgumentException("forestry is not delivering its cargo");
        return WorkCargoOrders.store(state, job.id(), job.execution().actorId(), job.carriedAccount(), job.outputKind(),
                job.storageTarget(), job.phase().wireTag(), job.revision());
    }
    public static ResourceLot output(FrontierWorldState state, ForestryWork job, BlockExtraction.Output output) {
        if (!output.itemKind().equals(job.outputKind())) throw new IllegalArgumentException("forestry consequence changed its admitted output");
        var prior = state.inventory().fungibleResources().lots().get(job.outputLot());
        return prior == null ? new ResourceLot(job.outputLot(), job.home(), output.itemKind(), output.quantity(),
                "forestry:" + job.id().value(), List.of()) : prior.withQuantity(Math.addExact(prior.quantity(), output.quantity()));
    }
    public static SubjectId partAccount(ForestryWork job, long part) { return new SubjectId("custody:" + job.id().value().replace(':', '-') + "/part-" + part); }
    public static SubjectId partLot(ForestryWork job, long part) { return new SubjectId("lot:" + job.id().value().replace(':', '-') + "/part-" + part); }
}
