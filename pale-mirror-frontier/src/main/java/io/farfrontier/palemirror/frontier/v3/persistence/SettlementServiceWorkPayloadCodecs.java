package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWork;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkStarted;

import java.util.List;

/** Stable WAL codecs owned by the settlement-service-work process. */
final class SettlementServiceWorkPayloadCodecs {
    private SettlementServiceWorkPayloadCodecs() { }

    static PayloadCodecs codecs() { return new PayloadCodecs(List.of(new Started())); }

    private static final class Started implements PayloadCodec {
        @Override public String type() { return "frontier.settlement_service_work_started"; }

        @Override public byte[] encode(FrontierPayload payload) {
            SettlementServiceWorkStarted started = (SettlementServiceWorkStarted) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                FrontierWorldPayloadCodecs.writeSubject(output, started.taskId());
                SettlementServiceWorkStateCodec.writeOne(output, started.work());
                PhysicalIntentPayloadCodec.write(output, started.inputIssueIntent());
                PhysicalIntentPayloadCodec.write(output, started.endpointIntent());
            });
        }

        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId task = FrontierWorldPayloadCodecs.readSubject(input).value();
                SettlementServiceWork work = SettlementServiceWorkStateCodec.readOne(input);
                PhysicalIntent inputIssue = PhysicalIntentPayloadCodec.read(input);
                PhysicalIntent endpoint = PhysicalIntentPayloadCodec.read(input);
                return new SettlementServiceWorkStarted(task, work, inputIssue, endpoint);
            });
        }
    }
}
