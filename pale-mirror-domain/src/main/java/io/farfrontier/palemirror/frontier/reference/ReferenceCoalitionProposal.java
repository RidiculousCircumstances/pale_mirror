package io.farfrontier.palemirror.frontier.reference;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable coalition proposal ported from Python {@code CoalitionProposal}. */
public final class ReferenceCoalitionProposal {
    private final int id;
    private final int proposerId;
    private final ReferenceOperationKind operationKind;
    private final ReferenceTargetRef target;
    private final int createdDay;
    private final int expiresDay;
    private final EnumMap<ReferenceResource, Double> requested;
    private final LinkedHashSet<Integer> acceptedBy = new LinkedHashSet<>();
    private final LinkedHashSet<Integer> refusedBy = new LinkedHashSet<>();
    private String status = "open";

    public ReferenceCoalitionProposal(int id, int proposerId, ReferenceOperationKind operationKind, ReferenceTargetRef target,
                                      int createdDay, int expiresDay, Map<ReferenceResource, Double> requested) {
        this.id = id;
        this.proposerId = proposerId;
        this.operationKind = Objects.requireNonNull(operationKind, "operationKind");
        this.target = Objects.requireNonNull(target, "target");
        this.createdDay = createdDay;
        this.expiresDay = expiresDay;
        this.requested = new EnumMap<>(ReferenceResource.class);
        this.requested.putAll(Objects.requireNonNull(requested, "requested"));
    }

    public int id() { return id; }
    public int proposerId() { return proposerId; }
    public ReferenceOperationKind operationKind() { return operationKind; }
    public ReferenceTargetRef target() { return target; }
    public int createdDay() { return createdDay; }
    public int expiresDay() { return expiresDay; }
    public Map<ReferenceResource, Double> requested() { return Collections.unmodifiableMap(new EnumMap<>(requested)); }
    public Set<Integer> acceptedBy() { return Collections.unmodifiableSet(new LinkedHashSet<>(acceptedBy)); }
    public Set<Integer> refusedBy() { return Collections.unmodifiableSet(new LinkedHashSet<>(refusedBy)); }
    public String status() { return status; }
    public void accept(int settlementId) { acceptedBy.add(settlementId); }
    public void refuse(int settlementId) { refusedBy.add(settlementId); }
    public void status(String value) { status = Objects.requireNonNull(value, "status"); }
}
