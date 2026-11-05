package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;

import java.util.*;

/** One explicit bounded interchange for retained layouts, persistence and developer drafts. */
public final class SettlementLayoutFormat {
    public static byte[] encode(SettlementLayoutPlanner.Result plan) {
        var layout = plan.layout();
        var p = new TreeMap<String, String>();
        p.put("format", "1");
        p.put("owner", layout.settlementId().value());
        p.put("revision", Long.toString(layout.revision()));
        p.put("scheme", layout.scheme());
        p.put("style", layout.style().value());
        p.put("count", Integer.toString(plan.buildings().size()));
        for (int i = 0; i < plan.buildings().size(); i++) {
            var building = plan.buildings().get(i);
            var key = "building." + i + ".";
            p.put(key + "id", building.id().value());
            p.put(key + "function", building.kind().name());
            p.put(key + "anchor", point(building.anchor()));
            p.put(key + "facing", building.facing().name());
            p.put(key + "style", building.building().style().value());
            p.put(key + "template", building.building().template().value());
            p.put(key + "hash", building.building().sha256());
            var plot =
                    layout.plots().values().stream()
                            .filter(v -> v.buildingId().equals(building.id()))
                            .findFirst()
                            .orElseThrow();
            p.put(key + "plot", plot.id().value());
            p.put(key + "street", point(plot.streetAccess().support()));
        }
        p.put(
                "paving",
                layout.paving().stream()
                        .sorted(
                                Comparator.comparingInt(BlockPosition::x)
                                        .thenComparingInt(BlockPosition::z)
                                        .thenComparingInt(BlockPosition::y))
                        .map(SettlementLayoutFormat::point)
                        .collect(java.util.stream.Collectors.joining(";")));
        p.put(
                "fronts",
                layout.extensionFronts().stream()
                        .map(v -> point(v.support()))
                        .collect(java.util.stream.Collectors.joining(";")));
        return BuildingDefinitionFormat.encodeProperties(p);
    }

    public static SettlementLayoutPlanner.Result decode(byte[] bytes) {
        var p = new TreeMap<>(BuildingDefinitionFormat.properties(bytes, 2_097_152));
        if (!take(p, "format").equals("1"))
            throw new IllegalArgumentException("unsupported settlement plan format");
        var owner = new SubjectId(take(p, "owner"));
        long revision = Long.parseLong(take(p, "revision"));
        var scheme = take(p, "scheme");
        var style = new BuildingStyleId(take(p, "style"));
        int count = Integer.parseInt(take(p, "count"));
        if (count < 1 || count > SettlementLayout.MAX_PLOTS)
            throw new IllegalArgumentException("unbounded settlement buildings");
        var buildings = new ArrayList<SettlementStructure>();
        var plots = new LinkedHashMap<SettlementLayout.PlotId, SettlementLayout.Plot>();
        for (int i = 0; i < count; i++) {
            var key = "building." + i + ".";
            var id = new SubjectId(take(p, key + "id"));
            var kind = StructureKind.valueOf(take(p, key + "function"));
            var position = coordinate(take(p, key + "anchor"));
            var facing = FacilityFacing.valueOf(take(p, key + "facing"));
            var content =
                    new BuildingPlanRef(
                            new BuildingStyleId(take(p, key + "style")),
                            new BuildingTemplateId(take(p, key + "template")),
                            take(p, key + "hash"));
            var building = new SettlementStructure(id, owner, kind, position, facing, content);
            buildings.add(building);
            var plotId = new SettlementLayout.PlotId(take(p, key + "plot"));
            var street = new SurfaceAnchor(coordinate(take(p, key + "street")));
            if (plots.putIfAbsent(
                            plotId,
                            new SettlementLayout.Plot(
                                    plotId,
                                    id,
                                    building.placement().volume(building.definition().parcel()),
                                    street))
                    != null) throw new IllegalArgumentException("duplicate plot id");
        }
        var declaredPaving = points(take(p, "paving"));
        var paving = new LinkedHashSet<BlockPosition>(declaredPaving);
        if (paving.size() != declaredPaving.size())
            throw new IllegalArgumentException("duplicate paving cells");
        var fronts = points(take(p, "fronts")).stream().map(SurfaceAnchor::new).toList();
        if (!p.isEmpty())
            throw new IllegalArgumentException("unknown settlement plan fields " + p.keySet());
        return new SettlementLayoutPlanner.Result(
                buildings,
                new SettlementLayout(owner, revision, scheme, style, plots, paving, fronts));
    }

    public static String overview(SettlementLayoutPlanner.Result plan) {
        var points = new ArrayList<>(plan.layout().paving());
        for (var plot : plan.layout().plots().values()) {
            points.add(plot.bounds().min());
            points.add(plot.bounds().max());
        }
        int minX = points.stream().mapToInt(BlockPosition::x).min().orElseThrow() - 5,
                minZ = points.stream().mapToInt(BlockPosition::z).min().orElseThrow() - 5;
        int width = points.stream().mapToInt(BlockPosition::x).max().orElseThrow() - minX + 5,
                depth = points.stream().mapToInt(BlockPosition::z).max().orElseThrow() - minZ + 5;
        var out =
                new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"")
                        .append(minX)
                        .append(' ')
                        .append(minZ)
                        .append(' ')
                        .append(width)
                        .append(' ')
                        .append(depth)
                        .append("\"><rect x=\"")
                        .append(minX)
                        .append("\" y=\"")
                        .append(minZ)
                        .append("\" width=\"")
                        .append(width)
                        .append("\" height=\"")
                        .append(depth)
                        .append("\" fill=\"#edf1e5\"/>");
        for (var p : plan.layout().paving())
            out.append("<rect x=\"")
                    .append(p.x())
                    .append("\" y=\"")
                    .append(p.z())
                    .append("\" width=\"1\" height=\"1\" fill=\"#999a94\"/>");
        for (var building : plan.buildings()) {
            var box = building.placement().volume(building.definition().fabric());
            out.append("<rect x=\"")
                    .append(box.min().x())
                    .append("\" y=\"")
                    .append(box.min().z())
                    .append("\" width=\"")
                    .append(box.max().x() - box.min().x())
                    .append("\" height=\"")
                    .append(box.max().z() - box.min().z())
                    .append("\" fill=\"#796149\" stroke=\"#352b20\" stroke-width=\".3\"><title>")
                    .append(building.id().value())
                    .append(' ')
                    .append(building.kind())
                    .append("</title></rect>");
            var entry = BuildingSpatialPlan.of(building).primaryEntrance().thresholdSurface();
            out.append("<circle cx=\"")
                    .append(entry.x() + .5)
                    .append("\" cy=\"")
                    .append(entry.z() + .5)
                    .append("\" r=\"1.2\" fill=\"#45c66b\"/>");
            out.append("<text x=\"")
                    .append(box.min().x() + 1)
                    .append("\" y=\"")
                    .append(box.min().z() + 3)
                    .append("\" font-size=\"2\" fill=\"white\">")
                    .append(building.kind())
                    .append("</text>");
        }
        for (var front : plan.layout().extensionFronts())
            out.append("<circle cx=\"")
                    .append(front.x() + .5)
                    .append("\" cy=\"")
                    .append(front.z() + .5)
                    .append("\" r=\"1.8\" fill=\"#367ada\"/>");
        return out.append("</svg>\n").toString();
    }

    private static List<BlockPosition> points(String value) {
        if (value.length() > 1_048_576)
            throw new IllegalArgumentException("unbounded settlement surfaces");
        return Arrays.stream(value.split(";")).map(SettlementLayoutFormat::coordinate).toList();
    }

    private static BlockPosition coordinate(String value) {
        if (value.length() > 128) throw new IllegalArgumentException("unbounded world coordinate");
        var parts = value.split(",", -1);
        if (parts.length != 3)
            throw new IllegalArgumentException("world coordinate needs three components");
        return new BlockPosition(
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static String point(BlockPosition p) {
        return p.x() + "," + p.y() + "," + p.z();
    }

    private static String take(Map<String, String> p, String key) {
        var v = p.remove(key);
        if (v == null) throw new IllegalArgumentException("missing layout declaration " + key);
        return v;
    }

    private SettlementLayoutFormat() {}
}
