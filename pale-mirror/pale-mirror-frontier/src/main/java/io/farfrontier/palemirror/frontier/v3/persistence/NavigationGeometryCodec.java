package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import java.io.*;
import java.util.*;

final class NavigationGeometryCodec {
    private NavigationGeometryCodec() { }
    static void write(DataOutputStream out, NavigationGeometryState value) throws IOException {
        out.writeLong(value.revision()); writeCells(out, value.cells()); writeDefinitions(out, value.definitions());
    }
    static NavigationGeometryState read(DataInputStream in) throws IOException {
        return new NavigationGeometryState(in.readLong(), readCells(in, NavigationGeometryState.MAX_CELLS),
                readDefinitions(in, NavigationGeometryState.MAX_DEFINITIONS));
    }
    private static void writeCells(DataOutputStream out, Map<BlockPosition, Optional<NavigationCell>> cells) throws IOException {
        out.writeInt(cells.size());
        for (var position : cells.keySet().stream().sorted(Comparator.comparingInt(BlockPosition::x)
                .thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z)).toList()) {
            FrontierWorldStateCodec.writePosition(out, position);
            writeCell(out, cells.get(position));
        }
    }
    private static void writeCell(DataOutputStream out, Optional<NavigationCell> value) throws IOException {
            out.writeBoolean(value.isPresent());
            if (value.isEmpty()) return;
            out.writeBoolean(value.orElseThrow().dry()); var boxes = value.orElseThrow().shape().boxes();
            var state = value.orElseThrow().observedState(); out.writeBoolean(state.isPresent());
            if (state.isPresent()) {
                out.writeUTF(state.orElseThrow().kind()); out.writeInt(state.orElseThrow().properties().size());
                for (var property : new TreeMap<>(state.orElseThrow().properties()).entrySet()) {
                    out.writeUTF(property.getKey()); out.writeUTF(property.getValue());
                }
            }
            out.writeInt(boxes.size());
            for (var box : boxes) {
                out.writeDouble(box.minX()); out.writeDouble(box.minY()); out.writeDouble(box.minZ());
                out.writeDouble(box.maxX()); out.writeDouble(box.maxY()); out.writeDouble(box.maxZ());
            }
    }
    private static Map<BlockPosition, Optional<NavigationCell>> readCells(DataInputStream in, int bound) throws IOException {
        int count = in.readInt(); if (count < 0 || count > bound) throw new IllegalArgumentException("unbounded geometry cells");
        var cells = new LinkedHashMap<BlockPosition, Optional<NavigationCell>>();
        for (int i = 0; i < count; i++) {
            var position = FrontierWorldStateCodec.readPosition(in);
            if (cells.put(position, readCell(in)) != null) throw new IllegalArgumentException("duplicate navigation cell");
        }
        return Map.copyOf(cells);
    }
    private static Optional<NavigationCell> readCell(DataInputStream in) throws IOException {
            Optional<NavigationCell> cell = Optional.empty();
            if (in.readBoolean()) {
                boolean dry = in.readBoolean(); Optional<NavigationBlockState> state = Optional.empty();
                if (in.readBoolean()) {
                    String kind = in.readUTF(); int properties = in.readInt();
                    if (properties < 0 || properties > 32) throw new IllegalArgumentException("unbounded state properties");
                    var values = new TreeMap<String, String>();
                    for (int p = 0; p < properties; p++) if (values.put(in.readUTF(), in.readUTF()) != null)
                        throw new IllegalArgumentException("duplicate state property");
                    state = Optional.of(new NavigationBlockState(kind, values));
                }
                int size = in.readInt();
                if (size < 0 || size > NavigationShape.MAX_BOXES) throw new IllegalArgumentException("unbounded geometry shape");
                var boxes = new ArrayList<NavigationShape.Box>();
                for (int j = 0; j < size; j++) boxes.add(new NavigationShape.Box(in.readDouble(), in.readDouble(), in.readDouble(),
                        in.readDouble(), in.readDouble(), in.readDouble()));
                cell = Optional.of(new NavigationCell(new NavigationShape(boxes), dry, state));
            }
            return cell;
    }
    private static void writeDefinitions(DataOutputStream out, Map<NavigationBlockState, Optional<NavigationCell>> values) throws IOException {
        out.writeInt(values.size());
        for (var block : values.keySet().stream().sorted(Comparator.comparing(NavigationBlockState::kind)
                .thenComparing(value -> new TreeMap<>(value.properties()).toString())).toList()) {
            out.writeUTF(block.kind()); out.writeInt(block.properties().size());
            for (var entry : new TreeMap<>(block.properties()).entrySet()) { out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue()); }
            writeCell(out, values.get(block));
        }
    }
    private static Map<NavigationBlockState, Optional<NavigationCell>> readDefinitions(DataInputStream in, int bound) throws IOException {
        int count = in.readInt(); if (count < 0 || count > bound) throw new IllegalArgumentException("unbounded shape definitions");
        var result = new HashMap<NavigationBlockState, Optional<NavigationCell>>();
        for (int i = 0; i < count; i++) {
            String kind = in.readUTF(); int size = in.readInt();
            if (size < 0 || size > 32) throw new IllegalArgumentException("unbounded definition properties");
            var properties = new TreeMap<String, String>();
            for (int p = 0; p < size; p++) if (properties.put(in.readUTF(), in.readUTF()) != null)
                throw new IllegalArgumentException("duplicate definition property");
            if (result.put(new NavigationBlockState(kind, properties), readCell(in)) != null)
                throw new IllegalArgumentException("duplicate shape definition");
        }
        return Map.copyOf(result);
    }
    static PayloadCodecs payloads() {
        return new PayloadCodecs(List.of(new PayloadCodec() {
            @Override public String type() { return NavigationGeometryObserved.TYPE; }
            @Override public byte[] encode(FrontierPayload payload) {
                try {
                    var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
                    var value = (NavigationGeometryObserved) payload;
                    out.writeLong(value.expectedRevision()); writeCells(out, value.cells()); writeDefinitions(out, value.definitions()); return bytes.toByteArray();
                } catch (IOException error) { throw new IllegalArgumentException("geometry encode", error); }
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                try {
                    var in = new DataInputStream(new ByteArrayInputStream(bytes));
                    var value = new NavigationGeometryObserved(in.readLong(), readCells(in, NavigationGeometryState.MAX_CHANGE),
                            readDefinitions(in, NavigationGeometryState.MAX_CHANGE));
                    if (in.available() != 0) throw new IllegalArgumentException("trailing geometry payload");
                    return value;
                } catch (IOException error) { throw new IllegalArgumentException("truncated geometry payload", error); }
            }
        }));
    }
}
