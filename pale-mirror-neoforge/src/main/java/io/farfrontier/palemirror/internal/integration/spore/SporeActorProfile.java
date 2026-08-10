package io.farfrontier.palemirror.internal.integration.spore;

import java.util.Arrays;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Version-pinned, intentionally small first roster from Spore 2.2.0j. */
enum SporeActorProfile {
    INFECTED_HUMAN("pale_mirror:spore_infected_human", "spore:inf_human", 18, 3.0F, 3.0D, 30, "spore:inf_damage",
            MovementPolicy.PATROL, 8),
    INFECTED_HUSK("pale_mirror:spore_infected_husk", "spore:inf_husk", 24, 3.5F, 3.0D, 30, "spore:inf_damage",
            MovementPolicy.PATROL, 8),
    BRAIOMIL("pale_mirror:spore_braiomil", "spore:braiomil", 40, 6.0F, 4.5D, 50, "spore:braiomil_attack",
            MovementPolicy.PURSUE, 5),
    /**
     * Uses PM's ranged-pressure executor for damage and cooldowns.  The
     * native projectile path remains deliberately disabled until it has a
     * separately persisted projectile/effect provenance record.
     */
    SPITTER("pale_mirror:spore_spitter", "spore:spitter", 30, 3.0F, 12.0D, 50, "spore:spitter_spit",
            MovementPolicy.STATIONARY, 0);

    enum MovementPolicy { STATIONARY, PATROL, PURSUE }

    private final String id;
    private final String entityTypeId;
    private final int combatHitPoints;
    private final float attackDamage;
    private final double attackRange;
    private final int attackCooldownTicks;
    private final String attackSoundId;
    private final MovementPolicy movementPolicy;
    private final int movementCooldownTicks;

    SporeActorProfile(String id, String entityTypeId, int combatHitPoints, float attackDamage, double attackRange,
                      int attackCooldownTicks, String attackSoundId, MovementPolicy movementPolicy, int movementCooldownTicks) {
        this.id = id;
        this.entityTypeId = entityTypeId;
        this.combatHitPoints = combatHitPoints;
        this.attackDamage = attackDamage;
        this.attackRange = attackRange;
        this.attackCooldownTicks = attackCooldownTicks;
        this.attackSoundId = attackSoundId;
        this.movementPolicy = movementPolicy;
        this.movementCooldownTicks = movementCooldownTicks;
    }

    String id() { return id; }
    String entityTypeId() { return entityTypeId; }
    int combatHitPoints() { return combatHitPoints; }
    float attackDamage() { return attackDamage; }
    double attackRange() { return attackRange; }
    int attackCooldownTicks() { return attackCooldownTicks; }
    String attackSoundId() { return attackSoundId; }
    SoundEvent attackSound() { return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(attackSoundId)); }
    MovementPolicy movementPolicy() { return movementPolicy; }
    int movementCooldownTicks() { return movementCooldownTicks; }

    static Optional<SporeActorProfile> byId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }
}
