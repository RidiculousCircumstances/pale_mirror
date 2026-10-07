package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorCondition;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Minecraft construction provider used only by the shared body controller.
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
        Mob body = physicalType(declaration.kind()).create(level);
        if (body == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 actor body");
        if (body instanceof net.minecraft.world.entity.animal.horse.Donkey donkey) {
            donkey.setTamed(true);
            if (!donkey.getSlot(499).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST))
                    || !donkey.hasChest() || donkey.getInventory().getContainerSize() != 16)
                throw new IllegalStateException("Minecraft could not initialize the declared chest donkey");
        }
        // Only a newly constructed, not-yet-admitted body receives canonical health.
        // Existing/rejoined bodies retain their observed physical health; never heal
        // them as part of an ownership handoff or a repeated materialization tick.
        body.setHealth(physicalHealth(condition, body.getMaxHealth()));
        body.setUUID(declaration.entityId());
        FrontierV3ActorCarrierComposition.stamp(body, declaration);
        return body;
    }

    static String entityType(io.farfrontier.palemirror.frontier.v3.model.ActorKind kind) {
        return switch (kind) {
            case RESIDENT -> "minecraft:villager";
            case BIOFORM -> "minecraft:zombie";
            case PACK_ANIMAL -> "minecraft:donkey";
        };
    }
    static EntityType<? extends Mob> physicalType(io.farfrontier.palemirror.frontier.v3.model.ActorKind kind) {
        return switch (kind) {
            case RESIDENT -> EntityType.VILLAGER;
            case BIOFORM -> EntityType.ZOMBIE;
            case PACK_ANIMAL -> EntityType.DONKEY;
        };
    }

    static boolean matchesKind(net.minecraft.world.entity.Entity entity,
                               io.farfrontier.palemirror.frontier.v3.model.ActorKind kind) {
        return switch (kind) {
            case RESIDENT -> entity instanceof net.minecraft.world.entity.npc.Villager;
            case BIOFORM -> entity instanceof net.minecraft.world.entity.monster.Zombie;
            case PACK_ANIMAL -> entity instanceof net.minecraft.world.entity.animal.horse.Donkey;
        };
    }

    static float physicalHealth(ActorCondition condition, float maximumHealth) {
        java.util.Objects.requireNonNull(condition, "canonical actor condition");
        double health = (double) condition.health().raw() / FixedScalar.SCALE;
        if (condition.status() != ActorLifeStatus.ALIVE || !Float.isFinite(maximumHealth)
                || maximumHealth <= 0.0F || health <= 0.0D || health > maximumHealth)
            throw new IllegalArgumentException("canonical health cannot be represented by this living body");
        return (float) health;
    }

}
