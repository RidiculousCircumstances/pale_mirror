package io.farfrontier.palemirror.internal.integration.spore;

import io.farfrontier.palemirror.internal.adapter.VanillaAnchorAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/**
 * Exact-version firewall for Spore's global runtime.  Its mixins never write
 * PM state: they only suppress upstream global behaviour.  PM provenance and
 * SavedData remain the authority for every entity that is allowed through.
 */
public final class SporeRuntimeFirewall {
    private static volatile boolean hookObserved;

    private SporeRuntimeFirewall() { }

    public static boolean enabled() {
        return ModList.get().getModContainerById(SporeSandboxAdapter.MOD_ID)
                .map(container -> SporeSandboxAdapter.VERSION.equals(container.getModInfo().getVersion().toString()))
                .orElse(false);
    }

    public static boolean rejectUnmanagedEntity(Entity entity) {
        if (!enabled() || entity == null) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return SporeSandboxAdapter.MOD_ID.equals(id.getNamespace()) && !isOwnedActor(entity);
    }

    public static boolean suppressOwnedActorAi(Entity entity) {
        if (!enabled() || !isOwnedActor(entity)) return false;
        hookObserved = true;
        return true;
    }

    /** PM moves and impacts its tagged visual projectile carrier itself. */
    public static boolean suppressOwnedProjectile(Entity entity) {
        if (!enabled() || entity == null) return false;
        return SporeSandboxAdapter.PROJECTILE_ROLE.equals(entity.getPersistentData().getString(VanillaAnchorAdapter.ROLE_KEY));
    }

    public static void observeGlobalHook() {
        if (enabled()) hookObserved = true;
    }

    public static boolean hookObserved() { return hookObserved; }

    private static boolean isOwnedActor(Entity entity) {
        String role = entity.getPersistentData().getString(VanillaAnchorAdapter.ROLE_KEY);
        return (SporeSandboxAdapter.ACTOR_ROLE.equals(role) || SporeSandboxAdapter.PROJECTILE_ROLE.equals(role))
                && !entity.getPersistentData().getString(VanillaAnchorAdapter.OBJECT_ID_KEY).isBlank()
                && !entity.getPersistentData().getString(SporeSandboxAdapter.SLOT_KEY).isBlank();
    }
}
