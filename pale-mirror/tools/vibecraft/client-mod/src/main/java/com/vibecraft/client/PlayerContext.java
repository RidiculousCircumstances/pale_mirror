package com.vibecraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides detailed player context including position, rotation, and raycast targeting.
 */
public final class PlayerContext {
    private static final Logger LOGGER = LoggerFactory.getLogger("vibecraft-context");
    private static final double DEFAULT_REACH = 128.0; // Extended reach for building context

    private PlayerContext() {}

    /**
     * Get comprehensive player context including position, rotation, and target.
     */
    public static JsonObject getPlayerContext(double reach) {
        JsonObject result = new JsonObject();

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel world = client.level;

        if (player == null || world == null) {
            result.addProperty("error", "Not in a world");
            return result;
        }

        double effectiveReach = reach > 0 ? reach : DEFAULT_REACH;

        // Position
        JsonObject position = new JsonObject();
        position.addProperty("x", player.getX());
        position.addProperty("y", player.getY());
        position.addProperty("z", player.getZ());
        position.addProperty("block_x", player.getBlockX());
        position.addProperty("block_y", player.getBlockY());
        position.addProperty("block_z", player.getBlockZ());
        result.add("position", position);

        // Rotation
        JsonObject rotation = new JsonObject();
        rotation.addProperty("yaw", player.getYRot());
        rotation.addProperty("pitch", player.getXRot());
        rotation.addProperty("head_yaw", player.getYHeadRot());

        // Cardinal direction
        String facing = getCardinalDirection(player.getYRot());
        rotation.addProperty("facing", facing);
        result.add("rotation", rotation);

        // Eye position for raycast
        JsonObject eye = new JsonObject();
        Vec3 eyePos = player.getEyePosition();
        eye.addProperty("x", eyePos.x);
        eye.addProperty("y", eyePos.y);
        eye.addProperty("z", eyePos.z);
        result.add("eye_position", eye);

        // Look direction vector
        JsonObject lookDir = new JsonObject();
        Vec3 look = player.getViewVector(1.0f);
        lookDir.addProperty("x", look.x);
        lookDir.addProperty("y", look.y);
        lookDir.addProperty("z", look.z);
        result.add("look_direction", lookDir);

        // Raycast to find target block
        JsonObject target = raycastTarget(player, world, effectiveReach);
        result.add("target", target);

        // Held item
        String heldItem = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
        result.addProperty("held_item", heldItem);

        // Game mode (creative, survival, etc.)
        if (client.gameMode != null) {
            result.addProperty("game_mode", client.gameMode.getPlayerMode().getName());
        }

        // Flying status
        result.addProperty("is_flying", player.getAbilities().flying);
        result.addProperty("on_ground", player.onGround());

        // Dimension
        result.addProperty("dimension", world.dimension().location().toString());

        LOGGER.debug("Player context: pos={},{},{} facing={} target={}",
            player.getBlockX(), player.getBlockY(), player.getBlockZ(), facing,
            target.has("block") ? target.get("block").getAsString() : "none");

        return result;
    }

    /**
     * Perform a raycast from player's eye position in look direction.
     */
    private static JsonObject raycastTarget(LocalPlayer player, ClientLevel world, double reach) {
        JsonObject target = new JsonObject();

        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getViewVector(1.0f);
        Vec3 end = start.add(direction.scale(reach));

        // Block raycast
        ClipContext ctx = new ClipContext(
            start, end,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player
        );
        BlockHitResult blockHit = world.clip(ctx);

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = world.getBlockState(hitPos);
            Direction side = blockHit.getDirection();

            target.addProperty("type", "block");
            target.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());

            JsonObject blockPos = new JsonObject();
            blockPos.addProperty("x", hitPos.getX());
            blockPos.addProperty("y", hitPos.getY());
            blockPos.addProperty("z", hitPos.getZ());
            target.add("position", blockPos);

            target.addProperty("face", side.getName());
            target.addProperty("distance", Math.sqrt(start.distanceToSqr(blockHit.getLocation())));

            // Adjacent position (where a block would be placed)
            BlockPos adjacent = hitPos.relative(side);
            JsonObject adjacentPos = new JsonObject();
            adjacentPos.addProperty("x", adjacent.getX());
            adjacentPos.addProperty("y", adjacent.getY());
            adjacentPos.addProperty("z", adjacent.getZ());
            target.add("adjacent", adjacentPos);

            // Block state if non-default
            if (!state.getValues().isEmpty()) {
                target.addProperty("block_state", state.toString());
            }
        } else {
            target.addProperty("type", "miss");
            target.addProperty("distance", reach);

            // Still provide the end point of the ray
            JsonObject endPoint = new JsonObject();
            endPoint.addProperty("x", end.x);
            endPoint.addProperty("y", end.y);
            endPoint.addProperty("z", end.z);
            target.add("ray_end", endPoint);
        }

        return target;
    }

    /**
     * Get nearby entities in view frustum.
     */
    public static JsonObject getNearbyEntities(double radius) {
        JsonObject result = new JsonObject();

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel world = client.level;

        if (player == null || world == null) {
            result.addProperty("error", "Not in a world");
            return result;
        }

        JsonArray entities = new JsonArray();
        for (Entity entity : world.entitiesForRendering()) {
            if (entity == player) continue;

            double distance = player.distanceTo(entity);
            if (distance <= radius) {
                JsonObject entityData = new JsonObject();
                entityData.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
                entityData.addProperty("id", entity.getId());
                entityData.addProperty("distance", distance);

                JsonObject pos = new JsonObject();
                pos.addProperty("x", entity.getX());
                pos.addProperty("y", entity.getY());
                pos.addProperty("z", entity.getZ());
                entityData.add("position", pos);

                if (entity.hasCustomName()) {
                    entityData.addProperty("name", entity.getCustomName().getString());
                }

                entities.add(entityData);
            }
        }

        result.add("entities", entities);
        result.addProperty("count", entities.size());
        result.addProperty("radius", radius);

        return result;
    }

    /**
     * Convert yaw to cardinal direction.
     */
    private static String getCardinalDirection(float yaw) {
        // Normalize yaw to 0-360
        float normalized = ((yaw % 360) + 360) % 360;

        if (normalized >= 315 || normalized < 45) {
            return "south";
        } else if (normalized >= 45 && normalized < 135) {
            return "west";
        } else if (normalized >= 135 && normalized < 225) {
            return "north";
        } else {
            return "east";
        }
    }
}
