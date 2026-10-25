package io.farfrontier.palemirror.frontier.v3.model.forestry;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.*;

/** Forest-family continuation. UAE owns the actor, inventory owns the tool/cargo,
 * and the common navigator owns movement; no hidden per-block walking cursor. */
public record ForestryWork(SubjectId id, SubjectId home, ActorExecutionId execution, SubjectId toolId,
                           ShipmentEndpoint toolSource, ShipmentEndpoint storageTarget,
                           String outputKind, SubjectId carriedAccount, SubjectId outputLot, long cargoPart,
                           long revision, Phase phase, Optional<Target> target, Optional<WorkProgress> labour,
                           Optional<TreeFelling> felling, int appliedEffects, Optional<ForestryPhysicalStep> pending) {
    public enum Phase {
        TAKE_TOOL(1), SELECT_TREE(2), FELL(3), STORE(4), RETURN_TOOL(5), FINISHED(6);
        private final int tag;
        Phase(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Phase decode(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown forestry work phase");
        }
    }
    public record Target(TreeReference tree, long sourceRevision, SurfaceAnchor station, int expectedLogs) {
        public Target {
            Objects.requireNonNull(tree); Objects.requireNonNull(station);
            if (sourceRevision < 1 || expectedLogs < 1 || expectedLogs > io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtractionBatch.MAX_BLOCKS)
                throw new IllegalArgumentException("invalid retained forestry source target");
        }
    }
    public ForestryWork {
        Objects.requireNonNull(id); Objects.requireNonNull(home); Objects.requireNonNull(execution); Objects.requireNonNull(toolId);
        Objects.requireNonNull(toolSource); Objects.requireNonNull(storageTarget); Objects.requireNonNull(carriedAccount);
        Objects.requireNonNull(outputLot); Objects.requireNonNull(phase); Objects.requireNonNull(target); Objects.requireNonNull(labour);
        Objects.requireNonNull(felling); Objects.requireNonNull(pending);
        if (execution.activityKind() != ActorActivityKind.FORESTRY || !execution.activityOwnerId().equals(id)
                || !toolSource.settlementId().equals(home) || !storageTarget.settlementId().equals(home)
                || revision < 1 || cargoPart < 0 || appliedEffects < 0
                || outputKind == null || !outputKind.matches("[a-z0-9_]+:[a-z0-9_./-]+")
                || phase == Phase.FELL && target.isEmpty()
                || phase != Phase.TAKE_TOOL && phase != Phase.FELL && target.isPresent()
                || labour.isPresent() && phase != Phase.FELL || felling.isPresent() && phase != Phase.FELL
                || felling.isEmpty() && appliedEffects != 0
                || felling.filter(value -> !value.tree().equals(target.orElseThrow().tree())
                    || value.sourceRevision() != target.orElseThrow().sourceRevision()
                    || !value.batch().execution().equals(execution) || !value.batch().workStation().equals(target.orElseThrow().station())
                    || appliedEffects >= value.batch().effects().size()).isPresent()
                || pending.filter(step -> !step.observation().actuation().execution().equals(execution)
                    || step.observation().scopeRevision() != revision || terminalPhase(phase)).isPresent())
            throw new IllegalArgumentException("forestry work has a stale, incomplete or competing nominal declaration");
    }
    private static boolean terminalPhase(Phase phase) { return phase == Phase.FINISHED; }
    public boolean terminal() { return terminalPhase(phase); }
    public MovementOrder movementOrder() {
        var destination = switch (phase) {
            case TAKE_TOOL -> toolSource.station();
            case FELL -> target.orElseThrow().station();
            case STORE, RETURN_TOOL -> storageTarget.station();
            case SELECT_TREE, FINISHED -> throw new IllegalArgumentException("forestry phase has no admitted movement goal");
        };
        return new MovementOrder(id, execution.actorId(), phase.wireTag(), revision, List.of(destination),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    public ForestryWork withLabour(WorkProgress next) {
        if (phase != Phase.FELL || pending.isPresent() || felling.isPresent()) throw new IllegalArgumentException("forestry labour has no available work interval");
        return copy(execution, revision, phase, target, Optional.of(next), felling, appliedEffects, pending);
    }
    public ForestryWork beginFelling(TreeFelling prepared) {
        if (phase != Phase.FELL || pending.isPresent() || felling.isPresent() || labour.isEmpty())
            throw new IllegalArgumentException("forestry consequences require completed declared labour");
        return copy(execution, Math.addExact(revision, 1), phase, target, labour, Optional.of(prepared), 0, Optional.empty());
    }
    public ForestryWork prepare(ForestryPhysicalStep step) {
        if (pending.isPresent() || terminal()) throw new IllegalArgumentException("forestry already retains a physical consequence");
        return copy(execution, revision, phase, target, labour, felling, appliedEffects, Optional.of(step));
    }
    public ForestryWork observed(ForestryPhysicalStep step) {
        if (!pending.equals(Optional.of(step))) throw new IllegalArgumentException("forestry receipt substituted its prepared effect");
        return copy(execution, revision, phase, target, labour, felling, appliedEffects, Optional.empty());
    }
    public ForestryWork delivered(SubjectId nextAccount, SubjectId nextLot) {
        if (phase != Phase.STORE || pending.isPresent() || carriedAccount.equals(nextAccount) || outputLot.equals(nextLot))
            throw new IllegalArgumentException("forestry delivery lacks a settled changed cargo part");
        return new ForestryWork(id, home, execution, toolId, toolSource, storageTarget, outputKind, nextAccount, nextLot,
                Math.addExact(cargoPart, 1), Math.addExact(revision, 1), Phase.SELECT_TREE, Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty());
    }
    public ForestryWork selected(Target selected, ShipmentEndpoint storage) {
        if (phase != Phase.SELECT_TREE || pending.isPresent() || !storage.settlementId().equals(home))
            throw new IllegalArgumentException("forestry selection lost its safe same-home boundary");
        return new ForestryWork(id, home, execution, toolId, toolSource, storage, outputKind, carriedAccount, outputLot,
                cargoPart, Math.addExact(revision, 1), Phase.FELL, Optional.of(selected), Optional.empty(), Optional.empty(), 0, Optional.empty());
    }
    public ForestryWork retargetStorage(ShipmentEndpoint destination) {
        if (pending.isPresent() || phase != Phase.STORE && phase != Phase.RETURN_TOOL
                || !destination.settlementId().equals(home) || storageTarget.equals(destination))
            throw new IllegalArgumentException("forestry storage replacement lacks an unbegun same-home boundary");
        return new ForestryWork(id, home, execution, toolId, toolSource, destination, outputKind, carriedAccount, outputLot,
                cargoPart, Math.addExact(revision, 1), phase, target, labour, felling, appliedEffects, pending);
    }
    public ForestryWork childApplied() {
        if (phase != Phase.FELL || felling.isEmpty()) throw new IllegalArgumentException("forestry has no retained object consequence");
        if (appliedEffects + 1 == felling.orElseThrow().batch().effects().size())
            return copy(execution, Math.addExact(revision, 1), Phase.SELECT_TREE, Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty());
        return copy(execution, Math.addExact(revision, 1), phase, target, labour, felling, appliedEffects + 1, Optional.empty());
    }
    public ForestryWork next(Phase next, Optional<Target> selected) {
        if (pending.isPresent() || felling.isPresent() || terminal()) throw new IllegalArgumentException("forestry transition retains unresolved consequences");
        boolean legal = switch (phase) {
            case TAKE_TOOL -> next == Phase.FELL || next == Phase.SELECT_TREE || next == Phase.FINISHED;
            case SELECT_TREE -> next == Phase.FELL || next == Phase.STORE || next == Phase.RETURN_TOOL;
            case FELL -> next == Phase.SELECT_TREE || next == Phase.STORE || next == Phase.RETURN_TOOL;
            case RETURN_TOOL -> next == Phase.FINISHED;
            case STORE, FINISHED -> false;
        };
        if (!legal) throw new IllegalArgumentException("invalid forestry successor phase");
        return copy(execution, Math.addExact(revision, 1), next, selected, Optional.empty(), Optional.empty(), 0, Optional.empty());
    }
    /** Only the family reducer may declare source invalidation, after any native receipt settles. */
    public ForestryWork abandonSource() {
        if (pending.isPresent() || phase != Phase.FELL && phase != Phase.TAKE_TOOL)
            throw new IllegalArgumentException("source abandonment still retains an unsettled consequence");
        return copy(execution, Math.addExact(revision, 1), phase == Phase.TAKE_TOOL ? Phase.TAKE_TOOL : Phase.SELECT_TREE,
                Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty());
    }
    public ForestryWork resumed(ActorExecutionId successor) {
        if (terminal() || pending.isPresent() || !successor.actorId().equals(execution.actorId())
                || successor.activityKind() != execution.activityKind() || !successor.activityOwnerId().equals(id)
                || successor.generation() <= execution.generation() || felling.isPresent())
            throw new IllegalArgumentException("forestry resumption lost the exact safe continuation");
        return copy(successor, revision, phase, target, labour, felling, appliedEffects, pending);
    }
    private ForestryWork copy(ActorExecutionId execution, long revision, Phase phase, Optional<Target> target,
                               Optional<WorkProgress> labour, Optional<TreeFelling> felling, int applied,
                               Optional<ForestryPhysicalStep> pending) {
        return new ForestryWork(id, home, execution, toolId, toolSource, storageTarget, outputKind, carriedAccount, outputLot,
                cargoPart, revision, phase, target, labour, felling, applied, pending);
    }
}
