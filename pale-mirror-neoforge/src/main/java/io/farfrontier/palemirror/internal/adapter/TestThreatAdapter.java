package io.farfrontier.palemirror.internal.adapter;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.IntegrationAdapter;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;

import java.util.Set;

/** Core-only physical threat. It proves the loop without assuming any external mod API. */
public final class TestThreatAdapter implements IntegrationAdapter {
    public static final String OBJECT_ID_KEY = "pale_mirror_object_id";
    public static final String JOB_ID_KEY = "pale_mirror_job_id";

    @Override
    public String id() { return "pale_mirror:test_threat"; }

    @Override
    public AdapterHealth health() {
        return new AdapterHealth(AdapterHealth.Status.AVAILABLE, "Built-in deterministic test threat", Set.of(
                Capability.TEST_THREAT_MATERIALIZATION, Capability.TEST_THREAT_OBSERVATION));
    }

    public boolean ensureController(ServerLevel level, TestMineRecord mine, String jobId) {
        if (mine.controllerId() != null) {
            Entity existing = level.getEntity(mine.controllerId());
            if (existing != null && !existing.isRemoved()) return true;
        }
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) return false;
        zombie.moveTo(mine.anchor().getX() + 0.5D, mine.anchor().getY() + 1.0D, mine.anchor().getZ() + 0.5D, 0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.setCustomName(Component.literal("Pale Mirror Test Threat"));
        zombie.setCustomNameVisible(true);
        zombie.getPersistentData().putString(OBJECT_ID_KEY, mine.id().value());
        zombie.getPersistentData().putString(JOB_ID_KEY, jobId);
        if (!level.addFreshEntity(zombie)) return false;
        mine.setControllerId(zombie.getUUID());
        return true;
    }

    public void removeController(ServerLevel level, TestMineRecord mine) {
        if (mine.controllerId() == null) return;
        Entity entity = level.getEntity(mine.controllerId());
        if (entity != null && !entity.isRemoved()) entity.discard();
        mine.setControllerId(null);
    }
}
