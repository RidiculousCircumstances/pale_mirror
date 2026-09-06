package io.farfrontier.palemirror.internal.integration.millenaire;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.domain.SettlementCohort;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.SettlementAdapter;
import io.farfrontier.palemirror.internal.adapter.SettlementObservation;
import io.farfrontier.palemirror.internal.adapter.SettlementRepresentativeObservation;
import io.farfrontier.palemirror.internal.world.SettlementObservationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

/**
 * Exact-version, read-only native settlement observer. Every private class name
 * and reflective call is contained in this package; generic PM layers receive
 * only typed evidence, an opaque reference and a source-neutral authority profile.
 */
public final class MillenaireSettlementAdapter implements SettlementAdapter {
    private static final String MOD_ID = "millenaire";
    private static final String PINNED_VERSION = "9.0.0-beta.2";
    private static final String SAVED_DATA = "org.millenaire.village.VillageSavedData";
    private static final String VILLAGE = "org.millenaire.village.Village";
    private static final String RECORD = "org.millenaire.village.VillagerRecord";
    private static final String PROVENANCE = "millenaire:native_village_v1";
    private static final int OBSERVATION_RADIUS = 160;
    private static final int VERTICAL_OBSERVATION_RADIUS = 64;
    private static final int MAX_RESIDENT_RECORDS = 512;
    private volatile String lastObservationError = "";

    @Override public String id() { return "pale_mirror:millenaire_settlement_observer"; }

    @Override
    public AdapterHealth health() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return new AdapterHealth(AdapterHealth.Status.ABSENT, "Millenaire is not installed", Set.of());
        }
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        if (!PINNED_VERSION.equals(version)) {
            return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                    "Millenaire " + version + " is installed; PM requires " + PINNED_VERSION, Set.of());
        }
        if (!lastObservationError.isBlank()) {
            return new AdapterHealth(AdapterHealth.Status.DEGRADED,
                    "Native settlement observation failed closed: " + lastObservationError, Set.of());
        }
        try {
            verifySurface();
            return new AdapterHealth(AdapterHealth.Status.AVAILABLE,
                    "Native villages are available through the pinned read-only reconciliation profile; campaign opt-in is "
                            + (campaignEnabled() ? "enabled" : "disabled"),
                    Set.of(Capability.SETTLEMENT_OBSERVATION, Capability.SETTLEMENT_NATIVE_RECONCILIATION));
        } catch (ReflectiveOperationException failure) {
            return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                    "Pinned Millenaire read surface failed self-check: " + failure.getClass().getSimpleName(), Set.of());
        }
    }

    @Override
    public List<SettlementObservation> observeNearby(ServerLevel level, BlockPos focus) {
        if (health().status() != AdapterHealth.Status.AVAILABLE) return List.of();
        try {
            Object saved = type(SAVED_DATA).getMethod("get", ServerLevel.class).invoke(null, level);
            Object manager = saved.getClass().getMethod("getVillageManager").invoke(saved);
            Collection<?> villages = (Collection<?>) manager.getClass().getMethod("getAllVillages").invoke(manager);
            List<SettlementObservation> observations = new ArrayList<>();
            for (Object village : villages) {
                SettlementObservation observation = observeVillage(level, focus, village);
                if (observation != null) observations.add(observation);
            }
            observations.sort(Comparator.comparing(value -> value.settlementId().value()));
            lastObservationError = "";
            return List.copyOf(observations);
        } catch (ReflectiveOperationException | ClassCastException failure) {
            lastObservationError = failure.getClass().getSimpleName();
            return List.of();
        }
    }

    public boolean owns(SettlementObservationRecord record) { return PROVENANCE.equals(record.provenance()); }
    public boolean campaignEnabled() { return MillenaireIntegrationConfig.ALLOW_CAMPAIGN.get(); }

    private static SettlementObservation observeVillage(ServerLevel level, BlockPos focus, Object village)
            throws ReflectiveOperationException {
        BlockPos center = (BlockPos) village.getClass().getMethod("getCenter").invoke(village);
        if (center.distSqr(focus) > (long) OBSERVATION_RADIUS * OBSERVATION_RADIUS) return null;
        AABB bounds = (AABB) village.getClass().getMethod("computeBounds").invoke(village);
        BlockPos min = new BlockPos(Math.max(center.getX() - OBSERVATION_RADIUS, (int) Math.floor(bounds.minX)),
                Math.max(center.getY() - VERTICAL_OBSERVATION_RADIUS, (int) Math.floor(bounds.minY)),
                Math.max(center.getZ() - OBSERVATION_RADIUS, (int) Math.floor(bounds.minZ)));
        BlockPos max = new BlockPos(Math.min(center.getX() + OBSERVATION_RADIUS, (int) Math.ceil(bounds.maxX)),
                Math.min(center.getY() + VERTICAL_OBSERVATION_RADIUS, (int) Math.ceil(bounds.maxY)),
                Math.min(center.getZ() + OBSERVATION_RADIUS, (int) Math.ceil(bounds.maxZ)));
        Object villageId = village.getClass().getMethod("getId").invoke(village);
        UUID uuid = (UUID) villageId.getClass().getMethod("uuid").invoke(villageId);
        @SuppressWarnings("unchecked")
        Map<UUID, ?> records = (Map<UUID, ?>) village.getClass().getMethod("getVillagerRecords").invoke(village);
        List<SettlementRepresentativeObservation> representatives = new ArrayList<>();
        int guards = 0;
        for (Map.Entry<UUID, ?> entry : records.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            Object record = entry.getValue();
            if ((boolean) record.getClass().getMethod("isKilled").invoke(record)) continue;
            if (representatives.size() >= MAX_RESIDENT_RECORDS) {
                throw new ReflectiveOperationException("Native resident record bound exceeded");
            }
            SettlementCohort cohort = cohort(record);
            if (cohort == SettlementCohort.GUARDS) guards++;
            representatives.add(new SettlementRepresentativeObservation(entry.getKey().toString(), cohort));
        }
        String compactId = uuid.toString().replace("-", "");
        return new SettlementObservation(new WorldObjectId("pale_mirror:native_village_" + compactId),
                level.dimension().location().toString(), center, min, max, representatives.size(), guards,
                level.getGameTime(), fullyLoaded(level, min, max), representatives, PROVENANCE,
                "pale_mirror:native_reconciled", uuid.toString());
    }

    private static SettlementCohort cohort(Object record) throws ReflectiveOperationException {
        int childSize = (int) record.getClass().getMethod("getChildSize").invoke(record);
        if (childSize > 0) return SettlementCohort.CHILDREN;
        int military = (int) record.getClass().getMethod("getMilitaryStrength").invoke(record);
        if (military > 0) return SettlementCohort.GUARDS;
        String role = String.valueOf(record.getClass().getMethod("getVillagerTypeId").invoke(record))
                .toLowerCase(java.util.Locale.ROOT);
        if (role.contains("priest") || role.contains("merchant") || role.contains("scholar")) return SettlementCohort.SPECIALISTS;
        return role.isBlank() ? SettlementCohort.CIVILIANS : SettlementCohort.WORKERS;
    }

    private static boolean fullyLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        for (int x = Math.floorDiv(min.getX(), 16); x <= Math.floorDiv(max.getX(), 16); x++) {
            for (int z = Math.floorDiv(min.getZ(), 16); z <= Math.floorDiv(max.getZ(), 16); z++) {
                if (!level.hasChunkAt(new BlockPos(x << 4, min.getY(), z << 4))) return false;
            }
        }
        return true;
    }

    private static void verifySurface() throws ReflectiveOperationException {
        Class<?> saved = type(SAVED_DATA);
        Method get = saved.getMethod("get", ServerLevel.class);
        if (!Modifier.isStatic(get.getModifiers())) throw new NoSuchMethodException("VillageSavedData.get is not static");
        Class<?> village = type(VILLAGE);
        village.getMethod("getId");
        village.getMethod("getCenter");
        village.getMethod("computeBounds");
        village.getMethod("getVillagerRecords");
        Class<?> record = type(RECORD);
        record.getMethod("isKilled");
        record.getMethod("getChildSize");
        record.getMethod("getMilitaryStrength");
        record.getMethod("getVillagerTypeId");
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, MillenaireSettlementAdapter.class.getClassLoader());
    }

}
