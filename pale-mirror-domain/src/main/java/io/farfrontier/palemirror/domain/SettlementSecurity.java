package io.farfrontier.palemirror.domain;

import java.util.Objects;

public final class SettlementSecurity {
    private final WorldObjectId communityId;
    private final int baseDefence;
    private int defenceReadiness;
    private int registeredGuards;
    private GuardCapability guardCapability;

    public SettlementSecurity(WorldObjectId communityId, int baseDefence) {
        this(communityId, baseDefence, baseDefence, 0, GuardCapability.UNKNOWN);
    }

    public SettlementSecurity(WorldObjectId communityId, int baseDefence, int defenceReadiness,
                              int registeredGuards, GuardCapability guardCapability) {
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        if (baseDefence < 0 || baseDefence > 100 || defenceReadiness < 0 || defenceReadiness > 100 || registeredGuards < 0) {
            throw new IllegalArgumentException("Invalid settlement security values");
        }
        this.baseDefence = baseDefence;
        this.defenceReadiness = defenceReadiness;
        this.registeredGuards = registeredGuards;
        this.guardCapability = Objects.requireNonNull(guardCapability, "guardCapability");
    }

    public WorldObjectId communityId() { return communityId; }
    public int baseDefence() { return baseDefence; }
    public int defenceReadiness() { return defenceReadiness; }
    public int registeredGuards() { return registeredGuards; }
    public GuardCapability guardCapability() { return guardCapability; }
    boolean applySupplyFailure(int loss) {
        int before = defenceReadiness;
        defenceReadiness = Math.max(0, defenceReadiness - Math.max(0, loss));
        return defenceReadiness != before;
    }
    public boolean observeGuards(int count) {
        if (count < 0) throw new IllegalArgumentException("Guard count must not be negative");
        GuardCapability next = count > 0 ? GuardCapability.PRESENT : GuardCapability.ABSENT;
        boolean changed = registeredGuards != count || guardCapability != next;
        registeredGuards = count;
        guardCapability = next;
        return changed;
    }
}
