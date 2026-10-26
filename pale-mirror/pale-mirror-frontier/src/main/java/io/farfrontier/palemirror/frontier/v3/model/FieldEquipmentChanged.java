package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

public record FieldEquipmentChanged(SubjectId siteId, SubjectId jobId, WorkToolAssignment.Stage expected,
                                    Action action, Optional<WorkEquipmentStep> physical,
                                    Optional<ShipmentEndpoint> returnTarget) implements FrontierPayload {
    public enum Action {
        COLD_TRANSFER(1), PREPARE(2), CONFIRM(3), SELECT_RETURN(4), RETIRED_APPLIED(5), RETIRED_UNAPPLIED(6), ABORT_UNATTEMPTED(7);
        private final int tag;
        Action(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Action decode(int tag) {
            for (var action : values()) if (action.tag == tag) return action;
            throw new IllegalArgumentException("unknown equipment transition");
        }
    }
    public FieldEquipmentChanged(SubjectId siteId, SubjectId jobId, WorkToolAssignment.Stage expected,
                                 Action action, Optional<WorkEquipmentStep> physical) {
        this(siteId, jobId, expected, action, physical, Optional.empty());
    }
    public FieldEquipmentChanged {
        Objects.requireNonNull(siteId); Objects.requireNonNull(jobId); Objects.requireNonNull(expected);
        Objects.requireNonNull(action); Objects.requireNonNull(physical); Objects.requireNonNull(returnTarget);
        if (expected == WorkToolAssignment.Stage.RETURNED || expected == WorkToolAssignment.Stage.RELEASED
                || (action != Action.COLD_TRANSFER && action != Action.SELECT_RETURN) != physical.isPresent()
                || (action == Action.SELECT_RETURN) != returnTarget.isPresent())
            throw new IllegalArgumentException("equipment transition lacks exact mode-specific evidence");
    }
    @Override public String type() { return "frontier.field_equipment_changed"; }
    @Override public boolean requiresDurableBeforeEffect() { return physical.isPresent(); }
}
