package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.io.*;
import java.util.*;

/** Exact authored sources and depletion; hydration never issues starter stock or rebuilds a deposit. */
final class ExtractionSiteStateCodec {
    private ExtractionSiteStateCodec() { }
    static void write(DataOutputStream out, ExtractionSiteState state) throws IOException {
        out.writeInt(state.deposits().size());
        for (var deposit : state.deposits().values().stream().sorted(Comparator.comparing(value -> value.site().id())).toList()) {
            var site = deposit.site(); var layout = site.layout();
            out.writeUTF(site.id().value()); out.writeUTF(site.settlementId().value()); out.writeUTF(site.containerId().value());
            FrontierWorldStateCodec.writePosition(out, layout.entrance().support());
            FrontierWorldStateCodec.writePosition(out, layout.container());
            FrontierWorldStateCodec.writePosition(out, layout.storagePort().support());
            out.writeInt(layout.accessSurfaces().size());
            for (var surface : layout.accessSurfaces()) FrontierWorldStateCodec.writePosition(out, surface.support());
            out.writeInt(layout.fixedBlocks().size());
            var positionOrder = Comparator.comparingInt(BlockPosition::x).thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z);
            for (var entry : layout.fixedBlocks().entrySet().stream().sorted(Map.Entry.comparingByKey(positionOrder)).toList()) {
                FrontierWorldStateCodec.writePosition(out, entry.getKey()); writeBlock(out, entry.getValue());
            }
            out.writeInt(layout.cells().size());
            for (var cell : layout.cells()) {
                out.writeLong(cell.id()); FrontierWorldStateCodec.writePosition(out, cell.source());
                FrontierWorldStateCodec.writePosition(out, cell.workstation().support()); writeDefinition(out, cell.definition());
                out.writeByte(cell.prerequisites().size());
                for (long id : cell.prerequisites().stream().sorted().toList()) out.writeLong(id);
                var current = deposit.cells().get(cell.id()); out.writeLong(current.revision());
                out.writeByte(current.disposition().wireTag()); writeBlock(out, current.knownBlock());
                out.writeBoolean(current.extractionOperation().isPresent());
                if (current.extractionOperation().isPresent()) out.writeUTF(current.extractionOperation().orElseThrow());
            }
            out.writeInt(deposit.geometry().size());
            for (var entry : deposit.geometry().entrySet().stream().sorted(Map.Entry.comparingByKey(positionOrder)).toList()) {
                FrontierWorldStateCodec.writePosition(out, entry.getKey()); out.writeLong(entry.getValue().revision()); writeBlock(out, entry.getValue().block());
            }
        }
        out.writeLong(state.nextWorkOrdinal()); out.writeInt(state.work().size());
        for (var job : state.work().values().stream().sorted(Comparator.comparing(ExtractionWork::id)).toList()) ExtractionWorkCodec.write(out, job);
    }
    static ExtractionSiteState read(DataInputStream in) throws IOException {
        var deposits = new LinkedHashMap<SubjectId, ExtractionDeposit>();
        for (int i = 0, count = count(in, 64); i < count; i++) {
            SubjectId id = new SubjectId(in.readUTF()), home = new SubjectId(in.readUTF()), containerId = new SubjectId(in.readUTF());
            var entrance = new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));
            BlockPosition container = FrontierWorldStateCodec.readPosition(in);
            var port = new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));
            var surfaces = new ArrayList<SurfaceAnchor>();
            for (int s = 0, n = count(in, ExtractionLayout.MAX_CELLS * 4); s < n; s++)
                surfaces.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
            var fixed = new LinkedHashMap<BlockPosition, BlockExtraction.Block>();
            for (int b = 0, blocks = count(in, ExtractionLayout.MAX_CELLS * 4); b < blocks; b++)
                if (fixed.putIfAbsent(FrontierWorldStateCodec.readPosition(in), readBlock(in)) != null)
                    throw new IllegalArgumentException("duplicate extraction fixed block");
            var cells = new ArrayList<ExtractionLayout.Cell>(); var states = new LinkedHashMap<Long, ExtractionDeposit.CellState>();
            for (int c = 0, cellCount = count(in, ExtractionLayout.MAX_CELLS); c < cellCount; c++) {
                long cellId = in.readLong(); BlockPosition source = FrontierWorldStateCodec.readPosition(in);
                var station = new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));
                var definition = readDefinition(in); int parents = in.readUnsignedByte();
                if (parents > 16) throw new IllegalArgumentException("unbounded extraction prerequisites");
                var prerequisites = new LinkedHashSet<Long>();
                for (int p = 0; p < parents; p++) if (!prerequisites.add(in.readLong()))
                    throw new IllegalArgumentException("duplicate extraction prerequisite");
                cells.add(new ExtractionLayout.Cell(cellId, source, station, definition, prerequisites));
                var cellRevision = in.readLong(); var disposition = ExtractionDeposit.Disposition.fromWireTag(in.readUnsignedByte());
                var block = readBlock(in); var operation = in.readBoolean() ? Optional.of(in.readUTF()) : Optional.<String>empty();
                var state = new ExtractionDeposit.CellState(cellRevision, disposition, block, operation);
                if (states.putIfAbsent(cellId, state) != null) throw new IllegalArgumentException("duplicate extraction cell state");
            }
            var site = new ExtractionSite(id, home, containerId, new ExtractionLayout(cells, fixed, surfaces, entrance, container, port));
            var geometry = new LinkedHashMap<BlockPosition, ExtractionDeposit.GeometryState>();
            for (int g = 0, n = count(in, ExtractionLayout.MAX_CELLS * 4); g < n; g++) {
                var position = FrontierWorldStateCodec.readPosition(in);
                var observed = new ExtractionDeposit.GeometryState(in.readLong(), readBlock(in));
                if (geometry.putIfAbsent(position, observed) != null) throw new IllegalArgumentException("duplicate worksite geometry observation");
            }
            if (deposits.putIfAbsent(id, new ExtractionDeposit(site, states, geometry)) != null)
                throw new IllegalArgumentException("duplicate extraction site");
        }
        long ordinal = in.readLong(); var work = new LinkedHashMap<SubjectId, ExtractionWork>();
        for (int i = 0, count = count(in, ExtractionSiteState.MAX_WORK); i < count; i++) {
            var job = ExtractionWorkCodec.read(in);
            if (work.putIfAbsent(job.id(), job) != null) throw new IllegalArgumentException("duplicate extraction work");
        }
        return new ExtractionSiteState(deposits, work, ordinal);
    }
    static void writeDefinition(DataOutputStream out, BlockExtraction.Definition definition) throws IOException {
        out.writeUTF(definition.id()); writeBlock(out, definition.before()); writeBlock(out, definition.after());
        out.writeUTF(definition.toolKind()); out.writeUTF(definition.lootTable()); out.writeByte(definition.coldOutput().size());
        for (var output : definition.coldOutput()) { out.writeUTF(output.itemKind()); out.writeByte(output.quantity()); }
    }
    static BlockExtraction.Definition readDefinition(DataInputStream in) throws IOException {
        String id = in.readUTF(); var before = readBlock(in); var after = readBlock(in);
        String tool = in.readUTF(), loot = in.readUTF(); int outputs = in.readUnsignedByte();
        if (outputs < 1 || outputs > BlockExtraction.MAX_OUTPUTS) throw new IllegalArgumentException("invalid extraction output count");
        var values = new ArrayList<BlockExtraction.Output>();
        for (int i = 0; i < outputs; i++) values.add(new BlockExtraction.Output(in.readUTF(), in.readUnsignedByte()));
        return new BlockExtraction.Definition(id, before, after, tool, loot, values);
    }
    static void writeBlock(DataOutputStream out, BlockExtraction.Block block) throws IOException {
        out.writeUTF(block.kind()); out.writeByte(block.properties().size());
        for (var entry : block.properties().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue());
        }
    }
    static BlockExtraction.Block readBlock(DataInputStream in) throws IOException {
        String kind = in.readUTF(); int count = in.readUnsignedByte();
        if (count > 32) throw new IllegalArgumentException("unbounded block properties");
        var properties = new LinkedHashMap<String, String>();
        for (int i = 0; i < count; i++) if (properties.putIfAbsent(in.readUTF(), in.readUTF()) != null)
            throw new IllegalArgumentException("duplicate extraction block property");
        return new BlockExtraction.Block(kind, properties);
    }
    private static int count(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("unbounded extraction register");
        return count;
    }
}
