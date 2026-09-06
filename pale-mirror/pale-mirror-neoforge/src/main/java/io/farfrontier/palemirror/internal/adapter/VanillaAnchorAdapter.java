package io.farfrontier.palemirror.internal.adapter;

import java.util.Set;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.IntegrationAdapter;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;

/**
 * Built-in, server-safe physical anchor. It is the only object whose death may
 * resolve the canonical PM threat; presentation adapters can never replace it.
 */
public final class VanillaAnchorAdapter implements IntegrationAdapter {
    public static final String OBJECT_ID_KEY = "pale_mirror_object_id";
    public static final String JOB_ID_KEY = "pale_mirror_job_id";
    public static final String ROLE_KEY = "pale_mirror_role";
    public static final String ANCHOR_ROLE = "anchor";

    @Override
    public String id() { return "pale_mirror:vanilla_anchor"; }

    @Override
    public AdapterHealth health() {
        return new AdapterHealth(AdapterHealth.Status.AVAILABLE, "Built-in PM-owned vanilla threat anchor", Set.of(
                Capability.PM_ANCHOR_MATERIALIZATION, Capability.PM_ANCHOR_OBSERVATION));
    }

    public boolean ensureAnchor(ServerLevel level, TestMineRecord mine, String jobId) {
        if (mine.anchorId() != null) {
            Entity existing = level.getEntity(mine.anchorId());
            if (isOwnedAnchor(existing, mine)) return true;
        }
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) return false;
        zombie.moveTo(mine.anchor().getX() + 0.5D, mine.anchor().getY() + 1.0D, mine.anchor().getZ() + 0.5D, 0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.setCustomName(Component.literal("Pale Mirror Infection Anchor"));
        zombie.setCustomNameVisible(true);
        zombie.getPersistentData().putString(OBJECT_ID_KEY, mine.id().value());
        zombie.getPersistentData().putString(JOB_ID_KEY, jobId);
        zombie.getPersistentData().putString(ROLE_KEY, ANCHOR_ROLE);
        if (!level.addFreshEntity(zombie)) return false;
        mine.setAnchorId(zombie.getUUID());
        return true;
    }

    public boolean hasAnchor(ServerLevel level, TestMineRecord mine) {
        return mine.anchorId() != null && isOwnedAnchor(level.getEntity(mine.anchorId()), mine);
    }

    public void removeAnchor(ServerLevel level, TestMineRecord mine) {
        if (mine.anchorId() == null) return;
        Entity entity = level.getEntity(mine.anchorId());
        if (isOwnedAnchor(entity, mine)) entity.discard();
        mine.setAnchorId(null);
    }

    public static boolean isOwnedAnchor(Entity entity, TestMineRecord mine) {
        if (entity == null || entity.isRemoved()) return false;
        String role = entity.getPersistentData().getString(ROLE_KEY);
        return mine.id().value().equals(entity.getPersistentData().getString(OBJECT_ID_KEY))
                && (role.isEmpty() || ANCHOR_ROLE.equals(role));
    }

    public static boolean isAnchor(Entity entity) {
        String role = entity.getPersistentData().getString(ROLE_KEY);
        return (role.isEmpty() || ANCHOR_ROLE.equals(role))
                && !entity.getPersistentData().getString(OBJECT_ID_KEY).isBlank();
    }
}
