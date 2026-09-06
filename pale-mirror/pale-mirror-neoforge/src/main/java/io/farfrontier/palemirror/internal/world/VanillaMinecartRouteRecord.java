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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.properties.RailShape;

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
    private final boolean worldgenAuthored;
    private final BlockPos start;
    private final BlockPos target;
    private final List<BlockPos> plannedPath;
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
    private final Map<Long, String> observedRailShapes;
    private List<Long> acceptedRailPath;
    private final Set<Long> dirtyTopologyCells;
    private final Set<Long> dirtyTopologyChunks;
    private int topologyVerificationCursor;
    private long topologyRevision;
    private BlockPos topologyIssue;

    public VanillaMinecartRouteRecord(String regionId, String dimensionId, String routeId, boolean worldgenAuthored,
                                      List<BlockPos> plannedPath,
                                      VanillaMinecartRouteStatus status, String diagnostic,
                                      Map<Long, VanillaMinecartMutableCell> cells, Set<Integer> completedSegments,
                                      Set<Long> damagedCriticalCells,
                                      int verificationCursor, String cartLeaseId, boolean cartLeaseDispatched,
                                      UUID representativeCartId, UUID representativeCargoId,
                                      double cartProgress, boolean cartForward) {
        this.regionId = text(regionId, "regionId");
        this.dimensionId = text(dimensionId, "dimensionId");
        this.routeId = text(routeId, "routeId");
        this.worldgenAuthored = worldgenAuthored;
        this.plannedPath = plannedPath.stream().map(BlockPos::immutable).toList();
        if (this.plannedPath.size() < 2) throw new IllegalArgumentException("Vanilla minecart path needs two nodes");
        this.start = this.plannedPath.getFirst(); this.target = this.plannedPath.getLast();
        for (int index = 1; index < this.plannedPath.size(); index++) {
            BlockPos previous = this.plannedPath.get(index - 1); BlockPos current = this.plannedPath.get(index);
            int horizontal = Math.abs(current.getX() - previous.getX()) + Math.abs(current.getZ() - previous.getZ());
            if (horizontal != 1 || Math.abs(current.getY() - previous.getY()) > 1) {
                throw new IllegalArgumentException("Vanilla minecart path is not adjacent and grade-safe at " + index);
            }
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
        this.observedRailShapes = new LinkedHashMap<>();
        this.acceptedRailPath = new ArrayList<>();
        this.dirtyTopologyCells = new LinkedHashSet<>();
        this.dirtyTopologyChunks = new LinkedHashSet<>();
        this.topologyVerificationCursor = 0;
        this.topologyRevision = 0;
        this.topologyIssue = null;
    }

    void restoreTopology(Map<Long, String> shapes, List<Long> path, Set<Long> dirtyCells, Set<Long> dirtyChunks,
                         int cursor, long revision, BlockPos issue) {
        shapes.forEach((position, shape) -> {
            BlockPos node = BlockPos.of(position);
            if (!containsTopologyPosition(node)) throw new IllegalArgumentException("Persisted rail node outside route bounds");
            RailShape.valueOf(shape);
        });
        if (path.stream().anyMatch(position -> !shapes.containsKey(position)))
            throw new IllegalArgumentException("Persisted accepted path references an unobserved rail node");
        if (!path.isEmpty() && (path.getFirst() != start.asLong() || path.getLast() != target.asLong()))
            throw new IllegalArgumentException("Persisted accepted path does not bind the route endpoints");
        boolean disconnected = !path.isEmpty() && VanillaRailTopology.path(shapes, start, target).isEmpty();
        if (dirtyCells.stream().map(BlockPos::of).anyMatch(position -> !containsTopologyPosition(position)))
            throw new IllegalArgumentException("Persisted dirty topology cell outside route bounds");
        if (issue != null && !containsTopologyPosition(issue))
            throw new IllegalArgumentException("Persisted topology issue outside route bounds");
        observedRailShapes.clear(); observedRailShapes.putAll(VanillaRailTopology.ordered(shapes));
        acceptedRailPath = new ArrayList<>(path);
        dirtyTopologyCells.clear(); dirtyTopologyCells.addAll(dirtyCells);
        dirtyTopologyChunks.clear(); dirtyTopologyChunks.addAll(dirtyChunks);
        topologyVerificationCursor = Math.max(0, cursor);
        topologyRevision = Math.max(0L, revision);
        topologyIssue = issue == null ? null : issue.immutable();
        if (disconnected) {
            BlockPos restoredIssue = firstMissingAcceptedRail();
            if (restoredIssue == null) restoredIssue = topologyIssue == null ? start : topologyIssue;
            disconnectTopology(restoredIssue);
            markAllTopologyChunksDirty();
        }
        if (cartProgress > Math.max(0, travelPathSize() - 1)) cartProgress = 0D;
    }

    public static VanillaMinecartRouteRecord planned(String regionId, String dimensionId, String routeId,
                                                      BlockPos start, BlockPos target) {
        return planned(regionId, dimensionId, routeId, straightPath(start, target));
    }
    public static VanillaMinecartRouteRecord planned(String regionId, String dimensionId, String routeId,
                                                      List<BlockPos> path) {
        return new VanillaMinecartRouteRecord(regionId, dimensionId, routeId, false, path,
                VanillaMinecartRouteStatus.PLANNED, "", Map.of(), Set.of(), Set.of(), 0, "", false,
                null, null, 0D, true);
    }

    public static VanillaMinecartRouteRecord authored(String regionId, String dimensionId, String routeId,
                                                       List<BlockPos> path) {
        return new VanillaMinecartRouteRecord(regionId, dimensionId, routeId, true, path,
                VanillaMinecartRouteStatus.PLANNED, "", Map.of(), Set.of(), Set.of(), 0, "", false,
                null, null, 0D, true);
    }

    public String regionId() { return regionId; }
    public String dimensionId() { return dimensionId; }
    public String routeId() { return routeId; }
    public BlockPos start() { return start; }
    public BlockPos target() { return target; }
    public List<BlockPos> plannedPath() { return plannedPath; }
    public boolean worldgenAuthored() { return worldgenAuthored; }
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
    public int segmentCount() { return plannedPath.size(); }
    public int verificationCursor() { return verificationCursor; }
    public String cartLeaseId() { return cartLeaseId; }
    public boolean cartLeaseDispatched() { return cartLeaseDispatched; }
    public UUID representativeCartId() { return representativeCartId; }
    public UUID representativeCargoId() { return representativeCargoId; }
    public double cartProgress() { return cartProgress; }
    public boolean cartForward() { return cartForward; }
    public Map<Long, String> observedRailShapes() { return Map.copyOf(observedRailShapes); }
    public List<Long> acceptedRailPath() { return List.copyOf(acceptedRailPath); }
    public Set<Long> dirtyTopologyCells() { return Set.copyOf(dirtyTopologyCells); }
    public Set<Long> dirtyTopologyChunks() { return Set.copyOf(dirtyTopologyChunks); }
    public int topologyVerificationCursor() { return topologyVerificationCursor; }
    public long topologyRevision() { return topologyRevision; }
    public BlockPos topologyIssue() { return topologyIssue; }
    public int topologyIssueCount() { return status == VanillaMinecartRouteStatus.SUSPENDED && topologyIssue != null ? 1 : 0; }
    public Direction direction() {
        return direction(0);
    }
    public Direction direction(int index) {
        int from = index == segmentCount() - 1 ? index - 1 : index;
        int to = index == segmentCount() - 1 ? index : index + 1;
        BlockPos a = railPosition(from); BlockPos b = railPosition(to);
        if (b.getX() > a.getX()) return Direction.EAST; if (b.getX() < a.getX()) return Direction.WEST;
        return b.getZ() > a.getZ() ? Direction.SOUTH : Direction.NORTH;
    }

    public BlockPos railPosition(int index) {
        if (index < 0 || index >= segmentCount()) throw new IllegalArgumentException("Invalid rail segment index");
        return plannedPath.get(index);
    }

    public int nextRailY(int index) { return railPosition(Math.min(segmentCount() - 1, index + 1)).getY(); }
    public int previousRailY(int index) { return railPosition(Math.max(0, index - 1)).getY(); }
    public boolean isComplete(int index) { return completedSegments.contains(index); }
    public boolean allSegmentsComplete() { return completedSegments.size() == segmentCount(); }
    public int travelPathSize() { return acceptedRailPath.isEmpty() ? segmentCount() : acceptedRailPath.size(); }
    public BlockPos travelPosition(int index) {
        if (index < 0 || index >= travelPathSize()) throw new IllegalArgumentException("Invalid travel path index");
        return acceptedRailPath.isEmpty() ? railPosition(index) : BlockPos.of(acceptedRailPath.get(index));
    }
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

    public void initializeAuthoredTopology() {
        if (!observedRailShapes.isEmpty()) return;
        for (int index = 0; index < segmentCount(); index++) {
            observedRailShapes.put(railPosition(index).asLong(), authoredShape(index).name());
        }
        acceptedRailPath = java.util.stream.IntStream.range(0, segmentCount())
                .mapToObj(index -> railPosition(index).asLong()).toList();
        topologyRevision++;
    }
    public boolean containsTopologyPosition(BlockPos position) {
        int horizontalMargin = 48;
        int verticalMargin = 32;
        return position.getX() >= minX() - horizontalMargin && position.getX() <= maxX() + horizontalMargin
                && position.getZ() >= minZ() - horizontalMargin && position.getZ() <= maxZ() + horizontalMargin
                && position.getY() >= minY() - verticalMargin && position.getY() <= maxY() + verticalMargin;
    }
    public boolean markTopologyDirty(BlockPos position) {
        return containsTopologyPosition(position) && dirtyTopologyCells.add(position.asLong());
    }
    public boolean markTopologyChunkDirty(ChunkPos chunk) {
        int minX = minX() - 48; int maxX = maxX() + 48;
        int minZ = minZ() - 48; int maxZ = maxZ() + 48;
        if (chunk.getMaxBlockX() < minX || chunk.getMinBlockX() > maxX
                || chunk.getMaxBlockZ() < minZ || chunk.getMinBlockZ() > maxZ) return false;
        return dirtyTopologyChunks.add(chunk.toLong());
    }
    public BlockPos pollDirtyTopologyCell() {
        if (dirtyTopologyCells.isEmpty()) return null;
        long value = dirtyTopologyCells.iterator().next(); dirtyTopologyCells.remove(value); return BlockPos.of(value);
    }
    public ChunkPos pollLoadedDirtyTopologyChunk(java.util.function.LongPredicate loaded) {
        var iterator = dirtyTopologyChunks.iterator();
        while (iterator.hasNext()) {
            long value = iterator.next();
            if (!loaded.test(value)) continue;
            iterator.remove(); return new ChunkPos(value);
        }
        return null;
    }
    public boolean hasObservedRail(BlockPos position) { return observedRailShapes.containsKey(position.asLong()); }
    public boolean observeRail(BlockPos position, RailShape shape) {
        long key = position.asLong();
        if (shape == null) return observedRailShapes.remove(key) != null;
        return !shape.name().equals(observedRailShapes.put(key, shape.name()));
    }
    public List<BlockPos> topologyVerificationSlice(int maximum) {
        if (maximum < 1 || acceptedRailPath.isEmpty()) return List.of();
        List<BlockPos> result = new ArrayList<>();
        for (int offset = 0; offset < Math.min(maximum, acceptedRailPath.size()); offset++) {
            result.add(BlockPos.of(acceptedRailPath.get((topologyVerificationCursor + offset) % acceptedRailPath.size())));
        }
        topologyVerificationCursor = (topologyVerificationCursor + result.size()) % acceptedRailPath.size();
        return List.copyOf(result);
    }
    public java.util.Optional<List<BlockPos>> connectedPath() {
        return VanillaRailTopology.path(observedRailShapes, start, target);
    }
    public BlockPos firstMissingAcceptedRail() {
        for (long position : acceptedRailPath) if (!observedRailShapes.containsKey(position)) return BlockPos.of(position);
        return null;
    }
    public boolean acceptTopology(List<BlockPos> path) {
        List<Long> encoded = path.stream().map(BlockPos::asLong).toList();
        boolean changed = !encoded.equals(acceptedRailPath) || status != VanillaMinecartRouteStatus.ACTIVE
                || !damagedCriticalCells.isEmpty();
        acceptedRailPath = new ArrayList<>(encoded);
        damagedCriticalCells.clear();
        topologyIssue = null;
        status = VanillaMinecartRouteStatus.ACTIVE;
        diagnostic = "";
        if (cartProgress > Math.max(0, travelPathSize() - 1)) cartProgress = 0D;
        if (changed) topologyRevision++;
        return changed;
    }
    public boolean disconnectTopology(BlockPos issue) {
        BlockPos safeIssue = issue == null ? start : issue;
        boolean changed = status != VanillaMinecartRouteStatus.SUSPENDED
                || topologyIssue == null || !topologyIssue.equals(safeIssue);
        status = VanillaMinecartRouteStatus.SUSPENDED;
        topologyIssue = safeIssue.immutable();
        damagedCriticalCells.clear();
        diagnostic = "Rail graph disconnected near " + safeIssue.toShortString();
        return changed;
    }
    public void markAllTopologyChunksDirty() {
        int minChunkX = (minX() - 48) >> 4; int maxChunkX = (maxX() + 48) >> 4;
        int minChunkZ = (minZ() - 48) >> 4; int maxChunkZ = (maxZ() + 48) >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) for (int z = minChunkZ; z <= maxChunkZ; z++)
            dirtyTopologyChunks.add(ChunkPos.asLong(x, z));
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
        return Math.max(0D, Math.min(travelPathSize() - 1D, proposed));
    }
    public void moveCart(double progress) {
        if (!Double.isFinite(progress) || progress < 0 || progress > travelPathSize() - 1D) {
            throw new IllegalArgumentException("Invalid cart progress");
        }
        cartProgress = progress;
        if (progress <= 0D) cartForward = true;
        else if (progress >= travelPathSize() - 1D) cartForward = false;
    }
    public boolean parkCartAtOrigin() {
        boolean changed = cartProgress != 0D || !cartForward;
        cartProgress = 0D;
        cartForward = true;
        return changed;
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

    private RailShape authoredShape(int index) {
        int railY = railPosition(index).getY();
        Direction next = direction(index);
        Direction previous = index == 0 ? next.getOpposite() : direction(index - 1).getOpposite();
        if (nextRailY(index) > railY) return ascending(next);
        if (previousRailY(index) > railY) return ascending(previous);
        if (next.getAxis() == previous.getAxis()) return next.getAxis() == Direction.Axis.X
                ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        return curve(previous, next);
    }
    private static RailShape ascending(Direction direction) {
        return switch (direction) {
            case NORTH -> RailShape.ASCENDING_NORTH;
            case SOUTH -> RailShape.ASCENDING_SOUTH;
            case EAST -> RailShape.ASCENDING_EAST;
            case WEST -> RailShape.ASCENDING_WEST;
            default -> throw new IllegalArgumentException("Rail direction must be horizontal");
        };
    }
    private static RailShape curve(Direction first, Direction second) {
        boolean north = first == Direction.NORTH || second == Direction.NORTH;
        boolean south = first == Direction.SOUTH || second == Direction.SOUTH;
        boolean east = first == Direction.EAST || second == Direction.EAST;
        if (south && east) return RailShape.SOUTH_EAST; if (south) return RailShape.SOUTH_WEST;
        if (north && east) return RailShape.NORTH_EAST; return RailShape.NORTH_WEST;
    }
    private int minX() { return plannedPath.stream().mapToInt(BlockPos::getX).min().orElseThrow(); }
    private int maxX() { return plannedPath.stream().mapToInt(BlockPos::getX).max().orElseThrow(); }
    private int minY() { return plannedPath.stream().mapToInt(BlockPos::getY).min().orElseThrow(); }
    private int maxY() { return plannedPath.stream().mapToInt(BlockPos::getY).max().orElseThrow(); }
    private int minZ() { return plannedPath.stream().mapToInt(BlockPos::getZ).min().orElseThrow(); }
    private int maxZ() { return plannedPath.stream().mapToInt(BlockPos::getZ).max().orElseThrow(); }
    private static List<BlockPos> straightPath(BlockPos start, BlockPos target) {
        if (start.getX() != target.getX() && start.getZ() != target.getZ()) {
            throw new IllegalArgumentException("Legacy straight route must be cardinal");
        }
        int length = Math.abs(target.getX() - start.getX()) + Math.abs(target.getZ() - start.getZ());
        if (length < 1 || Math.abs(start.getY() - target.getY()) > length) throw new IllegalArgumentException("Invalid route grade");
        Direction direction = target.getX() > start.getX() ? Direction.EAST : target.getX() < start.getX()
                ? Direction.WEST : target.getZ() > start.getZ() ? Direction.SOUTH : Direction.NORTH;
        List<BlockPos> result = new ArrayList<>();
        for (int index = 0; index <= length; index++) {
            int y = start.getY() + Math.round((target.getY() - start.getY()) * (index / (float) length));
            BlockPos horizontal = start.relative(direction, index); result.add(new BlockPos(horizontal.getX(), y, horizontal.getZ()));
        }
        return List.copyOf(result);
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
