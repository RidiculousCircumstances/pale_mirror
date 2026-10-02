package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Fail-closed snapshot fragment for bounded resource-site lifecycle and active physical work. */
final class ResourceSiteStateCodec {
    private static final int MAX_SITES = 64;
    private ResourceSiteStateCodec() { }

    static void write(DataOutputStream output, ResourceSiteState state) throws IOException {
        if (state.sites().size() > MAX_SITES) throw new IllegalArgumentException("resource-site retention limit exceeded");
        output.writeByte(state.sites().size());
        for (ResourceSiteLifecycle lifecycle : state.sites().values().stream().sorted(Comparator.comparing(ResourceSiteLifecycle::siteId)).toList()) {
            FrontierWorldStateCodec.writeString(output, lifecycle.siteId().value()); output.writeByte(lifecycle.phase().wireTag());
            output.writeLong(lifecycle.growthEpoch()); output.writeByte(lifecycle.growthStage());
            output.writeBoolean(lifecycle.preparationWork().isPresent());
            if (lifecycle.preparationWork().isPresent()) writeWork(output, lifecycle.preparationWork().orElseThrow());
            output.writeLong(lifecycle.harvestSequence());
            output.writeByte(lifecycle.harvestJobs().size());
            for (var job : lifecycle.harvestJobs().values().stream()
                    .sorted(Comparator.comparing(ResourceSiteHarvestJob::id)).toList()) writeWork(output, job);
            output.writeBoolean(lifecycle.conflictDisposition().isPresent());
            if (lifecycle.conflictDisposition().isPresent()) {
                ResourceSiteConflictDisposition disposition = lifecycle.conflictDisposition().orElseThrow();
                output.writeInt(disposition.position().x()); output.writeInt(disposition.position().y()); output.writeInt(disposition.position().z());
                output.writeByte(disposition.reason().wireTag()); output.writeByte(disposition.policy().wireTag());
                writeIncident(output, disposition.incident());
            }
            output.writeByte(lifecycle.harvestLineages().size());
            for (var lineage : lifecycle.harvestLineages().values().stream()
                    .sorted(Comparator.comparing(ResourceSiteHarvestLineage::predecessorIntentId)).toList())
                writeHarvestLineage(output, lineage);
            ResourceFieldCycleStateCodec.write(output, state.cycle(lifecycle.siteId()));
            ResourceFieldCellObserved held = state.pendingWorldChange(lifecycle.siteId());
            output.writeBoolean(held != null);
            if (held != null) {
                byte[] encoded = ResourceSitePayloadCodecs.cellObserved().encode(held);
                output.writeInt(encoded.length); output.write(encoded);
            }
            ResourceFieldForeignChangeHeld foreign = state.pendingForeignChange(lifecycle.siteId());
            output.writeBoolean(foreign != null);
            if (foreign != null) {
                byte[] encoded = ResourceSitePayloadCodecs.foreignChangeHeld().encode(foreign);
                output.writeInt(encoded.length); output.write(encoded);
            }
        }
    }

    static ResourceSiteState read(DataInputStream input) throws IOException {
        int count = input.readUnsignedByte(); if (count > MAX_SITES) throw new IllegalArgumentException("resource-site retention limit exceeded");
        Map<SubjectId, ResourceSiteLifecycle> sites = new LinkedHashMap<>();
        Map<SubjectId, ResourceFieldCycle> cycles = new LinkedHashMap<>();
        Map<SubjectId, ResourceFieldCellObserved> heldWorldChanges = new LinkedHashMap<>();
        Map<SubjectId, ResourceFieldForeignChangeHeld> heldForeignChanges = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            SubjectId siteId = new SubjectId(FrontierWorldStateCodec.readString(input)); int phase = input.readUnsignedByte();
            long epoch = input.readLong(); int stage = input.readUnsignedByte(); boolean hasWork = input.readBoolean();
            if (phase >= ResourceSitePhase.values().length) throw new IllegalArgumentException("unknown resource-site phase");
            Optional<ResourceSitePreparationJob> work = Optional.empty();
            if (hasWork) {
                ResourceSiteWork decoded = readWork(input);
                if (!(decoded instanceof ResourceSitePreparationJob preparation))
                    throw new IllegalArgumentException("preparation slot contains a foreign work type");
                work = Optional.of(preparation);
            }
            long sequence = input.readLong();
            int jobCount = input.readUnsignedByte();
            if (jobCount > ResourceSiteLifecycle.MAX_HARVEST_WORKERS)
                throw new IllegalArgumentException("field execution retention limit exceeded");
            Map<SubjectId, ResourceSiteHarvestJob> jobs = new LinkedHashMap<>();
            for (int worker = 0; worker < jobCount; worker++) {
                ResourceSiteWork decoded = readWork(input);
                if (!(decoded instanceof ResourceSiteHarvestJob job) || jobs.put(job.id(), job) != null)
                    throw new IllegalArgumentException("field execution has a foreign type or duplicate identity");
            }
            Optional<ResourceSiteConflictDisposition> disposition = Optional.empty();
            if (input.readBoolean()) {
                BlockPosition position = new BlockPosition(input.readInt(), input.readInt(), input.readInt());
                ResourceSiteConflictReason reason = FrontierWireTags.require(ResourceSiteConflictReason.class, input.readUnsignedByte());
                ResourceSiteConflictPolicy policy = FrontierWireTags.require(ResourceSiteConflictPolicy.class, input.readUnsignedByte());
                disposition = Optional.of(new ResourceSiteConflictDisposition(position, reason, policy, readIncident(input)));
            }
            int historyCount = input.readUnsignedByte();
            if (historyCount > ResourceSiteLifecycle.MAX_HARVEST_WORKERS * 2)
                throw new IllegalArgumentException("field history retention limit exceeded");
            Map<PhysicalIntentId, ResourceSiteHarvestLineage> histories = new LinkedHashMap<>();
            for (int history = 0; history < historyCount; history++) {
                var lineage = readHarvestLineage(input);
                if (histories.put(lineage.predecessorIntentId(), lineage) != null)
                    throw new IllegalArgumentException("duplicate field history intent");
            }
            ResourceSiteLifecycle lifecycle = new ResourceSiteLifecycle(siteId, FrontierWireTags.require(ResourceSitePhase.class, phase),
                    epoch, stage, work, disposition, jobs, histories, sequence);
            if (sites.put(siteId, lifecycle) != null) throw new IllegalArgumentException("duplicate resource-site lifecycle");
            cycles.put(siteId, ResourceFieldCycleStateCodec.read(input));
            if (input.readBoolean()) {
                int length = input.readInt();
                if (length < 1 || length > 600) throw new IllegalArgumentException("invalid held world field change length");
                ResourceFieldCellObserved held = (ResourceFieldCellObserved) ResourceSitePayloadCodecs.cellObserved()
                        .decode(input.readNBytes(length));
                if (held.source() != ResourceFieldCellObserved.Source.WORLD || !siteId.equals(held.siteId()))
                    throw new IllegalArgumentException("held world field change has a foreign owner or source");
                heldWorldChanges.put(siteId, held);
            }
            if (input.readBoolean()) {
                int length = input.readInt();
                if (length < 1 || length > 600) throw new IllegalArgumentException("invalid held foreign field change length");
                var held = (ResourceFieldForeignChangeHeld) ResourceSitePayloadCodecs.foreignChangeHeld()
                        .decode(input.readNBytes(length));
                if (!siteId.equals(held.siteId()))
                    throw new IllegalArgumentException("held foreign field change has a different site owner");
                heldForeignChanges.put(siteId, held);
            }
        }
        return new ResourceSiteState(sites, cycles, heldWorldChanges, heldForeignChanges);
    }

    private static void writeHarvestLineage(DataOutputStream output, ResourceSiteHarvestLineage lineage) throws IOException {
        FrontierWorldStateCodec.writeString(output, lineage.predecessorJobId().value());
        FrontierWorldStateCodec.writeString(output, lineage.predecessorTaskId().value());
        FrontierWorldStateCodec.writeString(output, lineage.workerId().value());
        FrontierWorldStateCodec.writeString(output, lineage.actorAccountId().value());
        FrontierWorldStateCodec.writeString(output, lineage.depotAccountId().value());
        FrontierWorldStateCodec.writeString(output, lineage.outputItemId().value());
        output.writeLong(lineage.completedGrowthEpoch());
        output.writeInt(lineage.terminalBody().x()); output.writeInt(lineage.terminalBody().y()); output.writeInt(lineage.terminalBody().z());
        FrontierWorldStateCodec.writeString(output, lineage.predecessorIntentId().value());
        FrontierWorldStateCodec.writeCustody(output, lineage.outputSlot());
        output.writeBoolean(lineage.outputReceiptResolved());
        output.writeBoolean(lineage.successorTaskId().isPresent());
        if (lineage.successorTaskId().isPresent()) {
            FrontierWorldStateCodec.writeString(output, lineage.successorTaskId().orElseThrow().value());
            FrontierWorldStateCodec.writeString(output, lineage.successorJobId().orElseThrow().value());
        }
        ResourceSiteHarvestCausality causality = lineage.causality();
        FrontierWorldStateCodec.writeString(output, causality.coldScheduleId()); output.writeLong(causality.coldDueAt());
        output.writeByte(causality.hotLeaseIds().size());
        for (var lease : causality.hotLeaseIds()) FrontierWorldStateCodec.writeString(output, lease.value());
        FrontierWorldStateCodec.writeString(output, causality.expectedPhysical());
        FrontierWorldStateCodec.writeString(output, causality.observedPhysical());
        FrontierWorldStateCodec.writeString(output, causality.reconciliation());
        RetainedDiagnosticTrace trace = causality.trace();
        FrontierWorldStateCodec.writeString(output, trace.correlation()); FrontierWorldStateCodec.writeString(output, trace.driver());
        FrontierWorldStateCodec.writeString(output, trace.coldCommandId()); FrontierWorldStateCodec.writeString(output, trace.coldEventId());
        output.writeLong(trace.coldRevision()); output.writeLong(trace.coldInstant()); FrontierWorldStateCodec.writeString(output, trace.observationId());
        FrontierWorldStateCodec.writeString(output, trace.observationCommandId()); FrontierWorldStateCodec.writeString(output, trace.observationEventId());
        output.writeLong(trace.observationRevision()); output.writeLong(trace.observationInstant());
    }

    private static ResourceSiteHarvestLineage readHarvestLineage(DataInputStream input) throws IOException {
        SubjectId predecessorJob = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId predecessorTask = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId worker = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId actorAccount = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId depotAccount = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId output = new SubjectId(FrontierWorldStateCodec.readString(input));
        long epoch = input.readLong();
        BodyPosition terminal = new BodyPosition(input.readInt(), input.readInt(), input.readInt());
        PhysicalIntentId intent = new PhysicalIntentId(FrontierWorldStateCodec.readString(input));
        InventoryCustody.ContainerSlot slot = readOutputSlot(input); boolean confirmed = input.readBoolean();
        Optional<SubjectId> successorTask = Optional.empty(); Optional<SubjectId> successorJob = Optional.empty();
        if (input.readBoolean()) {
            successorTask = Optional.of(new SubjectId(FrontierWorldStateCodec.readString(input)));
            successorJob = Optional.of(new SubjectId(FrontierWorldStateCodec.readString(input)));
        }
        String schedule = FrontierWorldStateCodec.readString(input); long due = input.readLong(); int leaseCount = input.readUnsignedByte();
        if (leaseCount > 32) throw new IllegalArgumentException("resource-site harvest HOT lease retention limit exceeded");
        java.util.List<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId> leases = new java.util.ArrayList<>();
        for (int index = 0; index < leaseCount; index++) leases.add(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(FrontierWorldStateCodec.readString(input)));
        String expected = FrontierWorldStateCodec.readString(input), observed = FrontierWorldStateCodec.readString(input), reconciliation = FrontierWorldStateCodec.readString(input);
        RetainedDiagnosticTrace trace = new RetainedDiagnosticTrace(FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input),
                FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input), input.readLong(), input.readLong(),
                FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input), input.readLong(), input.readLong());
        ResourceSiteHarvestCausality causality = new ResourceSiteHarvestCausality(schedule, due, leases, intent, expected, observed, reconciliation, trace);
        return new ResourceSiteHarvestLineage(predecessorJob, predecessorTask, worker, actorAccount, depotAccount,
                output, epoch, terminal, intent, slot, confirmed,
                successorTask, successorJob, causality);
    }

    private static void writeIncident(DataOutputStream output, ConflictIncident incident) throws IOException {
        FrontierWorldStateCodec.writeString(output, incident.id());
        output.writeShort(incident.diagnostic().reason().wireTag()); output.writeByte(incident.diagnostic().category().wireTag());
        output.writeByte(DiagnosticWireTags.ownerTag(incident.diagnostic().owner().kind())); FrontierWorldStateCodec.writeString(output, incident.ownerId().value());
        output.writeByte(DiagnosticWireTags.subjectTag(incident.diagnostic().subject().kind())); FrontierWorldStateCodec.writeString(output, incident.subjectId().value());
        output.writeByte(incident.diagnostic().disposition().wireTag()); FrontierWorldStateCodec.writeString(output, incident.source());
        FrontierWorldStateCodec.writeString(output, incident.expectedFact()); FrontierWorldStateCodec.writeString(output, incident.observedFact());
        FrontierWorldStateCodec.writeString(output, incident.preCanonicalFact()); FrontierWorldStateCodec.writeString(output, incident.postCanonicalFact());
        FrontierWorldStateCodec.writeString(output, incident.traceCorrelation());
    }

    private static ConflictIncident readIncident(DataInputStream input) throws IOException {
        String id = FrontierWorldStateCodec.readString(input);
        DiagnosticReason reason = DiagnosticWireTags.reason(input.readUnsignedShort());
        DiagnosticCategory category = DiagnosticWireTags.category(input.readUnsignedByte());
        DiagnosticOwner owner = new DiagnosticOwner(DiagnosticWireTags.ownerKind(input.readUnsignedByte()), new SubjectId(FrontierWorldStateCodec.readString(input)));
        DiagnosticSubject subject = new DiagnosticSubject(DiagnosticWireTags.subjectKind(input.readUnsignedByte()), new SubjectId(FrontierWorldStateCodec.readString(input)));
        DiagnosticDisposition disposition = DiagnosticWireTags.disposition(input.readUnsignedByte());
        return new ConflictIncident(id, new DiagnosticTuple(reason, category, owner, subject, disposition), FrontierWorldStateCodec.readString(input),
                FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input),
                FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input));
    }

    private static void writeWork(DataOutputStream output, ResourceSiteWork work) throws IOException {
        if (work instanceof ResourceSitePreparationJob preparation) {
            output.writeByte(0); writeIdentity(output, preparation); return;
        }
        if (work instanceof ResourceSiteHarvestJob harvest) {
            output.writeByte(1); writeIdentity(output, harvest); FrontierWorldStateCodec.writeString(output, harvest.taskId().value());
            FrontierWorldStateCodec.writeString(output, harvest.workerId().value());
            FrontierWorldStateCodec.writeString(output, harvest.actorAccountId().value());
            FrontierWorldStateCodec.writeString(output, harvest.depotAccountId().value());
            FrontierWorldStateCodec.writeString(output, harvest.outputItemId().value()); FrontierWorldStateCodec.writeCustody(output, harvest.outputSlot());
            output.writeInt(harvest.progress().totalCropSlots()); output.writeInt(harvest.progress().completedCropSlots());
            output.writeInt(harvest.progress().pendingCropSlotIndex());
            output.writeInt(harvest.progress().selectedCropSlotIndex());
            output.writeInt(harvest.progress().lastCompletedCropSlotIndex());
            WorkStateCodec.writeProgress(output, harvest.progress().work());
            output.writeInt(harvest.deliveredYieldQuantity());
            output.writeInt(harvest.harvestedYieldQuantity());
            output.writeLong(harvest.target().layoutRevision());
            output.writeLong(harvest.target().cellId().value());
            output.writeLong(harvest.target().generation());
            output.writeBoolean(harvest.returningForBatch());
            output.writeBoolean(harvest.batchSuccessorSlot().isPresent());
            if (harvest.batchSuccessorSlot().isPresent())
                FrontierWorldStateCodec.writeCustody(output, harvest.batchSuccessorSlot().orElseThrow());
            output.writeBoolean(harvest.lastConfirmedBatch().isPresent());
            if (harvest.lastConfirmedBatch().isPresent()) {
                ResourceSiteHarvestBatchDelivered batch = harvest.lastConfirmedBatch().orElseThrow();
                output.writeInt(batch.deliveredYieldBefore());
                PhysicalEffectObservationPayloadCodec.write(output, batch.receipt());
            }
            output.writeBoolean(harvest.navigationBlock().isPresent());
            if (harvest.navigationBlock().isPresent()) {
                var blocked = harvest.navigationBlock().orElseThrow();
                output.writeInt(blocked.target().x()); output.writeInt(blocked.target().y()); output.writeInt(blocked.target().z());
                output.writeLong(blocked.layoutRevision());
                output.writeByte(blocked.reason().wireTag());
            }
            return;
        }
        throw new IllegalArgumentException("unknown resource-site work type");
    }

    private static ResourceSiteWork readWork(DataInputStream input) throws IOException {
        int kind = input.readUnsignedByte(); SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId site = new SubjectId(FrontierWorldStateCodec.readString(input)); PhysicalIntentId intent = new PhysicalIntentId(FrontierWorldStateCodec.readString(input));
        return switch (kind) {
            case 0 -> new ResourceSitePreparationJob(id, site, intent);
            case 1 -> readHarvestWork(input, id, site, intent);
            default -> throw new IllegalArgumentException("unknown resource-site work type");
        };
    }

    private static ResourceSiteHarvestJob readHarvestWork(DataInputStream input, SubjectId id, SubjectId site,
                                                          PhysicalIntentId intent) throws IOException {
        SubjectId task = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId worker = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId actor = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId depot = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId output = new SubjectId(FrontierWorldStateCodec.readString(input));
        InventoryCustody.ContainerSlot slot = readOutputSlot(input);
        ResourceSiteHarvestProgress progress = new ResourceSiteHarvestProgress(input.readInt(), input.readInt(), input.readInt(),
                input.readInt(), input.readInt(), WorkStateCodec.readProgress(input));
        int delivered = input.readInt(), harvested = input.readInt();
        var target = new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldWorkTarget(site,
                input.readLong(), new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout.CellId(input.readLong()), input.readLong());
        boolean returning = input.readBoolean();
        java.util.Optional<InventoryCustody.ContainerSlot> successorSlot = input.readBoolean()
                ? java.util.Optional.of(readOutputSlot(input)) : java.util.Optional.empty();
        java.util.Optional<ResourceSiteHarvestBatchDelivered> lastBatch = java.util.Optional.empty();
        if (input.readBoolean()) {
            int before = input.readInt();
            PhysicalEffectObservation observation = PhysicalEffectObservationPayloadCodec.read(input);
            if (!(observation instanceof ResourceSiteHarvestDeliveryObservation receipt))
                throw new IllegalArgumentException("field snapshot has a foreign batch receipt");
            lastBatch = java.util.Optional.of(new ResourceSiteHarvestBatchDelivered(receipt, before));
        }
        java.util.Optional<ResourceSiteHarvestNavigationBlock> navigationBlock = java.util.Optional.empty();
        if (input.readBoolean()) navigationBlock = java.util.Optional.of(new ResourceSiteHarvestNavigationBlock(
                SurfaceAnchor.at(input.readInt(), input.readInt(), input.readInt()),
                input.readLong(), ResourceSiteHarvestNavigationBlock.Reason.requireWireTag(input.readUnsignedByte())));
        return new ResourceSiteHarvestJob(id, task, site, worker, actor, depot, output, slot, intent,
                progress, delivered, returning,
                successorSlot, lastBatch, navigationBlock, harvested, target);
    }

    private static InventoryCustody.ContainerSlot readOutputSlot(DataInputStream input) throws IOException {
        InventoryCustody custody = FrontierWorldStateCodec.readCustody(input);
        if (custody instanceof InventoryCustody.ContainerSlot slot) return slot;
        throw new IllegalArgumentException("resource-site harvest output must target a container slot");
    }

    private static void writeIdentity(DataOutputStream output, ResourceSiteWork work) throws IOException {
        FrontierWorldStateCodec.writeString(output, work.id().value()); FrontierWorldStateCodec.writeString(output, work.siteId().value());
        FrontierWorldStateCodec.writeString(output, work.intentId().value());
    }
}
