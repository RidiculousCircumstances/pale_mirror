package io.farfrontier.palemirror.internal.integration.spore;

import java.util.Arrays;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Version-pinned, intentionally small first roster from Spore 2.2.0j. */
enum SporeActorProfile {
    INFECTED_HUMAN("pale_mirror:spore_infected_human", "spore:inf_human", 18, 3.0F, 3.0D, 30, "spore:inf_damage"),
    BRAIOMIL("pale_mirror:spore_braiomil", "spore:braiomil", 40, 6.0F, 4.5D, 50, "spore:braiomil_attack");

    private final String id;
    private final String entityTypeId;
    private final int combatHitPoints;
    private final float attackDamage;
    private final double attackRange;
    private final int attackCooldownTicks;
    private final String attackSoundId;

    SporeActorProfile(String id, String entityTypeId, int combatHitPoints, float attackDamage, double attackRange,
                      int attackCooldownTicks, String attackSoundId) {
        this.id = id;
        this.entityTypeId = entityTypeId;
        this.combatHitPoints = combatHitPoints;
        this.attackDamage = attackDamage;
        this.attackRange = attackRange;
        this.attackCooldownTicks = attackCooldownTicks;
        this.attackSoundId = attackSoundId;
    }

    String id() { return id; }
    String entityTypeId() { return entityTypeId; }
    int combatHitPoints() { return combatHitPoints; }
    float attackDamage() { return attackDamage; }
    double attackRange() { return attackRange; }
    int attackCooldownTicks() { return attackCooldownTicks; }
    String attackSoundId() { return attackSoundId; }
    SoundEvent attackSound() { return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(attackSoundId)); }

    static Optional<SporeActorProfile> byId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }
}
