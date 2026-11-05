package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One bounded authoring format used by packaged content and developer import/export. */
public final class BuildingDefinitionFormat {
    public static Map<String, String> properties(byte[] bytes) {
        if (bytes.length > 262_144)
            throw new IllegalArgumentException("building markup exceeds bound");
        var p =
                new Properties() {
                    @Override
                    public synchronized Object put(Object key, Object value) {
                        if (containsKey(key))
                            throw new IllegalArgumentException(
                                    "duplicate building property " + key);
                        return super.put(key, value);
                    }
                };
        try {
            p.load(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        var values = new TreeMap<String, String>();
        p.forEach((k, v) -> values.put((String) k, (String) v));
        return Collections.unmodifiableMap(values);
    }

    public static Map<BlockPosition, BuildingDefinition.Block> blocks(byte[] bytes) {
        if (bytes.length > 16_777_216)
            throw new IllegalArgumentException("authored block data exceeds bound");
        var result = new LinkedHashMap<BlockPosition, BuildingDefinition.Block>();
        for (var line : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            var fields = line.split("\\t", -1);
            if (fields.length != 3)
                throw new IllegalArgumentException("invalid authored block row");
            var material = StructureMaterial.require(fields[1]);
            if (!(material instanceof StructureMaterial.Authored authored))
                throw new IllegalArgumentException("authored block needs exact native state");
            var value =
                    new BuildingDefinition.Block(
                            authored.block(),
                            authored.repairItemKind(),
                            GrayboxSemanticPart.valueOf(fields[2]));
            if (result.putIfAbsent(point(fields[0]), value) != null || result.size() > 65_536)
                throw new IllegalArgumentException("duplicate or unbounded authored blocks");
        }
        return Collections.unmodifiableMap(result);
    }

    public static BuildingDefinition decode(
            Map<String, String> source,
            Map<BlockPosition, BuildingDefinition.Block> blocks,
            String sha256) {
        var r = new Reader(source);
        if (r.integer("format") != 2)
            throw new IllegalArgumentException("unsupported building markup format");
        var id = new BuildingTemplateId(r.take("id"));
        int version = r.integer("version");
        var functions = new LinkedHashSet<StructureKind>();
        r.names("functions").forEach(s -> functions.add(StructureKind.valueOf(s)));
        var representation = BuildingDefinition.Representation.valueOf(r.take("representation"));
        var origin = point(r.take("origin"));
        var facing = FacilityFacing.valueOf(r.take("front"));
        var parcel = r.volume("parcel");
        var fabric = r.volume("fabric");
        var rooms = new LinkedHashMap<String, BuildingDefinition.Room>();
        for (var name : r.names("rooms"))
            rooms.put(name, new BuildingDefinition.Room(name, r.volume("room." + name)));
        var zones = new LinkedHashMap<String, BuildingDefinition.Zone>();
        for (var name : r.names("zones")) {
            String p = "zone." + name;
            zones.put(
                    name,
                    new BuildingDefinition.Zone(
                            name,
                            r.take(p + ".room"),
                            BuildingDefinition.ZoneUse.valueOf(r.take(p + ".use")),
                            r.volume(p),
                            Set.copyOf(r.names(p + ".categories")),
                            r.integer(p + ".priority")));
        }
        var passages = new LinkedHashMap<String, BuildingDefinition.Passage>();
        for (var name : r.names("passages")) {
            String p = "passage." + name;
            var from = r.endpoint(p + ".from");
            var to = r.endpoint(p + ".to");
            var path =
                    Arrays.stream(r.take(p + ".supports").split(";", -1))
                            .map(BuildingDefinitionFormat::point)
                            .toList();
            passages.put(
                    name,
                    new BuildingDefinition.Passage(
                            name,
                            from,
                            to,
                            path,
                            r.integer(p + ".threshold"),
                            r.bool(p + ".public")));
        }
        var workplaces = new LinkedHashMap<String, BuildingDefinition.Workplace>();
        for (var name : r.names("workplaces")) {
            String p = "workplace." + name;
            workplaces.put(
                    name,
                    new BuildingDefinition.Workplace(
                            name,
                            r.take(p + ".room"),
                            point(r.take(p + ".input")),
                            point(r.take(p + ".worker")),
                            point(r.take(p + ".socket")),
                            new ProductionEquipment.Id(r.take(p + ".equipmentType")),
                            FacilityFacing.valueOf(r.take(p + ".facing")),
                            r.volume(p + ".equipment"),
                            r.volume(p + ".maintenance")));
        }
        var entrance = r.optional("primaryEntrance");
        var workplace = r.optional("primaryWorkplace");
        r.finished();
        return new BuildingDefinition(
                id,
                version,
                sha256,
                functions,
                representation,
                origin,
                facing,
                parcel,
                fabric,
                rooms,
                zones,
                passages,
                workplaces,
                entrance,
                workplace,
                blocks);
    }

    public static byte[] encodeProperties(Map<String, String> source) {
        var result = new StringBuilder();
        new TreeMap<>(source)
                .forEach(
                        (key, value) -> {
                            if (!key.matches("[A-Za-z0-9_./-]+")
                                    || value.indexOf('\n') >= 0
                                    || value.indexOf('\r') >= 0
                                    || value.indexOf('\\') >= 0)
                                throw new IllegalArgumentException(
                                        "unsafe building property spelling");
                            result.append(key).append('=').append(value).append('\n');
                        });
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static BlockPosition point(String encoded) {
        return point(encoded, 256, "local building");
    }

    /** Placement manifests carry absolute Minecraft coordinates, not local annotations. */
    public static BlockPosition worldPoint(String encoded) {
        return point(encoded, 30_000_000, "world building");
    }

    private static BlockPosition point(String encoded, int maximum, String kind) {
        var fields = encoded.split(",", -1);
        if (fields.length != 3)
            throw new IllegalArgumentException("building position requires x,y,z");
        var p =
                new BlockPosition(
                        Integer.parseInt(fields[0]),
                        Integer.parseInt(fields[1]),
                        Integer.parseInt(fields[2]));
        if (Math.abs((long) p.x()) > maximum
                || Math.abs((long) p.y()) > maximum
                || Math.abs((long) p.z()) > maximum)
            throw new IllegalArgumentException("unbounded " + kind + " coordinate");
        return p;
    }

    private static final class Reader {
        private final Map<String, String> remaining;

        Reader(Map<String, String> source) {
            remaining = new LinkedHashMap<>(source);
        }

        String take(String key) {
            var value = remaining.remove(key);
            if (value == null)
                throw new IllegalArgumentException("missing building property " + key);
            return value;
        }

        int integer(String key) {
            return Integer.parseInt(take(key));
        }

        boolean bool(String key) {
            return switch (take(key)) {
                case "true" -> true;
                case "false" -> false;
                default -> throw new IllegalArgumentException("invalid building boolean");
            };
        }

        List<String> names(String key) {
            var s = take(key);
            if (s.isEmpty()) return List.of();
            var names = List.of(s.split(",", -1));
            if (names.size() > 128
                    || new HashSet<>(names).size() != names.size()
                    || names.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("duplicate or unbounded building list " + key);
            return names;
        }

        Optional<String> optional(String key) {
            var s = take(key);
            return s.isEmpty() ? Optional.empty() : Optional.of(s);
        }

        BuildingVolume volume(String prefix) {
            return new BuildingVolume(point(take(prefix + ".min")), point(take(prefix + ".max")));
        }

        BuildingDefinition.Endpoint endpoint(String prefix) {
            return new BuildingDefinition.Endpoint(
                    BuildingDefinition.EndpointKind.valueOf(take(prefix + ".kind")),
                    optional(prefix + ".room"));
        }

        void finished() {
            if (!remaining.isEmpty())
                throw new IllegalArgumentException(
                        "unknown building properties " + remaining.keySet());
        }
    }

    private BuildingDefinitionFormat() {}
}
