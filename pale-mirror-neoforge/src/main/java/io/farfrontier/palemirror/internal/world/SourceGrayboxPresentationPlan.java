package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable block-claim plan derived from one source-graybox snapshot.
 *
 * <p>This is the only place where a source fact acquires a Minecraft-space
 * footprint. It validates the whole plan before the materializer can touch a
 * loaded chunk, so two canonical facts can never be hidden by placement order.
 * Player-owned blocks are intentionally outside this plan and remain runtime
 * presentation conflicts.</p>
 */
final class SourceGrayboxPresentationPlan {
    private static final int SURFACE_Y = ReferenceGrayboxLayout.GROUND_Y;
    private static final int LABEL_Y = SURFACE_Y + 4;
    private static final int OVERLAY_Y = LABEL_Y + 1;
    private static final int ACTIVITY_Y = OVERLAY_Y + 1;
    private static final int SECTOR_METRIC_MAX_HEIGHT = 10;
    /** Keep collision recovery compact and below the dedicated label plane. */
    private static final int MAX_CLAIM_Y = SURFACE_Y + 15;
    /** Shared plan/ledger limit; every claim has already been validated before Minecraft receives it. */
    static final int MAX_CLAIMS = 10_240;

    private SourceGrayboxPresentationPlan() { }

    static LinkedHashMap<String, Desired> from(ReferenceGrayboxSnapshot snapshot) {
        requireProfile(snapshot);
        LinkedHashMap<String, Desired> result = new LinkedHashMap<>();
        for (ReferenceGrayboxSnapshot.Cell cell : snapshot.cells()) {
            add(result, marker("cell:" + cell.x() + ":" + cell.y(), "cell:" + cell.x() + ":" + cell.y(), "CELL",
                    snapshot.stateRevision(), cell.rectangle().x() + 1, cell.rectangle().z() + 1, cell.colour()));
        }
        for (ReferenceGrayboxSnapshot.Facility facility : snapshot.facilities()) {
            if (facility.kind().equals("fortification")) addFortification(result, snapshot.stateRevision(), facility);
            else add(result, rectangle("facility:" + facility.id(), facility.id(), "FACILITY", snapshot.stateRevision(), facility.rectangle(), 1,
                    facility.colour()));
        }
        for (ReferenceGrayboxSnapshot.ResourceSite site : snapshot.resourceSites()) {
            add(result, rectangle("resource-site:" + site.id(), "site:" + site.id(), "RESOURCE_SITE", snapshot.stateRevision(), site.rectangle(), 1,
                    site.colour()));
        }
        for (ReferenceGrayboxSnapshot.Route route : snapshot.routes()) {
            List<ReferenceGrayboxLayout.Point> slots = ReferenceGrayboxLayout.routeSlots(route.start(), route.end());
            for (int index = 0; index < slots.size(); index++) {
                ReferenceGrayboxLayout.Point slot = slots.get(index);
                add(result, new Desired("route-segment:" + route.id() + ":" + index, route.id(), "ROUTE", snapshot.stateRevision(),
                        slot.x(), SURFACE_Y, slot.z(), 1, 1, 1, route.colour()));
            }
        }
        for (ReferenceGrayboxSnapshot.HiveOrgan organ : snapshot.hiveOrgans()) {
            add(result, rectangle("hive-organ:" + organ.id(), "organ:" + organ.id(), "HIVE_ORGAN", snapshot.stateRevision(), organ.rectangle(), 2,
                    organ.colour()));
        }
        for (ReferenceGrayboxSnapshot.Cargo cargo : snapshot.cargoes()) {
            add(result, rectangle("cargo-pallet:" + cargo.id(), cargo.id(), "CARGO", snapshot.stateRevision(), cargo.rectangle(), 1, cargo.colour()));
        }
        for (ReferenceGrayboxSnapshot.FieldPost post : snapshot.fieldPosts()) {
            add(result, rectangle("field-post:" + post.id(), "field-post:" + post.id(), "FIELD_POST", snapshot.stateRevision(), post.rectangle(), 1,
                    post.colour()));
        }
        for (ReferenceGrayboxSnapshot.FieldLink link : snapshot.fieldLinks()) {
            for (int index = 0; index < link.slots().size(); index++) {
                ReferenceGrayboxLayout.Point slot = link.slots().get(index);
                add(result, new Desired("field-link:" + link.id() + ":segment:" + index, "field_link:" + link.id(), "FIELD_LINK",
                        snapshot.stateRevision(), slot.x(), SURFACE_Y, slot.z(), 1, 1, 1, link.colour()));
            }
        }
        for (ReferenceGrayboxSnapshot.Sector sector : snapshot.sectors()) {
            add(result, marker("sector:" + sector.key(), "sector:" + sector.key(), "SECTOR", snapshot.stateRevision(),
                    sector.rectangle().centreX(), OVERLAY_Y, sector.rectangle().centreZ(), sector.colour()));
            addSectorMetric(result, snapshot.stateRevision(), sector, "infection", 3, 3, sector.infection(), 1.0d,
                    "metric.infection");
            addSectorMetric(result, snapshot.stateRevision(), sector, "spores", 5, 3, sector.sporeLoad(), 10.0d,
                    "metric.spores");
            addSectorMetric(result, snapshot.stateRevision(), sector, "human_access", 3, 5, sector.humanAccess(), 1.0d,
                    "metric.human_access");
            addSectorMetric(result, snapshot.stateRevision(), sector, "hive_influence", 5, 5, sector.hiveInfluence(), 1.0d,
                    "metric.hive_influence");
        }
        for (ReferenceGrayboxSnapshot.Chrysalis chrysalis : snapshot.chrysalises()) {
            int x = chrysalis.rectangle().centreX() - 2;
            int z = chrysalis.rectangle().centreZ() - 2;
            add(result, new Desired("chrysalis:" + chrysalis.organId(), "chrysalis:" + chrysalis.organId(), "CHRYSALIS",
                    snapshot.stateRevision(), x, SURFACE_Y, z, 4, 4, 2, chrysalis.colour()));
        }
        for (ReferenceGrayboxSnapshot.Interaction interaction : snapshot.interactions()) {
            for (int index = 0; index < interaction.slots().size(); index++) {
                ReferenceGrayboxLayout.Point slot = interaction.slots().get(index);
                add(result, interactionSlot(interaction, index, snapshot.stateRevision(), slot));
            }
        }
        for (ReferenceGrayboxSnapshot.Activity activity : snapshot.activities()) {
            if (!activity.terminal()) add(result, marker("activity:" + activity.id(), activity.id(), "ACTIVITY", snapshot.stateRevision(),
                    activity.position().x(), ACTIVITY_Y, activity.position().z(), activity.colour()));
        }
        if (result.size() > MAX_CLAIMS) {
            throw new IllegalStateException("source graybox projection exceeds its bounded claim ledger");
        }
        separateCanonicalLayers(result);
        return result;
    }

    static void validate(ReferenceGrayboxSnapshot snapshot) {
        from(snapshot);
    }

    static List<Position> positions(Desired item) {
        return positions(item.x(), item.y(), item.z(), item.width(), item.depth(), item.height());
    }

    private static void add(Map<String, Desired> values, Desired item) {
        if (values.putIfAbsent(item.id(), item) != null) throw new IllegalStateException("duplicate source graybox materialization ID: " + item.id());
    }

    private static Desired marker(String id, String subject, String kind, String revision, int x, int z, String colour) {
        return marker(id, subject, kind, revision, x, SURFACE_Y, z, colour);
    }

    private static Desired marker(String id, String subject, String kind, String revision, int x, int y, int z, String colour) {
        return new Desired(id, subject, kind, revision, x, y, z, 1, 1, 1, colour);
    }

    private static Desired rectangle(String id, String subject, String kind, String revision, ReferenceGrayboxLayout.Rectangle area,
                                     int height, String colour) {
        return new Desired(id, subject, kind, revision, area.x(), SURFACE_Y, area.z(), area.width(), area.depth(), height, colour);
    }

    /** A fortification is a perimeter, not a filled foundation that hides its named buildings. */
    private static void addFortification(Map<String, Desired> result, String revision, ReferenceGrayboxSnapshot.Facility facility) {
        ReferenceGrayboxLayout.Rectangle area = facility.rectangle();
        String id = "facility:" + facility.id();
        add(result, new Desired(id + ":north", facility.id(), "FACILITY_FORTIFICATION", revision,
                area.x(), SURFACE_Y, area.z(), area.width(), 1, 1, facility.colour()));
        add(result, new Desired(id + ":south", facility.id(), "FACILITY_FORTIFICATION", revision,
                area.x(), SURFACE_Y, area.z() + area.depth() - 1, area.width(), 1, 1, facility.colour()));
        if (area.depth() > 2) {
            add(result, new Desired(id + ":west", facility.id(), "FACILITY_FORTIFICATION", revision,
                    area.x(), SURFACE_Y, area.z() + 1, 1, area.depth() - 2, 1, facility.colour()));
            add(result, new Desired(id + ":east", facility.id(), "FACILITY_FORTIFICATION", revision,
                    area.x() + area.width() - 1, SURFACE_Y, area.z() + 1, 1, area.depth() - 2, 1, facility.colour()));
        }
    }

    private static Desired interactionSlot(ReferenceGrayboxSnapshot.Interaction interaction, int index, String revision,
                                           ReferenceGrayboxLayout.Point slot) {
        return new Desired("interaction:" + interaction.id() + ":" + index, interaction.subjectId(), "INTERACTION", revision,
                slot.x(), SURFACE_Y + interaction.yOffset(), slot.z(), 1, 1, 1, interaction.colour(), interaction.id(),
                interaction.kind(), interaction.totalWeight());
    }

    /**
     * Four short towers make the territorial values readable at map scale.
     * Exact values remain in the immutable snapshot and are exposed by the
     * spatial inspector; this is deliberately a bounded chart, not a hidden
     * second numerical model.
     */
    private static void addSectorMetric(Map<String, Desired> result, String revision, ReferenceGrayboxSnapshot.Sector sector,
                                        String metric, int localX, int localZ, double value, double fullScale, String colour) {
        int height = sectorMetricHeight(value, fullScale, sector.key(), metric);
        if (height == 0) return;
        ReferenceGrayboxLayout.Rectangle area = sector.rectangle();
        add(result, new Desired("sector-metric:" + sector.key() + ":" + metric, "sector:" + sector.key(), "SECTOR_METRIC", revision,
                area.x() + localX, SURFACE_Y, area.z() + localZ, 1, 1, height, colour));
    }

    private static int sectorMetricHeight(double value, double fullScale, String sectorKey, String metric) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalStateException("sector metric is invalid: " + sectorKey + " " + metric);
        }
        if (value == 0.0d) return 0;
        return Math.min(SECTOR_METRIC_MAX_HEIGHT, Math.max(1, (int) Math.ceil(value / fullScale * SECTOR_METRIC_MAX_HEIGHT)));
    }

    /**
     * The simulation may deliberately place several facts in one logical
     * cell: for example, an infected resource site can also host a hive organ.
     * Their x/z address remains canonical.  The presentation adapter gives
     * later facts the first free vertical layer, so neither fact is hidden and
     * no source-scale coordinate is changed.
     */
    private static void separateCanonicalLayers(LinkedHashMap<String, Desired> desired) {
        Map<Position, String> ownerByPosition = new LinkedHashMap<>();
        for (Map.Entry<String, Desired> entry : desired.entrySet()) {
            Desired resolved = entry.getValue();
            while (overlaps(resolved, ownerByPosition)) {
                if (resolved.y() + resolved.height() > MAX_CLAIM_Y) {
                    throw new IllegalStateException("source graybox vertical presentation budget is exhausted by " + resolved.id());
                }
                resolved = resolved.atY(resolved.y() + 1);
            }
            entry.setValue(resolved);
            // Every position was checked against every earlier claim before it
            // is reserved here, so this map is the complete pre-write proof of
            // distinct physical ownership; a second full traversal is redundant.
            for (Position position : positions(resolved)) ownerByPosition.put(position, resolved.id());
        }
    }

    private static boolean overlaps(Desired item, Map<Position, String> ownerByPosition) {
        return positions(item).stream().anyMatch(ownerByPosition::containsKey);
    }

    private static List<Position> positions(int x, int y, int z, int width, int depth, int height) {
        List<Position> result = new ArrayList<>(width * depth * height);
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) for (int dy = 0; dy < height; dy++) {
            result.add(new Position(x + dx, y + dy, z + dz));
        }
        return result;
    }

    private static void requireProfile(ReferenceGrayboxSnapshot snapshot) {
        if (!snapshot.profileId().equals("graybox_1_40") || snapshot.bounds().groundY() != SURFACE_Y
                || snapshot.bounds().width() != ReferenceGrayboxLayout.ARENA_BLOCKS_X
                || snapshot.bounds().depth() != ReferenceGrayboxLayout.ARENA_BLOCKS_Z
                || snapshot.bounds().blocksPerCell() != ReferenceGrayboxLayout.BLOCKS_PER_CELL) {
            throw new IllegalStateException("source graybox materializer rejected an incompatible profile");
        }
    }

    record Desired(String id, String subjectId, String kind, String revision, int x, int y, int z, int width, int depth,
                   int height, String colour, String interactionId, String interactionKind, double interactionWeight) {
        Desired(String id, String subjectId, String kind, String revision, int x, int y, int z, int width, int depth,
                int height, String colour) {
            this(id, subjectId, kind, revision, x, y, z, width, depth, height, colour, "", "", 0.0d);
        }

        Desired withInteractionWeight(double weight) {
            return new Desired(id, subjectId, kind, revision, x, y, z, width, depth, height, colour, interactionId, interactionKind, weight);
        }

        Desired atY(int value) {
            return new Desired(id, subjectId, kind, revision, x, value, z, width, depth, height, colour, interactionId, interactionKind, interactionWeight);
        }
    }

    record Position(int x, int y, int z) { }
}
