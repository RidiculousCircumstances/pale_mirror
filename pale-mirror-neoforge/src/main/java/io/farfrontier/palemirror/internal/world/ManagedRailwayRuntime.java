package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.FreightServiceObservation;
import io.farfrontier.palemirror.internal.adapter.FreightServiceRequest;
import io.farfrontier.palemirror.internal.adapter.FreightServiceStatus;
import io.farfrontier.palemirror.internal.adapter.RailConnectionObservation;
import io.farfrontier.palemirror.internal.adapter.RailConnectionRequest;
import io.farfrontier.palemirror.internal.adapter.RailConnectionStatus;
import io.farfrontier.palemirror.internal.adapter.RailInfrastructureAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;

/** Restart-safe commissioning coordinator for the product-profile legacy freight line. */
public final class ManagedRailwayRuntime {
    private static final int MAXIMUM_LINE_LENGTH = 1_200;

    private ManagedRailwayRuntime() { }

    public static void stop(MinecraftServer server) {
        // Production rail construction owns no PM chunk tickets. Natural chunk lifecycle is authoritative.
    }

    public static void installPlacementAuthority(PaleMirrorSavedData data) {
        AdapterRegistry.managedRailway().installPlacementAuthority((level, connectionId, position, current, proposed) -> {
            CampaignCommissioningRecord record = data.campaignCommissioning().values().stream()
                    .filter(value -> value.connectionId().equals(connectionId)).findFirst().orElse(null);
            if (record == null) return false;
            boolean allowed = record.authorize(level, position, current, proposed);
            data.setDirty();
            return allowed;
        });
    }

    public static boolean tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
        RailInfrastructureAdapter adapter = AdapterRegistry.managedRailway();
        if (adapter.health().status() != AdapterHealth.Status.AVAILABLE) return false;
        boolean changed = ensureRecords(server, data);
        for (CampaignCommissioningRecord record : data.campaignCommissioning().values()) {
            if (!record.dimensionId().equals(server.overworld().dimension().location().toString())) continue;
            if (record.mayRetryFirstGenerationPlacement(server.overworld())) {
                record.retryFirstGenerationPlacement();
                RailConnectionObservation resumed = adapter.resume(server.overworld(), record.connectionId());
                if (resumed.status() == RailConnectionStatus.BLOCKED || resumed.status() == RailConnectionStatus.FAILED
                        || resumed.status() == RailConnectionStatus.UNAVAILABLE) record.block(resumed.diagnostic());
                else {
                    record.railBuilding(resumed.planHash(), resumed.nativeReference());
                    record.observeRailProgress(resumed.completedSegments(), resumed.totalSegments(), resumed.diagnostic());
                }
                changed = true;
            }
            changed |= advance(server.overworld(), record, adapter, data.worldState().simulationStep());
            changed |= validateCanonicalRoute(data, commands, record);
        }
        return changed;
    }

    private static boolean validateCanonicalRoute(PaleMirrorSavedData data, DomainCommandProcessor commands,
                                                   CampaignCommissioningRecord record) {
        if (record.status() != CampaignCommissioningStatus.BASELINE_VALIDATION
                && record.status() != CampaignCommissioningStatus.ACTIVE) return false;
        var region = data.worldState().livingRegion(record.regionId()).orElse(null);
        if (region == null) return false;
        var route = data.worldState().routeContract(region.primaryRouteId()).orElse(null);
        if (route == null) return false;
        String observationId = "managed-rail:" + record.serviceId() + ":" + data.worldState().simulationStep();
        if (route.lastObservationId().equals(observationId)) return false;
        return !commands.execute(data.worldState(), new DomainCommand.ValidateRouteContract(region.primaryRouteId(),
                route.nominalCapacity(), data.worldState().simulationStep(), observationId,
                "commissioning:" + record.regionId())).isEmpty();
    }

    private static boolean ensureRecords(MinecraftServer server, PaleMirrorSavedData data) {
        boolean changed = false;
        for (CampaignRegionRecord region : data.campaignRegions().values()) {
            if (region.status() != CampaignRegionPresentationStatus.MATERIALIZED || region.primaryMineAnchor() == null
                    || data.campaignCommissioning().containsKey(region.id())) continue;
            BlockPos start = region.primaryMineAnchor().offset(0, 0, -3);
            BlockPos target = railTarget(server.overworld(), region.settlementAnchor(), start);
            Direction.Axis axis = Math.abs(target.getX() - start.getX()) >= Math.abs(target.getZ() - start.getZ())
                    ? Direction.Axis.X : Direction.Axis.Z;
            Direction assemblyDirection = axis == Direction.Axis.X
                    ? (target.getX() >= start.getX() ? Direction.EAST : Direction.WEST)
                    : (target.getZ() >= start.getZ() ? Direction.SOUTH : Direction.NORTH);
            CampaignCommissioningRecord record = CampaignCommissioningRecord.planned(region.id(), region.dimensionId(),
                    "pm:" + safeId(region.id()) + ":legacy_line", "pm:" + safeId(region.id()) + ":legacy_freight",
                    start, target, start, axis, assemblyDirection, MAXIMUM_LINE_LENGTH,
                    "PM Mine17 Loading", CampaignRegionBootstrapper.IRONHILL_RECEIVING);
            data.campaignCommissioning().put(region.id(), record);
            changed = true;
        }
        return changed;
    }

    private static BlockPos railTarget(ServerLevel level, BlockPos anchor, BlockPos mine) {
        int dx = mine.getX() - anchor.getX(); int dz = mine.getZ() - anchor.getZ();
        int x = anchor.getX(); int z = anchor.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) x += Integer.signum(dx) * 64;
        else z += Integer.signum(dz) * 64;
        BlockPos targetColumn = new BlockPos(x, anchor.getY(), z);
        // Full recognition normally keeps this nearby column loaded. Retain the persisted ground
        // infrastructure height as the fail-closed fallback; never query an unseen column.
        int y = level.hasChunkAt(targetColumn)
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : anchor.getY();
        return new BlockPos(x, y, z);
    }

    private static boolean advance(ServerLevel level, CampaignCommissioningRecord record,
                                   RailInfrastructureAdapter adapter, long simulationStep) {
        return switch (record.status()) {
            case PLANNED -> plan(level, record, adapter);
            case RAIL_BUILDING -> build(level, record, adapter);
            case RAIL_READY -> { record.trainCommissioning(); yield true; }
            case TRAIN_COMMISSIONING, BASELINE_VALIDATION -> commissionTrain(level, record, adapter, simulationStep);
            case BLOCKED -> recoverProviderService(level, record, adapter);
            default -> false;
        };
    }

    private static boolean recoverProviderService(ServerLevel level, CampaignCommissioningRecord record,
                                                  RailInfrastructureAdapter adapter) {
        FreightServiceObservation observed = adapter.freightService(level, record.serviceId()).orElse(null);
        if (observed == null || !RailwayRecoveryPolicy.serviceCanResume(observed.status())) return false;
        record.resumeTrainCommissioning();
        return true;
    }

    private static boolean plan(ServerLevel level, CampaignCommissioningRecord record, RailInfrastructureAdapter adapter) {
        RailConnectionObservation observed = adapter.plan(level, new RailConnectionRequest(record.connectionId(),
                record.railStart(), record.railTarget(), record.trackAxis(), record.maximumLength()));
        if (observed.status() == RailConnectionStatus.BLOCKED || observed.status() == RailConnectionStatus.FAILED
                || observed.status() == RailConnectionStatus.UNAVAILABLE) {
            record.block(observed.diagnostic()); return true;
        }
        record.railBuilding(observed.planHash(), observed.nativeReference());
        record.observeRailProgress(observed.completedSegments(), observed.totalSegments(), observed.diagnostic());
        return true;
    }

    private static boolean build(ServerLevel level, CampaignCommissioningRecord record, RailInfrastructureAdapter adapter) {
        RailConnectionObservation observed = adapter.connection(level, record.connectionId()).orElse(null);
        if (observed == null || observed.status() == RailConnectionStatus.PLANNED) {
            observed = adapter.start(level, record.connectionId());
        }
        boolean changed = record.observeRailProgress(observed.completedSegments(), observed.totalSegments(),
                observed.diagnostic());
        if (observed.status() == RailConnectionStatus.BLOCKED || observed.status() == RailConnectionStatus.FAILED) {
            record.block(observed.diagnostic()); return true;
        }
        if (observed.status() == RailConnectionStatus.VERIFYING || observed.status() == RailConnectionStatus.READY) {
            record.railReady(observed.nativeReference()); return true;
        }
        return changed;
    }

    private static boolean commissionTrain(ServerLevel level, CampaignCommissioningRecord record,
                                           RailInfrastructureAdapter adapter, long simulationStep) {
        FreightServiceObservation observed = adapter.freightService(level, record.serviceId()).orElse(null);
        if (observed == null) observed = adapter.ensureFreightService(level, new FreightServiceRequest(record.serviceId(),
                record.connectionId(), record.assemblyTrack(), record.assemblyDirection(), record.originStation(),
                record.destinationStation()));
        if (observed.status() == FreightServiceStatus.BLOCKED || observed.status() == FreightServiceStatus.UNAVAILABLE) {
            record.block(observed.diagnostic()); return true;
        }
        if (observed.status() == FreightServiceStatus.PLAYER_MANAGED) {
            record.suspend("Player changed the PM service schedule; canonical flow is paused"); return true;
        }
        boolean changed = record.observeTrain(observed.nativeReference(), observed.scheduleFingerprint());
        if (record.baselineArrivals() == 0 && record.destinationStation().equals(observed.currentStation())) {
            record.observeBaselineArrival(simulationStep); return true;
        }
        if (record.baselineArrivals() > 0 && simulationStep >= record.infectionEligibleAtStep()) {
            record.activate(); return true;
        }
        return changed;
    }

    private static String safeId(String value) { return value.replace(':', '_').replace('/', '_'); }

}
