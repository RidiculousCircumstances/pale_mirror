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
            output.writeBoolean(lifecycle.activeWork().isPresent());
            if (lifecycle.activeWork().isPresent()) writeWork(output, lifecycle.activeWork().orElseThrow());
            output.writeBoolean(lifecycle.conflictDisposition().isPresent());
            if (lifecycle.conflictDisposition().isPresent()) {
                ResourceSiteConflictDisposition disposition = lifecycle.conflictDisposition().orElseThrow();
                output.writeInt(disposition.position().x()); output.writeInt(disposition.position().y()); output.writeInt(disposition.position().z());
                output.writeByte(disposition.reason().wireTag()); output.writeByte(disposition.policy().wireTag());
                writeIncident(output, disposition.incident());
            }
            output.writeBoolean(lifecycle.harvestLineage().isPresent());
            if (lifecycle.harvestLineage().isPresent()) writeHarvestLineage(output, lifecycle.harvestLineage().orElseThrow());
        }
    }

    static ResourceSiteState read(DataInputStream input) throws IOException {
        int count = input.readUnsignedByte(); if (count > MAX_SITES) throw new IllegalArgumentException("resource-site retention limit exceeded");
        Map<SubjectId, ResourceSiteLifecycle> sites = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            SubjectId siteId = new SubjectId(FrontierWorldStateCodec.readString(input)); int phase = input.readUnsignedByte();
            long epoch = input.readLong(); int stage = input.readUnsignedByte(); boolean hasWork = input.readBoolean();
            if (phase >= ResourceSitePhase.values().length) throw new IllegalArgumentException("unknown resource-site phase");
            Optional<ResourceSiteWork> work = hasWork ? Optional.of(readWork(input)) : Optional.empty();
            Optional<ResourceSiteConflictDisposition> disposition = Optional.empty();
            if (input.readBoolean()) {
                BlockPosition position = new BlockPosition(input.readInt(), input.readInt(), input.readInt());
                ResourceSiteConflictReason reason = FrontierWireTags.require(ResourceSiteConflictReason.class, input.readUnsignedByte());
                ResourceSiteConflictPolicy policy = FrontierWireTags.require(ResourceSiteConflictPolicy.class, input.readUnsignedByte());
                disposition = Optional.of(new ResourceSiteConflictDisposition(position, reason, policy, readIncident(input)));
            }
            Optional<ResourceSiteHarvestLineage> lineage = input.readBoolean() ? Optional.of(readHarvestLineage(input)) : Optional.empty();
            ResourceSiteLifecycle lifecycle = new ResourceSiteLifecycle(siteId, FrontierWireTags.require(ResourceSitePhase.class, phase), epoch, stage, work, disposition, lineage);
            if (sites.put(siteId, lifecycle) != null) throw new IllegalArgumentException("duplicate resource-site lifecycle");
        }
        return new ResourceSiteState(sites);
    }

    private static void writeHarvestLineage(DataOutputStream output, ResourceSiteHarvestLineage lineage) throws IOException {
        FrontierWorldStateCodec.writeString(output, lineage.predecessorJobId().value());
        FrontierWorldStateCodec.writeString(output, lineage.predecessorTaskId().value());
        FrontierWorldStateCodec.writeString(output, lineage.workerId().value());
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
    }

    private static ResourceSiteHarvestLineage readHarvestLineage(DataInputStream input) throws IOException {
        SubjectId predecessorJob = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId predecessorTask = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId worker = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId output = new SubjectId(FrontierWorldStateCodec.readString(input));
        long epoch = input.readLong();
        BodyPosition terminal = new BodyPosition(input.readInt(), input.readInt(), input.readInt());
        PhysicalIntentId intent = new PhysicalIntentId(FrontierWorldStateCodec.readString(input));
        InventoryCustody.ContainerSlot slot = readOutputSlot(input); boolean confirmed = input.readBoolean();
        if (!input.readBoolean()) return new ResourceSiteHarvestLineage(predecessorJob, predecessorTask, worker, output, epoch, terminal, intent, slot, confirmed, Optional.empty(), Optional.empty());
        return new ResourceSiteHarvestLineage(predecessorJob, predecessorTask, worker, output, epoch, terminal, intent, slot, confirmed,
                Optional.of(new SubjectId(FrontierWorldStateCodec.readString(input))), Optional.of(new SubjectId(FrontierWorldStateCodec.readString(input))));
    }

    private static void writeIncident(DataOutputStream output, ConflictIncident incident) throws IOException {
        FrontierWorldStateCodec.writeString(output, incident.id()); output.writeByte(incident.category().wireTag());
        FrontierWorldStateCodec.writeString(output, incident.reason()); FrontierWorldStateCodec.writeString(output, incident.ownerId().value());
        FrontierWorldStateCodec.writeString(output, incident.subjectId().value()); FrontierWorldStateCodec.writeString(output, incident.source());
        FrontierWorldStateCodec.writeString(output, incident.expectedFact()); FrontierWorldStateCodec.writeString(output, incident.observedFact());
        FrontierWorldStateCodec.writeString(output, incident.preCanonicalFact()); FrontierWorldStateCodec.writeString(output, incident.postCanonicalFact());
        FrontierWorldStateCodec.writeString(output, incident.disposition()); FrontierWorldStateCodec.writeString(output, incident.traceCorrelation());
    }

    private static ConflictIncident readIncident(DataInputStream input) throws IOException {
        return new ConflictIncident(FrontierWorldStateCodec.readString(input),
                FrontierWireTags.require(ConflictIncidentCategory.class, input.readUnsignedByte()), FrontierWorldStateCodec.readString(input),
                new SubjectId(FrontierWorldStateCodec.readString(input)), new SubjectId(FrontierWorldStateCodec.readString(input)),
                FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input),
                FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input), FrontierWorldStateCodec.readString(input),
                FrontierWorldStateCodec.readString(input));
    }

    private static void writeWork(DataOutputStream output, ResourceSiteWork work) throws IOException {
        if (work instanceof ResourceSitePreparationJob preparation) {
            output.writeByte(0); writeIdentity(output, preparation); return;
        }
        if (work instanceof ResourceSiteHarvestJob harvest) {
            output.writeByte(1); writeIdentity(output, harvest); FrontierWorldStateCodec.writeString(output, harvest.taskId().value());
            FrontierWorldStateCodec.writeString(output, harvest.workerId().value());
            FrontierWorldStateCodec.writeString(output, harvest.outputItemId().value()); FrontierWorldStateCodec.writeCustody(output, harvest.outputSlot());
            output.writeByte(harvest.progress().completedCropSlots()); output.writeByte(harvest.progress().pendingCropSlotIndex());
            TraversalTopologyStateCodec.write(output, harvest.traversal()); output.writeShort(harvest.traversalCursor()); return;
        }
        throw new IllegalArgumentException("unknown resource-site work type");
    }

    private static ResourceSiteWork readWork(DataInputStream input) throws IOException {
        int kind = input.readUnsignedByte(); SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId site = new SubjectId(FrontierWorldStateCodec.readString(input)); PhysicalIntentId intent = new PhysicalIntentId(FrontierWorldStateCodec.readString(input));
        return switch (kind) {
            case 0 -> new ResourceSitePreparationJob(id, site, intent);
            case 1 -> new ResourceSiteHarvestJob(id, new SubjectId(FrontierWorldStateCodec.readString(input)), site, new SubjectId(FrontierWorldStateCodec.readString(input)),
                    new SubjectId(FrontierWorldStateCodec.readString(input)), readOutputSlot(input), intent,
                    new ResourceSiteHarvestProgress(input.readUnsignedByte(), input.readByte()), TraversalTopologyStateCodec.read(input), input.readUnsignedShort());
            default -> throw new IllegalArgumentException("unknown resource-site work type");
        };
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
