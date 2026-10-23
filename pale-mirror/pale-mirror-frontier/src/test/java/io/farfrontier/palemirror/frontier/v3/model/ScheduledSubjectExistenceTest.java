package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScheduledSubjectExistenceTest {
    @Test void valueChangesRetainExistenceButNestedJobRetirementStillRejectsUnchangedSchedule() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:subject-existence"), 77L));
        var before = ScheduledSubjectExistence.subjects(initial);
        var locations = new HashMap<>(initial.actorLocations());
        var actor = locations.keySet().iterator().next(); var location = locations.get(actor);
        var body = location.body();
        locations.put(actor, location.withBody(new BodyPosition(body.x() + 1, body.y(), body.z())));
        var moved = FrontierWorldState.duringUnpublishedTransition(() ->
                initial.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations)));
        assertSame(before, ScheduledSubjectExistence.subjects(moved));
        var site = new SubjectId("site:1-wheat-field");
        var events = ResourceSiteProcess.planPreparation(initial, ResourceSiteProcess.preparation(site, 4_000));
        var preparing = ResourceSiteProcess.reducePreparationStarted(initial, site,
                (ResourceSitePreparationStarted) events.getFirst().payload());
        var job = preparing.resourceSites().site(site).preparationWork().orElseThrow().id();
        var schedule = new ScheduledAction(new ScheduleId("schedule:nested-work"), new SimInstant(5_000), 0, job, "test", 1);
        assertTrue(ScheduledSubjectExistence.subjects(preparing).contains(job));
        var prepared = ResourceSiteProcess.reducePrepared(preparing, site, (ResourceSitePrepared) events.get(1).payload());
        assertFalse(ScheduledSubjectExistence.subjects(prepared).contains(job));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validateScheduledSubjects(prepared, List.of(schedule)));
        assertTrue(ScheduledSubjectExistence.subjects(preparing).contains(job), "branch rollback restores exact nested identity");
    }
}
