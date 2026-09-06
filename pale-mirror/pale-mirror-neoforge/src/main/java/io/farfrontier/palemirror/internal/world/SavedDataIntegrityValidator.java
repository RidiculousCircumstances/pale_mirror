package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.List;

/** Cross-checks canonical references against persisted physical projections. */
final class SavedDataIntegrityValidator {
    private SavedDataIntegrityValidator() { }

    static void validate(PaleMirrorSavedData data) {
        List<String> errors = new ArrayList<>();
        data.testMines().forEach((id, mine) -> {
            required(errors, id.equals(mine.id()), "test mine map key mismatch " + id);
            required(errors, data.worldState().facility(id).isPresent(), "test mine without facility " + id);
            required(errors, data.worldRegistry().find(id).isPresent(), "test mine without registry entry " + id);
        });
        data.campaignRegions().forEach((id, region) -> {
            required(errors, id.equals(region.id()), "campaign region map key mismatch " + id);
            required(errors, data.worldState().livingRegion(id).isPresent(), "campaign region without canonical region " + id);
            required(errors, data.worldState().place(region.placeId()).isPresent(), "campaign region without place " + id);
        });
        data.settlementDepots().forEach((id, depot) -> {
            required(errors, id.equals(depot.communityId()), "depot map key mismatch " + id);
            required(errors, data.worldState().site(depot.siteId()).isPresent(), "depot without canonical site " + depot.siteId());
            required(errors, data.semanticSlots().find(depot.semanticSlot()).isPresent(), "depot without semantic slot " + id);
        });
        data.vanillaMinecartRoutes().forEach((regionId, route) -> {
            required(errors, regionId.equals(route.regionId()), "minecart route map key mismatch " + regionId);
            required(errors, data.worldState().livingRegion(regionId).isPresent(), "minecart route without region " + regionId);
            required(errors, data.worldState().routeContract(new io.farfrontier.palemirror.domain.WorldObjectId(route.routeId())).isPresent(),
                    "minecart route without contract " + route.routeId());
        });
        data.semanticSlots().slots().forEach(slot -> {
            var parcel = data.parcels().find(slot.parcelId()).orElse(null);
            required(errors, parcel != null, "semantic slot without parcel " + slot.key().value());
            if (parcel != null) {
                required(errors, parcel.kind() == slot.parcelKind(), "semantic slot parcel kind mismatch " + slot.key().value());
                required(errors, slot.cells().stream().allMatch(cell -> parcel.contains(cell.position())),
                        "semantic slot escapes parcel " + slot.key().value());
            }
        });
        data.materializationJobs().validateUniqueCurrent(errors);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid Pale Mirror physical state: " + String.join("; ", errors));
        }
    }

    private static void required(List<String> errors, boolean condition, String message) {
        if (!condition) errors.add(message);
    }
}
