package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Exact equipment reservation retained by the work owner; inventory alone owns custody/condition. */
public record WorkToolAssignment(SubjectId itemId, ShipmentEndpoint source, Stage stage, java.util.Optional<WorkEquipmentStep> pending,
                                java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse> lastUse,
                                java.util.Optional<ShipmentEndpoint> returnTarget) {
    public enum Stage {
        ACQUIRING(1), EQUIPPED(2), RETURNED(3), RELEASED(4);
        private final int tag;
        Stage(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Stage decode(int tag) {
            for (var stage : values()) if (stage.tag == tag) return stage;
            throw new IllegalArgumentException("unknown work equipment stage");
        }
    }
    public WorkToolAssignment(SubjectId itemId, ShipmentEndpoint source, Stage stage) {
        this(itemId, source, stage, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
    }
    public WorkToolAssignment {
        Objects.requireNonNull(itemId); Objects.requireNonNull(source); Objects.requireNonNull(stage); Objects.requireNonNull(pending); Objects.requireNonNull(lastUse);
        Objects.requireNonNull(returnTarget);
        if (returnTarget.filter(target -> !target.settlementId().equals(source.settlementId())).isPresent()
                || stage == Stage.ACQUIRING && returnTarget.isPresent())
            throw new IllegalArgumentException("equipment return has a foreign owner or precedes acquisition");
        if (pending.filter(step -> !step.item().id().equals(itemId)).isPresent() || (stage == Stage.RETURNED || stage == Stage.RELEASED) && pending.isPresent())
            throw new IllegalArgumentException("equipment assignment retains a foreign or terminal transfer");
        if (lastUse.filter(use -> !use.before().id().equals(itemId)).isPresent()
                || lastUse.isPresent() && stage != Stage.EQUIPPED)
            throw new IllegalArgumentException("equipment use belongs to another tool or unequipped assignment");
    }
    public WorkToolAssignment prepare(WorkEquipmentStep step) {
        if (pending.isPresent() || settled()) throw new IllegalArgumentException("equipment already has an effect or is returned");
        return new WorkToolAssignment(itemId, source, stage, java.util.Optional.of(step), lastUse, returnTarget);
    }
    public WorkToolAssignment withUse(java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse> use) {
        if (stage != Stage.EQUIPPED || pending.isPresent() || use.filter(value -> !value.before().id().equals(itemId)).isPresent())
            throw new IllegalArgumentException("work use requires this equipped individual tool");
        return new WorkToolAssignment(itemId, source, stage, pending, use, returnTarget);
    }
    public WorkToolAssignment returningTo(ShipmentEndpoint endpoint) {
        if (stage != Stage.EQUIPPED || pending.isPresent()) throw new IllegalArgumentException("return destination requires an idle held tool");
        return new WorkToolAssignment(itemId, source, stage, pending, lastUse, java.util.Optional.of(endpoint));
    }
    public boolean settled() { return stage == Stage.RETURNED || stage == Stage.RELEASED; }
    public WorkToolAssignment cancelUnapplied() {
        if (pending.isEmpty()) throw new IllegalArgumentException("equipment has no pending effect to cancel");
        return new WorkToolAssignment(itemId, source, stage, java.util.Optional.empty(), lastUse, returnTarget);
    }
    public WorkToolAssignment releaseUnacquired() {
        if (stage != Stage.ACQUIRING || pending.isPresent()) throw new IllegalArgumentException("only unacquired equipment may be released");
        return new WorkToolAssignment(itemId, source, Stage.RELEASED);
    }
    public WorkToolAssignment transferred() {
        return new WorkToolAssignment(itemId, source, switch (stage) {
            case ACQUIRING -> Stage.EQUIPPED;
            case EQUIPPED -> Stage.RETURNED;
            case RETURNED, RELEASED -> throw new IllegalArgumentException("equipment already released");
        }, java.util.Optional.empty(), java.util.Optional.empty(), returnTarget);
    }
}
