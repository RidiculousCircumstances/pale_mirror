package io.farfrontier.palemirror.internal.integration.crimson;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Small, PM-owned local behaviours for audited Crimson forms. These never call
 * Crimson functions or read/write its global scoreboard state.
 */
enum CrimsonActorBehavior {
    VANILLA {
        @Override
        long execute(Mob actor, ServerPlayer target) { return TARGET_REFRESH_TICKS; }
    },
    RUSHER_DASH {
        @Override
        long execute(Mob actor, ServerPlayer target) {
            if (target == null) return TARGET_REFRESH_TICKS;
            Vec3 offset = target.position().subtract(actor.position());
            double horizontalDistance = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            if (horizontalDistance < RUSHER_MIN_DASH_DISTANCE || horizontalDistance > RUSHER_MAX_DASH_DISTANCE) {
                return TARGET_REFRESH_TICKS;
            }
            Vec3 dash = new Vec3(offset.x / horizontalDistance * RUSHER_DASH_SPEED,
                    Mth.clamp(offset.y * 0.08D, 0.0D, RUSHER_MAX_VERTICAL_SPEED),
                    offset.z / horizontalDistance * RUSHER_DASH_SPEED);
            actor.setDeltaMovement(actor.getDeltaMovement().add(dash));
            actor.hurtMarked = true;
            actor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, RUSHER_EFFECT_TICKS, 1, true, false));
            actor.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, RUSHER_EFFECT_TICKS, 0, true, false));
            return RUSHER_DASH_COOLDOWN_TICKS;
        }
    },
    RAPTOR_AURA {
        @Override
        long execute(Mob actor, ServerPlayer target) {
            actor.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, RAPTOR_INVISIBILITY_TICKS, 0, true, false));
            if (target != null && actor.distanceToSqr(target) <= RAPTOR_AURA_RANGE_SQUARED) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, RAPTOR_AURA_EFFECT_TICKS, 1, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, RAPTOR_AURA_EFFECT_TICKS, 0, false, false));
            }
            return TARGET_REFRESH_TICKS;
        }
    };

    private static final long TARGET_REFRESH_TICKS = 20L;
    private static final long RUSHER_DASH_COOLDOWN_TICKS = 100L;
    private static final int RUSHER_EFFECT_TICKS = 20;
    private static final double RUSHER_MIN_DASH_DISTANCE = 7.0D;
    private static final double RUSHER_MAX_DASH_DISTANCE = 11.0D;
    private static final double RUSHER_DASH_SPEED = 0.60D;
    private static final double RUSHER_MAX_VERTICAL_SPEED = 0.25D;
    private static final int RAPTOR_INVISIBILITY_TICKS = 40;
    private static final int RAPTOR_AURA_EFFECT_TICKS = 60;
    private static final double RAPTOR_AURA_RANGE_SQUARED = 1.8D * 1.8D;

    abstract long execute(Mob actor, ServerPlayer target);
}
