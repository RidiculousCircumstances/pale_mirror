package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BioformLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.BioformLifecyclePhase;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.HiveCocoonSlot;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Stable bounded snapshot section for exact bioform lifecycle and cocoon custody. */
final class BioformLifecycleStateCodec {
    private BioformLifecycleStateCodec() { }

    static void write(DataOutputStream output, Map<SubjectId, BioformLifecycle> lifecycles) throws IOException {
        FrontierWorldStateCodec.writeCount(output, lifecycles.size());
        for (Map.Entry<SubjectId, BioformLifecycle> entry : lifecycles.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value());
            BioformLifecycle lifecycle = entry.getValue();
            output.writeByte(lifecycle.phase().wireTag());
            output.writeBoolean(lifecycle.homeSlot().isPresent());
            if (lifecycle.homeSlot().isPresent()) {
                HiveCocoonSlot slot = lifecycle.homeSlot().orElseThrow();
                FrontierWorldStateCodec.writeString(output, slot.hibernaculumId().value());
                output.writeByte(slot.index());
            }
        }
    }

    static Map<SubjectId, BioformLifecycle> read(DataInputStream input) throws IOException {
        Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId bioform = new SubjectId(FrontierWorldStateCodec.readString(input));
            BioformLifecyclePhase phase = FrontierWireTags.require(BioformLifecyclePhase.class, input.readUnsignedByte());
            Optional<HiveCocoonSlot> home = input.readBoolean()
                    ? Optional.of(new HiveCocoonSlot(new SubjectId(FrontierWorldStateCodec.readString(input)), input.readUnsignedByte()))
                    : Optional.empty();
            if (lifecycles.put(bioform, new BioformLifecycle(phase, home)) != null) {
                throw new IllegalArgumentException("duplicate bioform lifecycle");
            }
        }
        return Map.copyOf(lifecycles);
    }
}
