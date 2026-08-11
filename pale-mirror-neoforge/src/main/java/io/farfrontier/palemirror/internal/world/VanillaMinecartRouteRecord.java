package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Restart-safe physical job for a narrow authored vanilla rail corridor.
 * Its states and provenance are presentation metadata; RouteContract remains
 * the sole authority for resource capacity and flow.
 */
public final class VanillaMinecartRouteRecord {
    public static final String POLICY_VERSION = "vanilla_minecart_v1";

    private final String regionId;
    private final String dimensionId;
    private final String routeId;
    private final BlockPos start;
    private final BlockPos target;
    private final Map<Long, VanillaMinecartMutableCell> cells;
    private final Set<Integer> completedSegments;
    private final Set<Long> damagedCriticalCells;
    private VanillaMinecartRouteStatus status;
    private String diagnostic;
    private int verificationCursor;
    private String cartLeaseId;
    private boolean cartLeaseDispatched;
    private UUID representativeCartId;
    private UUID representativeCargoId;
    private double cartProgress;
    private boolean cartForward;

    public VanillaMinecartRouteRecord(String regionId, String dimensionId, String routeId, BlockPos start, BlockPos target,
                                      VanillaMinecartRouteStatus status, String diagnostic,
                                      Map<Long, VanillaMinecartMutableCell> cells, Set<Integer> completedSegments,
                                      Set<Long> damagedCriticalCells,
                                      int verificationCursor, String cartLeaseId, boolean cartLeaseDispatched,
                                      UUID representativeCartId, UUID representativeCargoId,
                                      double cartProgress, boolean cartForward) {
        this.regionId = text(regionId, "regionId");
        this.dimensionId = text(dimensionId, "dimensionId");
        this.routeId = text(routeId, "routeId");
        this.start = Objects.requireNonNull(start, "start").immutable();
        this.target = Objects.requireNonNull(target, "target").immutable();
        if (start.getX() != target.getX() && start.getZ() != target.getZ()) {
            throw new IllegalArgumentException("Vanilla minecart route must be cardinal");
        }
        if (start.equals(target) || Math.abs(start.getY() - target.getY()) > horizontalLength()) {
            throw new IllegalArgumentException("Vanilla minecart route grade is invalid");
        }
        this.status = Objects.requireNonNull(status, "status");
        this.diagnostic = diagnostic == null ? "" : diagnostic;
        this.cells = new LinkedHashMap<>(cells);
        this.completedSegments = new LinkedHashSet<>(completedSegments);
        this.damagedCriticalCells = new LinkedHashSet<>(damagedCriticalCells);
        if (this.damagedCriticalCells.stream().anyMatch(position -> !this.cells.containsKey(position))) {
            throw new IllegalArgumentException("Damaged route position is not a captured cell");
        }
        if (this.completedSegments.stream().anyMatch(index -> index < 0 || index >= segmentCount())) {
            throw new IllegalArgumentException("Invalid completed vanilla rail segment");
        }
        if (verificationCursor < 0) throw new IllegalArgumentException("Negative verification cursor");
        this.verificationCursor = verificationCursor;
        this.cartLeaseId = cartLeaseId == null ? "" : cartLeaseId;
        this.cartLeaseDispatched = cartLeaseDispatched;
        if (cartLeaseDispatched && this.cartLeaseId.isBlank()) {
            throw new IllegalArgumentException("Dispatched minecart lease is absent");
        }
        this.representativeCartId = representativeCartId;
        this.representativeCargoId = representativeCargoId;
        if (!Double.isFinite(cartProgress) || cartProgress < 0 || cartProgress > segmentCount() - 1D) {
            throw new IllegalArgumentException("Invalid representative cart progress");
        }
        this.cartProgress = cartProgress;
        this.cartForward = cartForward;
    }

    public static VanillaMinecartRouteRecord planned(String regionId, String dimensionId, String routeId,
                                                      BlockPos start, BlockPos target) {
        return new VanillaMinecartRouteRecord(regionId, dimensionId, routeId, start, target,
                VanillaMinecartRouteStatus.PLANNED, "", Map.of(), Set.of(), Set.of(), 0, "", false,
                null, null, 0D, true);
    }

    public String regionId() { return regionId; }
    public String dimensionId() { return dimensionId; }
    public String routeId() { return routeId; }
    public BlockPos start() { return start; }
    public BlockPos target() { return target; }
    public VanillaMinecartRouteStatus status() { return status; }
    public String diagnostic() { return diagnostic; }
    public Map<Long, VanillaMinecartMutableCell> cells() { return Map.copyOf(cells); }
    public Set<Integer> completedSegments() { return Set.copyOf(completedSegments); }
    public Set<Long> damagedCriticalCells() { return Set.copyOf(damagedCriticalCells); }
    public int damagedCriticalCellCount() { return damagedCriticalCells.size(); }
    public BlockPos firstDamagedCriticalCell() {
        return damagedCriticalCells.isEmpty() ? null : BlockPos.of(damagedCriticalCells.iterator().next());
    }
    public int completedSegmentCount() { return completedSegments.size(); }
    public int segmentCount() { return horizontalLength() + 1; }
    public int verificationCursor() { return verificationCursor; }
    public String cartLeaseId() { return cartLeaseId; }
    public boolean cartLeaseDispatched() { return cartLeaseDispatched; }
    public UUID representativeCartId() { return representativeCartId; }
    public UUID representativeCargoId() { return representativeCargoId; }
    public double cartProgress() { return cartProgress; }
    public boolean cartForward() { return cartForward; }
    public Direction direction() {
        if (target.getX() > start.getX()) return Direction.EAST;
        if (target.getX() < start.getX()) return Direction.WEST;
        return target.getZ() > start.getZ() ? Direction.SOUTH : Direction.NORTH;
    }

    public BlockPos railPosition(int index) {
        if (index < 0 || index >= segmentCount()) throw new IllegalArgumentException("Invalid rail segment index");
        Direction direction = direction();
        int horizontal = horizontalLength();
        int y = start.getY() + Math.round((target.getY() - start.getY()) * (index / (float) horizontal));
        BlockPos horizontalPosition = start.relative(direction, index);
        return new BlockPos(horizontalPosition.getX(), y, horizontalPosition.getZ());
    }

    public int nextRailY(int index) { return railPosition(Math.min(segmentCount() - 1, index + 1)).getY(); }
    public int previousRailY(int index) { return railPosition(Math.max(0, index - 1)).getY(); }
    public boolean isComplete(int index) { return completedSegments.contains(index); }
    public boolean allSegmentsComplete() { return completedSegments.size() == segmentCount(); }
    public long closestCompletedRailDistanceSqr(BlockPos position) {
        long closest = Long.MAX_VALUE;
        for (int index : completedSegments) closest = Math.min(closest,
                (long) railPosition(index).distSqr(position));
        return closest;
    }
    public void begin() { require(VanillaMinecartRouteStatus.PLANNED); status = VanillaMinecartRouteStatus.BUILDING; }
    public boolean capture(BlockPos position, String baseline) {
        if (cells.containsKey(position.asLong())) return false;
        cells.put(position.asLong(), new VanillaMinecartMutableCell(position, baseline, baseline, false));
        return true;
    }
    public VanillaMinecartMutableCell cell(BlockPos position) { return cells.get(position.asLong()); }
    public void approve(BlockPos position, String applied) { requireCell(position).apply(applied); }
    public void complete(int index) { completedSegments.add(index); }
    public void verify() { require(VanillaMinecartRouteStatus.BUILDING); status = VanillaMinecartRouteStatus.VERIFYING; }
    public void activate() { require(VanillaMinecartRouteStatus.VERIFYING); status = VanillaMinecartRouteStatus.ACTIVE; diagnostic = ""; }
    public void block(String reason) { status = VanillaMinecartRouteStatus.BLOCKED; diagnostic = text(reason, "diagnostic"); }
    public boolean suspend(BlockPos position, String reason) {
        boolean added = damagedCriticalCells.add(position.asLong());
        status = VanillaMinecartRouteStatus.SUSPENDED;
        diagnostic = damagedCriticalCells.size() == 1 ? text(reason, "diagnostic")
                : "Vanilla minecart route has " + damagedCriticalCells.size()
                + " known damaged cells; first at " + firstDamagedCriticalCell().toShortString();
        return added;
    }
    public void decorativeConflict(String reason) { diagnostic = text(reason, "diagnostic"); }
    public boolean repaired(BlockPos position) {
        VanillaMinecartMutableCell cell = requireCell(position);
        cell.clearConflict();
        boolean removed = damagedCriticalCells.remove(position.asLong());
        if (removed && !damagedCriticalCells.isEmpty()) {
            diagnostic = "Vanilla minecart route has " + damagedCriticalCells.size()
                    + " known damaged cells; first at " + firstDamagedCriticalCell().toShortString();
        }
        return removed;
    }
    public boolean repairComplete() { return damagedCriticalCells.isEmpty(); }
    public void resume() {
        require(VanillaMinecartRouteStatus.SUSPENDED);
        if (!damagedCriticalCells.isEmpty()) throw new IllegalStateException("Damaged route cells remain");
        status = VanillaMinecartRouteStatus.ACTIVE;
        diagnostic = "";
    }
    public void advanceVerificationCursor(int cellsChecked) {
        if (cellsChecked > 0 && !cells.isEmpty()) verificationCursor = (verificationCursor + cellsChecked) % cells.size();
    }
    public void reserveCartLease(String id) {
        if (!cartLeaseId.isBlank() && !cartLeaseId.equals(id)) throw new IllegalStateException("Minecart lease is immutable");
        cartLeaseId = text(id, "cartLeaseId");
    }
    public void dispatchCartLease() {
        if (cartLeaseId.isBlank()) throw new IllegalStateException("Minecart lease must be reserved first");
        cartLeaseDispatched = true;
    }
    public void observeRepresentativeCart(UUID id, UUID cargoId) {
        representativeCartId = Objects.requireNonNull(id, "id");
        representativeCargoId = Objects.requireNonNull(cargoId, "cargoId");
    }
    public void forgetMissingRepresentativeCart() {
        representativeCartId = null;
        representativeCargoId = null;
        cartLeaseId = "";
        cartLeaseDispatched = false;
    }
    public double proposedCartProgress(double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid cart distance");
        double proposed = cartProgress + (cartForward ? distance : -distance);
        return Math.max(0D, Math.min(segmentCount() - 1D, proposed));
    }
    public void moveCart(double progress) {
        if (!Double.isFinite(progress) || progress < 0 || progress > segmentCount() - 1D) {
            throw new IllegalArgumentException("Invalid cart progress");
        }
        cartProgress = progress;
        if (progress <= 0D) cartForward = true;
        else if (progress >= segmentCount() - 1D) cartForward = false;
    }

    public List<VanillaMinecartMutableCell> verificationSlice(int maximum) {
        if (maximum < 1 || cells.isEmpty()) return List.of();
        List<VanillaMinecartMutableCell> values = new ArrayList<>(cells.values());
        List<VanillaMinecartMutableCell> result = new ArrayList<>();
        for (int offset = 0; offset < Math.min(maximum, values.size()); offset++) {
            result.add(values.get((verificationCursor + offset) % values.size()));
        }
        return List.copyOf(result);
    }

    private int horizontalLength() {
        return Math.abs(target.getX() - start.getX()) + Math.abs(target.getZ() - start.getZ());
    }
    private VanillaMinecartMutableCell requireCell(BlockPos position) {
        VanillaMinecartMutableCell value = cell(position);
        if (value == null) throw new IllegalStateException("Uncaptured vanilla rail cell " + position.toShortString());
        return value;
    }
    private void require(VanillaMinecartRouteStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected " + expected + " but was " + status);
    }
    private static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
