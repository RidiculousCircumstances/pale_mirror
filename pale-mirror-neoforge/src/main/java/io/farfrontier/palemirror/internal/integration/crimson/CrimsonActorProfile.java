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
            "Crimsonified_Wither_Skeleton", "initialize_wither_skeleton"),
    DECAYED_HUMAN("pale_mirror:decayed_human", EntityType.ZOMBIE, "Decayed_Human", "initialize_decayed_human"),
    DECAYED_VILLAGER("pale_mirror:decayed_villager", EntityType.ZOMBIE_VILLAGER, "Decayed_Villager", "initialize_decayed_villager"),
    DECAYED_HUSK("pale_mirror:decayed_husk", EntityType.HUSK, "Decayed_Husk", "initialize_decayed_husk"),
    DECAYED_SKELETON("pale_mirror:decayed_skeleton", EntityType.SKELETON, "Decayed_Skeleton", "initialize_decayed_skeleton"),
    DECAYED_DROWNED("pale_mirror:decayed_drowned", EntityType.DROWNED, "Decayed_Drowned", "initialize_decayed_drowned"),
    DECAYED_BOGGED("pale_mirror:decayed_bogged", EntityType.BOGGED, "Decayed_Bogged", "initialize_decayed_bogged"),
    DECAYED_WITHER_SKELETON("pale_mirror:decayed_wither_skeleton", EntityType.WITHER_SKELETON,
            "Decayed_Wither_Skeleton", "initialize_decayed_wither_skeleton"),
    RUSHER("pale_mirror:rusher", EntityType.RAVAGER, "Rusher", "initialize_rusher", CrimsonActorBehavior.RUSHER_DASH),
    RAPTOR("pale_mirror:raptor", EntityType.ZOMBIE, "Raptor", "initialize_raptor", CrimsonActorBehavior.RAPTOR_AURA);

    private final String id;
    private final EntityType<? extends Mob> entityType;
    private final String markerTag;
    private final String initializer;
    private final CrimsonActorBehavior behavior;

    CrimsonActorProfile(String id, EntityType<? extends Mob> entityType, String markerTag, String initializer) {
        this(id, entityType, markerTag, initializer, CrimsonActorBehavior.VANILLA);
    }

    CrimsonActorProfile(String id, EntityType<? extends Mob> entityType, String markerTag, String initializer,
                        CrimsonActorBehavior behavior) {
        this.id = id;
        this.entityType = entityType;
        this.markerTag = markerTag;
        this.initializer = initializer;
        this.behavior = behavior;
    }

    static Optional<CrimsonActorProfile> byId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }

    String id() { return id; }
    String entityTypeId() { return EntityType.getKey(entityType).toString(); }
    String markerTag() { return markerTag; }
    String initializer() { return initializer; }
    CrimsonActorBehavior behavior() { return behavior; }
    Mob create(ServerLevel level) { return entityType.create(level); }
    boolean matches(Entity entity) {
        return entity != null && entity.getType() == entityType && entity.getTags().contains(markerTag);
    }
}
