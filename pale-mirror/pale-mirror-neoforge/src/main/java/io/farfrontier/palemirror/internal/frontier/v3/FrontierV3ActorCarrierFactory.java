package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
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
                      FrontierV3ActorCarrierComposition.Declaration declaration) {
        FrontierV3ActorCarrierComposition.requireRole(producer, FrontierV3ActorCarrierComposition.Role.PRODUCER);
        Mob body = switch (declaration.kind()) {
            case RESIDENT -> EntityType.VILLAGER.create(level);
            case BIOFORM -> EntityType.ZOMBIE.create(level);
        };
        if (body == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 actor body");
        body.setUUID(declaration.entityId());
        FrontierV3ActorCarrierComposition.stamp(body, declaration);
        return body;
    }

    static FrontierV3SceneExecutor.BodyMaterialization materializeSceneBodies(FrontierV3ActorCarrierComposition.InventoryEntry entry,
                                                                                 ServerLevel level, FrontierWorldState state, SceneLease lease) {
        FrontierV3ActorCarrierComposition.requireRegistered(entry);
        return FrontierV3SceneExecutor.materializeBodies(level, state, lease, entry);
    }
}
