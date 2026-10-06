package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sole roster/journey owner; membership lookup is a derived view, not an authority resolver. */
public record UnitGroupState(Map<SubjectId, UnitGroup> groups) {
    public static final int MAX_GROUPS = 256;
    public UnitGroupState {
        groups = Map.copyOf(groups);
        if (groups.size() > MAX_GROUPS) throw new IllegalArgumentException("group retention capacity reached");
        var actors = new HashSet<SubjectId>();
        for (var entry : groups.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("foreign group key");
            if (entry.getValue().phase() != UnitGroup.Phase.CLOSED)
                for (var member : entry.getValue().members()) if (!actors.add(member.actorId()))
                    throw new IllegalArgumentException("actor belongs to competing active groups");
        }
    }
    public static UnitGroupState empty() { return new UnitGroupState(Map.of()); }
    public UnitGroupState admit(UnitGroup group) {
        if (groups.containsKey(group.id()) || group.phase() != UnitGroup.Phase.READY || group.revision() != 1)
            throw new IllegalArgumentException("group admission requires one fresh declaration");
        var next = new LinkedHashMap<>(groups); next.put(group.id(), group); return new UnitGroupState(next);
    }
    public UnitGroupState replace(UnitGroup previous, UnitGroup next) {
        if (!previous.equals(groups.get(previous.id())) || !previous.id().equals(next.id())
                || next.revision() != previous.revision() + 1 || !previous.mission().equals(next.mission())
                || !previous.members().equals(next.members()) || previous.formation() != next.formation())
            throw new IllegalArgumentException("group transition has a stale or changed roster declaration");
        var result = new LinkedHashMap<>(groups); result.put(next.id(), next); return new UnitGroupState(result);
    }
    public UnitGroupState retire(UnitGroup group) {
        if (!group.equals(groups.get(group.id())) || group.phase() != UnitGroup.Phase.CLOSED)
            throw new IllegalArgumentException("only the exact closed group may retire");
        var result = new LinkedHashMap<>(groups); result.remove(group.id()); return new UnitGroupState(result);
    }
}
