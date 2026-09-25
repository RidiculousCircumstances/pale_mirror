package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fresh-world field geometry belongs to the immutable bootstrap header, not a recovered guess. */
final class InitialFieldLayoutManifestCodec {
    private static final int MAX_SITES = 12;

    private InitialFieldLayoutManifestCodec() { }

    static void write(DataOutputStream output, Map<SubjectId, ResourceFieldLayout> manifest) throws IOException {
        if (manifest.size() > MAX_SITES) throw new IllegalArgumentException("too many initial field layouts");
        output.writeInt(manifest.size());
        for (var entry : manifest.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value());
            ResourceFieldLayout layout = entry.getValue();
            output.writeLong(layout.revision()); output.writeLong(layout.nextCellId());
            output.writeInt(layout.cells().size());
            for (ResourceFieldLayout.Cell cell : layout.cells()) {
                output.writeLong(cell.id().value());
                position(output, cell.crop()); position(output, cell.soil().support()); position(output, cell.workstation().support());
            }
            output.writeInt(layout.irrigationSlots().size());
            for (BlockPosition irrigation : layout.irrigationSlots()) position(output, irrigation);
        }
    }

    static Map<SubjectId, ResourceFieldLayout> read(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_SITES) throw new IllegalArgumentException("initial field manifest count is invalid");
        Map<SubjectId, ResourceFieldLayout> manifest = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            SubjectId siteId = new SubjectId(FrontierWorldStateCodec.readString(input));
            long revision = input.readLong(), nextId = input.readLong();
            int cellsCount = boundedCount(input);
            List<ResourceFieldLayout.Cell> cells = new ArrayList<>(cellsCount);
            for (int cellIndex = 0; cellIndex < cellsCount; cellIndex++) {
                cells.add(new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(input.readLong()), position(input),
                        new SurfaceAnchor(position(input)), new SurfaceAnchor(position(input))));
            }
            int waterCount = boundedCount(input);
            List<BlockPosition> water = new ArrayList<>(waterCount);
            for (int waterIndex = 0; waterIndex < waterCount; waterIndex++) water.add(position(input));
            if (manifest.put(siteId, new ResourceFieldLayout(revision, nextId, cells, water)) != null)
                throw new IllegalArgumentException("duplicate initial field site");
        }
        return Map.copyOf(manifest);
    }

    private static int boundedCount(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > ResourceFieldLayout.MAX_CELLS)
            throw new IllegalArgumentException("initial field geometry count exceeds its bound");
        return count;
    }

    private static void position(DataOutputStream output, BlockPosition value) throws IOException {
        output.writeInt(value.x()); output.writeInt(value.y()); output.writeInt(value.z());
    }

    private static BlockPosition position(DataInputStream input) throws IOException {
        return new BlockPosition(input.readInt(), input.readInt(), input.readInt());
    }
}
