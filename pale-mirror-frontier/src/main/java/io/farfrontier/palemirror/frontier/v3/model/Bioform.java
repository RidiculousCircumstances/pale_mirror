package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Set;
import java.util.Objects;

/**
 * One canonical hive creature, later represented by one managed graybox Zombie while HOT.
 *
 * <p>Its chassis, installed visible mutations and current assignment are separate authority.
 * Vitality and physical custody remain owned by the exact {@link ActorLocation} and its
 * persisted ambient lease until the cocoon/lifecycle slice moves those owners together; no
 * duplicate health or Minecraft-entity ledger is introduced here.</p>
 */
public record Bioform(SubjectId id, SubjectId hiveId, SubjectId nestId, BioformChassis chassis,
                      Set<BioformMutation> mutations, BioformAssignment assignment, BlockPosition position) {
    public static final int MAX_VISIBLE_MUTATIONS = 5;

    public Bioform {
        Objects.requireNonNull(id, "bioform id");
        Objects.requireNonNull(hiveId, "hive id");
        Objects.requireNonNull(nestId, "nest id");
        Objects.requireNonNull(chassis, "bioform chassis");
        mutations = Set.copyOf(Objects.requireNonNull(mutations, "bioform mutations"));
        if (mutations.size() > MAX_VISIBLE_MUTATIONS) throw new IllegalArgumentException("bioform mutation capacity exceeded");
        Objects.requireNonNull(assignment, "bioform assignment");
        Objects.requireNonNull(position, "bioform position");
    }

    public boolean isScout() { return chassis == BioformChassis.SENTINEL && assignment == BioformAssignment.SCOUT; }

    public boolean isDefender() { return assignment == BioformAssignment.DEFEND; }

    public boolean isExplosiveAssaulter() {
        return assignment == BioformAssignment.ASSAULT && mutations.contains(BioformMutation.EXPLOSIVE);
    }
}
