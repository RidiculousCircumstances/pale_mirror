package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure bounded graybox geometry for source-owned facilities and hive organs.
 *
 * <p>This grammar deliberately derives only presentation claims from an
 * immutable snapshot. {@link SourceGrayboxPresentationPlan} retains sole
 * responsibility for registering those claims and resolving collisions.</p>
 */
final class SourceGrayboxInfrastructureGrammar {
    private static final int SURFACE_Y = ReferenceGrayboxLayout.GROUND_Y;
    private static final int HIVE_SPIRE_WIDTH = 4;
    private static final int HIVE_SPIRE_HEIGHT = 5;
    private static final int HIVE_ROOT_WIDTH = 2;
    private static final int HIVE_ROOT_LENGTH = 3;
    private static final int HIVE_CROWN_WIDTH = 7;
    private static final int HIVE_SIGNAL_HEIGHT = 2;
    private static final int FACILITY_FRAME_HEIGHT = 2;

    private SourceGrayboxInfrastructureGrammar() { }

    /**
     * One source organ uses one shared low, rooted graybox grammar. The
     * canonical organ kind is represented by source colour alone: the adapter
     * must not invent a biological capability from a block shape.
     */
    static List<SourceGrayboxPresentationPlan.Desired> hiveLandmark(String revision, ReferenceGrayboxSnapshot.HiveOrgan organ) {
        ReferenceGrayboxLayout.Rectangle area = organ.rectangle();
        String id = "hive-organ:" + organ.id();
        String subject = "organ:" + organ.id();
        int spireX = area.centreX() - HIVE_SPIRE_WIDTH / 2;
        int spireZ = area.centreZ() - HIVE_SPIRE_WIDTH / 2;
        int landmarkY = SURFACE_Y + 2;
        List<SourceGrayboxPresentationPlan.Desired> result = new ArrayList<>(7);
        result.add(new SourceGrayboxPresentationPlan.Desired(id + ":spire", subject, "HIVE_ORGAN_LANDMARK", revision,
                spireX, landmarkY, spireZ, HIVE_SPIRE_WIDTH, HIVE_SPIRE_WIDTH, HIVE_SPIRE_HEIGHT, organ.colour()));
        result.add(new SourceGrayboxPresentationPlan.Desired(id + ":root:north", subject, "HIVE_ORGAN_ROOT", revision,
                spireX + 1, landmarkY, area.z(), HIVE_ROOT_WIDTH, HIVE_ROOT_LENGTH, 2, organ.colour()));
        result.add(new SourceGrayboxPresentationPlan.Desired(id + ":root:south", subject, "HIVE_ORGAN_ROOT", revision,
                spireX + 1, landmarkY, area.z() + area.depth() - HIVE_ROOT_LENGTH, HIVE_ROOT_WIDTH, HIVE_ROOT_LENGTH, 2, organ.colour()));
        result.add(new SourceGrayboxPresentationPlan.Desired(id + ":root:west", subject, "HIVE_ORGAN_ROOT", revision,
                area.x(), landmarkY, spireZ + 1, HIVE_ROOT_LENGTH, HIVE_ROOT_WIDTH, 2, organ.colour()));
        result.add(new SourceGrayboxPresentationPlan.Desired(id + ":root:east", subject, "HIVE_ORGAN_ROOT", revision,
                area.x() + area.width() - HIVE_ROOT_LENGTH, landmarkY, spireZ + 1, HIVE_ROOT_LENGTH, HIVE_ROOT_WIDTH, 2, organ.colour()));
        int crownY = landmarkY + HIVE_SPIRE_HEIGHT;
        result.add(new SourceGrayboxPresentationPlan.Desired(id + ":crown", subject, "HIVE_ORGAN_LANDMARK", revision,
                area.centreX() - HIVE_CROWN_WIDTH / 2, crownY, area.centreZ() - HIVE_CROWN_WIDTH / 2,
                HIVE_CROWN_WIDTH, HIVE_CROWN_WIDTH, 1, organ.colour()));
        // The lamp adds night-time legibility, not a new simulation quantity.
        result.add(new SourceGrayboxPresentationPlan.Desired(id + ":signal", subject, "HIVE_ORGAN_SIGNAL", revision,
                spireX + 1, crownY + 1, spireZ + 1, HIVE_ROOT_WIDTH, HIVE_ROOT_WIDTH, HIVE_SIGNAL_HEIGHT, "hive.signal"));
        return List.copyOf(result);
    }

    /**
     * A uniform open facility frame makes the existing functional footprint
     * legible without an adapter-side facility taxonomy. Warehouse roofs stay
     * absent so exact source-owned barrel shelves remain inspectable.
     */
    static List<SourceGrayboxPresentationPlan.Desired> facilityFrame(String revision, ReferenceGrayboxSnapshot.Facility facility) {
        ReferenceGrayboxLayout.Rectangle area = facility.rectangle();
        String id = "facility:" + facility.id();
        List<SourceGrayboxPresentationPlan.Desired> result = new ArrayList<>(5);
        if (facility.kind().equals("fortification")) {
            addFacilityCorner(result, id, facility, revision, area.x(), area.z(), "north_west");
            addFacilityCorner(result, id, facility, revision, area.x() + area.width() - 2, area.z(), "north_east");
            addFacilityCorner(result, id, facility, revision, area.x(), area.z() + area.depth() - 2, "south_west");
            addFacilityCorner(result, id, facility, revision, area.x() + area.width() - 2, area.z() + area.depth() - 2, "south_east");
            return List.copyOf(result);
        }
        int frameY = SURFACE_Y + 1;
        result.add(new SourceGrayboxPresentationPlan.Desired(id + ":frame:north", facility.id(), "FACILITY_FRAME", revision,
                area.x(), frameY, area.z(), area.width(), 1, FACILITY_FRAME_HEIGHT, facility.colour()));
        result.add(new SourceGrayboxPresentationPlan.Desired(id + ":frame:south", facility.id(), "FACILITY_FRAME", revision,
                area.x(), frameY, area.z() + area.depth() - 1, area.width(), 1, FACILITY_FRAME_HEIGHT, facility.colour()));
        if (area.depth() > 2) {
            result.add(new SourceGrayboxPresentationPlan.Desired(id + ":frame:west", facility.id(), "FACILITY_FRAME", revision,
                    area.x(), frameY, area.z() + 1, 1, area.depth() - 2, FACILITY_FRAME_HEIGHT, facility.colour()));
            result.add(new SourceGrayboxPresentationPlan.Desired(id + ":frame:east", facility.id(), "FACILITY_FRAME", revision,
                    area.x() + area.width() - 1, frameY, area.z() + 1, 1, area.depth() - 2, FACILITY_FRAME_HEIGHT, facility.colour()));
        }
        if (!facility.kind().equals("warehouse")) {
            result.add(new SourceGrayboxPresentationPlan.Desired(id + ":frame:roof", facility.id(), "FACILITY_FRAME", revision,
                    area.x(), frameY + FACILITY_FRAME_HEIGHT, area.z(), area.width(), area.depth(), 1, facility.colour()));
        }
        return List.copyOf(result);
    }

    private static void addFacilityCorner(List<SourceGrayboxPresentationPlan.Desired> result, String id,
                                          ReferenceGrayboxSnapshot.Facility facility, String revision, int x, int z, String corner) {
        result.add(new SourceGrayboxPresentationPlan.Desired(id + ":corner:" + corner, facility.id(), "FACILITY_FORTIFICATION", revision,
                x, SURFACE_Y + 1, z, 2, 2, FACILITY_FRAME_HEIGHT + 1, facility.colour()));
    }
}
