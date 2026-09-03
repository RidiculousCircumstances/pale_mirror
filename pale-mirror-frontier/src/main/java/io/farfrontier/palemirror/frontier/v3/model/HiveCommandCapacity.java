package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Pure weighted admission for the exact organisms held by one mobile Overseer.
 *
 * <p>The controller itself is deliberately not charged as a subordinate. Its retained
 * {@link HiveMobilization#memberIds()} is the authoritative group roster: this helper only
 * derives its bounded balance cost from immutable chassis and visible mutations.</p>
 */
public final class HiveCommandCapacity {
    private HiveCommandCapacity() { }

    public static int subordinateWeight(FrontierRuleset ruleset, Bioform bioform) {
        Objects.requireNonNull(ruleset, "hive command ruleset");
        Objects.requireNonNull(bioform, "hive command bioform");
        FrontierRuleset.HiveCommand balance = ruleset.hiveCommand();
        int chassis = switch (bioform.chassis()) {
            case RUNT -> balance.runtSubordinateWeight();
            case SENTINEL -> balance.sentinelSubordinateWeight();
            case OVERSEER -> throw new IllegalArgumentException("an Overseer cannot be its own subordinate");
        };
        return Math.addExact(chassis, Math.multiplyExact(balance.visibleMutationSurcharge(), bioform.mutations().size()));
    }

    public static int usedCapacity(FrontierRuleset ruleset, SubjectId overseerId, Collection<SubjectId> memberIds,
                                   Map<SubjectId, Bioform> bioforms) {
        Objects.requireNonNull(overseerId, "hive command overseer");
        Objects.requireNonNull(memberIds, "hive command members");
        Objects.requireNonNull(bioforms, "hive command bioforms");
        int total = 0;
        for (SubjectId memberId : memberIds) {
            if (overseerId.equals(memberId)) continue;
            Bioform member = bioforms.get(memberId);
            if (member == null) throw new IllegalArgumentException("hive command member is absent: " + memberId.value());
            total = Math.addExact(total, subordinateWeight(ruleset, member));
        }
        return total;
    }

    public static boolean admits(FrontierRuleset ruleset, SubjectId overseerId, Collection<SubjectId> memberIds,
                                 Map<SubjectId, Bioform> bioforms) {
        Bioform overseer = Objects.requireNonNull(bioforms, "hive command bioforms").get(overseerId);
        if (overseer == null || !overseer.isOverseer()) return false;
        return usedCapacity(ruleset, overseerId, memberIds, bioforms) <= ruleset.hiveCommand().overseerSubordinateCapacity();
    }
}
