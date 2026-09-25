package io.farfrontier.palemirror.frontier.v3.model;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestOutputStream;
import java.util.*;

/** Explicit immutable field geometry. Cell identity is not its current list index. */
public final class ResourceFieldLayout {
    /** Allocation safety ceiling, not a crop yield or a Minecraft stack limit. */
    public static final int MAX_CELLS = 65_536;

    public record CellId(long value) {
        public CellId { if (value < 1) throw new IllegalArgumentException("field cell id must be positive"); }
    }
    public record Cell(CellId id, BlockPosition crop, SurfaceAnchor soil, SurfaceAnchor workstation) {
        public Cell {
            Objects.requireNonNull(id); Objects.requireNonNull(crop);
            Objects.requireNonNull(soil); Objects.requireNonNull(workstation);
            if (!soil.support().offset(0, 1, 0).equals(crop))
                throw new IllegalArgumentException("crop must belong to its declared soil support");
        }
    }
    public record ChunkColumn(int x, int z) { }

    private final long revision;
    private final long nextCellId;
    private final List<Cell> cells;
    private final Map<CellId, Cell> byId;
    private final Map<CellId, Integer> workIndexById;
    private final List<BlockPosition> cropSlots, soilSlots, irrigationSlots, managedSlots;
    private final Map<BlockPosition, Cell> byCrop, bySoil;
    private final Set<BlockPosition> managedSet;
    private final List<ChunkColumn> occupiedChunks;
    private final Map<ChunkColumn, List<Cell>> cellsByChunk;
    private final String fingerprint;
    private final String cellIdsFingerprint;

    public ResourceFieldLayout(long revision, long nextCellId, List<Cell> cells, List<BlockPosition> irrigationSlots) {
        if (revision < 1 || nextCellId < 1) throw new IllegalArgumentException("invalid field layout revision/id allocator");
        this.revision = revision;
        this.nextCellId = nextCellId;
        this.cells = List.copyOf(cells);
        this.irrigationSlots = List.copyOf(irrigationSlots);
        if (cells.size() > MAX_CELLS || irrigationSlots.size() > MAX_CELLS)
            throw new IllegalArgumentException("field layout exceeds explicit allocation bounds");
        var indexed = new LinkedHashMap<CellId, Cell>();
        var workIndexes = new HashMap<CellId, Integer>();
        for (int index = 0; index < this.cells.size(); index++) {
            Cell cell = this.cells.get(index);
            if (cell.id().value() >= nextCellId || indexed.put(cell.id(), cell) != null)
                throw new IllegalArgumentException("field cells require unique allocated identities");
            workIndexes.put(cell.id(), index);
        }
        this.byId = Map.copyOf(indexed);
        this.workIndexById = Map.copyOf(workIndexes);
        this.cropSlots = this.cells.stream().map(Cell::crop).toList();
        this.soilSlots = this.cells.stream().map(cell -> cell.soil().support()).toList();
        var footprint = new ArrayList<BlockPosition>(soilSlots.size() + cropSlots.size() + irrigationSlots.size());
        footprint.addAll(soilSlots); footprint.addAll(cropSlots); footprint.addAll(this.irrigationSlots);
        if (new HashSet<>(footprint).size() != footprint.size())
            throw new IllegalArgumentException("field soil, crops and irrigation must not overlap");
        this.managedSlots = List.copyOf(footprint);
        this.managedSet = Set.copyOf(footprint);
        var crops = new HashMap<BlockPosition, Cell>();
        var soils = new HashMap<BlockPosition, Cell>();
        for (Cell cell : this.cells) {
            crops.put(cell.crop(), cell);
            soils.put(cell.soil().support(), cell);
        }
        this.byCrop = Map.copyOf(crops);
        this.bySoil = Map.copyOf(soils);
        this.occupiedChunks = footprint.stream()
                .map(position -> new ChunkColumn(Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16)))
                .distinct().toList();
        var chunkCells = new LinkedHashMap<ChunkColumn, List<Cell>>();
        for (Cell cell : this.cells) {
            ChunkColumn chunk = new ChunkColumn(Math.floorDiv(cell.crop().x(), 16), Math.floorDiv(cell.crop().z(), 16));
            chunkCells.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(cell);
        }
        chunkCells.replaceAll((ignored, values) -> List.copyOf(values));
        this.cellsByChunk = Map.copyOf(chunkCells);
        this.fingerprint = fingerprint(revision, nextCellId, this.cells, this.irrigationSlots);
        this.cellIdsFingerprint = fingerprintCellIds(this.byId.keySet());
    }

    /** Validates geometry evolution; domain job/relationship retirement remains the caller's responsibility. */
    public ResourceFieldLayout revise(long nextRevision, long nextId, List<Cell> replacement,
                                      List<BlockPosition> irrigation) {
        if (nextRevision != Math.addExact(revision, 1) || nextId < nextCellId)
            throw new IllegalArgumentException("field layout must advance its exact revision and retain its id allocator");
        var next = new ResourceFieldLayout(nextRevision, nextId, replacement, irrigation);
        for (Cell cell : next.cells) {
            Cell previous = byId.get(cell.id());
            if (previous != null && !previous.equals(cell))
                throw new IllegalArgumentException("retained field cell identity cannot move to another station");
            if (previous == null && cell.id().value() < nextCellId)
                throw new IllegalArgumentException("retired field cell identity cannot be reused");
        }
        return next;
    }

    public long revision() { return revision; }
    public long nextCellId() { return nextCellId; }
    /** Stable exact geometry/work-order identity, computed once and retained across JVMs. */
    public String fingerprint() { return fingerprint; }
    /** A cached identity check for physical claims recovered without layout geometry. */
    public String cellIdsFingerprint() { return cellIdsFingerprint; }
    public List<Cell> cells() { return cells; }
    public Cell requireCell(CellId id) {
        Cell cell = byId.get(Objects.requireNonNull(id));
        if (cell == null) throw new IllegalArgumentException("field layout has no cell " + id.value());
        return cell;
    }
    public Optional<Cell> cell(CellId id) { return Optional.ofNullable(byId.get(Objects.requireNonNull(id))); }
    /** Position in this revision's declared work order; identity itself is never the index. */
    public int workIndex(CellId id) {
        Integer index = workIndexById.get(Objects.requireNonNull(id));
        if (index == null) throw new IllegalArgumentException("field layout has no cell " + id.value());
        return index;
    }
    public List<BlockPosition> cropSlots() { return cropSlots; }
    public List<BlockPosition> soilSlots() { return soilSlots; }
    public List<BlockPosition> irrigationSlots() { return irrigationSlots; }
    public List<BlockPosition> managedSlots() { return managedSlots; }
    public boolean contains(BlockPosition position) { return managedSet.contains(Objects.requireNonNull(position)); }
    public Optional<Cell> cropAt(BlockPosition position) { return Optional.ofNullable(byCrop.get(Objects.requireNonNull(position))); }
    public Optional<Cell> soilAt(BlockPosition position) { return Optional.ofNullable(bySoil.get(Objects.requireNonNull(position))); }
    /** A read-only chunk footprint for bounded natural-loading checks; never loads a chunk. */
    public List<ChunkColumn> occupiedChunks() { return occupiedChunks; }
    public List<Cell> cellsIn(ChunkColumn chunk) { return cellsByChunk.getOrDefault(Objects.requireNonNull(chunk), List.of()); }

    @Override public boolean equals(Object other) {
        return other instanceof ResourceFieldLayout value && revision == value.revision && nextCellId == value.nextCellId
                && cells.equals(value.cells) && irrigationSlots.equals(value.irrigationSlots);
    }
    @Override public int hashCode() { return Objects.hash(revision, nextCellId, cells, irrigationSlots); }
    @Override public String toString() { return "ResourceFieldLayout[revision=" + revision + ", cells=" + cells.size() + "]"; }

    private static String fingerprint(long revision, long nextCellId, List<Cell> cells, List<BlockPosition> irrigation) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                output.writeInt(1); // fingerprint grammar
                output.writeLong(revision);
                output.writeLong(nextCellId);
                output.writeInt(cells.size());
                for (Cell cell : cells) {
                    output.writeLong(cell.id().value());
                    writePosition(output, cell.crop());
                    writePosition(output, cell.soil().support());
                    writePosition(output, cell.workstation().support());
                }
                output.writeInt(irrigation.size());
                for (BlockPosition position : irrigation) writePosition(output, position);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException unavailable) {
            throw new IllegalStateException("SHA-256 field layout fingerprint is unavailable", unavailable);
        }
    }

    public static String fingerprintCellIds(Collection<CellId> ids) {
        Objects.requireNonNull(ids, "field cell identities");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                output.writeInt(1); // cell-identity fingerprint grammar
                output.writeInt(ids.size());
                for (CellId id : ids.stream().sorted(Comparator.comparingLong(CellId::value)).toList())
                    output.writeLong(Objects.requireNonNull(id, "field cell identity").value());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException unavailable) {
            throw new IllegalStateException("SHA-256 field identity fingerprint is unavailable", unavailable);
        }
    }

    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException {
        output.writeInt(position.x());
        output.writeInt(position.y());
        output.writeInt(position.z());
    }
}
