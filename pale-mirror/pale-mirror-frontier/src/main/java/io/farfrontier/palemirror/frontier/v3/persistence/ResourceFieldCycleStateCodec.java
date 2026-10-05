package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict current-schema snapshot of exact field geometry and per-cell facts. */
final class ResourceFieldCycleStateCodec {
    private ResourceFieldCycleStateCodec() { }

    static void write(DataOutputStream output, ResourceFieldCycle cycle) throws IOException {
        ResourceFieldLayout layout = cycle.layout();
        FrontierWorldStateCodec.writeString(output, cycle.siteId().value());
        output.writeLong(layout.revision()); output.writeLong(layout.nextCellId()); output.writeLong(cycle.epoch());
        output.writeInt(layout.cells().size());
        for (ResourceFieldLayout.Cell cell : layout.cells()) {
            output.writeLong(cell.id().value());
            output.writeLong(cycle.generation(cell.id()));
            writePosition(output, cell.crop()); writePosition(output, cell.soil().support());
            writePosition(output, cell.workstation().support());
            ResourceFieldCycle.CellState state = cycle.cell(cell.id());
            output.writeByte(soilTag(state.soil())); output.writeByte(cropTag(state.crop()));
            output.writeByte(state.growthStage()); output.writeBoolean(state.accounted()); output.writeBoolean(state.yielded());
            output.writeBoolean(state.workAccessBlocked());
            ResourceFieldCycle.PendingPlayerBreak pending = cycle.pendingPlayerBreaks().get(cell.id());
            output.writeBoolean(pending != null);
            if (pending != null) {
                output.writeLong(pending.playerId().getMostSignificantBits());
                output.writeLong(pending.playerId().getLeastSignificantBits());
                FrontierWorldStateCodec.writeString(output, pending.actionId());
                output.writeByte(soilTag(pending.before().soil())); output.writeByte(cropTag(pending.before().crop()));
                output.writeByte(pending.before().growthStage());
            }
        }
        output.writeInt(layout.irrigationSlots().size());
        for (BlockPosition water : layout.irrigationSlots()) writePosition(output, water);
    }

    static ResourceFieldCycle read(DataInputStream input) throws IOException {
        SubjectId siteId = new SubjectId(FrontierWorldStateCodec.readString(input));
        long revision = input.readLong(), nextCellId = input.readLong(), epoch = input.readLong();
        int count = input.readInt();
        if (count < 0 || count > ResourceFieldLayout.MAX_CELLS)
            throw new IllegalArgumentException("field snapshot cell count exceeds its bound");
        List<ResourceFieldLayout.Cell> cells = new ArrayList<>(count);
        Map<ResourceFieldLayout.CellId, ResourceFieldCycle.CellState> states = new LinkedHashMap<>();
        Map<ResourceFieldLayout.CellId, Long> generations = new LinkedHashMap<>();
        Map<ResourceFieldLayout.CellId, ResourceFieldCycle.PendingPlayerBreak> pending = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            ResourceFieldLayout.CellId id = new ResourceFieldLayout.CellId(input.readLong());
            long generation = input.readLong();
            if (generation < 0 || generations.put(id, generation) != null)
                throw new IllegalArgumentException("invalid or duplicate field generation");
            ResourceFieldLayout.Cell cell = new ResourceFieldLayout.Cell(id, readPosition(input),
                    new SurfaceAnchor(readPosition(input)), new SurfaceAnchor(readPosition(input)));
            ResourceFieldCycle.CellState state = new ResourceFieldCycle.CellState(
                    soil(input.readUnsignedByte()), crop(input.readUnsignedByte()), input.readUnsignedByte(),
                    input.readBoolean(), input.readBoolean(), input.readBoolean());
            if (input.readBoolean()) {
                var player = new java.util.UUID(input.readLong(), input.readLong());
                String action = FrontierWorldStateCodec.readString(input);
                var before = new ResourceFieldPhysicalSurface.Condition(
                        soil(input.readUnsignedByte()), crop(input.readUnsignedByte()), input.readUnsignedByte());
                pending.put(id, new ResourceFieldCycle.PendingPlayerBreak(player, action, before));
            }
            if (states.put(id, state) != null) throw new IllegalArgumentException("duplicate field snapshot cell id");
            cells.add(cell);
        }
        int waterCount = input.readInt();
        if (waterCount < 0 || waterCount > ResourceFieldLayout.MAX_CELLS)
            throw new IllegalArgumentException("field snapshot irrigation count exceeds its bound");
        List<BlockPosition> irrigation = new ArrayList<>(waterCount);
        for (int index = 0; index < waterCount; index++) irrigation.add(readPosition(input));
        return ResourceFieldCycle.restore(siteId, new ResourceFieldLayout(revision, nextCellId, cells, irrigation),
                epoch, states, pending, generations);
    }

    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException {
        output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z());
    }
    private static BlockPosition readPosition(DataInputStream input) throws IOException {
        return new BlockPosition(input.readInt(), input.readInt(), input.readInt());
    }
    static int soilTag(ResourceFieldCycle.Soil value) {
        return switch (value) { case UNKNOWN -> 1; case FARMLAND -> 2; case DIRT -> 3; case OBSTRUCTED -> 4; };
    }
    static ResourceFieldCycle.Soil soil(int tag) {
        return switch (tag) {
            case 1 -> ResourceFieldCycle.Soil.UNKNOWN; case 2 -> ResourceFieldCycle.Soil.FARMLAND;
            case 3 -> ResourceFieldCycle.Soil.DIRT; case 4 -> ResourceFieldCycle.Soil.OBSTRUCTED;
            default -> throw new IllegalArgumentException("unknown field soil wire tag " + tag);
        };
    }
    static int cropTag(ResourceFieldCycle.Crop value) {
        return switch (value) { case UNKNOWN -> 1; case ABSENT -> 2; case GROWING -> 3; case MATURE -> 4; case OBSTRUCTED -> 5; };
    }
    static ResourceFieldCycle.Crop crop(int tag) {
        return switch (tag) {
            case 1 -> ResourceFieldCycle.Crop.UNKNOWN; case 2 -> ResourceFieldCycle.Crop.ABSENT;
            case 3 -> ResourceFieldCycle.Crop.GROWING; case 4 -> ResourceFieldCycle.Crop.MATURE;
            case 5 -> ResourceFieldCycle.Crop.OBSTRUCTED;
            default -> throw new IllegalArgumentException("unknown field crop wire tag " + tag);
        };
    }
}
