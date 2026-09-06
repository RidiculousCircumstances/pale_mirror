package io.farfrontier.palemirror.internal.world;

import java.util.Objects;

import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;

/** Persistent physical work for a campaign bound to an already observed settlement. */
public final class CampaignRegionRecord {
    private final String id;
    private final String archetypeId;
    private final int layoutVersion;
    private final String displayName;
    private final String dimensionId;
    private final WorldObjectId placeId;
    private final BlockPos settlementAnchor;
    private final BlockPos primaryMineColumn;
    private final BlockPos alternateMineColumn;
    private BlockPos receivingTerminalAnchor;
    private BlockPos depotAnchor;
    private BlockPos primaryMineAnchor;
    private BlockPos alternateMineAnchor;
    private CampaignRegionPresentationStatus status;
    private String diagnostic;
    private int nextOperationIndex;
    private long originTrainSeenAtStep;
    private long destinationTrainSeenAtStep;
    private int originTrainCapacity;
    private int destinationTrainCapacity;
    private String originVehicleId;
    private String destinationVehicleId;
    private java.util.Map<Long, String> primaryMineBaseline = java.util.Map.of();
    private java.util.Map<Long, String> alternateMineBaseline = java.util.Map.of();

    public CampaignRegionRecord(String id, String dimensionId, WorldObjectId placeId, BlockPos settlementAnchor,
                                BlockPos primaryMineColumn, BlockPos alternateMineColumn,
                                BlockPos primaryMineAnchor, BlockPos alternateMineAnchor,
                                CampaignRegionPresentationStatus status, String diagnostic, int nextOperationIndex,
                                long originTrainSeenAtStep, long destinationTrainSeenAtStep,
                                int originTrainCapacity, int destinationTrainCapacity,
                                String originVehicleId, String destinationVehicleId) {
        this(id, "pale_mirror:iron_frontier", 1, "Ironhill", dimensionId, placeId, settlementAnchor,
                primaryMineColumn, alternateMineColumn, null, null, primaryMineAnchor, alternateMineAnchor, status, diagnostic,
                nextOperationIndex, originTrainSeenAtStep, destinationTrainSeenAtStep, originTrainCapacity,
                destinationTrainCapacity, originVehicleId, destinationVehicleId);
    }

    public CampaignRegionRecord(String id, String archetypeId, int layoutVersion, String displayName,
                                String dimensionId, WorldObjectId placeId, BlockPos settlementAnchor,
                                BlockPos primaryMineColumn, BlockPos alternateMineColumn,
                                BlockPos receivingTerminalAnchor, BlockPos depotAnchor,
                                BlockPos primaryMineAnchor, BlockPos alternateMineAnchor,
                                CampaignRegionPresentationStatus status, String diagnostic, int nextOperationIndex,
                                long originTrainSeenAtStep, long destinationTrainSeenAtStep,
                                int originTrainCapacity, int destinationTrainCapacity,
                                String originVehicleId, String destinationVehicleId) {
        this.id = Objects.requireNonNull(id, "id");
        this.archetypeId = Objects.requireNonNull(archetypeId, "archetypeId");
        if (layoutVersion < 1) throw new IllegalArgumentException("Invalid region layout version");
        this.layoutVersion = layoutVersion;
        this.displayName = requireText(displayName, "displayName");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.placeId = Objects.requireNonNull(placeId, "placeId");
        this.settlementAnchor = Objects.requireNonNull(settlementAnchor, "settlementAnchor").immutable();
        this.primaryMineColumn = Objects.requireNonNull(primaryMineColumn, "primaryMineColumn").immutable();
        this.alternateMineColumn = Objects.requireNonNull(alternateMineColumn, "alternateMineColumn").immutable();
        this.receivingTerminalAnchor = receivingTerminalAnchor == null ? null : receivingTerminalAnchor.immutable();
        this.depotAnchor = depotAnchor == null ? null : depotAnchor.immutable();
        if (layoutVersion >= 2 && (this.receivingTerminalAnchor == null || this.depotAnchor == null)) {
            throw new IllegalArgumentException("Layout-v2 requires pinned terminal and depot anchors");
        }
        this.primaryMineAnchor = primaryMineAnchor == null ? null : primaryMineAnchor.immutable();
        this.alternateMineAnchor = alternateMineAnchor == null ? null : alternateMineAnchor.immutable();
        this.status = Objects.requireNonNull(status, "status");
        this.diagnostic = diagnostic == null ? "" : diagnostic;
        if (nextOperationIndex < 0 || nextOperationIndex > 2) throw new IllegalArgumentException("Invalid campaign operation index");
        if (originTrainSeenAtStep < -1 || destinationTrainSeenAtStep < -1
                || originTrainCapacity < 0 || destinationTrainCapacity < 0) {
            throw new IllegalArgumentException("Invalid persisted logistics observation");
        }
        this.nextOperationIndex = nextOperationIndex;
        this.originTrainSeenAtStep = originTrainSeenAtStep;
        this.destinationTrainSeenAtStep = destinationTrainSeenAtStep;
        this.originTrainCapacity = originTrainCapacity;
        this.destinationTrainCapacity = destinationTrainCapacity;
        this.originVehicleId = originVehicleId == null ? "" : originVehicleId;
        this.destinationVehicleId = destinationVehicleId == null ? "" : destinationVehicleId;
    }

    public String id() { return id; }
    public String archetypeId() { return archetypeId; }
    public int layoutVersion() { return layoutVersion; }
    public String displayName() { return displayName; }
    public String jobId() { return "pm:campaign:" + id; }
    public String dimensionId() { return dimensionId; }
    public WorldObjectId placeId() { return placeId; }
    public BlockPos settlementAnchor() { return settlementAnchor; }
    public BlockPos primaryMineColumn() { return primaryMineColumn; }
    public BlockPos alternateMineColumn() { return alternateMineColumn; }
    public BlockPos receivingTerminalAnchor() { return receivingTerminalAnchor; }
    public BlockPos depotAnchor() { return depotAnchor; }
    /** Reconciles presentation-only anchors from the same immutable authored manifest. */
    public boolean reconcileAuthoredDepotAnchors(BlockPos receivingTerminal, BlockPos depot) {
        BlockPos terminal = Objects.requireNonNull(receivingTerminal, "receivingTerminal").immutable();
        BlockPos functional = Objects.requireNonNull(depot, "depot").immutable();
        if (terminal.equals(functional)) {
            throw new IllegalArgumentException("Authored depot terminal and functional core must be distinct");
        }
        boolean changed = !terminal.equals(receivingTerminalAnchor) || !functional.equals(depotAnchor);
        receivingTerminalAnchor = terminal;
        depotAnchor = functional;
        return changed;
    }
    public BlockPos primaryMineAnchor() { return primaryMineAnchor; }
    public BlockPos alternateMineAnchor() { return alternateMineAnchor; }
    public CampaignRegionPresentationStatus status() { return status; }
    public String diagnostic() { return diagnostic; }
    public int nextOperationIndex() { return nextOperationIndex; }
    public long originTrainSeenAtStep() { return originTrainSeenAtStep; }
    public long destinationTrainSeenAtStep() { return destinationTrainSeenAtStep; }
    public int originTrainCapacity() { return originTrainCapacity; }
    public int destinationTrainCapacity() { return destinationTrainCapacity; }
    public String originVehicleId() { return originVehicleId; }
    public String destinationVehicleId() { return destinationVehicleId; }
    public java.util.Map<Long, String> pendingMineBaseline() {
        return nextOperationIndex == 0 ? primaryMineBaseline : alternateMineBaseline;
    }
    public java.util.Map<Long, String> primaryMineBaseline() { return primaryMineBaseline; }
    public java.util.Map<Long, String> alternateMineBaseline() { return alternateMineBaseline; }
    public BlockPos pendingMineColumn() { return nextOperationIndex == 0 ? primaryMineColumn : alternateMineColumn; }
    public BlockPos pendingMineAnchor() { return nextOperationIndex == 0 ? primaryMineAnchor : alternateMineAnchor; }
    public boolean pendingMineAnchorResolved() { return pendingMineAnchor() != null; }
    /** Persists terrain-dependent placement before the job starts touching blocks. */
    public void resolvePendingMineAnchor(BlockPos anchor) {
        resolvePendingMineAnchor(anchor, java.util.Map.of());
    }
    public void resolvePendingMineAnchor(BlockPos anchor, java.util.Map<Long, String> baseline) {
        if (status != CampaignRegionPresentationStatus.PLANNED || pendingMineAnchorResolved()) {
            throw new IllegalStateException("Cannot resolve an inactive or already resolved campaign mine anchor");
        }
        if (nextOperationIndex == 0) {
            primaryMineAnchor = Objects.requireNonNull(anchor, "anchor").immutable();
            primaryMineBaseline = java.util.Map.copyOf(baseline);
        } else {
            alternateMineAnchor = Objects.requireNonNull(anchor, "anchor").immutable();
            alternateMineBaseline = java.util.Map.copyOf(baseline);
        }
    }
    void restoreMineBaselines(java.util.Map<Long, String> primary, java.util.Map<Long, String> alternate) {
        primaryMineBaseline = java.util.Map.copyOf(primary); alternateMineBaseline = java.util.Map.copyOf(alternate);
    }
    public void completedOperation() {
        if (status != CampaignRegionPresentationStatus.RUNNING) {
            throw new IllegalStateException("Cannot complete a campaign operation that is not running");
        }
        nextOperationIndex++;
        if (nextOperationIndex == 2) materialized();
        else status = CampaignRegionPresentationStatus.PLANNED;
    }
    /** Must be saved before the executor is permitted to touch the physical world. */
    public void startOperation() {
        if (status != CampaignRegionPresentationStatus.PLANNED || nextOperationIndex >= 2 || !pendingMineAnchorResolved()) {
            throw new IllegalStateException("Cannot start an invalid campaign operation");
        }
        status = CampaignRegionPresentationStatus.RUNNING;
    }
    public void materialized() { status = CampaignRegionPresentationStatus.MATERIALIZED; diagnostic = ""; }
    /** Accepts immutable worldgen facts without replaying the superseded runtime placement state machine. */
    public void observeWorldgenMaterialized(BlockPos primaryAnchor, BlockPos alternateAnchor) {
        if (status == CampaignRegionPresentationStatus.MATERIALIZED) return;
        if (status != CampaignRegionPresentationStatus.PLANNED || nextOperationIndex != 0) {
            throw new IllegalStateException("Cannot adopt authored genesis after runtime placement has started");
        }
        primaryMineAnchor = Objects.requireNonNull(primaryAnchor, "primaryAnchor").immutable();
        alternateMineAnchor = Objects.requireNonNull(alternateAnchor, "alternateAnchor").immutable();
        nextOperationIndex = 2;
        materialized();
    }
    public void block(String reason) { status = CampaignRegionPresentationStatus.BLOCKED; diagnostic = Objects.requireNonNull(reason, "reason"); }
    /** Reopens only a preflight that failed before an anchor/baseline or any physical operation was persisted. */
    public void retryBlockedPreflight() {
        if (status != CampaignRegionPresentationStatus.BLOCKED || pendingMineAnchorResolved()
                || !diagnostic.startsWith("MineSite conflict at ")) {
            throw new IllegalStateException("Only an untouched MineSite preflight conflict can be retried");
        }
        status = CampaignRegionPresentationStatus.PLANNED;
        diagnostic = "";
    }
    /** Recovers a job stopped by the superseded exact-terrain first-generation precondition. */
    public void retryBlockedFirstGenerationExecution() {
        if (status != CampaignRegionPresentationStatus.BLOCKED || !pendingMineAnchorResolved()
                || !diagnostic.startsWith("MineSite changed after planning at ")) {
            throw new IllegalStateException("Only a legacy first-generation terrain change can be retried");
        }
        status = CampaignRegionPresentationStatus.RUNNING;
        diagnostic = "";
    }
    public boolean observeRouteEndpoint(boolean origin, long simulationStep, int capacity, String vehicleId) {
        if (simulationStep < 0 || capacity < 0 || vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("Invalid route observation");
        }
        if (origin) {
            boolean changed = originTrainSeenAtStep != simulationStep || originTrainCapacity != capacity
                    || !originVehicleId.equals(vehicleId);
            originTrainSeenAtStep = simulationStep;
            originTrainCapacity = capacity;
            originVehicleId = vehicleId;
            return changed;
        }
        boolean changed = destinationTrainSeenAtStep != simulationStep || destinationTrainCapacity != capacity
                || !destinationVehicleId.equals(vehicleId);
        destinationTrainSeenAtStep = simulationStep;
        destinationTrainCapacity = capacity;
        destinationVehicleId = vehicleId;
        return changed;
    }
    public int certifiedRouteCapacity(long simulationStep, long proofWindowSteps) {
        if (proofWindowSteps < 0 || originTrainSeenAtStep < 0 || destinationTrainSeenAtStep < 0
                || simulationStep - originTrainSeenAtStep > proofWindowSteps
                || simulationStep - destinationTrainSeenAtStep > proofWindowSteps
                || originVehicleId.isBlank() || !originVehicleId.equals(destinationVehicleId)) return 0;
        return Math.min(originTrainCapacity, destinationTrainCapacity);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
