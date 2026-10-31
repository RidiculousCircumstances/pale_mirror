package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
/** Guard-owned semantic transitions; issue effects use the common physical/actor-item protocol. */
public sealed interface GuardDutyEvent extends FrontierPayload permits GuardDutyEvent.Appointed, GuardDutyEvent.Changed {
    record Appointed(GuardDuty duty) implements GuardDutyEvent {
        public static final String TYPE = "frontier.guard.appointed";
        public Appointed { Objects.requireNonNull(duty); }
        public String type() { return TYPE; }
    }
    enum Change {
        COLD_ISSUED(1), EQUIPPED(2), RESELECTED(3), ENDED(4), RETURN_SELECTED(5), COLD_RETURNED(6), GOAL_WITHDRAWN(7);
        private final int tag;
        Change(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Change require(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown guard change");
        }
    }
    record Selection(SubjectId item, ShipmentEndpoint source) {
        public Selection { Objects.requireNonNull(item); Objects.requireNonNull(source); }
    }
    record Changed(SubjectId duty, long revision, Change change, Optional<Selection> selection) implements GuardDutyEvent {
        public static final String TYPE = "frontier.guard.changed";
        public Changed {
            Objects.requireNonNull(duty); Objects.requireNonNull(change); selection = Objects.requireNonNull(selection);
            if (revision < 1 || (change == Change.RESELECTED || change == Change.RETURN_SELECTED) != selection.isPresent())
                throw new IllegalArgumentException("guard change has an incomplete nominal declaration");
        }
        public String type() { return TYPE; }
    }
}
