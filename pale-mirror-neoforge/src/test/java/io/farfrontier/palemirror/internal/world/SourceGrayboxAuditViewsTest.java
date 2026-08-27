package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceGrayboxAuditViewsTest {
    @Test
    void representativeViewsAreDeterministicBoundedAndPlayerEyeLevel() {
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxSimulation.create(7L).snapshot();

        List<SourceGrayboxAuditViews.View> first = SourceGrayboxAuditViews.from(snapshot);
        List<SourceGrayboxAuditViews.View> second = SourceGrayboxAuditViews.from(snapshot);

        assertEquals(first, second, "one immutable source frame must yield the same audit camera plan");
        assertTrue(first.size() >= 3 && first.size() <= 5,
                "every normal source frame must expose settlement, hive and route scenes without an unbounded camera cloud");
        assertTrue(first.stream().anyMatch(view -> view.kind().equals("GRAYBOX_SETTLEMENT")));
        assertTrue(first.stream().anyMatch(view -> view.kind().equals("GRAYBOX_HIVE_ORGAN")));
        assertTrue(first.stream().anyMatch(view -> view.kind().equals("GRAYBOX_ROUTE")));
        assertTrue(first.stream().allMatch(view -> view.y() == snapshot.bounds().groundY() + 1),
                "semantic cameras must begin at player feet, not from the old detached observation deck");
        assertTrue(first.stream().allMatch(view -> view.x() > snapshot.bounds().minX()
                        && view.x() < snapshot.bounds().minX() + snapshot.bounds().width() - 1
                        && view.z() > snapshot.bounds().minZ()
                        && view.z() < snapshot.bounds().minZ() + snapshot.bounds().depth() - 1),
                "a camera cannot be projected outside the finite source arena");
    }

    @Test
    void absentCampaignScenesAreNotInventedForTheAudit() {
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(17L).snapshot();
        ReferenceGrayboxSnapshot noCampaign = new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(),
                baseline.stateRevision(), baseline.bounds(), baseline.cells(), baseline.settlements(), baseline.facilities(),
                baseline.warehouses(), baseline.resourceSites(), baseline.routes(), baseline.hiveOrgans(), baseline.bioforms(),
                baseline.residents(), List.of(), baseline.fieldLinks(), List.of(), baseline.effects(), baseline.cargoes(),
                baseline.interactions(), baseline.sectors(), baseline.chrysalises(), baseline.readouts(), baseline.events());

        List<SourceGrayboxAuditViews.View> views = SourceGrayboxAuditViews.from(noCampaign);

        assertFalse(views.stream().anyMatch(view -> view.kind().equals("GRAYBOX_ACTIVITY")),
                "a camera plan must never imply a live operation when the canonical frame has none");
        assertFalse(views.stream().anyMatch(view -> view.kind().equals("GRAYBOX_FIELD_POST")),
                "a camera plan must never fabricate a field post for screenshot coverage");
    }

    @Test
    void liveCampaignFrameAddsOneOperationAndOnePostWithoutGrowingThePlan() {
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(41L).snapshot();
        ReferenceGrayboxSnapshot liveCampaign = new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(),
                baseline.stateRevision(), baseline.bounds(), baseline.cells(), baseline.settlements(), baseline.facilities(),
                baseline.warehouses(), baseline.resourceSites(), baseline.routes(), baseline.hiveOrgans(), baseline.bioforms(),
                baseline.residents(), List.of(new ReferenceGrayboxSnapshot.FieldPost(91, 3, "outpost", "active",
                        new io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout.Rectangle(20, 20, 8, 8),
                        0.75d, 6, 1, List.of("depot"), "field.post")), baseline.fieldLinks(),
                List.of(new ReferenceGrayboxSnapshot.Activity("operation:91", "operation", "containment", "engaging",
                        new io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout.Point(44, 44),
                        6.0d, 2.0d, false, "activity.operation"),
                        new ReferenceGrayboxSnapshot.Activity("operation:retired", "operation", "patrol", "complete",
                                new io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout.Point(48, 48),
                                20.0d, 20.0d, true, "activity.operation")), baseline.effects(), baseline.cargoes(),
                baseline.interactions(), baseline.sectors(), baseline.chrysalises(), baseline.readouts(), baseline.events());

        List<SourceGrayboxAuditViews.View> views = SourceGrayboxAuditViews.from(liveCampaign);

        assertEquals(5, views.size(), "the five current semantic scene categories must remain bounded to one view each");
        assertTrue(views.stream().anyMatch(view -> view.id().equals("graybox/activity/operation:91/scene")));
        assertTrue(views.stream().anyMatch(view -> view.id().equals("graybox/field-post/91/approach")));
        assertFalse(views.stream().anyMatch(view -> view.id().contains("retired")),
                "a terminal operation must not be visually revived as a live player scene");
    }
}
