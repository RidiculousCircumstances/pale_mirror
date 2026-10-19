package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Optional;

/** Separate wire kind: settling an existing clock carries no permission to start an operation. */
final class ResourceSiteHarvestSuspensionCodec implements PayloadCodec {
    @Override public String type() { return "frontier.resource_site_harvest_work_suspended"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (ResourceSiteHarvestWorkSuspended) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            FrontierWorldPayloadCodecs.writeSubject(out, value.siteId());
            FrontierWorldPayloadCodecs.writeSubject(out, value.jobId());
            FrontierWorldPayloadCodecs.writeSubject(out, value.workerId());
            out.writeLong(value.epoch()); out.writeLong(value.layoutRevision()); out.writeLong(value.cellId().value());
            out.writeLong(value.atTick());
            WorkStateCodec.writeProgress(out, value.previous()); WorkStateCodec.writeProgress(out, Optional.of(value.next()));
            FrontierWorldPayloadCodecs.writeString(out, value.scheduleId().value()); out.writeLong(value.dueAt());
            out.writeBoolean(value.hotLeaseId().isPresent());
            if (value.hotLeaseId().isPresent()) FrontierWorldPayloadCodecs.writeString(out, value.hotLeaseId().orElseThrow().value());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ResourceSiteHarvestWorkSuspended(
                FrontierWorldPayloadCodecs.readSubject(in).value(), FrontierWorldPayloadCodecs.readSubject(in).value(),
                FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong(), in.readLong(),
                new ResourceFieldLayout.CellId(in.readLong()), in.readLong(),
                WorkStateCodec.readProgress(in), WorkStateCodec.readProgress(in).orElseThrow(),
                new ScheduleId(FrontierWorldPayloadCodecs.readString(in)), in.readLong(),
                in.readBoolean() ? Optional.of(new SceneLeaseId(FrontierWorldPayloadCodecs.readString(in))) : Optional.empty()));
    }
}
