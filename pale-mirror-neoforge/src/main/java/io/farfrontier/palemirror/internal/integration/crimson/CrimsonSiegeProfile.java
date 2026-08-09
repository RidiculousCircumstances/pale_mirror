package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.Arrays;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** Exact 1.4.3.1 local forms used only by PM's staged siege runtime. */
enum CrimsonSiegeProfile {
    JUGGERNAUT("pale_mirror:juggernaut", EntityType.ZOMBIE, "Juggernaut", "initialize_juggernaut"),
    KNIGHT("pale_mirror:knight", EntityType.ZOMBIE, "Knight", "initialize_knight"),
    MANGLER("pale_mirror:mangler", EntityType.RAVAGER, "Mangler", "initialize_mangler"),
    PUMMELER("pale_mirror:pummeler", EntityType.GHAST, "Pummeler", "initialize_pummeler"),
    KRAKEN("pale_mirror:kraken", EntityType.PHANTOM, "Kraken", "initialize_kraken"),
    OSIRIS("pale_mirror:osiris", EntityType.ZOMBIE, "Osiris", "initialize_osiris"),
    BLOODLINK_I("pale_mirror:bloodlink_i", EntityType.WITHER_SKELETON, "PM_Bloodlink_I", "initialize_bloodlink_i"),
    BLOODLINK_II("pale_mirror:bloodlink_ii", EntityType.WITHER_SKELETON, "PM_Bloodlink_II", "initialize_bloodlink_ii"),
    BLOODLINK_III("pale_mirror:bloodlink_iii", EntityType.WITHER_SKELETON, "PM_Bloodlink_III", "initialize_bloodlink_iii");

    private final String id;
    private final EntityType<? extends Mob> entityType;
    private final String markerTag;
    private final String initializer;

    CrimsonSiegeProfile(String id, EntityType<? extends Mob> entityType, String markerTag, String initializer) {
        this.id = id;
        this.entityType = entityType;
        this.markerTag = markerTag;
        this.initializer = initializer;
    }

    static Optional<CrimsonSiegeProfile> byId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }

    String id() { return id; }
    String markerTag() { return markerTag; }
    String initializer() { return initializer; }
    String entityTypeId() { return EntityType.getKey(entityType).toString(); }
    Mob create(ServerLevel level) { return entityType.create(level); }
    boolean matches(Entity entity) { return entity != null && entity.getType() == entityType && entity.getTags().contains(markerTag); }
}
