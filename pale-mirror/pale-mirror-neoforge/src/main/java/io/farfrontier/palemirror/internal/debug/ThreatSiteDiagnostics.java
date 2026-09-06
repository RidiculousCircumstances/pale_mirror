package io.farfrontier.palemirror.internal.debug;

import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;

/** Read-only formatting kept out of the server-thread coordinator. */
public final class ThreatSiteDiagnostics {
    private ThreatSiteDiagnostics() { }
    public static String inspect(PaleMirrorSavedData data, String objectId) {
        var mine = data.testMines().get(new WorldObjectId(objectId));
        if (mine == null) return "Unknown PM world object " + objectId;
        var activeJob = data.materializationJobs().activeFor(mine.id().value(), "threat").orElse(null);
        String job = activeJob == null ? "none" : activeJob.jobId() + ":" + activeJob.state()
                + ":op=" + activeJob.nextOperationIndex();
        String source = data.worldState().facility(mine.id()).map(value -> value.infectionSource().value()).orElse("missing");
        return "object=" + mine.id().value() + ", source=" + source + ", lifecycle=" + mine.object().lifecycle()
                + ", anchor=" + (mine.anchorId() == null ? "none" : mine.anchorId())
                + ", encounter=" + mine.encounter().state() + ":" + mine.encounter().profileId()
                + (mine.encounter().diagnostic().isBlank() ? "" : " (" + mine.encounter().diagnostic() + ")")
                + ", job=" + job;
    }
}
