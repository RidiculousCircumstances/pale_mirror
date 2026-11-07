package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.*;

/**
 * Derived bed register. Building content and exact structural aftermath remain the only writers.
 */
public final class SettlementBeds {
    public record Id(SubjectId buildingId, String localId) {
        public Id {
            Objects.requireNonNull(buildingId);
            Objects.requireNonNull(localId);
        }
    }

    public record Place(
            Id id,
            SubjectId settlementId,
            BlockPosition head,
            BlockPosition foot,
            boolean physicallyPresent, io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaceProfile profile) {
        public io.farfrontier.palemirror.frontier.v3.model.rest.RestPlace restPlace() {
            return new io.farfrontier.palemirror.frontier.v3.model.rest.RestPlace(id,settlementId,head,foot,profile,physicallyPresent);
        }
    }

    public static List<Place> places(FrontierWorldState state, Settlement home) {
        var result = new ArrayList<Place>();
        for (var structure : home.structures()) {
            var damage = state.structureDamage().get(structure.id());
            for (var bed : structure.definition().beds().values()) {
                var head = structure.placement().world(bed.head());
                var foot = structure.placement().world(bed.foot());
                boolean available =
                        (damage == null
                                        || !damage.cells().containsKey(head)
                                                && !damage.cells().containsKey(foot));
                result.add(
                        new Place(
                                new Id(structure.id(), bed.id()),
                                home.id(),
                                head,
                                foot,
                                available,bed.profile()));
            }
        }
        return List.copyOf(result);
    }

    public static int declaredCapacity(Settlement home) {
        return home.structures().stream()
                .filter(s -> s.kind() == StructureKind.HOUSING)
                .mapToInt(s -> s.definition().beds().size())
                .sum();
    }

    private SettlementBeds() {}
}
