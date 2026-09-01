package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Exact durable approaches of a named convoy before it may start route travel. */
public record OperationAssembly(Map<SubjectId, Member> members, SubjectId cargoCarrierId, Optional<OperationAssemblyDeferral> deferral) {
    public static final int MAX_COLD_ADVANCE = 32;
    public OperationAssembly(Map<SubjectId, Member> members, SubjectId cargoCarrierId) { this(members, cargoCarrierId, Optional.empty()); }
    public OperationAssembly {
        Objects.requireNonNull(members, "assembly members"); cargoCarrierId = Objects.requireNonNull(cargoCarrierId, "assembly cargo carrier"); deferral = Objects.requireNonNull(deferral, "assembly deferral");
        Map<SubjectId, Member> copy = new LinkedHashMap<>();
        members.forEach((actor, member) -> {
            if (copy.put(Objects.requireNonNull(actor, "assembly actor"), Objects.requireNonNull(member, "assembly member")) != null) throw new IllegalArgumentException("duplicate assembly actor");
        });
        if (copy.isEmpty() || copy.size() > 8 || !copy.containsKey(cargoCarrierId)) throw new IllegalArgumentException("assembly requires its cargo carrier and 1..8 members");
        if (copy.values().stream().map(Member::destinationSurface).distinct().count() != copy.size()) throw new IllegalArgumentException("assembly destinations must be distinct");
        if (copy.values().stream().map(Member::currentSurface).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("assembly current positions must be distinct");
        }
        if (deferral.isPresent()) {
            OperationAssemblyDeferral blocked = deferral.orElseThrow(); Member member = copy.get(blocked.actorId());
            if (member == null || member.arrived() || !member.nextSurface().equals(blocked.target())) {
                throw new IllegalArgumentException("assembly deferral must name one exact next member cursor");
            }
        }
        members = Map.copyOf(copy);
    }
    public boolean complete() { return members.values().stream().allMatch(Member::arrived); }
    public Map<SubjectId, SurfaceAnchor> positions() { Map<SubjectId, SurfaceAnchor> result = new LinkedHashMap<>(); members.forEach((actor, member) -> result.put(actor, member.currentSurface())); return Map.copyOf(result); }

    /**
     * The shipment is a separate physical object, never an invisible extension of its hauler.
     * The supply compiler reserves adjacent hauler/escort slots at a public port; reflecting
     * the hauler across that escort yields the third outward cargo slot.
     */
    public TransportAnchor cargoAnchor() {
        if (!complete()) throw new IllegalStateException("only a complete assembly reserves its physical cargo anchor");
        SurfaceAnchor hauler = members.get(cargoCarrierId).currentSurface();
        SurfaceAnchor cargo = members.entrySet().stream().filter(entry -> !entry.getKey().equals(cargoCarrierId))
                .map(Map.Entry::getValue).map(Member::currentSurface)
                .filter(escort -> Math.abs(hauler.x() - escort.x()) + Math.abs(hauler.z() - escort.z()) == 1)
                .sorted(java.util.Comparator.comparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z))
                .findFirst().map(escort -> escort.offset(escort.x() - hauler.x(), 0, escort.z() - hauler.z()))
                .orElseThrow(() -> new IllegalStateException("assembly needs one adjacent escort to reserve a separate cargo anchor"));
        if (positions().containsValue(cargo)) {
            throw new IllegalStateException("assembly cargo anchor must remain distinct from every exact actor");
        }
        return new TransportAnchor(cargo);
    }

    /** Advances only the already compiled approaches; callers cannot retarget a convoy mid-assembly. */
    public OperationAssembly advance(Map<SubjectId, Member> nextMembers) {
        Objects.requireNonNull(nextMembers, "next assembly members");
        if (!members.keySet().equals(nextMembers.keySet())) throw new IllegalArgumentException("assembly advance changes formation");
        Map<SubjectId, Member> next = new LinkedHashMap<>();
        SubjectId advancedActor = null;
        for (Map.Entry<SubjectId, Member> entry : members.entrySet()) {
            Member current = entry.getValue(); Member candidate = Objects.requireNonNull(nextMembers.get(entry.getKey()), "next assembly member");
            if (!current.topology().equals(candidate.topology()) || candidate.cursor() < current.cursor()
                    || candidate.cursor() > current.nextColdCursor()) {
                throw new IllegalArgumentException("assembly member must advance its existing bounded corridor");
            }
            if (candidate.cursor() > current.cursor()) {
                if (advancedActor != null && deferral.isPresent()) {
                    throw new IllegalArgumentException("loaded-world assembly deferral permits only its blocked member to advance");
                }
                advancedActor = entry.getKey();
            }
            next.put(entry.getKey(), candidate);
        }
        if (advancedActor == null) throw new IllegalArgumentException("assembly advance must move at least one member");
        if (deferral.isPresent()) {
            OperationAssemblyDeferral blocked = deferral.orElseThrow();
            Member prior = members.get(blocked.actorId()), advanced = next.get(blocked.actorId());
            if (!blocked.actorId().equals(advancedActor) || advanced.cursor() != prior.cursor() + 1
                    || !advanced.currentSurface().equals(blocked.target())) {
                throw new IllegalArgumentException("loaded-world assembly deferral may clear only through its blocked exact next cursor");
            }
        }
        return new OperationAssembly(next, cargoCarrierId);
    }

    public OperationAssembly defer(OperationAssemblyDeferral nextDeferral) {
        nextDeferral = Objects.requireNonNull(nextDeferral, "assembly deferral");
        if (deferral.isPresent() && !deferral.orElseThrow().equals(nextDeferral)) {
            throw new IllegalArgumentException("assembly already has a different loaded-world deferral");
        }
        return new OperationAssembly(members, cargoCarrierId, Optional.of(nextDeferral));
    }

    /** One exact member's immutable pedestrian topology and its sole assembly cursor. */
    public record Member(TraversalTopology topology, int cursor) {
        public Member {
            topology = Objects.requireNonNull(topology, "assembly topology");
            if (topology.nodes().size() > OperationTravel.MAX_CELLS || topology.edges().stream().anyMatch(edge -> edge.kind() != TraversalKind.PEDESTRIAN
                    || !edge.traversableBy(TraversalCapability.PEDESTRIAN))) {
                throw new IllegalArgumentException("assembly requires one open pedestrian topology");
            }
            if (cursor < 0 || cursor >= topology.linearCorridorSurfaces().size()) throw new IllegalArgumentException("assembly cursor is outside topology");
        }
        public java.util.List<SurfaceAnchor> corridor() { return topology.linearCorridorSurfaces(); }
        public SurfaceAnchor currentSurface() { return corridor().get(cursor); }
        public SurfaceAnchor nextSurface() { if (arrived()) throw new IllegalStateException("arrived assembly member has no next surface"); return corridor().get(cursor + 1); }
        public SurfaceAnchor destinationSurface() { return corridor().getLast(); }
        public boolean arrived() { return cursor == corridor().size() - 1; }
        public int nextColdCursor() { return Math.min(cursor + MAX_COLD_ADVANCE, corridor().size() - 1); }
    }
}
