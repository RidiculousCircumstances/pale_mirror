package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.StructureMaterial;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Native-compiled immutable facts for pristine authored blocks, usable before a COLD site's first
 * visit.
 */
public final class BuildingCollisionCatalog {
    private static final class Standard {
        static final Map<NavigationBlockState, NavigationObservation> FACTS = load();
    }

    public static Optional<NavigationObservation> standard(NavigationBlockState block) {
        return Optional.ofNullable(Standard.FACTS.get(block));
    }

    private static Map<NavigationBlockState, NavigationObservation> load() {
        var result = new HashMap<NavigationBlockState, NavigationObservation>();
        for (var path : BuildingCatalog.assetPaths()) {
            try (var input =
                    BuildingCollisionCatalog.class.getResourceAsStream(
                            "/io/farfrontier/palemirror/frontier/v3/buildings/"
                                    + path
                                    + ".geometry")) {
                if (input == null)
                    throw new IllegalStateException(
                            "missing compiled building collision catalogue " + path);
                decode(input.readAllBytes())
                        .forEach(
                                (key, value) -> {
                                    var old = result.putIfAbsent(key, value);
                                    if (old != null && !old.equals(value))
                                        throw new IllegalArgumentException(
                                                "conflicting native building collision"
                                                        + " certificate");
                                });
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }
        return Map.copyOf(result);
    }

    public static Map<NavigationBlockState, NavigationObservation> decode(byte[] bytes) {
        if (bytes.length > 4_194_304)
            throw new IllegalArgumentException("unbounded authored collision facts");
        var result = new LinkedHashMap<NavigationBlockState, NavigationObservation>();
        for (var line : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            var fields = line.split("\\t", -1);
            if ((fields.length != 4 && fields.length != 5)
                    || !(StructureMaterial.require(fields[0])
                            instanceof StructureMaterial.Authored material))
                throw new IllegalArgumentException("invalid authored collision row");
            var kind = NavigationObservation.Kind.requireWireTag(Integer.parseInt(fields[1]));
            NavigationObservation observation;
            if (kind == NavigationObservation.Kind.KNOWN) {
                if (!fields[2].equals("true") && !fields[2].equals("false"))
                    throw new IllegalArgumentException("invalid authored medium");
                var boxes = new ArrayList<NavigationShape.Box>();
                if (!fields[3].isEmpty())
                    for (var box : fields[3].split(";")) {
                        var v = box.split(",", -1);
                        if (v.length != 6)
                            throw new IllegalArgumentException("invalid authored collision box");
                        boxes.add(
                                new NavigationShape.Box(
                                        Double.parseDouble(v[0]),
                                        Double.parseDouble(v[1]),
                                        Double.parseDouble(v[2]),
                                        Double.parseDouble(v[3]),
                                        Double.parseDouble(v[4]),
                                        Double.parseDouble(v[5])));
                    }
                observation =
                        NavigationObservation.known(
                                new NavigationCell(
                                        new NavigationShape(boxes),
                                        Boolean.parseBoolean(fields[2]),
                                        Optional.of(material.block()), fields.length == 5 ? Optional.of(passageShape(fields[4])) : Optional.empty()));
            } else {
                if (!fields[2].isEmpty() || !fields[3].isEmpty())
                    throw new IllegalArgumentException("unknown collision has fabricated facts");
                observation = NavigationObservation.unavailable(kind);
            }
            if (result.putIfAbsent(material.block(), observation) != null || result.size() > 4096)
                throw new IllegalArgumentException("duplicate or unbounded collision palette");
        }
        return Collections.unmodifiableMap(result);
    }

    public static String row(
            StructureMaterial.Authored material, NavigationObservation observation) {
        String boxes =
                observation
                        .cell()
                        .map(
                                cell ->
                                        cell.shape().boxes().stream()
                                                .map(
                                                        b ->
                                                                b.minX() + "," + b.minY() + ","
                                                                        + b.minZ() + "," + b.maxX()
                                                                        + "," + b.maxY() + ","
                                                                        + b.maxZ())
                                                .collect(java.util.stream.Collectors.joining(";")))
                        .orElse("");
        return material.wireIdentity()
                + '\t'
                + observation.kind().wireTag()
                + '\t'
                + observation.cell().map(cell -> Boolean.toString(cell.dry())).orElse("")
                + '\t'
                + boxes
                + observation.cell().flatMap(NavigationCell::passageShape).map(shape -> "\tmanual:" + shape.boxes().stream()
                    .map(b -> b.minX()+","+b.minY()+","+b.minZ()+","+b.maxX()+","+b.maxY()+","+b.maxZ())
                    .collect(java.util.stream.Collectors.joining(";"))).orElse("");
    }

    private static NavigationShape passageShape(String encoded) {
        if (!encoded.startsWith("manual:")) throw new IllegalArgumentException("unknown authored passage capability");
        var boxes = new ArrayList<NavigationShape.Box>();
        String text = encoded.substring("manual:".length());
        if (!text.isEmpty()) for (var box : text.split(";")) {
            var v = box.split(",", -1);
            if (v.length != 6) throw new IllegalArgumentException("invalid passage shape");
            boxes.add(new NavigationShape.Box(Double.parseDouble(v[0]),Double.parseDouble(v[1]),Double.parseDouble(v[2]),
                    Double.parseDouble(v[3]),Double.parseDouble(v[4]),Double.parseDouble(v[5])));
        }
        return new NavigationShape(boxes);
    }
    private BuildingCollisionCatalog() {}
}
