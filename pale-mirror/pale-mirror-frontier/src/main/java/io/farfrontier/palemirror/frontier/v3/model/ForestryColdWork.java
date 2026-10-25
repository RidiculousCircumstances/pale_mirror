package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse;
import java.util.*;

/** Forest-owned semantic reducer; source loss, tool wear and resource credit settle atomically.
 * Native consequences have a separate observed-receipt path and cannot use this COLD credit. */
public final class ForestryColdWork {
    private ForestryColdWork() { }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ForestryWorkAdvanced event, long tick) {
        var job = Objects.requireNonNull(state.forest().work().get(event.job()), "forestry work");
        if (!subject.equals(job.id()) || job.revision() != event.expectedRevision() || job.pending().isPresent()
                || state.actorMovements().containsKey(job.execution().actorId()))
            throw new IllegalArgumentException("forestry semantic operation has stale execution/physical authority");
        state.actorExecutions().requireCurrent(job.execution());
        var update = FrontierWorldStateUpdate.begin();
        switch (event.operation()) {
            case SELECT_TREE -> {
                var selected = (ForestryWorkAdvanced.TreeSelection) event.selection().orElseThrow();
                ForestryWorkSelection.require(state, job, selected);
                var forest = state.forest().find(selected.target().tree()).isPresent() ? state.forest() : state.forest().admit(selected.declaration());
                return state.withChanges(update.forest(forest.replaceWork(job, job.selected(selected.target(), selected.storage()))));
            }
            case SELECT_STORAGE -> {
                var selected = (ForestryWorkAdvanced.StorageSelection) event.selection().orElseThrow();
                var portion = ForestryWorkSelection.portion(state, job);
                if (WorkStorageSelection.available(state, portion, job.storageTarget()))
                    throw new IllegalArgumentException("available forestry storage cannot be silently replaced");
                WorkStorageSelection.require(state, portion, state.actorLocations().get(job.execution().actorId()).supportingSurface(),
                        new WorkStorageSelection.Selected(selected.storage(), selected.approach()));
                return state.withChanges(update.forest(state.forest().replaceWork(job, job.retargetStorage(selected.storage()))));
            }
            case ABANDON_TARGET -> {
                if (ForestryWorkSelection.targetCurrent(state, job) || job.target().isEmpty())
                    throw new IllegalArgumentException("forestry abandonment has no proven source change");
                return state.withChanges(update.forest(state.forest().replaceWork(job, job.abandonSource())));
            }
            case DELIVER_PART -> {
                if (ForestryWorkAuthority.carried(state, job) < 1)
                    throw new IllegalArgumentException("forestry cannot deliver an empty part");
                return state.withChanges(update.forest(state.forest().replaceWork(job, job.next(ForestryWork.Phase.STORE, Optional.empty()))));
            }
            case END_WORK -> {
                if (job.phase() == ForestryWork.Phase.TAKE_TOOL) {
                    var tool = Objects.requireNonNull(state.inventory().items().get(job.toolId()));
                    if (!(tool.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(job.toolSource().containerId())
                            || ForestryWorkAuthority.carried(state, job) != 0)
                        throw new IllegalArgumentException("unstarted forestry still holds actor resources");
                    return state.withChanges(update.forest(state.forest().replaceWork(job, job.next(ForestryWork.Phase.FINISHED, Optional.empty())))
                            .actorExecutions(state.actorExecutions().finish(job.execution())));
                }
                if (job.phase() != ForestryWork.Phase.SELECT_TREE && (job.phase() != ForestryWork.Phase.FELL
                        || ForestryStockDemand.requested(state, job.home(), job.outputKind(), Optional.of(job.id()))))
                    throw new IllegalArgumentException("forestry shutdown lacks its safe withdrawn work boundary");
                return state.withChanges(update.forest(state.forest().replaceWork(job, job.next(
                        ForestryWorkAuthority.carried(state, job) > 0 ? ForestryWork.Phase.STORE : ForestryWork.Phase.RETURN_TOOL, Optional.empty()))));
            }
            default -> { }
        }
        if (!job.movementOrder().arrivedAt(state.actorLocations().get(job.execution().actorId()).supportingSurface()))
            throw new IllegalArgumentException("forestry semantic operation lacks its actual work station");
        switch (event.operation()) {
            case LABOUR -> {
                if (!ResidentActivityCoordinator.mayStartOrdinaryWork(state, job.execution().actorId(), tick))
                    throw new IllegalArgumentException("forestry labour is outside its schedule/needs interval");
                return state.withChanges(update.forest(state.forest().replaceWork(job,
                        job.withLabour(ForestryWorkAuthority.labour(state, job, tick, true)))));
            }
            case BEGIN_FELLING -> {
                if (job.phase() != ForestryWork.Phase.FELL || !ForestryWorkSelection.targetCurrent(state, job)
                        || job.labour().isEmpty() || job.labour().orElseThrow().completedAt(tick) != job.labour().orElseThrow().requiredMilliWork())
                    throw new IllegalArgumentException("forestry felling lacks completed labour on its exact current tree");
                var tree = state.forest().require(job.target().orElseThrow().tree());
                if (!ForestryWorkSelection.serviceable(state, job, tree) || tree.remaining() + ForestryWorkAuthority.carried(state, job)
                        > state.bootstrap().ruleset().forestry().cargoItems())
                    throw new IllegalArgumentException("forestry felling exceeds its held equipment/cargo envelope");
                var batch = TreeFelling.prepareKnown(tree, job.execution(), job.target().orElseThrow().station(),
                        "forestry:" + job.id().value() + ":" + job.revision(), state.bootstrap().ruleset().forestry());
                return state.withChanges(update.forest(state.forest().replaceWork(job, job.beginFelling(batch))));
            }
            case APPLY_CHILD -> {
                if (job.phase() != ForestryWork.Phase.FELL || job.felling().isEmpty()
                        || !ActorExecutionCoordinator.coldAvailable(state, job.execution().actorId())
                        || ForestrySourceCustody.blocksCold(state, job.target().orElseThrow().tree()))
                    throw new IllegalArgumentException("COLD felling lacks exclusive actor/source authority");
                var batch = job.felling().orElseThrow(); var tree = state.forest().require(batch.tree());
                batch.requireProgress(tree, job.appliedEffects(), state.bootstrap().ruleset().forestry());
                var effect = batch.batch().effects().get(job.appliedEffects());
                var tool = Objects.requireNonNull(state.inventory().items().get(job.toolId()));
                if (!tool.custody().equals(new InventoryCustody.Actor(job.execution().actorId())))
                    throw new IllegalArgumentException("forest worker no longer holds its admitted axe");
                var use = ToolUse.prepare(tool, state.bootstrap().ruleset().tools(), effect.definition().toolRequirement());
                var output = effect.output().getFirst();
                if (effect.output().size() != 1 || !output.itemKind().equals(job.outputKind())
                        || Math.addExact(ForestryWorkAuthority.carried(state, job), output.quantity()) > state.bootstrap().ruleset().forestry().cargoItems())
                    throw new IllegalArgumentException("felling output changed or exceeds its retained cargo envelope");
                int log = tree.declaration().logs().indexOf(tree.declaration().origin(), effect.target());
                var ledger = state.inventory().fungibleResources().accrueColdActorExtractionPart(output,
                        ForestryWorkEffects.output(state, job, output), job.carriedAccount(), job.execution().actorId());
                return state.withChanges(update.inventory(state.inventory().changeCondition(use.before(), use.after().condition()).withFungibleResources(ledger))
                        .forest(state.forest().replace(tree, tree.extract(log, effect.operationId())).replaceWork(job, job.childApplied())));
            }
            case TAKE_TOOL, STORE, RETURN_TOOL -> {
                if (!ActorExecutionCoordinator.coldAvailable(state, job.execution().actorId())
                        || !ServiceAccessCoordinator.available(state, ForestryServiceAccess.identity(job)))
                    throw new IllegalArgumentException("COLD forestry interaction competes with physical/service authority");
                if (event.operation() == ForestryWorkAdvanced.Operation.STORE) {
                    if (job.phase() != ForestryWork.Phase.STORE || !WorkStorageSelection.available(state, ForestryWorkSelection.portion(state, job), job.storageTarget()))
                        throw new IllegalArgumentException("forestry cargo has no available storage");
                    var next = job.delivered(ForestryWorkEffects.partAccount(job, Math.addExact(job.cargoPart(), 1)),
                            ForestryWorkEffects.partLot(job, Math.addExact(job.cargoPart(), 1)));
                    return state.withChanges(update.inventory(ActorItemCustody.transferCold(state, ForestryWorkEffects.cargo(state, job)))
                            .forest(state.forest().replaceWork(job, next)));
                }
                boolean take = event.operation() == ForestryWorkAdvanced.Operation.TAKE_TOOL;
                if (job.phase() != (take ? ForestryWork.Phase.TAKE_TOOL : ForestryWork.Phase.RETURN_TOOL)
                        || !take && (ForestryWorkAuthority.carried(state, job) != 0
                            || !WorkStorageSelection.available(state, ForestryWorkSelection.portion(state, job), job.storageTarget())))
                    throw new IllegalArgumentException("forestry tool handoff differs from its actual phase/cargo");
                update.inventory(ActorItemCustody.transferCold(state, ForestryWorkEffects.equipment(state, job)));
                var next = job.next(take ? (job.target().isPresent() ? ForestryWork.Phase.FELL : ForestryWork.Phase.SELECT_TREE)
                        : ForestryWork.Phase.FINISHED, take ? job.target() : Optional.empty());
                if (!take) update.actorExecutions(state.actorExecutions().finish(job.execution()));
                return state.withChanges(update.forest(state.forest().replaceWork(job, next)));
            }
            default -> throw new IllegalArgumentException("forestry operation has no declared semantic reducer");
        }
    }
}
