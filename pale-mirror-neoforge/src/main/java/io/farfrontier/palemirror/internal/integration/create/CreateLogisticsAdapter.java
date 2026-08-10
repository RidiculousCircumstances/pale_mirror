package io.farfrontier.palemirror.internal.integration.create;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.internal.adapter.LogisticsAdapter;
import io.farfrontier.palemirror.internal.adapter.LogisticsRouteContract;
import io.farfrontier.palemirror.internal.adapter.LogisticsRouteObservation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Version-pinned, read-only Create train observer. Reflection is intentionally
 * contained here so Create types and train internals never enter the domain,
 * generic materialization, or persisted PM state.
 */
public final class CreateLogisticsAdapter implements LogisticsAdapter {
    private static final String MOD_ID = "create";
    private static final String PINNED_VERSION = "6.0.10";
    private static final String STATION_CLASS = "com.simibubi.create.content.trains.station.StationBlockEntity";
    private static final int NODE_RADIUS = 8;

    @Override public String id() { return "pale_mirror:create_logistics"; }

    @Override
    public AdapterHealth health() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return new AdapterHealth(AdapterHealth.Status.ABSENT, "Create is not installed", Set.of());
        }
        String version = ModList.get().getModContainerById(MOD_ID).map(container ->
                container.getModInfo().getVersion().toString()).orElse("unknown");
        if (!PINNED_VERSION.equals(version)) {
            return new AdapterHealth(AdapterHealth.Status.BLOCKED, "Create " + version + " is installed; PM requires "
                    + PINNED_VERSION + " for its isolated train observer", Set.of());
        }
        try {
            Class.forName(STATION_CLASS, false, getClass().getClassLoader());
            return new AdapterHealth(AdapterHealth.Status.AVAILABLE,
                    "Create train stations can be observed through the PM logistics contract",
                    Set.of(Capability.LOGISTICS_ROUTE_OBSERVATION));
        } catch (ClassNotFoundException failure) {
            return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                    "Installed Create does not expose the pinned StationBlockEntity surface", Set.of());
        }
    }

    @Override
    public LogisticsRouteObservation observe(ServerLevel level, LogisticsRouteContract contract) {
        if (health().status() != AdapterHealth.Status.AVAILABLE) {
            return LogisticsRouteObservation.unobserved(health().detail());
        }
        if (!level.hasChunkAt(contract.originAnchor()) || !level.hasChunkAt(contract.destinationAnchor())) {
            return LogisticsRouteObservation.unobserved("Create route endpoints are not both loaded");
        }
        try {
            StationFact origin = findStation(level, contract.originAnchor(), contract.originStationName());
            StationFact destination = findStation(level, contract.destinationAnchor(), contract.destinationStationName());
            if (!origin.found() || !destination.found()) {
                return LogisticsRouteObservation.invalid("Expected named Create stations are missing: "
                        + contract.originStationName() + " -> " + contract.destinationStationName());
            }
            return new LogisticsRouteObservation(true, origin.trainPresent(), origin.capacity(), origin.vehicleId(),
                    destination.trainPresent(), destination.capacity(), destination.vehicleId(), "");
        } catch (ReflectiveOperationException failure) {
            return LogisticsRouteObservation.invalid("Create station observation failed: " + failure.getClass().getSimpleName());
        }
    }

    private static StationFact findStation(ServerLevel level, BlockPos anchor, String expectedName)
            throws ReflectiveOperationException {
        Class<?> stationType = Class.forName(STATION_CLASS, false, CreateLogisticsAdapter.class.getClassLoader());
        for (int x = -NODE_RADIUS; x <= NODE_RADIUS; x++) for (int y = -4; y <= 8; y++) for (int z = -NODE_RADIUS; z <= NODE_RADIUS; z++) {
            BlockPos position = anchor.offset(x, y, z);
            if (!level.hasChunkAt(position)) continue;
            BlockEntity candidate = level.getBlockEntity(position);
            if (candidate == null || !stationType.isInstance(candidate)) continue;
            Object globalStation = stationType.getMethod("getStation").invoke(candidate);
            if (globalStation == null) continue;
            Field name = globalStation.getClass().getField("name");
            if (!expectedName.equals(name.get(globalStation))) continue;
            Object train = globalStation.getClass().getMethod("getPresentTrain").invoke(globalStation);
            return train == null ? new StationFact(true, false, 0, "")
                    : new StationFact(true, true, capacity(train), vehicleId(train));
        }
        return new StationFact(false, false, 0, "");
    }

    private static int capacity(Object train) throws ReflectiveOperationException {
        Field carriages = train.getClass().getField("carriages");
        int count = carriages.get(train) instanceof Collection<?> values ? values.size() : 1;
        return count >= 4 ? 36 : count >= 2 ? 18 : 12;
    }

    /** A stable native identifier is intentionally reduced to an opaque generic vehicle id at the adapter boundary. */
    private static String vehicleId(Object train) throws ReflectiveOperationException {
        Object id = train.getClass().getField("id").get(train);
        return id == null ? "" : id.toString();
    }

    private record StationFact(boolean found, boolean trainPresent, int capacity, String vehicleId) { }
}
