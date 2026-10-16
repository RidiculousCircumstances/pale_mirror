package io.farfrontier.palemirror.frontier.v3.model.geology;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Immutable declarations with a derived chunk index. A point query never scans the world or loads chunks. */
public final class MineralGeology {
    public static final int MAX_DEPOSITS = 256;
    private static final int MAX_INDEX_ENTRIES = 65_536;
    public record Match(SubjectId depositId, long revision, MineralDeposit.Section section) { }
    private final Map<SubjectId, MineralDeposit> deposits;
    private final Map<Region, List<Match>> regions;
    public MineralGeology(Map<SubjectId, MineralDeposit> declarations) {
        deposits = Map.copyOf(declarations);
        if (deposits.size() > MAX_DEPOSITS || deposits.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id())))
            throw new IllegalArgumentException("invalid geological catalog");
        var index = new HashMap<Region, List<Match>>(); int entries = 0;
        for (var deposit : deposits.values().stream().sorted(Comparator.comparing(MineralDeposit::id)).toList()) {
            for (var section : deposit.sections()) {
                var min = Region.at(section.volume().min()); var max = Region.at(section.volume().max());
                for (int x = min.x(); x <= max.x(); x++) for (int z = min.z(); z <= max.z(); z++) {
                    if (++entries > MAX_INDEX_ENTRIES) throw new IllegalArgumentException("geological chunk index exceeds safety bound");
                    var part = index.computeIfAbsent(new Region(x, z), ignored -> new ArrayList<>());
                    if (part.stream().anyMatch(other -> other.section().volume().overlaps(section.volume())))
                        throw new IllegalArgumentException("competing geological declarations");
                    part.add(new Match(deposit.id(), deposit.revision(), section));
                }
            }
        }
        var frozen = new HashMap<Region, List<Match>>(); index.forEach((region, values) -> frozen.put(region, List.copyOf(values)));
        regions = Map.copyOf(frozen);
    }
    public static MineralGeology empty() { return new MineralGeology(Map.of()); }
    public Map<SubjectId, MineralDeposit> deposits() { return deposits; }
    public Optional<Match> at(BlockPosition position) {
        return regions.getOrDefault(Region.at(position), List.of()).stream()
                .filter(match -> match.section().volume().contains(position)).findFirst();
    }
    public int indexedChunks() { return regions.size(); }
    @Override public boolean equals(Object other) { return this == other || other instanceof MineralGeology geology && deposits.equals(geology.deposits); }
    @Override public int hashCode() { return deposits.hashCode(); }
}
