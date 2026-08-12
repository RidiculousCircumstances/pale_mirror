package io.farfrontier.palemirror.visuals.resident;

import io.farfrontier.palemirror.api.ResidentSeed;
import net.minecraft.world.entity.Entity;

public final class ManagedResident {
    public static final String ID = "pale_mirror_visuals_resident_id";
    public static final String REGION = "pale_mirror_visuals_region_id";
    public static final String COHORT = "pale_mirror_visuals_cohort";
    public static final String ROLE = "pale_mirror_visuals_role";
    public static final String HOME = "pale_mirror_visuals_home";
    public static final String WORKPLACE = "pale_mirror_visuals_workplace";
    public static final String GROWTH_PERMIT = "pale_mirror_visuals_growth_permit";

    private ManagedResident() { }

    public static boolean isManaged(Entity entity) { return !entity.getPersistentData().getString(ID).isBlank(); }

    static void attach(Entity entity, String regionId, ResidentSeed seed) {
        var data = entity.getPersistentData();
        data.putString(ID, seed.residentId());
        data.putString(REGION, regionId);
        data.putString(COHORT, seed.cohort());
        data.putString(ROLE, seed.role());
        data.putLong(HOME, pack(seed.home().x(), seed.home().y(), seed.home().z()));
        if (seed.workplace() != null) data.putLong(WORKPLACE,
                pack(seed.workplace().x(), seed.workplace().y(), seed.workplace().z()));
    }

    private static long pack(int x, int y, int z) { return net.minecraft.core.BlockPos.asLong(x, y, z); }
}
