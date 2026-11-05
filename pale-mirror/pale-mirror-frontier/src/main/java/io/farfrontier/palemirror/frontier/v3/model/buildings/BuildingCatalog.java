package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/**
 * Closed content catalogue. Style selections are explicit data; unknown content has no fallback.
 */
public final class BuildingCatalog {
    public record Style(BuildingStyleId id, Map<StructureKind, BuildingTemplateId> templates) {
        public Style {
            Objects.requireNonNull(id);
            templates = Map.copyOf(templates);
            if (!templates.keySet().equals(EnumSet.allOf(StructureKind.class)))
                throw new IllegalArgumentException("incomplete building style");
        }
    }

    public static final BuildingStyleId MEDIEVAL_SPRUCE =
            new BuildingStyleId("pale_mirror:medieval_spruce");
    private static final String ROOT = "/io/farfrontier/palemirror/frontier/v3/buildings/";

    private static final class Standard {
        static final BuildingCatalog VALUE = load();
    }

    private final Map<BuildingTemplateId, BuildingDefinition> definitions;
    private final Map<BuildingStyleId, Style> styles;
    private final Map<BuildingTemplateId, byte[]> markup;
    private final String canonical;
    private final BuildingStyleId defaultStyle;
    private final Map<String, Set<String>> categories;

    public BuildingCatalog(
            Collection<BuildingDefinition> definitions,
            Collection<Style> styles,
            Map<BuildingTemplateId, byte[]> markup,
            BuildingStyleId defaultStyle,
            Map<String, Set<String>> categories) {
        this.defaultStyle = Objects.requireNonNull(defaultStyle);
        this.categories = new ResourceStorageFilter(false, Set.of(), categories).categories();
        var d = new LinkedHashMap<BuildingTemplateId, BuildingDefinition>();
        for (var value : definitions)
            if (d.putIfAbsent(value.id(), value) != null)
                throw new IllegalArgumentException("duplicate building template");
        var s = new LinkedHashMap<BuildingStyleId, Style>();
        for (var value : styles) {
            if (s.putIfAbsent(value.id(), value) != null)
                throw new IllegalArgumentException("duplicate building style");
            value.templates()
                    .forEach(
                            (kind, id) -> {
                                var definition = d.get(id);
                                if (definition == null || !definition.functions().contains(kind))
                                    throw new IllegalArgumentException(
                                            "style declares unsupported function/template");
                            });
        }
        if (d.isEmpty() || d.size() > 256 || s.isEmpty() || s.size() > 32)
            throw new IllegalArgumentException("unbounded building catalogue");
        if (!s.containsKey(defaultStyle))
            throw new IllegalArgumentException("default building style is not registered");
        for (var definition : d.values())
            for (var zone : definition.zones().values())
                if (!categories.keySet().containsAll(zone.resourceCategories()))
                    throw new IllegalArgumentException(
                            "building zone names unknown storage category");
        this.definitions = Collections.unmodifiableMap(d);
        this.styles = Collections.unmodifiableMap(s);
        var text = new LinkedHashMap<BuildingTemplateId, byte[]>();
        markup.forEach((id, bytes) -> text.put(id, bytes.clone()));
        this.markup = Collections.unmodifiableMap(text);
        canonical =
                defaultStyle.value()
                        + '|'
                        + this.categories.entrySet().stream()
                                .map(
                                        e ->
                                                e.getKey()
                                                        + '='
                                                        + e.getValue().stream()
                                                                .sorted()
                                                                .collect(
                                                                        java.util.stream.Collectors
                                                                                .joining(",")))
                                .sorted()
                                .collect(java.util.stream.Collectors.joining(";"))
                        + '|'
                        + d.values().stream()
                                .map(v -> v.id().value() + '=' + v.sha256())
                                .sorted()
                                .collect(java.util.stream.Collectors.joining(";"))
                        + '|'
                        + s.values().stream()
                                .map(
                                        v ->
                                                v.id().value()
                                                        + '='
                                                        + v.templates().entrySet().stream()
                                                                .map(
                                                                        e ->
                                                                                e.getKey().name()
                                                                                        + ':'
                                                                                        + e.getValue()
                                                                                                .value())
                                                                .sorted()
                                                                .collect(
                                                                        java.util.stream.Collectors
                                                                                .joining(",")))
                                .sorted()
                                .collect(java.util.stream.Collectors.joining(";"));
    }

    public static BuildingStyleId defaultStyle() {
        return standard().defaultStyle;
    }

    public Map<String, Set<String>> categories() {
        return categories;
    }

    public static BuildingCatalog standard() {
        return Standard.VALUE;
    }

    public String canonicalText() {
        return canonical;
    }

    private static Map<String, Set<String>> readCategories(Map<String, String> manifest) {
        var result = new TreeMap<String, Set<String>>();
        for (var name : manifest.get("categories").split(",")) {
            var id = Objects.requireNonNull(manifest.get("category." + name + ".id"));
            var members =
                    Set.of(
                            Objects.requireNonNull(manifest.get("category." + name + ".items"))
                                    .split(","));
            if (result.putIfAbsent(id, members) != null)
                throw new IllegalArgumentException("duplicate storage category");
        }
        return new ResourceStorageFilter(false, Set.of(), result).categories();
    }

    public Collection<Style> styles() {
        return styles.values();
    }

    public Collection<BuildingDefinition> definitions() {
        return definitions.values();
    }

    public BuildingPlanRef select(BuildingStyleId style, StructureKind function) {
        var set = styles.get(style);
        if (set == null) throw new IllegalArgumentException("unknown building style " + style);
        var definition = require(set.templates().get(function));
        return new BuildingPlanRef(style, definition.id(), definition.sha256());
    }

    public BuildingDefinition require(BuildingTemplateId id) {
        var value = definitions.get(id);
        if (value == null) throw new IllegalArgumentException("unknown building template " + id);
        return value;
    }

    public BuildingDefinition require(BuildingPlanRef selected, StructureKind function) {
        var expected = select(selected.style(), function);
        if (!expected.equals(selected))
            throw new IllegalArgumentException("stale or foreign building content selection");
        return require(selected.template());
    }

    public byte[] markup(BuildingTemplateId id) {
        require(id);
        var bytes = markup.get(id);
        if (bytes == null) throw new IllegalArgumentException("template has no external markup");
        return bytes.clone();
    }

    private static BuildingCatalog load() {
        var manifest = BuildingDefinitionFormat.properties(read(ROOT + "catalog.properties"));
        var definitions = new ArrayList<BuildingDefinition>();
        var markups = new HashMap<BuildingTemplateId, byte[]>();
        for (var kind : StructureKind.values()) definitions.add(graybox(kind));
        for (var path : manifest.get("templates").split(",")) {
            if (!path.matches("[a-z0-9_/-]+") || path.contains(".."))
                throw new IllegalArgumentException("invalid building asset path");
            var markup = read(ROOT + path + ".properties");
            var blocks = read(ROOT + path + ".blocks");
            var definition =
                    BuildingDefinitionFormat.decode(
                            BuildingDefinitionFormat.properties(markup),
                            BuildingDefinitionFormat.blocks(blocks),
                            sha256(
                                    markup,
                                    blocks,
                                    read(ROOT + path + ".nbt"),
                                    read(ROOT + path + ".geometry")));
            definitions.add(definition);
            markups.put(definition.id(), markup);
        }
        var styles = new ArrayList<Style>();
        for (var name : manifest.get("styles").split(",")) {
            var selected = new EnumMap<StructureKind, BuildingTemplateId>(StructureKind.class);
            for (var kind : StructureKind.values())
                selected.put(
                        kind,
                        new BuildingTemplateId(
                                Objects.requireNonNull(
                                        manifest.get("style." + name + "." + kind.name()),
                                        "missing style function")));
            styles.add(
                    new Style(
                            new BuildingStyleId(
                                    Objects.requireNonNull(manifest.get("style." + name + ".id"))),
                            selected));
        }
        return new BuildingCatalog(
                definitions,
                styles,
                markups,
                new BuildingStyleId(Objects.requireNonNull(manifest.get("defaultStyle"))),
                readCategories(manifest));
    }

    public static List<String> assetPaths() {
        return List.of(
                BuildingDefinitionFormat.properties(read(ROOT + "catalog.properties"))
                        .get("templates")
                        .split(","));
    }

    private static BuildingDefinition graybox(StructureKind kind) {
        int width =
                switch (kind) {
                    case HALL, DEPOT -> 8;
                    case FARM -> 9;
                    case WORKSHOP, SMELTERY, FORGE, WOODSHOP, INFIRMARY -> 7;
                    case HOUSING -> 6;
                };
        int depth =
                switch (kind) {
                    case HALL, FARM, WORKSHOP, SMELTERY, FORGE, WOODSHOP, DEPOT -> 7;
                    case HOUSING, INFIRMARY -> 6;
                };
        int height =
                switch (kind) {
                    case HALL -> 5;
                    case DEPOT, WORKSHOP, SMELTERY, FORGE, WOODSHOP -> 4;
                    default -> 3;
                };
        var id =
                new BuildingTemplateId(
                        "pale_mirror:graybox/" + kind.name().toLowerCase(Locale.ROOT));
        var box =
                new BuildingVolume(
                        new BlockPosition(-width / 2, 0, -depth / 2),
                        new BlockPosition((width - 1) / 2 + 1, height + 1, (depth - 1) / 2 + 1));
        var parcel = new BuildingVolume(box.min().offset(-6, -1, -6), box.max().offset(6, 2, 6));
        boolean production =
                Set.of(
                                StructureKind.WORKSHOP,
                                StructureKind.SMELTERY,
                                StructureKind.FORGE,
                                StructureKind.WOODSHOP)
                        .contains(kind);
        var room =
                new BuildingDefinition.Room(
                        "workroom", new BuildingVolume(parcel.min(), parcel.max()));
        var endpoint =
                new BuildingDefinition.Endpoint(
                        BuildingDefinition.EndpointKind.ROOM, Optional.of("workroom"));
        var entry =
                new BuildingDefinition.Passage(
                        "front",
                        new BuildingDefinition.Endpoint(
                                BuildingDefinition.EndpointKind.EXTERIOR, Optional.empty()),
                        endpoint,
                        List.of(
                                new BlockPosition(0, 0, 5),
                                new BlockPosition(0, 0, 4),
                                new BlockPosition(0, 0, 3),
                                new BlockPosition(0, 0, 2)),
                        2,
                        true);
        var workplace = !production ? null :
                new BuildingDefinition.Workplace(
                        "work",
                        "workroom",
                        new BlockPosition(1, 0, 2),
                        new BlockPosition(2, 0, 2),
                        new BlockPosition(2, 0, 1),
                        ProductionFacilityLayout.equipmentType(kind),
                        FacilityFacing.SOUTH,
                        new BuildingVolume(new BlockPosition(2, 1, 1), new BlockPosition(3, 3, 2)),
                        new BuildingVolume(new BlockPosition(1, 1, 2), new BlockPosition(3, 3, 3)));
        var hash =
                sha256(
                        (id.value() + '|' + width + '|' + depth + '|' + height + "|graybox-v2-oriented-equipment")
                                .getBytes(StandardCharsets.UTF_8));
        return new BuildingDefinition(
                id,
                1,
                hash,
                Set.of(kind),
                BuildingDefinition.Representation.GRAYBOX,
                new BlockPosition(0, 0, 0),
                FacilityFacing.SOUTH,
                parcel,
                box,
                production ? Map.of(room.id(), room) : Map.of(),
                Map.of(),
                production ? Map.of(entry.id(), entry) : Map.of(),
                production ? Map.of(workplace.id(), workplace) : Map.of(),
                production ? Optional.of(entry.id()) : Optional.empty(),
                production ? Optional.of(workplace.id()) : Optional.empty(),
                Map.of());
    }

    public static String sha256(byte[]... parts) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var part : parts) {
                digest.update(Integer.toString(part.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(part);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static byte[] read(String path) {
        try (var input = BuildingCatalog.class.getResourceAsStream(path)) {
            if (input == null)
                throw new IllegalStateException("missing authored building content " + path);
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
