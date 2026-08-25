package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Source-exact mutation boundary for orders selected by {@link ReferenceHivePlanner}. */
public final class ReferenceHiveOrderExecutor {
    public boolean execute(ReferenceInfectionModel infection, ReferenceHiveOrder order, int day) {
        Objects.requireNonNull(infection, "infection");
        Objects.requireNonNull(order, "order");
        ReferenceHiveOrgan source = infection.organs().get(order.sourceId());
        if (source == null) return false;
        if (order.kind() == ReferenceHiveOrderKind.MORPH_ORGAN && order.organKind() != null) {
            return infection.startMorphogenesis(source, order.organKind(), order.targetX(), order.targetY(), day);
        }
        if (order.kind() == ReferenceHiveOrderKind.LAUNCH_BIOFORM && order.bioformKind() != null) {
            ReferenceSwarm launched = infection.launchBioform(source, order.bioformKind(), order.targetX(), order.targetY(), order.targetId(), order.composition());
            if (launched != null) {
                source.lastProjectDay(day);
                return true;
            }
        }
        return false;
    }
}
