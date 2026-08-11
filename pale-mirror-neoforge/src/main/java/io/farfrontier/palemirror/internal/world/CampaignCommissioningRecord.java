package io.farfrontier.palemirror.internal.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import io.farfrontier.palemirror.internal.adapter.RailConstructionPolicy;

/** Restart-safe authority record for one physical living-region railway and service. */
public final class CampaignCommissioningRecord {
    private final String regionId;
    private final String dimensionId;
    private final String connectionId;
    private final String serviceId;
    private final BlockPos railStart;
    private final BlockPos railTarget;
    private final BlockPos assemblyTrack;
    private final Direction.Axis trackAxis;
    private final Direction assemblyDirection;
    private final int maximumLength;
    private final String originStation;
    private final String destinationStation;
    private final Map<Long, RailwayMutableCell> railCells;
    private CampaignCommissioningStatus status;
    private String planHash;
    private String nativeRailReference;
    private String nativeTrainReference;
    private String scheduleFingerprint;
    private String diagnostic;
    private int baselineArrivals;
    private long infectionEligibleAtStep;
    private final RailConstructionPolicy constructionPolicy;
    private int completedSegments;
    private int totalSegments;

    public CampaignCommissioningRecord(String regionId, String dimensionId, String connectionId, String serviceId,
            BlockPos railStart, BlockPos railTarget, BlockPos assemblyTrack, Direction.Axis trackAxis,
            Direction assemblyDirection, int maximumLength, String originStation, String destinationStation,
            CampaignCommissioningStatus status, String planHash, String nativeRailReference,
            String nativeTrainReference, String scheduleFingerprint, String diagnostic, int baselineArrivals,
            long infectionEligibleAtStep, Map<Long, RailwayMutableCell> railCells,
            RailConstructionPolicy constructionPolicy, int completedSegments, int totalSegments) {
        this.regionId = require(regionId); this.dimensionId = require(dimensionId);
        this.connectionId = require(connectionId); this.serviceId = require(serviceId);
        this.railStart = Objects.requireNonNull(railStart).immutable();
        this.railTarget = Objects.requireNonNull(railTarget).immutable();
        this.assemblyTrack = Objects.requireNonNull(assemblyTrack).immutable();
        this.trackAxis = Objects.requireNonNull(trackAxis); this.assemblyDirection = Objects.requireNonNull(assemblyDirection);
        if (maximumLength < 1 || baselineArrivals < 0 || infectionEligibleAtStep < -1
                || completedSegments < 0 || totalSegments < 0 || completedSegments > totalSegments) {
            throw new IllegalArgumentException("Invalid commissioning counters");
        }
        this.maximumLength = maximumLength; this.originStation = require(originStation); this.destinationStation = require(destinationStation);
        this.status = Objects.requireNonNull(status); this.planHash = safe(planHash);
        this.nativeRailReference = safe(nativeRailReference); this.nativeTrainReference = safe(nativeTrainReference);
        this.scheduleFingerprint = safe(scheduleFingerprint); this.diagnostic = safe(diagnostic);
        this.baselineArrivals = baselineArrivals; this.infectionEligibleAtStep = infectionEligibleAtStep;
        this.railCells = new LinkedHashMap<>(railCells);
        this.constructionPolicy = Objects.requireNonNull(constructionPolicy);
        this.completedSegments = completedSegments;
        this.totalSegments = totalSegments;
    }

    public static CampaignCommissioningRecord planned(String regionId, String dimensionId, String connectionId,
            String serviceId, BlockPos start, BlockPos target, BlockPos assembly, Direction.Axis axis,
            Direction direction, int maximumLength, String origin, String destination) {
        return new CampaignCommissioningRecord(regionId, dimensionId, connectionId, serviceId, start, target,
                assembly, axis, direction, maximumLength, origin, destination, CampaignCommissioningStatus.PLANNED,
                "", "", "", "", "", 0, -1, Map.of(),
                RailConstructionPolicy.LOADED_CHUNKS_ONLY, 0, 0);
    }

    public static CampaignCommissioningRecord legacyDisabled(String regionId, String dimensionId, BlockPos anchor) {
        return new CampaignCommissioningRecord(regionId, dimensionId, "pm:legacy-disabled", "pm:legacy-disabled",
                anchor, anchor, anchor, Direction.Axis.X, Direction.NORTH, 1, "disabled", "disabled",
                CampaignCommissioningStatus.LEGACY_WORLD_DISABLED, "", "", "", "",
                "Schema v24 world is not retrofitted with a generated railway", 0, -1, Map.of(),
                RailConstructionPolicy.LEGACY, 0, 0);
    }

    public boolean authorize(ServerLevel level, BlockPos position, BlockState current, BlockState proposed) {
        boolean physicalWork = status == CampaignCommissioningStatus.RAIL_BUILDING
                || status == CampaignCommissioningStatus.RAIL_READY
                || status == CampaignCommissioningStatus.TRAIN_COMMISSIONING;
        if (!physicalWork || !level.dimension().location().toString().equals(dimensionId)
                || !insideEnvelope(position)) return false;
        String currentSignature = current.toString();
        String proposedSignature = proposed.toString();
        RailwayMutableCell cell = railCells.get(position.asLong());
        boolean firstGeneration = baselineArrivals == 0;
        if (cell == null) {
            // Initial construction owns the PM-selected corridor and may replace ordinary terrain,
            // worldgen block entities, or the already commissioned MineSite yard. After the first
            // successful service arrival, repair/reconciliation may only touch previously admitted cells.
            if (!firstGeneration || protectedFirstGenerationCell(level, position, current)) {
                diagnostic = "Railway encountered a protected first-generation cell at " + position.toShortString();
                return false;
            }
            railCells.put(position.asLong(), new RailwayMutableCell(position, currentSignature, proposedSignature, false));
            return true;
        }
        if (firstGeneration) {
            // A trusted native provider can transform the same cell several times while assembling one
            // route (Create uses fake_track block entities for bezier curves). The whole commissioning
            // phase is authoritative; strict baseline/lastApproved checks begin after validation.
            cell.approveFirstGeneration(proposedSignature);
            return true;
        }
        if (cell.conflicted() || !currentSignature.equals(cell.baselineState())
                && !currentSignature.equals(cell.lastApprovedState())) {
            cell.conflict();
            diagnostic = "Rail cell conflict at " + position.toShortString();
            status = CampaignCommissioningStatus.BLOCKED;
            return false;
        }
        cell.approve(proposedSignature);
        return true;
    }

    public boolean mayRetryInitialPlacement(ServerLevel level) {
        return status == CampaignCommissioningStatus.BLOCKED && railCells.isEmpty()
                && diagnostic.equals("placement guard denied " + railStart.toShortString())
                && !protectedFirstGenerationCell(level, railStart, level.getBlockState(railStart));
    }

    /**
     * Recovers a route stopped by an older, narrower first-generation policy. The provider reports
     * this exact diagnostic before changing the denied cell. The route has not completed its first
     * service arrival yet, so all writes inside its persisted envelope still belong to authoritative
     * first-generation construction.
     */
    public boolean mayRetryFirstGenerationPlacement(ServerLevel level) {
        if (status != CampaignCommissioningStatus.BLOCKED
                || baselineArrivals != 0
                || !diagnostic.startsWith("placement guard denied ")) return false;
        return true;
    }

    public void retryInitialPlacement() {
        if (status != CampaignCommissioningStatus.BLOCKED || !railCells.isEmpty()) {
            throw new IllegalStateException("Only an untouched initial railway placement can be retried");
        }
        status = CampaignCommissioningStatus.RAIL_BUILDING;
        diagnostic = "";
    }

    public void retryFirstGenerationPlacement() {
        if (status != CampaignCommissioningStatus.BLOCKED
                || !diagnostic.startsWith("placement guard denied ")) {
            throw new IllegalStateException("Only a legacy first-generation guard denial can be retried");
        }
        status = CampaignCommissioningStatus.RAIL_BUILDING;
        diagnostic = "";
    }

    private static boolean protectedFirstGenerationCell(ServerLevel level, BlockPos position, BlockState state) {
        // This is authored first-generation infrastructure inside a persisted PM-selected envelope.
        // Native providers may also create temporary block entities (for example Create fake tracks)
        // outside the guarded write call. Treat every breakable state as part of the initial terrain;
        // strict baseline/lastApproved provenance starts after the route's first validated arrival.
        return state.getDestroySpeed(level, position) < 0.0F;
    }

    private boolean insideEnvelope(BlockPos position) {
        int minX = Math.min(railStart.getX(), railTarget.getX()) - 24;
        int maxX = Math.max(railStart.getX(), railTarget.getX()) + 24;
        int minZ = Math.min(railStart.getZ(), railTarget.getZ()) - 24;
        int maxZ = Math.max(railStart.getZ(), railTarget.getZ()) + 24;
        int minY = Math.min(railStart.getY(), railTarget.getY()) - 32;
        int maxY = Math.max(railStart.getY(), railTarget.getY()) + 48;
        return position.getX() >= minX && position.getX() <= maxX && position.getZ() >= minZ
                && position.getZ() <= maxZ && position.getY() >= minY && position.getY() <= maxY;
    }

    public String regionId() { return regionId; } public String dimensionId() { return dimensionId; }
    public String connectionId() { return connectionId; } public String serviceId() { return serviceId; }
    public BlockPos railStart() { return railStart; } public BlockPos railTarget() { return railTarget; }
    public BlockPos assemblyTrack() { return assemblyTrack; } public Direction.Axis trackAxis() { return trackAxis; }
    public Direction assemblyDirection() { return assemblyDirection; } public int maximumLength() { return maximumLength; }
    public String originStation() { return originStation; } public String destinationStation() { return destinationStation; }
    public CampaignCommissioningStatus status() { return status; } public String planHash() { return planHash; }
    public String nativeRailReference() { return nativeRailReference; } public String nativeTrainReference() { return nativeTrainReference; }
    public String scheduleFingerprint() { return scheduleFingerprint; } public String diagnostic() { return diagnostic; }
    public int baselineArrivals() { return baselineArrivals; } public long infectionEligibleAtStep() { return infectionEligibleAtStep; }
    public RailConstructionPolicy constructionPolicy() { return constructionPolicy; }
    public int completedSegments() { return completedSegments; }
    public int totalSegments() { return totalSegments; }
    public Map<Long, RailwayMutableCell> railCells() { return Map.copyOf(railCells); }
    public void railBuilding(String hash, String nativeReference) { status = CampaignCommissioningStatus.RAIL_BUILDING; planHash = safe(hash); nativeRailReference = safe(nativeReference); diagnostic = ""; }
    public boolean observeRailProgress(int completed, int total, String providerDiagnostic) {
        if (completed < 0 || total < 0 || completed > total) throw new IllegalArgumentException("Invalid rail progress");
        boolean changed = completedSegments != completed || totalSegments != total
                || !diagnostic.equals(safe(providerDiagnostic));
        completedSegments = completed;
        totalSegments = total;
        diagnostic = safe(providerDiagnostic);
        return changed;
    }
    public void railReady(String nativeReference) { status = CampaignCommissioningStatus.RAIL_READY; nativeRailReference = safe(nativeReference); diagnostic = ""; }
    public void trainCommissioning() { status = CampaignCommissioningStatus.TRAIN_COMMISSIONING; }
    public void resumeTrainCommissioning() {
        if (status != CampaignCommissioningStatus.BLOCKED) {
            throw new IllegalStateException("Only blocked commissioning can resume from provider reconciliation");
        }
        status = CampaignCommissioningStatus.TRAIN_COMMISSIONING;
        diagnostic = "";
    }
    public boolean observeTrain(String nativeReference, String fingerprint) {
        String nextNative = safe(nativeReference); String nextFingerprint = safe(fingerprint);
        boolean changed = !nativeTrainReference.equals(nextNative) || !scheduleFingerprint.equals(nextFingerprint);
        nativeTrainReference = nextNative; scheduleFingerprint = nextFingerprint; return changed;
    }
    public void observeBaselineArrival(long step) { baselineArrivals++; infectionEligibleAtStep = step + 5; status = CampaignCommissioningStatus.BASELINE_VALIDATION; }
    public void activate() { status = CampaignCommissioningStatus.ACTIVE; diagnostic = ""; }
    public void suspend(String reason) { status = CampaignCommissioningStatus.SUSPENDED; diagnostic = require(reason); }
    public void block(String reason) { status = CampaignCommissioningStatus.BLOCKED; diagnostic = require(reason); }
    private static String require(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Blank commissioning value"); return value; }
    private static String safe(String value) { return value == null ? "" : value; }
}
