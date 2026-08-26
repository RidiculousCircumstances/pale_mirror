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
    /**
     * Activities are transient source facts, but a single floating marker did
     * not read as a real operation on the ground.  These compact silhouettes
     * remain symbolic: the source still owns every phase, person and outcome.
     */
    private static final int ACTIVITY_SCENE_Y = SURFACE_Y + 1;
    private static final int SECTOR_METRIC_MAX_HEIGHT = 10;
    /** Keep collision recovery below the bounded local-label volume, including a hive signal and cap. */
    private static final int MAX_CLAIM_Y = SURFACE_Y + 24;
    /** Shared plan/ledger limit; every claim has already been validated before Minecraft receives it. */
    static final int MAX_CLAIMS = 65_536;
    private static final int ROUTE_WAYPOINT_INTERVAL = 24;
    /** A three-block mast reads as infrastructure, not as a sector-value column. */
    private static final int HIVE_SPIRE_WIDTH = 3;
    /** The cap projects a deliberately broad, type-coloured hive silhouette. */
    private static final int HIVE_CROWN_WIDTH = 9;
    /** Neutral lamps make an organ legible through the flat world's night cycle. */
    private static final int HIVE_SIGNAL_HEIGHT = 2;
    /** A visible tissue patch starts only once source tissue is no longer zero-level numerical noise. */
    private static final double INFECTION_TISSUE_MINIMUM = 0.01d;
    /** Four independent clumps make a six-by-six surface that grows without one damaged block freezing a whole cell. */
    private static final int INFECTION_TISSUE_CLUMPS = 4;
    private static final int INFECTION_TISSUE_CLUMP_WIDTH = 3;
    private static final int INFECTION_TISSUE_Y = SURFACE_Y + 1;

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
            List<ReferenceGrayboxLayout.Point> corridor = ReferenceGrayboxLayout.routeLine(route.start(), route.end());
            for (int index = 0; index < corridor.size(); index++) {
                ReferenceGrayboxLayout.Point point = corridor.get(index);
                add(result, new Desired("route-segment:" + route.id() + ":" + index, route.id(), "ROUTE", snapshot.stateRevision(),
                        point.x(), SURFACE_Y, point.z(), 1, 1, 1, route.colour()));
                if (index > 0 && index < corridor.size() - 1 && index % ROUTE_WAYPOINT_INTERVAL == 0) {
                    add(result, new Desired("route-waypoint:" + route.id() + ":" + index, route.id(), "ROUTE_WAYPOINT",
                            snapshot.stateRevision(), point.x(), SURFACE_Y, point.z(), 1, 1, 3, route.colour()));
                }
            }
        }
        for (ReferenceGrayboxSnapshot.HiveOrgan organ : snapshot.hiveOrgans()) {
            add(result, rectangle("hive-organ:" + organ.id(), "organ:" + organ.id(), "HIVE_ORGAN", snapshot.stateRevision(), organ.rectangle(), 2,
                    organ.colour()));
            int spireHeight = hiveSpireHeight(organ.kind());
            int spireX = organ.rectangle().centreX() - HIVE_SPIRE_WIDTH / 2;
            int spireZ = organ.rectangle().centreZ() - HIVE_SPIRE_WIDTH / 2;
            add(result, new Desired("hive-organ:" + organ.id() + ":spire", "organ:" + organ.id(), "HIVE_ORGAN_LANDMARK",
                    snapshot.stateRevision(), spireX, SURFACE_Y + 2, spireZ, HIVE_SPIRE_WIDTH, HIVE_SPIRE_WIDTH, spireHeight,
                    organ.colour()));
            // The lamps do not encode a new simulation quantity: they only
            // keep the coloured organ silhouette visible in the night cycle.
            add(result, new Desired("hive-organ:" + organ.id() + ":signal", "organ:" + organ.id(), "HIVE_ORGAN_SIGNAL",
                    snapshot.stateRevision(), spireX, SURFACE_Y + 2 + spireHeight, spireZ, HIVE_SPIRE_WIDTH, HIVE_SPIRE_WIDTH,
                    HIVE_SIGNAL_HEIGHT, "hive.signal"));
            // A broad cap plus a thick mast makes the organ read as hive
            // infrastructure at a distance, rather than as a territorial
            // metric tower. Its colour remains the organ kind's colour.
            add(result, new Desired("hive-organ:" + organ.id() + ":crown", "organ:" + organ.id(), "HIVE_ORGAN_LANDMARK",
                    snapshot.stateRevision(), organ.rectangle().centreX() - HIVE_CROWN_WIDTH / 2,
                    SURFACE_Y + 2 + spireHeight + HIVE_SIGNAL_HEIGHT, organ.rectangle().centreZ() - HIVE_CROWN_WIDTH / 2,
                    HIVE_CROWN_WIDTH, HIVE_CROWN_WIDTH, 1, organ.colour()));
        }
        for (ReferenceGrayboxSnapshot.Cargo cargo : snapshot.cargoes()) {
            add(result, rectangle("cargo-pallet:" + cargo.id(), cargo.id(), "CARGO", snapshot.stateRevision(), cargo.rectangle(), 1, cargo.colour()));
        }
        for (ReferenceGrayboxSnapshot.FieldPost post : snapshot.fieldPosts()) addFieldPost(result, snapshot.stateRevision(), post);
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
            if (!activity.terminal()) addActivityScene(result, snapshot.stateRevision(), activity);
        }
        for (ReferenceGrayboxSnapshot.Effect effect : snapshot.effects()) {
            add(result, marker("effect:" + effect.id(), effect.subjectId(), "SOURCE_EFFECT", snapshot.stateRevision(),
                    effect.position().x(), ACTIVITY_Y + 1, effect.position().z(), effect.colour()));
        }
        addInfectionTissue(result, snapshot);
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

    /**
     * A field post has a durable source identity, lifecycle and module set.
     * The ground plate is its exact interaction footprint; the raised shapes
     * below are legibility-only fixtures carrying that same subject identity.
     * They deliberately introduce no writable module state in Minecraft.
     */
    private static void addFieldPost(Map<String, Desired> result, String revision, ReferenceGrayboxSnapshot.FieldPost post) {
        String subject = "field-post:" + post.id();
        add(result, rectangle(subject, subject, "FIELD_POST", revision, post.rectangle(), 1, post.colour()));
        PostShape landmark = postLandmark(post);
        add(result, new Desired(subject + ":landmark", subject, "FIELD_POST_LANDMARK", revision,
                post.rectangle().x() + landmark.localX(), ACTIVITY_SCENE_Y, post.rectangle().z() + landmark.localZ(),
                landmark.width(), landmark.depth(), landmark.height(), post.colour()));
        for (String module : post.modules()) {
            PostShape shape = moduleShape(module);
            add(result, new Desired(subject + ":module:" + module, subject, "FIELD_POST_MODULE", revision,
                    post.rectangle().x() + shape.localX(), ACTIVITY_SCENE_Y, post.rectangle().z() + shape.localZ(),
                    shape.width(), shape.depth(), shape.height(), moduleColour(post, module)));
        }
    }

    /**
     * A low silhouette makes operations visible before a player can read the
     * board: travelling units form a column, a campaign forms a camp, and a
     * V2 front campaign presents a short line.  The high phase marker remains
     * so the scene is still readable from the overview deck.
     */
    private static void addActivityScene(Map<String, Desired> result, String revision, ReferenceGrayboxSnapshot.Activity activity) {
        String prefix = "activity:" + activity.id();
        int x = activity.position().x();
        int z = activity.position().z();
        switch (activity.family()) {
            case "operation" -> {
                // Leave the centre open for the beacon: layer packing is a
                // collision recovery mechanism, not the way this source
                // silhouette is meant to acquire its shape.
                add(result, new Desired(prefix + ":scene:column-west", activity.id(), "ACTIVITY_OPERATION_COLUMN", revision,
                        x - 2, ACTIVITY_SCENE_Y, z, 2, 1, 1, activity.colour()));
                add(result, new Desired(prefix + ":scene:column-east", activity.id(), "ACTIVITY_OPERATION_COLUMN", revision,
                        x + 1, ACTIVITY_SCENE_Y, z, 2, 1, 1, activity.colour()));
                add(result, new Desired(prefix + ":scene:beacon", activity.id(), "ACTIVITY_BEACON", revision,
                        x, ACTIVITY_SCENE_Y, z, 1, 1, 5, activity.colour()));
            }
            case "field_campaign" -> {
                addActivityCampCorner(result, revision, activity, "north-west", x - 2, z - 2);
                addActivityCampCorner(result, revision, activity, "north-east", x + 2, z - 2);
                addActivityCampCorner(result, revision, activity, "south-west", x - 2, z + 2);
                addActivityCampCorner(result, revision, activity, "south-east", x + 2, z + 2);
                add(result, new Desired(prefix + ":scene:beacon", activity.id(), "ACTIVITY_BEACON", revision,
                        x, ACTIVITY_SCENE_Y, z, 1, 1, 4, activity.colour()));
            }
            case "front_campaign" -> {
                add(result, new Desired(prefix + ":scene:front-north", activity.id(), "ACTIVITY_FRONT_LINE", revision,
                        x, ACTIVITY_SCENE_Y, z - 2, 1, 2, 2, activity.colour()));
                add(result, new Desired(prefix + ":scene:front-south", activity.id(), "ACTIVITY_FRONT_LINE", revision,
                        x, ACTIVITY_SCENE_Y, z + 1, 1, 2, 2, activity.colour()));
                add(result, new Desired(prefix + ":scene:beacon", activity.id(), "ACTIVITY_BEACON", revision,
                        x, ACTIVITY_SCENE_Y, z, 1, 1, 5, activity.colour()));
            }
            default -> throw new IllegalStateException("unknown source graybox activity family: " + activity.family());
        }
        add(result, marker(prefix, activity.id(), "ACTIVITY", revision, x, ACTIVITY_Y, z, activity.colour()));
    }

    private static void addActivityCampCorner(Map<String, Desired> result, String revision, ReferenceGrayboxSnapshot.Activity activity,
                                               String corner, int x, int z) {
        add(result, new Desired("activity:" + activity.id() + ":scene:" + corner, activity.id(), "ACTIVITY_CAMPAIGN_CAMP", revision,
                x, ACTIVITY_SCENE_Y, z, 1, 1, 2, activity.colour()));
    }

    /** Shapes have a fixed slot budget inside the authoritative 12×12 post footprint. */
    private static PostShape postLandmark(ReferenceGrayboxSnapshot.FieldPost post) {
        return switch (post.kind()) {
            case "observation_post" -> new PostShape(5, 5, 2, 2, 5);
            case "checkpoint" -> new PostShape(3, 5, 6, 2, 2);
            case "strongpoint" -> new PostShape(4, 4, 4, 4, 3);
            case "forward_base" -> new PostShape(3, 5, 6, 3, 2);
            default -> throw new IllegalStateException("unknown source graybox field-post kind: " + post.kind());
        };
    }

    private static PostShape moduleShape(String module) {
        return switch (module) {
            case "depot" -> new PostShape(1, 1, 2, 2, 2);
            case "field_hospital" -> new PostShape(9, 1, 2, 2, 2);
            case "fire_support" -> new PostShape(9, 9, 2, 2, 4);
            case "decontamination" -> new PostShape(1, 9, 2, 2, 2);
            case "fortification" -> new PostShape(5, 1, 2, 2, 3);
            default -> throw new IllegalStateException("unknown source graybox field-post module: " + module);
        };
    }

    private static String moduleColour(ReferenceGrayboxSnapshot.FieldPost post, String module) {
        return switch (post.status()) {
            case "abandoned", "overrun", "dismantled" -> "post." + post.status();
            default -> "post.module." + module;
        };
    }

    private record PostShape(int localX, int localZ, int width, int depth, int height) { }

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
     * Projects source tissue as four independent, source-owned surface clumps.
     *
     * <p>The source cell remains the only infection value.  These clumps are
     * merely a readable physical contour: one quarter, half, three quarters
     * and a full six-by-six patch correspond to the current cell level.  The
     * separate provenance of each clump is intentional.  A player-caused
     * conflict in one clump remains a visible scar, while the other source
     * clumps may still grow or retreat normally.</p>
     */
    private static void addInfectionTissue(Map<String, Desired> result, ReferenceGrayboxSnapshot snapshot) {
        for (ReferenceGrayboxSnapshot.Cell cell : snapshot.cells()) {
            int clumps = infectionTissueClumps(cell);
            for (int index = 0; index < clumps; index++) {
                int localX = switch (index) {
                    case 0, 3 -> 5;
                    case 1, 2 -> 8;
                    default -> throw new IllegalStateException("invalid infection tissue clump index");
                };
                int localZ = switch (index) {
                    case 0, 1 -> 5;
                    case 2, 3 -> 8;
                    default -> throw new IllegalStateException("invalid infection tissue clump index");
                };
                String cellId = "cell:" + cell.x() + ":" + cell.y();
                add(result, new Desired("infection-tissue:" + cellId + ":" + index, cellId, "INFECTION_TISSUE",
                        snapshot.stateRevision(), cell.rectangle().x() + localX, INFECTION_TISSUE_Y,
                        cell.rectangle().z() + localZ, INFECTION_TISSUE_CLUMP_WIDTH, INFECTION_TISSUE_CLUMP_WIDTH, 1,
                        infectionTissueColour(cell)));
            }
        }
    }

    private static int infectionTissueClumps(ReferenceGrayboxSnapshot.Cell cell) {
        double infection = cell.infection();
        if (!Double.isFinite(infection) || infection < 0.0d) {
            throw new IllegalStateException("source cell infection is invalid: " + cell.x() + "," + cell.y());
        }
        if (infection < INFECTION_TISSUE_MINIMUM) return 0;
        return Math.min(INFECTION_TISSUE_CLUMPS, Math.max(1, (int) Math.ceil(infection * INFECTION_TISSUE_CLUMPS)));
    }

    private static String infectionTissueColour(ReferenceGrayboxSnapshot.Cell cell) {
        if (cell.infection() >= .70d) return cell.signal() > INFECTION_TISSUE_MINIMUM
                ? "infection.tissue.signal_severe" : "infection.tissue.severe";
        if (cell.infection() >= .28d) return cell.signal() > INFECTION_TISSUE_MINIMUM
                ? "infection.tissue.signal_active" : "infection.tissue.active";
        return "infection.tissue.trace";
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

    /** Distinct block silhouettes make hive infrastructure recognizable before a player can read its label. */
    private static int hiveSpireHeight(String kind) {
        return switch (kind) {
            case "core" -> 9;
            case "sporulator" -> 8;
            case "brood" -> 7;
            case "synapse" -> 6;
            case "digestive" -> 5;
            case "harvester" -> 4;
            default -> 5;
        };
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
