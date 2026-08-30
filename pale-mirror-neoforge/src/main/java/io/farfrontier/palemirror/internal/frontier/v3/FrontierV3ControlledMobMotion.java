package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The small, deterministic physical-motion primitive shared by v3 HOT bodies.
 *
 * <p>This is deliberately narrower than Minecraft goal AI.  A durable v3 lease chooses the
 * destination; this class performs only collision-checked local motion toward it.  In
 * particular it cannot acquire a target, use an item, attack, breed, or create a new strategic
 * decision outside the physical-intent boundary.</p>
 */
final class FrontierV3ControlledMobMotion {
    private static final double RESIDENT_SPEED = 0.055D;
    private static final double BIOFORM_SPEED = 0.075D;
    private static final double ARRIVAL_DISTANCE = 0.35D;
    private static final double THIN_SURFACE_STEP = 0.125D;

    private FrontierV3ControlledMobMotion() { }

    static void moveToward(ServerLevel level, Mob actor, Vec3 target) {
        actor.setNoAi(true);
        // NoAI suppresses Minecraft's goal selector, not physical gravity.  Reassert the latter
        // because a retained entity can carry an old mod/AI no-gravity flag across a HOT handoff.
        actor.setNoGravity(false);
        actor.getNavigation().stop();
        Vec3 delta = target.subtract(actor.position());
        double distance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (distance <= ARRIVAL_DISTANCE) return;
        double speed = actor instanceof Zombie ? BIOFORM_SPEED : RESIDENT_SPEED;
        Vec3 direct = new Vec3(delta.x / distance * speed, 0.0D, delta.z / distance * speed);
        for (Vec3 step : List.of(direct, new Vec3(-direct.z, 0.0D, direct.x), new Vec3(direct.z, 0.0D, -direct.x))) {
            Vec3 before = actor.position();
            // Entity.move is Minecraft's collision authority.  A speculative noCollision check
            // rejects legitimate low steps (carpets, snow layers, slabs) before that authority
            // can apply normal step-up.  Accept only an actual horizontal displacement, so a
            // wall still yields no path and never becomes a pass-through.
            actor.move(MoverType.SELF, step);
            Vec3 moved = actor.position().subtract(before);
            if (moved.x * moved.x + moved.z * moved.z <= 1.0E-8D) {
                // A canonical grid column may have a thin physical surface (carpet, snow or
                // an infection overlay) at the feet datum.  The first collision-authoritative
                // horizontal move can legitimately reject it, even though a normal mob may
                // step onto it.  Only after that exact move fails, try one bounded low-step
                // candidate.  It neither changes the X/Z target nor bypasses full blocks.
                Vec3 lifted = step.add(0.0D, THIN_SURFACE_STEP, 0.0D);
                if (!level.noCollision(actor, actor.getBoundingBox().move(lifted))) continue;
                before = actor.position(); actor.move(MoverType.SELF, lifted); moved = actor.position().subtract(before);
                if (moved.x * moved.x + moved.z * moved.z <= 1.0E-8D) continue;
            }
            actor.setYRot((float) Math.toDegrees(Math.atan2(-moved.x, moved.z)));
            actor.yBodyRot = actor.getYRot();
            return;
        }
    }
}
