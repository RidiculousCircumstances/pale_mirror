package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/**
 * Pure spatial contract for the disposable {@code graybox_1_40} world.
 *
 * <p>The values deliberately describe Minecraft-space coordinates without
 * importing Minecraft.  NeoForge is responsible for turning these immutable
 * facts into blocks and entities; it must not choose a second scale or move a
 * canonical object to a different logical cell.</p>
 */
public final class ReferenceGrayboxLayout {
    public static final int BLOCKS_PER_CELL = 16;
    public static final int WORLD_BLOCKS = 1_024;
    public static final int ARENA_BLOCKS_X = 1_024;
    public static final int ARENA_BLOCKS_Z = 704;
    public static final int GROUND_Y = 64;
    public static final int MIN_X = -WORLD_BLOCKS / 2;
    public static final int MIN_Z = -ARENA_BLOCKS_Z / 2;
    public static final int MAX_X_EXCLUSIVE = MIN_X + ARENA_BLOCKS_X;
    public static final int MAX_Z_EXCLUSIVE = MIN_Z + ARENA_BLOCKS_Z;
    public static final int SETTLEMENT_BLOCKS = 48;

    private ReferenceGrayboxLayout() { }

    /** Fail closed rather than silently projecting a source-scale or legacy world. */
    public static void requireSupported(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceWorldConfig config = required.config();
        if (!required.v2Enabled()
                || !ReferenceSimulationProfile.GRAYBOX_1_40.equals(required.profile())
                || config.width() != ReferenceWorldConfig.SOURCE_WIDTH
                || config.height() != ReferenceWorldConfig.SOURCE_HEIGHT
                || config.settlementCount() != ReferenceWorldConfig.SOURCE_SETTLEMENT_COUNT
                || config.infectionSeeds() != ReferenceWorldConfig.SOURCE_INFECTION_SEEDS) {
            throw new IllegalStateException("graybox materialization requires the complete graybox_1_40 V2 source profile");
        }
    }

    public static Bounds bounds() {
        return new Bounds(MIN_X, MIN_Z, ARENA_BLOCKS_X, ARENA_BLOCKS_Z, GROUND_Y, BLOCKS_PER_CELL);
    }

    public static Rectangle cell(int x, int y) {
        requireCell(x, y);
        return new Rectangle(MIN_X + x * BLOCKS_PER_CELL, MIN_Z + y * BLOCKS_PER_CELL,
                BLOCKS_PER_CELL, BLOCKS_PER_CELL);
    }

    /** The source's settlement coordinate is the middle cell of a 48×48 readable settlement. */
    public static Rectangle settlement(int x, int y) {
        Rectangle cell = cell(x, y);
        return new Rectangle(cell.x() - BLOCKS_PER_CELL, cell.z() - BLOCKS_PER_CELL,
                SETTLEMENT_BLOCKS, SETTLEMENT_BLOCKS);
    }

    public static Rectangle site(int x, int y) {
        Rectangle cell = cell(x, y);
        return new Rectangle(cell.x() + 2, cell.z() + 2, 12, 12);
    }

    public static Rectangle organ(int x, int y) {
        Rectangle cell = cell(x, y);
        return new Rectangle(cell.x() + 3, cell.z() + 3, 10, 10);
    }

    public static Rectangle fieldPost(int x, int y) {
        Rectangle cell = cell(x, y);
        return new Rectangle(cell.x() + 2, cell.z() + 2, 12, 12);
    }

    public static Point centre(int x, int y) {
        Rectangle cell = cell(x, y);
        return new Point(cell.x() + BLOCKS_PER_CELL / 2, cell.z() + BLOCKS_PER_CELL / 2);
    }

    public static Point position(double x, double y) {
        int cellX = clamp((int) Math.round(x), 0, ReferenceWorldConfig.SOURCE_WIDTH - 1);
        int cellY = clamp((int) Math.round(y), 0, ReferenceWorldConfig.SOURCE_HEIGHT - 1);
        return centre(cellX, cellY);
    }

    /** Slots are deterministic and visibly separate individual actors without a population multiplier. */
    public static Point actorSlot(Point anchor, int ordinal) {
        if (ordinal < 0) throw new IllegalArgumentException("actor ordinal must be non-negative");
        int column = ordinal % 15;
        int row = ordinal / 15;
        return new Point(anchor.x() - 14 + column * 2, anchor.z() - 14 + row * 2);
    }

    public static Rectangle facility(Rectangle settlement, String kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case "civic_hall" -> local(settlement, 20, 20, 8, 8);
            case "workshop" -> local(settlement, 2, 2, 9, 8);
            case "armory" -> local(settlement, 37, 2, 9, 8);
            case "clinic" -> local(settlement, 2, 38, 9, 8);
            case "warehouse" -> local(settlement, 35, 37, 11, 9);
            case "housing" -> local(settlement, 13, 2, 10, 8);
            case "fortification" -> settlement;
            default -> throw new IllegalArgumentException("unknown graybox facility kind: " + kind);
        };
    }

    private static Rectangle local(Rectangle outer, int x, int z, int width, int depth) {
        return new Rectangle(outer.x() + x, outer.z() + z, width, depth);
    }

    private static void requireCell(int x, int y) {
        if (x < 0 || x >= ReferenceWorldConfig.SOURCE_WIDTH || y < 0 || y >= ReferenceWorldConfig.SOURCE_HEIGHT) {
            throw new IllegalArgumentException("logical coordinate is outside graybox_1_40: " + x + "," + y);
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Bounds(int minX, int minZ, int width, int depth, int groundY, int blocksPerCell) {
        public Bounds {
            if (width < 1 || depth < 1 || blocksPerCell < 1) throw new IllegalArgumentException("graybox bounds must be positive");
        }
    }

    public record Rectangle(int x, int z, int width, int depth) {
        public Rectangle {
            if (width < 1 || depth < 1) throw new IllegalArgumentException("graybox rectangle must be positive");
        }
        public int centreX() { return x + width / 2; }
        public int centreZ() { return z + depth / 2; }
    }

    public record Point(int x, int z) { }
}
