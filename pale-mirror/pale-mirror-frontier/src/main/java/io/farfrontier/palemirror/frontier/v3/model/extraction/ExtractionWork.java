package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.*;

/** Extraction owns a source/tool/work continuation, not a body, route or second resource balance. */
public record ExtractionWork(SubjectId id, SubjectId siteId, ActorExecutionId execution, SubjectId toolId,
        InventoryCustody.ContainerSlot toolReturnSlot, String outputKind, SubjectId carriedAccountId,
        SubjectId outputLotId, long batch, Phase phase, long revision, Optional<ExtractionTarget> target,
        Optional<WorkProgress> labour, Optional<ExtractionPhysicalStep> pending) {
    public enum Phase {
        TAKE_TOOL(1), EXTRACT(2), STORE(3), RETURN_TOOL(4), FINISHED(5), SELECT_SOURCE(6);
        private final int tag;
        Phase(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Phase decode(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown extraction work phase");
        }
    }
    public ExtractionWork {
        Objects.requireNonNull(id); Objects.requireNonNull(siteId); Objects.requireNonNull(execution); Objects.requireNonNull(toolId);
        Objects.requireNonNull(toolReturnSlot); Objects.requireNonNull(outputKind); Objects.requireNonNull(carriedAccountId);
        Objects.requireNonNull(outputLotId); Objects.requireNonNull(phase); Objects.requireNonNull(target);
        Objects.requireNonNull(labour); Objects.requireNonNull(pending);
        if (!execution.activityOwnerId().equals(id) || execution.activityKind() != ActorActivityKind.EXTRACTION
                || batch < 0 || revision < 1 || !outputKind.matches("[a-z0-9_]+:[a-z0-9_./-]+")
                || target.filter(value -> !value.key().owner().equals(siteId)).isPresent()
                || (phase == Phase.TAKE_TOOL || phase == Phase.EXTRACT) != target.isPresent()
                || labour.isPresent() && phase != Phase.EXTRACT
                || pending.isPresent() && (phase == Phase.FINISHED
                    || !pending.orElseThrow().observation().actuation().execution().equals(execution)
                    || pending.orElseThrow().observation().scopeRevision() != revision))
            throw new IllegalArgumentException("extraction work has a missing, stale or foreign nominal declaration");
        pending.ifPresent(step -> {
            boolean correct = switch (step) {
                case ExtractionPhysicalStep.Equipment ignored -> phase == Phase.TAKE_TOOL || phase == Phase.RETURN_TOOL;
                case ExtractionPhysicalStep.BlockWork ignored -> phase == Phase.EXTRACT;
                case ExtractionPhysicalStep.Cargo ignored -> phase == Phase.STORE;
            };
            if (!correct) throw new IllegalArgumentException("extraction effect does not match its declared work operation");
        });
    }
    public boolean terminal() { return phase == Phase.FINISHED; }
    public MovementOrder movementOrder(ExtractionSite site) {
        if (!site.id().equals(siteId) || terminal()) throw new IllegalArgumentException("work has no current declared extraction movement");
        SurfaceAnchor destination = phase == Phase.EXTRACT
                ? site.layout().require(target.orElseThrow().key().cell()).workstation() : site.layout().storagePort();
        return new MovementOrder(id, execution.actorId(), phase.wireTag(), revision, List.of(destination),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    public ExtractionWork withLabour(WorkProgress next) {
        if (phase != Phase.EXTRACT || pending.isPresent()) throw new IllegalArgumentException("non-working extraction cannot accrue labour");
        return new ExtractionWork(id, siteId, execution, toolId, toolReturnSlot, outputKind, carriedAccountId,
                outputLotId, batch, phase, revision, target, Optional.of(next), pending);
    }
    public ExtractionWork prepare(ExtractionPhysicalStep step) {
        if (terminal() || pending.isPresent()) throw new IllegalArgumentException("extraction already retains an effect");
        return new ExtractionWork(id, siteId, execution, toolId, toolReturnSlot, outputKind, carriedAccountId,
                outputLotId, batch, phase, revision, target, labour, Optional.of(step));
    }
    public ExtractionWork resumed(ActorExecutionId successor) {
        if (terminal() || pending.isPresent() || !successor.actorId().equals(execution.actorId())
                || !successor.activityOwnerId().equals(id) || successor.activityKind() != ActorActivityKind.EXTRACTION
                || successor.generation() <= execution.generation())
            throw new IllegalArgumentException("extraction successor lost its exact continuation");
        return new ExtractionWork(id, siteId, successor, toolId, toolReturnSlot, outputKind, carriedAccountId,
                outputLotId, batch, phase, revision, target, labour, pending);
    }
    /** The owner supplies the exhaustive next semantic target; no hidden cursor or automatic stock mutation. */
    public ExtractionWork transition(Phase next, Optional<ExtractionTarget> nextTarget) {
        if (terminal()) throw new IllegalArgumentException("finished extraction cannot restart");
        boolean legal = switch (phase) {
            case TAKE_TOOL -> next == Phase.EXTRACT || next == Phase.RETURN_TOOL || next == Phase.FINISHED;
            case EXTRACT -> next == Phase.EXTRACT || next == Phase.STORE || next == Phase.RETURN_TOOL || next == Phase.SELECT_SOURCE;
            case STORE -> false; // Only a settled whole-batch receipt may start the next part.
            case SELECT_SOURCE -> next == Phase.EXTRACT || next == Phase.STORE || next == Phase.RETURN_TOOL;
            case RETURN_TOOL -> next == Phase.FINISHED;
            case FINISHED -> false;
        };
        if (!legal) throw new IllegalArgumentException("invalid extraction lifecycle transition");
        return new ExtractionWork(id, siteId, execution, toolId, toolReturnSlot, outputKind, carriedAccountId,
                outputLotId, batch, next, Math.addExact(revision, 1), nextTarget, Optional.empty(), Optional.empty());
    }
    /** The job and tool survive delivery; only the bounded resource part receives new identities. */
    public ExtractionWork delivered() {
        if (phase != Phase.STORE) throw new IllegalArgumentException("only stored extraction can roll its resource part");
        long nextBatch = Math.addExact(batch, 1);
        String part = id.value().replace(':', '/') + "/batch-" + nextBatch;
        return new ExtractionWork(id, siteId, execution, toolId, toolReturnSlot, outputKind,
                new SubjectId("custody:extraction/" + part),
                new SubjectId("lot:extraction/" + part),
                nextBatch, Phase.SELECT_SOURCE, Math.addExact(revision, 1), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
