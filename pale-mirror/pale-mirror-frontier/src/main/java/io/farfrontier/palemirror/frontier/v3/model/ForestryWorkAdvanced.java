package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;
import java.util.*;

/** Owner-issued exhaustive semantic step. Movement and physical outcomes have their separate owners. */
public record ForestryWorkAdvanced(SubjectId job, long expectedRevision, Operation operation, Optional<Selection> selection) implements FrontierPayload {
    public static final String TYPE = "frontier.forestry_work_advanced.v1";
    public enum Operation {
        TAKE_TOOL(1), LABOUR(2), BEGIN_FELLING(3), APPLY_CHILD(4), STORE(5), RETURN_TOOL(6),
        SELECT_TREE(7), SELECT_STORAGE(8), DELIVER_PART(9), END_WORK(10), ABANDON_TARGET(11);
        private final int tag;
        Operation(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Operation decode(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown forestry semantic operation");
        }
    }
    public sealed interface Selection permits TreeSelection, StorageSelection { }
    public record TreeSelection(TreeDescriptor declaration, ForestryWork.Target target, Optional<PedestrianRouteReceipt> approach,
                                 ShipmentEndpoint storage, Optional<PedestrianRouteReceipt> delivery) implements Selection {
        public TreeSelection { Objects.requireNonNull(declaration); Objects.requireNonNull(target); Objects.requireNonNull(approach);
            Objects.requireNonNull(storage); Objects.requireNonNull(delivery);
            if (!target.tree().matches(declaration)) throw new IllegalArgumentException("selected tree identity changed"); }
    }
    public record StorageSelection(ShipmentEndpoint storage, Optional<PedestrianRouteReceipt> approach) implements Selection {
        public StorageSelection { Objects.requireNonNull(storage); Objects.requireNonNull(approach); }
    }
    public ForestryWorkAdvanced {
        Objects.requireNonNull(job); Objects.requireNonNull(operation); Objects.requireNonNull(selection);
        if (expectedRevision < 1 || (operation == Operation.SELECT_TREE || operation == Operation.SELECT_STORAGE) != selection.isPresent()
                || operation == Operation.SELECT_TREE && !(selection.orElse(null) instanceof TreeSelection)
                || operation == Operation.SELECT_STORAGE && !(selection.orElse(null) instanceof StorageSelection))
            throw new IllegalArgumentException("forestry successor lacks its exhaustive typed selection");
    }
    public ForestryWorkAdvanced(SubjectId job, long revision, Operation operation) { this(job, revision, operation, Optional.empty()); }
    @Override public String type() { return TYPE; }
}
