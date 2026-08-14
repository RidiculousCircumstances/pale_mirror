package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredBuildingPlan;
import io.farfrontier.palemirror.api.AuthoredOpenSpacePlan;
import io.farfrontier.palemirror.api.DevelopmentReservation;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.ManagedAreaPlan;
import io.farfrontier.palemirror.api.SettlementFoundationPlan;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;

/** Fits the vertical site index to terrain-led content without changing the approved horizontal masterplan. */
final class SettlementSiteBounds {
    private SettlementSiteBounds() { }

    static VisualBounds fitVertical(VisualBounds master, VisualPoint freightGate, VisualPoint receivingDepot,
                                    List<AuthoredBuildingPlan> buildings,
                                    List<SettlementFoundationPlan> foundations,
                                    List<LinearFeaturePlan> circulation, List<LinearFeaturePlan> defences,
                                    List<AuthoredOpenSpacePlan> openSpaces, ManagedAreaPlan managedArea,
                                    List<DevelopmentReservation> reservations) {
        Range range = new Range(master.min().y(), master.max().y());
        range.include(freightGate.y());
        range.include(receivingDepot.y());
        buildings.forEach(value -> range.include(value.parcel()));
        foundations.forEach(value -> range.include(value.footprint()));
        java.util.stream.Stream.concat(circulation.stream(), defences.stream())
                .flatMap(value -> value.nodes().stream()).forEach(value -> range.include(value.y()));
        openSpaces.forEach(value -> range.include(value.bounds()));
        managedArea.areas().forEach(range::include);
        reservations.forEach(value -> range.include(value.bounds()));
        return new VisualBounds(new VisualPoint(master.min().x(), range.minimum, master.min().z()),
                new VisualPoint(master.max().x(), range.maximum, master.max().z()));
    }

    private static final class Range {
        private int minimum;
        private int maximum;

        private Range(int minimum, int maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }

        private void include(VisualBounds bounds) {
            include(bounds.min().y());
            include(bounds.max().y());
        }

        private void include(int value) {
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
    }
}
