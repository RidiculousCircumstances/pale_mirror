package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.materialization.JobState;
import io.farfrontier.palemirror.internal.materialization.MaterializationJob;
import io.farfrontier.palemirror.internal.materialization.MaterializationJobClass;
import io.farfrontier.palemirror.internal.materialization.MaterializationJobRegistry;
import io.farfrontier.palemirror.internal.materialization.MaterializationOperation;
import io.farfrontier.palemirror.internal.materialization.MaterializationOperationType;
import io.farfrontier.palemirror.internal.materialization.MaterializationReceipt;
import io.farfrontier.palemirror.internal.materialization.OperationState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Schema-v39 codec for the one global physical-work registry. */
final class MaterializationJobCodec {
    private MaterializationJobCodec() { }

    static void write(CompoundTag root, MaterializationJobRegistry registry) {
        ListTag jobs = new ListTag();
        registry.jobs().forEach(job -> jobs.add(writeJob(job)));
        root.put("materializationJobs", jobs);
        ListTag receipts = new ListTag();
        registry.receipts().forEach(receipt -> {
            CompoundTag value = new CompoundTag();
            value.putString("id", receipt.jobId());
            value.putString("target", receipt.targetId());
            value.putString("channel", receipt.channel());
            value.putLong("desiredRevision", receipt.desiredRevision());
            value.putString("outcome", receipt.outcome().name());
            value.putString("diagnostic", receipt.diagnostic());
            receipts.add(value);
        });
        root.put("materializationReceipts", receipts);
        root.putLong("compactedMaterializationReceipts", registry.compactedReceiptCount());
    }

    static MaterializationJobRegistry read(CompoundTag root) {
        Map<String, MaterializationJob> jobs = new LinkedHashMap<>();
        for (Tag raw : root.getList("materializationJobs", Tag.TAG_COMPOUND)) {
            MaterializationJob job = readJob((CompoundTag) raw);
            if (jobs.putIfAbsent(job.jobId(), job) != null) {
                throw new IllegalStateException("Duplicate persisted materialization job " + job.jobId());
            }
        }
        Map<String, MaterializationReceipt> receipts = new LinkedHashMap<>();
        for (Tag raw : root.getList("materializationReceipts", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            MaterializationReceipt receipt = new MaterializationReceipt(value.getString("id"), value.getString("target"),
                    value.getString("channel"), value.getLong("desiredRevision"),
                    JobState.valueOf(value.getString("outcome")), value.getString("diagnostic"));
            if (receipts.putIfAbsent(receipt.jobId(), receipt) != null) {
                throw new IllegalStateException("Duplicate persisted materialization receipt " + receipt.jobId());
            }
        }
        return new MaterializationJobRegistry(jobs, receipts, root.getLong("compactedMaterializationReceipts"));
    }

    static CompoundTag writeJob(MaterializationJob job) {
        CompoundTag value = new CompoundTag();
        value.putString("id", job.jobId());
        value.putString("target", job.targetId());
        value.putString("channel", job.channel());
        value.putString("class", job.jobClass().name());
        value.putLong("desiredRevision", job.desiredRevision());
        value.putString("policy", job.policyId());
        value.putString("policyVersion", job.policyVersion());
        value.putString("state", job.state().name());
        value.putInt("attempts", job.attemptCount());
        value.putString("error", job.lastError());
        value.putInt("nextOperationIndex", job.nextOperationIndex());
        ListTag operations = new ListTag();
        job.operations().forEach(operation -> operations.add(writeOperation(operation)));
        value.put("operations", operations);
        return value;
    }

    static MaterializationJob readJob(CompoundTag value) {
        List<MaterializationOperation> operations = new ArrayList<>();
        for (Tag raw : value.getList("operations", Tag.TAG_COMPOUND)) operations.add(readOperation((CompoundTag) raw));
        return new MaterializationJob(value.getString("id"), value.getString("target"), value.getString("channel"),
                MaterializationJobClass.valueOf(value.getString("class")), value.getLong("desiredRevision"),
                value.getString("policy"), value.getString("policyVersion"), JobState.valueOf(value.getString("state")),
                operations, value.getInt("nextOperationIndex"), value.getInt("attempts"), value.getString("error"));
    }

    private static CompoundTag writeOperation(MaterializationOperation operation) {
        CompoundTag value = new CompoundTag();
        value.putString("id", operation.operationId());
        value.putString("key", operation.idempotencyKey());
        value.putString("type", operation.type().name());
        value.putString("target", operation.target());
        value.putString("state", operation.state().name());
        value.putInt("attempts", operation.attemptCount());
        value.putString("error", operation.lastError());
        return value;
    }

    private static MaterializationOperation readOperation(CompoundTag value) {
        return new MaterializationOperation(value.getString("id"), value.getString("key"),
                MaterializationOperationType.valueOf(value.getString("type")), value.getString("target"),
                OperationState.valueOf(value.getString("state")), value.getInt("attempts"), value.getString("error"));
    }
}
