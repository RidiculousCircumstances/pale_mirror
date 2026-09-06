package io.farfrontier.palemirror.internal.integration.crimson;

/**
 * PM-owned balance/control table for the exact local Crimson visual forms.
 * It intentionally contains no Crimson scoreboards, functions or global
 * state. Values are pinned to the initializer audit and are authoritative
 * only for bounded encounter presentation.
 */
record CrimsonCombatProfile(int hitPoints, float damage, double attackRange, long attackCooldown,
                            double movementPerTick, Mode mode, int experience) {
    enum Mode { MELEE, ARROW, RUSHER_DASH, RAPTOR_AURA }

    static CrimsonCombatProfile forActor(CrimsonActorProfile profile) {
        return switch (profile) {
            case HUMAN -> melee(21, 9.0F, 0.12D, 5);
            case VILLAGER -> melee(27, 11.0F, 0.13D, 6);
            case HUSK -> melee(17, 9.0F, 0.15D, 5);
            case SKELETON -> arrow(16, 5.0F, 0.11D, 4);
            case DROWNED -> melee(21, 10.0F, 0.12D, 6);
            case BOGGED -> arrow(18, 4.0F, 0.12D, 4);
            case WITHER_SKELETON -> melee(19, 5.0F, 0.13D, 5);
            case DECAYED_HUMAN -> melee(29, 13.0F, 0.15D, 7);
            case DECAYED_VILLAGER -> melee(38, 16.0F, 0.16D, 8);
            case DECAYED_HUSK -> melee(25, 13.0F, 0.18D, 7);
            case DECAYED_SKELETON -> arrow(23, 7.0F, 0.13D, 6);
            case DECAYED_DROWNED -> melee(30, 13.0F, 0.15D, 7);
            case DECAYED_BOGGED -> arrow(24, 6.0F, 0.14D, 6);
            case DECAYED_WITHER_SKELETON -> melee(23, 4.0F, 0.13D, 6);
            case RUSHER -> new CrimsonCombatProfile(32, 9.0F, 2.5D, 100L, 0.15D, Mode.RUSHER_DASH, 8);
            case RAPTOR -> new CrimsonCombatProfile(28, 12.0F, 2.5D, 20L, 0.14D, Mode.RAPTOR_AURA, 8);
        };
    }

    static CrimsonCombatProfile forSiege(CrimsonSiegeProfile profile) {
        return switch (profile) {
            case JUGGERNAUT -> melee(130, 16.0F, 0.11D, 24);
            case KNIGHT -> melee(28, 10.0F, 0.13D, 10);
            case MANGLER -> new CrimsonCombatProfile(170, 18.0F, 2.5D, 40L, 0.0D, Mode.RUSHER_DASH, 30);
            case PUMMELER -> new CrimsonCombatProfile(55, 6.0F, 24.0D, 60L, 0.0D, Mode.ARROW, 18);
            case KRAKEN -> new CrimsonCombatProfile(72, 5.0F, 8.0D, 40L, 0.0D, Mode.RAPTOR_AURA, 22);
            case OSIRIS -> melee(400, 20.0F, 2.5D, 35);
            case BLOODLINK_I -> new CrimsonCombatProfile(40, 0.0F, 10.0D, 40L, 0.0D, Mode.RAPTOR_AURA, 4);
            case BLOODLINK_II -> new CrimsonCombatProfile(55, 0.0F, 10.0D, 40L, 0.0D, Mode.RAPTOR_AURA, 5);
            case BLOODLINK_III -> new CrimsonCombatProfile(70, 0.0F, 10.0D, 40L, 0.0D, Mode.RAPTOR_AURA, 6);
        };
    }

    private static CrimsonCombatProfile melee(int hp, float damage, double speed, int xp) {
        return new CrimsonCombatProfile(hp, damage, 2.5D, 20L, speed, Mode.MELEE, xp);
    }

    private static CrimsonCombatProfile arrow(int hp, float damage, double speed, int xp) {
        return new CrimsonCombatProfile(hp, damage, 18.0D, 40L, speed, Mode.ARROW, xp);
    }
}
