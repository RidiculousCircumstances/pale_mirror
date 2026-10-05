package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

/** State persistence for exact and F0.3 fungible production holds. */
final class ProductionJobStateCodec {
    private ProductionJobStateCodec() { }

    static void write(DataOutputStream output, Map<SubjectId, ProductionJob> jobs) throws IOException {
        writeCount(output, jobs.size());
        for (ProductionJob job : jobs.values().stream().sorted(Comparator.comparing(ProductionJob::id)).toList()) {
            writeString(output, job.id().value()); writeString(output, job.taskId().value()); writeString(output, job.settlementId().value()); writeString(output, job.facilityId().value());
            writeString(output, job.workerId().value()); writeString(output, job.consumedItemId().value()); writeHold(output, job.inputHold()); writeString(output, job.outputItemId().value());
            writeString(output, job.outputItemKind()); output.writeByte(job.outputCount()); ProductionWorkProgressStateCodec.write(output, job.workProgress());
            TraversalTopologyStateCodec.write(output, job.workTraversal()); output.writeShort(job.traversalCursor());
            BakeryWorkStateCodec.write(output, job.bakeryWork()); StationApproachStateCodec.write(output, job.spatial());
        }
    }

    static Map<SubjectId, ProductionJob> read(DataInputStream input) throws IOException {
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId task = new SubjectId(readString(input)); SubjectId settlement = new SubjectId(readString(input)); SubjectId facility = new
                    SubjectId(readString(input)); SubjectId worker = new SubjectId(readString(input));
            SubjectId consumed = new SubjectId(readString(input)); ProductionInputHold hold = readHold(input, consumed);
            SubjectId output = new SubjectId(readString(input)); String outputKind = readString(input); int outputCount = input.readUnsignedByte();
            ProductionWorkProgress progress = ProductionWorkProgressStateCodec.read(input); TraversalTopology traversal = TraversalTopologyStateCodec.read(input);
            int cursor = input.readUnsignedShort();
            ProductionJob job = new ProductionJob(id, task, settlement, facility, worker, consumed, hold, output, outputKind, outputCount,
                    progress, traversal, cursor, BakeryWorkStateCodec.read(input), StationApproachStateCodec.read(input));
            if (jobs.put(id, job) != null) throw new IllegalArgumentException("duplicate production job id");
        }
        return jobs;
    }

    static void writeHold(DataOutputStream output, ProductionInputHold hold) throws IOException {
        if (hold instanceof ProductionInputHold.Materialized) { output.writeByte(0); return; }
        if (hold instanceof ProductionInputHold.Cold cold) {
            ExactItemStack item = cold.item(); output.writeByte(1); writeString(output, item.economicOwnerId().value()); writeString(output, item.itemKind()); output.writeByte(item.count()); writeCustody(output, item.custody());
        } else if (hold instanceof ProductionInputHold.FungibleCold cold) {
            output.writeByte(4); writeString(output, cold.accountId().value()); writeString(output, cold.claimId().value());
            writeInputLots(output, cold.inputLots());
        } else if (hold instanceof ProductionInputHold.FungibleBound bound) {
            output.writeByte(5); writeString(output, bound.accountId().value()); writeString(output, bound.claimId().value()); output.writeLong(bound.authorityEpoch());
            writeInputLots(output, bound.inputLots());
        } else throw new IllegalArgumentException("unknown production input hold");
    }

    static ProductionInputHold readHold(DataInputStream input, SubjectId itemId) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new ProductionInputHold.Materialized(itemId);
            case 1 -> new ProductionInputHold.Cold(new ExactItemStack(itemId, new SubjectId(readString(input)), readString(input), input.readUnsignedByte(), readCustody(input)));
            case 2 -> new ProductionInputHold.FungibleCold(itemId, new SubjectId(readString(input)), new SubjectId(readString(input)));
            case 3 -> new ProductionInputHold.FungibleBound(itemId, new SubjectId(readString(input)), new SubjectId(readString(input)), input.readLong());
            case 4 -> new ProductionInputHold.FungibleCold(itemId, new SubjectId(readString(input)), new SubjectId(readString(input)), readInputLots(input));
            case 5 -> new ProductionInputHold.FungibleBound(itemId, new SubjectId(readString(input)), new SubjectId(readString(input)), input.readLong(), readInputLots(input));
            default -> throw new IllegalArgumentException("unknown production input hold");
        };
    }

    private static void writeInputLots(DataOutputStream output, Map<SubjectId, Integer> lots) throws IOException {
        writeCount(output, lots.size());
        for (var entry : lots.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value()); output.writeByte(entry.getValue());
        }
    }

    private static Map<SubjectId, Integer> readInputLots(DataInputStream input) throws IOException {
        int count = readCount(input);
        if (count < 1 || count > 64) throw new IllegalArgumentException("invalid production input lot count");
        Map<SubjectId, Integer> lots = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            if (lots.put(new SubjectId(readString(input)), input.readUnsignedByte()) != null) {
                throw new IllegalArgumentException("duplicate production input lot");
            }
        }
        return lots;
    }
}
