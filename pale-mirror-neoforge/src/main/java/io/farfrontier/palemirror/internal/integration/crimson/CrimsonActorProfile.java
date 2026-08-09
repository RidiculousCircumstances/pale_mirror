package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.Arrays;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** Exact 1.4.3.1 local forms. Upstream identifiers stay inside the Crimson black box. */
enum CrimsonActorProfile {
    HUMAN("pale_mirror:crimsonified_human", EntityType.ZOMBIE, "Crimsonified_Human", "initialize_human"),
    VILLAGER("pale_mirror:crimsonified_villager", EntityType.ZOMBIE_VILLAGER, "Crimsonified_Villager", "initialize_villager"),
    HUSK("pale_mirror:crimsonified_husk", EntityType.HUSK, "Crimsonified_Husk", "initialize_husk"),
    SKELETON("pale_mirror:crimsonified_skeleton", EntityType.SKELETON, "Crimsonified_Skeleton", "initialize_skeleton"),
    DROWNED("pale_mirror:crimsonified_drowned", EntityType.DROWNED, "Crimsonified_Drowned", "initialize_drowned"),
    BOGGED("pale_mirror:crimsonified_bogged", EntityType.BOGGED, "Crimsonified_Bogged", "initialize_bogged"),
    WITHER_SKELETON("pale_mirror:crimsonified_wither_skeleton", EntityType.WITHER_SKELETON,
            "Crimsonified_Wither_Skeleton", "initialize_wither_skeleton");

    private final String id;
    private final EntityType<? extends Mob> entityType;
    private final String markerTag;
    private final String initializer;

    CrimsonActorProfile(String id, EntityType<? extends Mob> entityType, String markerTag, String initializer) {
        this.id = id;
        this.entityType = entityType;
        this.markerTag = markerTag;
        this.initializer = initializer;
    }

    static Optional<CrimsonActorProfile> byId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }

    String id() { return id; }
    String entityTypeId() { return EntityType.getKey(entityType).toString(); }
    String markerTag() { return markerTag; }
    String initializer() { return initializer; }
    Mob create(ServerLevel level) { return entityType.create(level); }
    boolean matches(Entity entity) {
        return entity != null && entity.getType() == entityType && entity.getTags().contains(markerTag);
    }
}
