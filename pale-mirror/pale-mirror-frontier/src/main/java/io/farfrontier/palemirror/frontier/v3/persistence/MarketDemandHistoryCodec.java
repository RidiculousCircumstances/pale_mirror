package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketDemandHistory;
import java.io.*;
import java.util.*;

/** Snapshot grammar for bounded funded-demand intervals, separate from completed sales. */
final class MarketDemandHistoryCodec {
    static void write(DataOutputStream out, MarketDemandHistory history) throws IOException {
        out.writeInt(history.observations().size());
        for (var entry : history.observations().entrySet().stream().sorted(Comparator
                .comparing((Map.Entry<MarketDemandHistory.Key, MarketDemandHistory.Observation> e) -> e.getKey().buyer())
                .thenComparing(e -> e.getKey().item())).toList()) {
            out.writeUTF(entry.getKey().buyer().value()); out.writeUTF(entry.getKey().item());
            var value = entry.getValue(); out.writeLong(value.firstTick()); out.writeLong(value.lastTick());
            out.writeLong(value.bucketTicks()); out.writeInt(value.samples().size());
            for (var sample : value.samples()) {
                out.writeLong(sample.bucket()); out.writeLong(sample.quantity()); out.writeLong(sample.price());
            }
        }
    }
    static MarketDemandHistory read(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MarketDemandHistory.MAX_ENTRIES) throw new IllegalArgumentException("invalid demand history count");
        var values = new HashMap<MarketDemandHistory.Key, MarketDemandHistory.Observation>();
        for (int i = 0; i < count; i++) {
            var key = new MarketDemandHistory.Key(new SubjectId(in.readUTF()), in.readUTF());
            long first = in.readLong(), last = in.readLong(), width = in.readLong(); int size = in.readInt();
            if (size < 1 || size > MarketDemandHistory.WINDOW_BUCKETS + 1) throw new IllegalArgumentException("invalid demand window size");
            var samples = new ArrayList<MarketDemandHistory.Sample>();
            for (int j = 0; j < size; j++) samples.add(new MarketDemandHistory.Sample(in.readLong(), in.readLong(), in.readLong()));
            var value = new MarketDemandHistory.Observation(first, last, width, samples);
            if (values.put(key, value) != null) throw new IllegalArgumentException("duplicate demand observation");
        }
        return new MarketDemandHistory(values);
    }
    private MarketDemandHistoryCodec() { }
}
