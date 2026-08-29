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
        Vec3 step = List.of(direct, new Vec3(-direct.z, 0.0D, direct.x), new Vec3(direct.z, 0.0D, -direct.x)).stream()
                .filter(candidate -> level.noCollision(actor, actor.getBoundingBox().move(candidate))).findFirst().orElse(null);
        if (step == null) return;
        actor.setYRot((float) Math.toDegrees(Math.atan2(-step.x, step.z)));
        actor.yBodyRot = actor.getYRot();
        actor.move(MoverType.SELF, step);
    }
}
