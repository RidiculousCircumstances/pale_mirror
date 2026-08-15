package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.PerimeterModuleKind;
import io.farfrontier.palemirror.api.PerimeterModulePlan;
import io.farfrontier.palemirror.api.PerimeterPlan;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Converts the semantic enclosure graph into one non-overlapping structural module stream. */
final class StructuralPerimeterPlanner {
    private StructuralPerimeterPlanner() { }

    static PerimeterPlan plan(List<LinearFeaturePlan> features) {
        List<PerimeterModulePlan> modules = new ArrayList<>();
        List<VisualPoint> wallPoints = new ArrayList<>();
        for (LinearFeaturePlan feature : features) {
            List<VisualPoint> points = raster(feature.nodes().getFirst(), feature.nodes().getLast());
            if (feature.kind() == LinearFeatureKind.PALISADE_GATE) {
                VisualPoint center = points.get(points.size() / 2);
                boolean freight = feature.id().contains("freight");
                // The semantic opening already reserves its complete span.
                // A structural module may articulate that span, never grow
                // into adjacent wall sockets as the old oversized arch did.
                int length = points.size();
                int turn = axisTurn(points);
                modules.add(new PerimeterModulePlan("gate:" + feature.id(), freight
                        ? PerimeterModuleKind.FREIGHT_GATE : PerimeterModuleKind.PEDESTRIAN_GATE,
                        center, turn, length, footprint(center, turn, length, 8)));
                continue;
            }
            if (feature.kind() != LinearFeatureKind.PALISADE) continue;
            wallPoints.addAll(points);
            int cursor = 0;
            while (cursor < points.size()) {
                int sameDatum = 1;
                while (cursor + sameDatum < points.size() && sameDatum < 9
                        && points.get(cursor + sameDatum).y() == points.get(cursor).y()) sameDatum++;
                int length = sameDatum >= 9 ? 9 : sameDatum >= 7 ? 7
                        : sameDatum >= 5 ? 5 : sameDatum >= 3 ? 3 : 1;
                VisualPoint start = points.get(cursor);
                VisualPoint end = points.get(Math.min(points.size() - 1, cursor + length - 1));
                VisualPoint center = new VisualPoint((start.x() + end.x()) / 2,
                        (start.y() + end.y()) / 2, (start.z() + end.z()) / 2);
                int turn = axisTurn(List.of(start, end));
                modules.add(new PerimeterModulePlan("wall:" + feature.id() + ":" + cursor,
                        PerimeterModuleKind.STRAIGHT,
                        center, turn, length, footprint(center, turn, length, 5)));
                cursor += length;
            }
        }
        for (VisualPoint point : watchPosts(wallPoints)) {
            modules.add(new PerimeterModulePlan("watch:" + point.x() + ":" + point.z(),
                    PerimeterModuleKind.WATCH_POST, point, 0, 7,
                    new VisualBounds(new VisualPoint(point.x() - 3, point.y(), point.z() - 3),
                            new VisualPoint(point.x() + 3, point.y() + 10, point.z() + 3))));
        }
        return new PerimeterPlan(modules);
    }

    private static Set<VisualPoint> watchPosts(List<VisualPoint> points) {
        Set<VisualPoint> result = new LinkedHashSet<>();
        if (points.isEmpty()) return result;
        result.add(points.stream().min(java.util.Comparator.comparingInt(value -> value.x() + value.z())).orElseThrow());
        result.add(points.stream().max(java.util.Comparator.comparingInt(value -> value.x() + value.z())).orElseThrow());
        result.add(points.stream().min(java.util.Comparator.comparingInt(value -> value.x() - value.z())).orElseThrow());
        result.add(points.stream().max(java.util.Comparator.comparingInt(value -> value.x() - value.z())).orElseThrow());
        return result;
    }

    private static int axisTurn(List<VisualPoint> points) {
        VisualPoint first = points.getFirst();
        VisualPoint last = points.getLast();
        return Math.abs(last.x() - first.x()) >= Math.abs(last.z() - first.z()) ? 0 : 1;
    }

    private static VisualBounds footprint(VisualPoint center, int turns, int length, int height) {
        int half = length / 2;
        return turns % 2 == 0
                ? new VisualBounds(new VisualPoint(center.x() - half, center.y(), center.z() - 1),
                new VisualPoint(center.x() + half, center.y() + height, center.z() + 1))
                : new VisualBounds(new VisualPoint(center.x() - 1, center.y(), center.z() - half),
                new VisualPoint(center.x() + 1, center.y() + height, center.z() + half));
    }

    private static List<VisualPoint> raster(VisualPoint from, VisualPoint to) {
        int dx = to.x() - from.x(); int dy = to.y() - from.y(); int dz = to.z() - from.z();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
        List<VisualPoint> result = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) result.add(new VisualPoint(from.x() + dx * step / steps,
                from.y() + dy * step / steps, from.z() + dz * step / steps));
        return result;
    }
}
