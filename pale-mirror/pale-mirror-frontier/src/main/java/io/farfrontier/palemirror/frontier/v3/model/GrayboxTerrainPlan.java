package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Bounded natural graybox content. One immutable voxel declaration feeds native generation
 * and COLD collision; it supplies no path, actor, work progress or materialization authority. */
public final class GrayboxTerrainPlan {
    public static final NavigationBlockState AIR = new NavigationBlockState("minecraft:air", Map.of());
    public static final NavigationBlockState STONE = new NavigationBlockState("minecraft:stone", Map.of());
    public static final NavigationBlockState WATER = new NavigationBlockState("minecraft:water", Map.of("level", "0"));
    private static final ImmutableResultView<Integer, GrayboxTerrainPlan> PLANS = new ImmutableResultView<>(8);
    private final RegionalBlockMap<NavigationBlockState> cells;
    private final Map<TerrainColumn, List<BlockPosition>> columns;
    private GrayboxTerrainPlan(FrontierWorldLayout layout, int y) {
        var blocks = new HashMap<BlockPosition, NavigationBlockState>();
        if (layout.terrain() == FrontierWorldLayout.Terrain.RELIEF) {
            // The fixtures occupy inter-settlement terrain, away from settlement,
            // field and extraction pads. Coordinates are content, not route answers.
            var left = layout.settlements().get(5); var right = layout.settlements().get(6);
            int mid = (left.x() + right.x()) / 2, lane = left.z() + 14;
            int hill = (left.x() + mid) / 2;
            // Terraces, including a genuine tunnel beneath the upper surface.
            for (int x = hill - 24; x <= hill + 24; x++) for (int z = lane - 24; z <= lane + 24; z++) {
                int rise = Math.min(8, (25 - Math.max(Math.abs(x - hill), Math.abs(z - lane))) / 2);
                for (int h = 1; h <= rise; h++) put(blocks, x, y + h, z, STONE);
                if (Math.abs(z - lane) <= 1) for (int h = 1; h <= 3; h++) put(blocks, x, y + h, z, AIR);
            }
            // A finite river/ravine. A three-wide bridge leaves real water underneath.
            for (int x = mid - 8; x <= mid + 8; x++) for (int z = lane - 48; z <= lane + 48; z++) {
                put(blocks, x, y - 6, z, STONE);
                for (int h = -5; h <= 0; h++) put(blocks, x, y + h, z, h == -5 ? WATER : AIR);
                if (Math.abs(z - lane) <= 1) put(blocks, x, y, z, STONE);
            }
            // Human-width passage, lateral bypass for a wider pack animal, and passing bays.
            for (int x = mid - 16; x <= mid + 16; x++) for (int h = 1; h <= 3; h++)
                for (int dz : List.of(0, 2)) if (Math.floorMod(x - mid, 12) > 2)
                    put(blocks, x, y + h, lane + 130 + dz, STONE);
            // A low-ceiling recess open only to the south: navigation must recognize
            // its geometry and leave backwards if an actor is displaced into it.
            for (int x = mid - 2; x <= mid + 2; x++) for (int z = lane + 190; z <= lane + 194; z++)
                for (int h = 1; h <= 3; h++) if (x == mid - 2 || x == mid + 2 || z == lane + 190 || h == 3)
                    put(blocks, x, y + h, z, STONE);
        }
        if (blocks.size() > 65_536) throw new IllegalArgumentException("graybox natural terrain exceeds its bounded content budget");
        cells = RegionalBlockMap.copyOf(blocks);
        var byColumn = new HashMap<TerrainColumn, List<BlockPosition>>();
        blocks.keySet().forEach(position -> byColumn.computeIfAbsent(new TerrainColumn(position.x(), position.z()), ignored -> new ArrayList<>()).add(position));
        byColumn.replaceAll((column, positions) -> positions.stream().sorted(Comparator.comparingInt(BlockPosition::y)).toList());
        columns = Collections.unmodifiableMap(byColumn);
    }
    private static void put(Map<BlockPosition, NavigationBlockState> blocks, int x, int y, int z, NavigationBlockState block) {
        blocks.put(new BlockPosition(x, y, z), block);
    }
    public static GrayboxTerrainPlan forLayout(FrontierWorldLayout layout, int baselineY) {
        return PLANS.get(List.of(layout), baselineY, () -> new GrayboxTerrainPlan(layout, baselineY));
    }
    public Map<BlockPosition, NavigationBlockState> in(Region region) { return cells.in(region); }
    public List<BlockPosition> column(int x, int z) { return columns.getOrDefault(new TerrainColumn(x, z), List.of()); }
    public Optional<NavigationBlockState> at(BlockPosition position) { return Optional.ofNullable(cells.get(position)); }
    public Set<Region> regions() { return cells.regions(); }
    public Set<NavigationBlockState> palette() { return cells.isEmpty() ? Set.of() : Set.of(AIR, STONE, WATER); }
    public boolean empty() { return cells.isEmpty(); }
    public Optional<NavigationCell> collisionAt(BlockPosition position) {
        return at(position).map(block -> block.equals(AIR) ? NavigationCell.AIR
                : new NavigationCell(block.equals(WATER) ? NavigationShape.EMPTY : NavigationShape.FULL,
                    !block.equals(WATER), Optional.of(block)));
    }
}
