package io.farfrontier.palemirror.frontier.v3.model.land;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockVolume;
import java.util.*;

/** Natural geography survives release. A use right is neither knowledge nor item title. */
public record SiteUseState(long revision, Map<SubjectId, Area> areas, Map<SubjectId, Grant> grants, Map<Operation, Set<Grant>> operations) {
    public static final int MAX_AREAS = 4096;
    public enum Kind {
        CULTIVATION(1), EXTRACTION(2), FORESTRY(3);
        private final int tag;
        Kind(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Kind require(int tag) {
            for (var kind : values()) if (kind.tag == tag) return kind;
            throw new IllegalArgumentException("unknown natural-site use kind");
        }
    }
    public record Area(SubjectId id, Kind kind, BlockVolume extent, Set<SubjectId> knownBy) {
        public Area {
            Objects.requireNonNull(id); Objects.requireNonNull(kind); Objects.requireNonNull(extent); knownBy = Set.copyOf(knownBy);
            if (knownBy.size() > SimulationCapacity.MAX_SETTLEMENTS) throw new IllegalArgumentException("unbounded site knowledge");
        }
    }
    public record Grant(SubjectId area, Kind kind, SubjectId holder, long generation) {
        public Grant { Objects.requireNonNull(area); Objects.requireNonNull(kind); Objects.requireNonNull(holder);
            if (generation < 1) throw new IllegalArgumentException("invalid site-use generation"); }
    }
    public record Operation(Kind kind, SubjectId id) {
        public Operation { Objects.requireNonNull(kind); Objects.requireNonNull(id); }
    }
    public SiteUseState(long revision,Map<SubjectId,Area> areas,Map<SubjectId,Grant> grants) {
        this(revision,areas,grants,Map.of());
    }
    public SiteUseState {
        areas = ImmutableLookupMap.copyOf(Objects.requireNonNull(areas)); grants = ImmutableLookupMap.copyOf(Objects.requireNonNull(grants));
        var retained=new LinkedHashMap<Operation,Set<Grant>>();
        Objects.requireNonNull(operations).forEach((operation,rights)->retained.put(Objects.requireNonNull(operation),Set.copyOf(rights)));
        operations=ImmutableLookupMap.copyOf(retained);
        if(operations.size()>MAX_AREAS*4)throw new IllegalArgumentException("unbounded operating-right relations");
        for(var entry:operations.entrySet()) {
            if(entry.getValue().isEmpty() || entry.getValue().size()>MAX_AREAS)throw new IllegalArgumentException("empty or unbounded operating right");
            for(var grant:entry.getValue()) if(entry.getKey().kind()!=grant.kind() || !grant.equals(grants.get(grant.area())))
                throw new IllegalArgumentException("operation lost its exact right/kind/generation");
        }
        if (revision < 0 || areas.size() > MAX_AREAS || grants.size() > areas.size()) throw new IllegalArgumentException("invalid site-use registry");
        for (var entry : areas.entrySet()) if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("foreign natural area key");
        for (var entry : grants.entrySet()) {
            var grant = entry.getValue(); var area = areas.get(entry.getKey());
            if (area == null || !entry.getKey().equals(grant.area()) || area.kind() != grant.kind() || !area.knownBy().contains(grant.holder()))
                throw new IllegalArgumentException("site use lost its exact area/kind/knowledge declaration");
        }
    }
    /** Serialized admission proves the exact revision and rejects competing claims. */
    public SiteUseState acquire(SubjectId areaId, Kind kind, SubjectId holder, long expectedRevision) {
        var area = Objects.requireNonNull(areas.get(areaId), "natural area");
        if (revision != expectedRevision || area.kind() != kind || !area.knownBy().contains(holder) || grants.containsKey(areaId))
            throw new IllegalArgumentException("stale, unknown or competing site acquisition");
        var next = new LinkedHashMap<>(grants); next.put(areaId, new Grant(areaId, kind, holder, Math.incrementExact(revision)));
        return new SiteUseState(Math.incrementExact(revision), areas, next, operations);
    }
    /** Only an unbound reservation can be released. Operating rights cannot disappear under live work. */
    public SiteUseState release(Grant expected, long expectedRevision) {
        if (revision != expectedRevision || !expected.equals(grants.get(expected.area()))
                || operations.values().stream().anyMatch(rights->rights.contains(expected)))
            throw new IllegalArgumentException("stale or unsettled site-use release");
        var next = new LinkedHashMap<>(grants); next.remove(expected.area());
        return new SiteUseState(Math.incrementExact(revision), areas, next, operations);
    }
    public SiteUseState bind(Operation operation, Grant grant) {
        if(operation.kind()!=grant.kind() || !grant.equals(grants.get(grant.area())))
            throw new IllegalArgumentException("binding lacks exact admitted right");
        var rights=new HashSet<>(operations.getOrDefault(operation,Set.of()));
        if(!rights.add(grant))return this;
        if(rights.stream().anyMatch(right->!right.holder().equals(grant.holder())))throw new IllegalArgumentException("operation has competing holders");
        var next=new LinkedHashMap<>(operations);next.put(operation,rights);
        return new SiteUseState(Math.incrementExact(revision),areas,grants,next);
    }
    /** Spatial discovery is admission only. Continuations use the retained nominal operation. */
    public Optional<Area> candidate(SubjectId holder,Kind kind,BlockPosition position) {
        return areas.values().stream().filter(area->area.kind()==kind && area.knownBy().contains(holder)
                && area.extent().contains(position) && available(area.id(),kind,holder))
                .sorted(Comparator.comparing(Area::id)).findFirst();
    }
    public boolean available(SubjectId area,Kind kind,SubjectId holder) {
        var geography=areas.get(area);var grant=grants.get(area);
        return geography!=null && geography.kind()==kind && geography.knownBy().contains(holder)
                && (grant==null || grant.holder().equals(holder));
    }
    public SiteUseState admit(Operation operation,SubjectId area,SubjectId holder) {
        if(!available(area,operation.kind(),holder))throw new IllegalArgumentException("natural site unavailable for this operator");
        var next=grants.containsKey(area)?this:acquire(area,operation.kind(),holder,revision);
        return next.bind(operation,next.grants().get(area));
    }
    public boolean authorizes(Operation operation,SubjectId holder) {
        var rights=operations.getOrDefault(operation,Set.of());
        return !rights.isEmpty() && rights.stream().allMatch(grant->grant.holder().equals(holder) && grant.equals(grants.get(grant.area())));
    }
    public boolean permits(Operation operation,SubjectId holder,BlockPosition position) {
        return operations.getOrDefault(operation,Set.of()).stream().anyMatch(grant->grant.holder().equals(holder)
                && grant.equals(grants.get(grant.area())) && areas.get(grant.area()).extent().contains(position));
    }
    public List<Area> held(SubjectId holder) {
        return grants.values().stream().filter(grant -> grant.holder().equals(holder)).map(grant -> areas.get(grant.area()))
                .sorted(Comparator.comparing(Area::id)).toList();
    }
}
