package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Durable social state of one exact person. Physical vitality remains solely in
 * {@link ActorCondition}; this avoids a second health truth at the HOT boundary.
 */
public record ResidentProfile(
        SubjectId id, SubjectId householdId, SubjectId settlementId, ResidentRole role,
        long birthTick, Map<ResidentSkill, Integer> skills
) {
    public static final int MAX_SKILL = 100;

    public ResidentProfile {
        Objects.requireNonNull(id, "resident id");
        Objects.requireNonNull(householdId, "resident household id");
        Objects.requireNonNull(settlementId, "resident settlement id");
        Objects.requireNonNull(role, "resident role");
        if (!id.value().startsWith("resident:")) throw new IllegalArgumentException("resident id must use resident: namespace");
        if (!householdId.value().startsWith("household:")) throw new IllegalArgumentException("resident household must use household: namespace");
        if (!settlementId.value().startsWith("settlement:")) throw new IllegalArgumentException("resident settlement must use settlement: namespace");
        EnumMap<ResidentSkill, Integer> copy = new EnumMap<>(ResidentSkill.class);
        Objects.requireNonNull(skills, "resident skills").forEach((skill, value) -> {
            if (skill == null || value == null || value < 0 || value > MAX_SKILL) throw new IllegalArgumentException("resident skill must be 0 through " + MAX_SKILL);
            copy.put(skill, value);
        });
        if (copy.size() != ResidentSkill.values().length) throw new IllegalArgumentException("resident profile must own every skill");
        skills = Map.copyOf(copy);
    }

    public int skill(ResidentSkill skill) { return skills.get(Objects.requireNonNull(skill, "skill")); }

    public ResidentProfile relocated(SubjectId nextSettlementId, SubjectId nextHouseholdId) {
        return new ResidentProfile(id, nextHouseholdId, nextSettlementId, role, birthTick, skills);
    }

    public ResidentProfile withRole(ResidentRole nextRole) { return new ResidentProfile(id, householdId, settlementId, nextRole, birthTick, skills); }
}
