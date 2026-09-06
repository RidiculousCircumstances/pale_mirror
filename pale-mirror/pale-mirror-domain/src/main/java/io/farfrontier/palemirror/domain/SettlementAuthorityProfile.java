package io.farfrontier.palemirror.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Pinned, source-neutral ownership contract for one settlement community. */
public final class SettlementAuthorityProfile {
    private final WorldObjectId communityId;
    private final String profileId;
    private final Map<SettlementAuthorityField, FieldAuthority> fields;
    private final boolean relocationAllowed;
    private final boolean pmRuinAllowed;
    private final boolean pmPopulationGrowthAllowed;

    public SettlementAuthorityProfile(WorldObjectId communityId, String profileId,
                                      Map<SettlementAuthorityField, FieldAuthority> fields,
                                      boolean relocationAllowed, boolean pmRuinAllowed,
                                      boolean pmPopulationGrowthAllowed) {
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        if (profileId == null || profileId.isBlank()) throw new IllegalArgumentException("profileId must not be blank");
        this.profileId = profileId;
        this.fields = new EnumMap<>(SettlementAuthorityField.class);
        this.fields.putAll(fields);
        for (SettlementAuthorityField field : SettlementAuthorityField.values()) {
            if (!this.fields.containsKey(field)) throw new IllegalArgumentException("Missing authority for " + field);
        }
        this.relocationAllowed = relocationAllowed;
        this.pmRuinAllowed = pmRuinAllowed;
        this.pmPopulationGrowthAllowed = pmPopulationGrowthAllowed;
    }

    public static SettlementAuthorityProfile pmManaged(WorldObjectId communityId) {
        return new SettlementAuthorityProfile(communityId, "pale_mirror:pm_managed", Map.of(
                SettlementAuthorityField.MACRO_POPULATION, FieldAuthority.PM_OWNED,
                SettlementAuthorityField.PHYSICAL_NPCS, FieldAuthority.OBSERVED_ONLY,
                SettlementAuthorityField.RESOURCE_FLOW, FieldAuthority.PM_OWNED,
                SettlementAuthorityField.LOCAL_CONSTRUCTION, FieldAuthority.PM_OWNED,
                SettlementAuthorityField.PHYSICAL_INTEGRITY, FieldAuthority.RECONCILED), true, true, true);
    }

    public static SettlementAuthorityProfile nativeReconciled(WorldObjectId communityId) {
        return new SettlementAuthorityProfile(communityId, "pale_mirror:native_reconciled", Map.of(
                SettlementAuthorityField.MACRO_POPULATION, FieldAuthority.RECONCILED,
                SettlementAuthorityField.PHYSICAL_NPCS, FieldAuthority.NATIVE_OWNED,
                SettlementAuthorityField.RESOURCE_FLOW, FieldAuthority.PM_OWNED,
                SettlementAuthorityField.LOCAL_CONSTRUCTION, FieldAuthority.NATIVE_OWNED,
                SettlementAuthorityField.PHYSICAL_INTEGRITY, FieldAuthority.RECONCILED), false, false, false);
    }

    public WorldObjectId communityId() { return communityId; }
    public String profileId() { return profileId; }
    public Map<SettlementAuthorityField, FieldAuthority> fields() { return Map.copyOf(fields); }
    public FieldAuthority authority(SettlementAuthorityField field) { return fields.get(field); }
    public boolean relocationAllowed() { return relocationAllowed; }
    public boolean pmRuinAllowed() { return pmRuinAllowed; }
    public boolean pmPopulationGrowthAllowed() { return pmPopulationGrowthAllowed; }
}
