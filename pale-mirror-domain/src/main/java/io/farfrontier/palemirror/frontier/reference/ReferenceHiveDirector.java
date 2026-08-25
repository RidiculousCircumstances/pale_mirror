package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Source-port application boundary joining hive preparation, immutable
 * planning, post-assault exploitation and order execution.
 *
 * <p>This base director is exact for a Python world without the optional V2
 * territorial perception. The V2 adapter must pass a separately filtered
 * {@link ReferenceHiveWorldView}; it must never give the hive omniscient
 * sector data as a shortcut.</p>
 */
public final class ReferenceHiveDirector {
    private static final int PLANNING_INTERVAL_DAYS = 6;
    private final ReferenceHivePlanner planner;
    private final ReferenceHiveOrderExecutor executor;

    public ReferenceHiveDirector() {
        this(new ReferenceHivePlanner(), new ReferenceHiveOrderExecutor());
    }

    public ReferenceHiveDirector(ReferenceHivePlanner planner, ReferenceHiveOrderExecutor executor) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** Execute the source director for a non-V2 world using a fresh snapshot after preparation. */
    public List<ReferenceHiveOrder> step(ReferenceInfectionModel infection, Collection<ReferenceSettlement> settlements, int day) {
        Objects.requireNonNull(infection, "infection");
        Objects.requireNonNull(settlements, "settlements");
        if (day % PLANNING_INTERVAL_DAYS != 0) return List.of();
        infection.prepareHiveOrders(day);
        return execute(infection, ReferenceHiveWorldView.from(infection, settlements, day));
    }

    /**
     * Execute against a V2-filtered perception that was built after
     * {@link ReferenceInfectionModel#prepareHiveOrders(int)} for the same day.
     */
    public List<ReferenceHiveOrder> stepPerceived(ReferenceInfectionModel infection, ReferenceHiveWorldView perceived) {
        Objects.requireNonNull(infection, "infection");
        Objects.requireNonNull(perceived, "perceived");
        if (perceived.day() % PLANNING_INTERVAL_DAYS != 0) return List.of();
        return execute(infection, perceived);
    }

    private List<ReferenceHiveOrder> execute(ReferenceInfectionModel infection, ReferenceHiveWorldView view) {
        List<ReferenceHiveOrder> orders = new ArrayList<>(planner.plan(view));
        for (ReferenceHiveOrgan source : infection.organs().values()) {
            if (source.feral() || source.kind() != ReferenceOrganKind.BROOD_SAC && source.kind() != ReferenceOrganKind.SPORULATOR) continue;
            ReferenceGridPosition target = infection.exploitationTarget(source, view.day());
            if (target == null) continue;
            ReferenceBioformKind kind = source.kind() == ReferenceOrganKind.BROOD_SAC ? ReferenceBioformKind.HARVESTER : ReferenceBioformKind.SPORE_CARRIER;
            orders.add(0, ReferenceHiveOrder.launch(source.id(), target.x(), target.y(), kind, Map.of(), -1,
                    "exploit a successful assault through a separate biological operation"));
        }
        for (ReferenceHiveOrder order : orders) {
            boolean accepted = executor.execute(infection, order, view.day());
            if (accepted && (order.bioformKind() == ReferenceBioformKind.HARVESTER || order.bioformKind() == ReferenceBioformKind.SPORE_CARRIER)
                    && order.reason().contains("exploit a successful assault")) {
                infection.resolveExploitation(order.targetX(), order.targetY(), infection.organs().get(order.sourceId()), order.bioformKind());
            }
        }
        return List.copyOf(orders);
    }
}
