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
import io.farfrontier.palemirror.domain.RouteProvider;

/** Restart-safe commissioning coordinator for the product-profile legacy freight line. */
public final class ManagedRailwayRuntime {
    private static final int MAXIMUM_LINE_LENGTH = 1_200;
    private static final String RED_VALLEY_EXERCISE_SUFFIX = ":red_valley_exercise";

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

    /**
     * Explicit closed-alpha convenience. It persists a provider-owned Create
     * connection and freight service for the alternate source, but never
     * validates the canonical route directly; the ordinary read-only Create
     * observation must still prove the same train at both endpoints.
     */
    public static CommissioningResult commissionRedValleyExercise(MinecraftServer server,
                                                                   PaleMirrorSavedData data,
                                                                   String requestedRegionId) {
        RailInfrastructureAdapter adapter = AdapterRegistry.managedRailway();
        if (adapter.health().status() != AdapterHealth.Status.AVAILABLE) {
            return CommissioningResult.failure("Railway Untold managed adapter is not AVAILABLE: "
                    + adapter.health().detail());
        }
        var regions = data.worldState().livingRegions().stream()
                .filter(region -> requestedRegionId == null || requestedRegionId.isBlank()
                        || region.id().equals(requestedRegionId))
                .sorted(java.util.Comparator.comparing(io.farfrontier.palemirror.domain.LivingRegionState::id)).toList();
        if (regions.isEmpty()) return CommissioningResult.failure(requestedRegionId == null || requestedRegionId.isBlank()
                ? "No living region is available" : "Unknown living region " + requestedRegionId);
        if ((requestedRegionId == null || requestedRegionId.isBlank()) && regions.size() != 1) {
            return CommissioningResult.failure("More than one living region exists; specify its full region id");
        }
        var region = regions.getFirst();
        CampaignRegionRecord physical = data.campaignRegions().get(region.id());
        if (physical == null || physical.status() != CampaignRegionPresentationStatus.MATERIALIZED
                || physical.alternateMineAnchor() == null) {
            return CommissioningResult.failure("Red Valley MineSite is not materialized for " + region.id());
        }
        String exerciseId = region.id() + RED_VALLEY_EXERCISE_SUFFIX;
        CampaignCommissioningRecord existing = data.campaignCommissioning().get(exerciseId);
        if (existing != null) {
            String message = "Red Valley exercise already exists at " + existing.railStart().toShortString()
                    + " -> " + existing.railTarget().toShortString();
            boolean stalledGraphMerge = existing.status() == CampaignCommissioningStatus.RAIL_BUILDING
                    && existing.totalSegments() > 0
                    && existing.completedSegments() == existing.totalSegments()
                    && existing.diagnostic().contains("waiting for Create graph merge");
            if (existing.status() == CampaignCommissioningStatus.BLOCKED || stalledGraphMerge) {
                RailConnectionObservation resumed = adapter.resume(server.overworld(), existing.connectionId());
                if (resumed.status() == RailConnectionStatus.BLOCKED || resumed.status() == RailConnectionStatus.FAILED
                        || resumed.status() == RailConnectionStatus.UNAVAILABLE) existing.block(resumed.diagnostic());
                else {
                    existing.railBuilding(resumed.planHash(), resumed.nativeReference());
                    existing.observeRailProgress(resumed.completedSegments(), resumed.totalSegments(), resumed.diagnostic());
                    message += "; explicit retry requested";
                }
                data.setDirty();
            }
            return CommissioningResult.success(exerciseId, existing, message);
        }

        BlockPos start = physical.alternateMineAnchor().offset(0, 1, -3);
        BlockPos target = physical.settlementAnchor();
        int dx = target.getX() - start.getX();
        int dz = target.getZ() - start.getZ();
        Direction.Axis axis = Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
        Direction direction = axis == Direction.Axis.X ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        int length = Math.abs(dx) + Math.abs(dz);
        if (length < 32 || length > MAXIMUM_LINE_LENGTH) {
            return CommissioningResult.failure("Red Valley exercise length " + length
                    + " is outside 32.." + MAXIMUM_LINE_LENGTH);
        }
        RegionBindings bindings = RegionBindings.fromRegionId(region.id());
        CampaignCommissioningRecord record = CampaignCommissioningRecord.planned(exerciseId,
                physical.dimensionId(), "pm:" + safeId(region.id()) + ":red_valley_line",
                "pm:" + safeId(region.id()) + ":red_valley_freight", start, target, start, axis, direction,
                MAXIMUM_LINE_LENGTH, bindings.alternateDispatchStation(), bindings.receivingStation(),
                io.farfrontier.palemirror.internal.adapter.RailConstructionPolicy.AUTONOMOUS_DEV);
        data.campaignCommissioning().put(exerciseId, record);
        data.setDirty();
        return CommissioningResult.success(exerciseId, record,
                "Red Valley exercise persisted at " + start.toShortString() + " -> " + target.toShortString()
                        + "; Railway Untold will build one bounded segment footprint at a time");
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
            var living = data.worldState().livingRegion(region.id()).orElse(null);
            if (living == null || !data.worldState().routeContract(living.primaryRouteId())
                    .map(route -> route.provider() == RouteProvider.MANAGED_RAILWAY).orElse(false)) continue;
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
                record.railStart(), record.railTarget(), record.trackAxis(), record.maximumLength(),
                record.constructionPolicy()));
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

    public record CommissioningResult(boolean success, String exerciseId, CampaignCommissioningStatus status,
                                      int completedSegments, int totalSegments, String diagnostic, String message) {
        static CommissioningResult success(String id, CampaignCommissioningRecord record, String message) {
            return new CommissioningResult(true, id, record.status(), record.completedSegments(),
                    record.totalSegments(), record.diagnostic(), message);
        }
        static CommissioningResult failure(String message) {
            return new CommissioningResult(false, "", null, 0, 0, "", message);
        }
        public String describe() {
            return success ? message + "; id=" + exerciseId + "; status=" + status
                    + "; segments=" + completedSegments + "/" + totalSegments
                    + (diagnostic.isBlank() ? "" : "; diagnostic=" + diagnostic) : message;
        }
    }

}
