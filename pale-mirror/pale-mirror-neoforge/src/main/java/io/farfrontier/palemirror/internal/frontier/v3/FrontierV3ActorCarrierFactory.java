package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.ActorCondition;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * The sole production construction and scene-adoption bridge for exact actor bodies.
 * A declaration is deliberately required before a Minecraft type is selected.
 */
final class FrontierV3ActorCarrierFactory {
    private FrontierV3ActorCarrierFactory() { }

    static Mob create(FrontierV3ActorCarrierComposition.InventoryEntry producer, ServerLevel level,
                      FrontierV3ActorCarrierComposition.Declaration declaration, ActorCondition condition) {
        FrontierV3ActorCarrierComposition.requireRole(producer, FrontierV3ActorCarrierComposition.Role.PRODUCER);
        java.util.Objects.requireNonNull(condition, "canonical actor condition");
        if (condition.status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("dead actor cannot acquire a new living body");
        Mob body = switch (declaration.kind()) {
            case RESIDENT -> EntityType.VILLAGER.create(level);
            case BIOFORM -> EntityType.ZOMBIE.create(level);
        };
        if (body == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 actor body");
        // Only a newly constructed, not-yet-admitted body receives canonical health.
        // Existing/rejoined bodies retain their observed physical health; never heal
        // them as part of an ownership handoff or a repeated materialization tick.
        body.setHealth(physicalHealth(condition, body.getMaxHealth()));
        body.setUUID(declaration.entityId());
        FrontierV3ActorCarrierComposition.stamp(body, declaration);
        return body;
    }

    static float physicalHealth(ActorCondition condition, float maximumHealth) {
        java.util.Objects.requireNonNull(condition, "canonical actor condition");
        double health = (double) condition.health().raw() / FixedScalar.SCALE;
        if (condition.status() != ActorLifeStatus.ALIVE || !Float.isFinite(maximumHealth)
                || maximumHealth <= 0.0F || health <= 0.0D || health > maximumHealth)
            throw new IllegalArgumentException("canonical health cannot be represented by this living body");
        return (float) health;
    }

    static FrontierV3SceneExecutor.BodyMaterialization materializeSceneBodies(FrontierV3ActorCarrierComposition.InventoryEntry entry,
                                                                                 ServerLevel level, FrontierWorldState state, SceneLease lease) {
        FrontierV3ActorCarrierComposition.requireRegistered(entry);
        return FrontierV3SceneExecutor.materializeBodies(level, state, lease, entry);
    }
}
