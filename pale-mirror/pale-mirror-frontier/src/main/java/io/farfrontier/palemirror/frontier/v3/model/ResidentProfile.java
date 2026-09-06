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
        ResidentProfession profession, long birthTick, Map<ResidentSkill, Integer> skills,
        Map<HumanCapability, Integer> capabilities
) {
    public static final int MAX_SKILL = 100;

    public ResidentProfile {
        Objects.requireNonNull(id, "resident id");
        Objects.requireNonNull(householdId, "resident household id");
        Objects.requireNonNull(settlementId, "resident settlement id");
        Objects.requireNonNull(role, "resident role");
        Objects.requireNonNull(profession, "resident profession");
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
        EnumMap<HumanCapability, Integer> capabilityCopy = new EnumMap<>(HumanCapability.class);
        Objects.requireNonNull(capabilities, "resident capabilities").forEach((capability, value) -> {
            if (capability == null || value == null || value < 0 || value > MAX_SKILL) {
                throw new IllegalArgumentException("resident capability must be 0 through " + MAX_SKILL);
            }
            capabilityCopy.put(capability, value);
        });
        if (capabilityCopy.size() != HumanCapability.values().length) throw new IllegalArgumentException("resident profile must own every capability");
        capabilities = Map.copyOf(capabilityCopy);
    }

    /** Compatibility constructor for snapshots and WAL payloads before the human-capability migration. */
    public ResidentProfile(SubjectId id, SubjectId householdId, SubjectId settlementId, ResidentRole role,
                           long birthTick, Map<ResidentSkill, Integer> skills) {
        this(id, householdId, settlementId, role, ResidentProfession.fromBootstrapAffinity(role), birthTick, skills, legacyCapabilities(skills));
    }

    public int skill(ResidentSkill skill) { return skills.get(Objects.requireNonNull(skill, "skill")); }
    public int capability(HumanCapability capability) { return capabilities.get(Objects.requireNonNull(capability, "capability")); }

    public ResidentProfile relocated(SubjectId nextSettlementId, SubjectId nextHouseholdId) {
        return new ResidentProfile(id, nextHouseholdId, nextSettlementId, role, profession, birthTick, skills, capabilities);
    }

    public ResidentProfile withRole(ResidentRole nextRole) {
        return new ResidentProfile(id, householdId, settlementId, nextRole, profession, birthTick, skills, capabilities);
    }

    public ResidentProfile withProfession(ResidentProfession nextProfession) {
        return new ResidentProfile(id, householdId, settlementId, role, nextProfession, birthTick, skills, capabilities);
    }

    public ResidentProfile withCapabilities(Map<HumanCapability, Integer> nextCapabilities) {
        return new ResidentProfile(id, householdId, settlementId, role, profession, birthTick, skills, nextCapabilities);
    }

    private static Map<HumanCapability, Integer> legacyCapabilities(Map<ResidentSkill, Integer> skills) {
        Objects.requireNonNull(skills, "resident skills");
        EnumMap<HumanCapability, Integer> values = new EnumMap<>(HumanCapability.class);
        for (HumanCapability capability : HumanCapability.values()) values.put(capability, 10);
        values.put(HumanCapability.AGRICULTURE, requireLegacySkill(skills, ResidentSkill.AGRICULTURE));
        values.put(HumanCapability.ENGINEERING, requireLegacySkill(skills, ResidentSkill.BUILDING));
        values.put(HumanCapability.INDUSTRY, requireLegacySkill(skills, ResidentSkill.CRAFTING));
        values.put(HumanCapability.SECURITY, requireLegacySkill(skills, ResidentSkill.SECURITY));
        values.put(HumanCapability.MEDICINE, requireLegacySkill(skills, ResidentSkill.MEDICINE));
        values.put(HumanCapability.LOGISTICS, requireLegacySkill(skills, ResidentSkill.LOGISTICS));
        return values;
    }

    private static int requireLegacySkill(Map<ResidentSkill, Integer> skills, ResidentSkill skill) {
        Integer value = skills.get(skill);
        if (value == null || value < 0 || value > MAX_SKILL) throw new IllegalArgumentException("resident profile must own every skill");
        return value;
    }
}
