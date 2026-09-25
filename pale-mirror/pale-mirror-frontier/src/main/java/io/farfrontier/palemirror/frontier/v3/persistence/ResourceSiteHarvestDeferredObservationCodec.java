package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestDeferredObservation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Shared bounded body for WAL and checkpoint representations of a deferred field receipt. */
final class ResourceSiteHarvestDeferredObservationCodec {
    private ResourceSiteHarvestDeferredObservationCodec() { }

    static void write(DataOutputStream output, ResourceSiteHarvestDeferredObservation value) throws IOException {
        FrontierWorldStateCodec.writeString(output, value.siteId().value());
        FrontierWorldStateCodec.writeString(output, value.jobId().value());
        FrontierWorldStateCodec.writeString(output, value.containerId().value());
        output.writeLong(value.completedGrowthEpoch());
        output.writeLong(value.custodyEpoch());
        output.writeLong(value.emittedCanonicalRevision());
        output.writeLong(value.replicaRevision());
        FrontierWorldStateCodec.writeString(output, value.depotFingerprint());
        FrontierWorldStateCodec.writeString(output, value.depotProvenance());
    }

    static ResourceSiteHarvestDeferredObservation read(DataInputStream input, PhysicalObservationId id,
                                                       PhysicalIntentId intent) throws IOException {
        return new ResourceSiteHarvestDeferredObservation(id, intent,
                new SubjectId(FrontierWorldStateCodec.readString(input)),
                new SubjectId(FrontierWorldStateCodec.readString(input)),
                new SubjectId(FrontierWorldStateCodec.readString(input)),
                input.readLong(), input.readLong(), input.readLong(), input.readLong(),
                FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input));
    }
}
