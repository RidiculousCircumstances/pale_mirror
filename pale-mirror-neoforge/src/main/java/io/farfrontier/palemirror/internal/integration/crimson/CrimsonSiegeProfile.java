package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.Arrays;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** Exact 1.4.3.1 local forms used only by PM's staged siege runtime. */
enum CrimsonSiegeProfile {
    JUGGERNAUT("pale_mirror:juggernaut", EntityType.ZOMBIE, "Juggernaut", "Juggernaut", "initialize_juggernaut"),
    KNIGHT("pale_mirror:knight", EntityType.ZOMBIE, "Knight", "Knight", "initialize_knight"),
    MANGLER("pale_mirror:mangler", EntityType.RAVAGER, "Mangler", "Mangler", "initialize_mangler"),
    PUMMELER("pale_mirror:pummeler", EntityType.GHAST, "Pummeler", "Pummeler", "initialize_pummeler"),
    KRAKEN("pale_mirror:kraken", EntityType.PHANTOM, "Kraken", "Kraken", "initialize_kraken"),
    OSIRIS("pale_mirror:osiris", EntityType.ZOMBIE, "Osiris", "Osiris", "initialize_osiris"),
    BLOODLINK_I("pale_mirror:bloodlink_i", EntityType.WITHER_SKELETON, "PM_Bloodlink_I", "Stage I Bloodlink", "initialize_bloodlink_i"),
    BLOODLINK_II("pale_mirror:bloodlink_ii", EntityType.WITHER_SKELETON, "PM_Bloodlink_II", "Stage II Bloodlink", "initialize_bloodlink_ii"),
    BLOODLINK_III("pale_mirror:bloodlink_iii", EntityType.WITHER_SKELETON, "PM_Bloodlink_III", "Stage III Bloodlink", "initialize_bloodlink_iii");

    private final String id;
    private final EntityType<? extends Mob> entityType;
    private final String markerTag;
    private final String visualName;
    private final String initializer;

    CrimsonSiegeProfile(String id, EntityType<? extends Mob> entityType, String markerTag, String visualName, String initializer) {
        this.id = id;
        this.entityType = entityType;
        this.markerTag = markerTag;
        this.visualName = visualName;
        this.initializer = initializer;
    }

    static Optional<CrimsonSiegeProfile> byId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }

    String id() { return id; }
    String markerTag() { return markerTag; }
    String visualName() { return visualName; }
    String initializer() { return initializer; }
    String entityTypeId() { return EntityType.getKey(entityType).toString(); }
    Mob create(ServerLevel level) { return entityType.create(level); }
    boolean matches(Entity entity) {
        if (entity == null || entity.getType() != entityType || !entity.getTags().contains(markerTag)
                || !visualName.equals(entity.getName().getString())) return false;
        return this != PUMMELER || entity.getPassengers().stream().anyMatch(passenger -> passenger.getType() == EntityType.ITEM_DISPLAY
                && passenger.getTags().contains("PM_Pummeler_Visual"));
    }
}
