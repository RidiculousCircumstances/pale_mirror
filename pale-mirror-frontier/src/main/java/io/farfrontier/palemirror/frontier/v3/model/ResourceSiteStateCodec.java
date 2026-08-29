package io.farfrontier.palemirror.frontier.v3.model;

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
            FrontierWorldStateCodec.writeString(output, lifecycle.siteId().value()); output.writeByte(lifecycle.phase().ordinal());
            output.writeLong(lifecycle.growthEpoch()); output.writeByte(lifecycle.growthStage());
            output.writeBoolean(lifecycle.activeWork().isPresent());
            if (lifecycle.activeWork().isPresent()) writeWork(output, lifecycle.activeWork().orElseThrow());
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
            ResourceSiteLifecycle lifecycle = new ResourceSiteLifecycle(siteId, ResourceSitePhase.values()[phase], epoch, stage, work);
            if (sites.put(siteId, lifecycle) != null) throw new IllegalArgumentException("duplicate resource-site lifecycle");
        }
        return new ResourceSiteState(sites);
    }

    private static void writeWork(DataOutputStream output, ResourceSiteWork work) throws IOException {
        if (work instanceof ResourceSitePreparationJob preparation) {
            output.writeByte(0); writeIdentity(output, preparation); return;
        }
        if (work instanceof ResourceSiteHarvestJob harvest) {
            output.writeByte(1); writeIdentity(output, harvest); FrontierWorldStateCodec.writeString(output, harvest.taskId().value());
            FrontierWorldStateCodec.writeString(output, harvest.workerId().value());
            FrontierWorldStateCodec.writeString(output, harvest.outputItemId().value()); FrontierWorldStateCodec.writeCustody(output, harvest.outputSlot()); return;
        }
        throw new IllegalArgumentException("unknown resource-site work type");
    }

    private static ResourceSiteWork readWork(DataInputStream input) throws IOException {
        int kind = input.readUnsignedByte(); SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId site = new SubjectId(FrontierWorldStateCodec.readString(input)); PhysicalIntentId intent = new PhysicalIntentId(FrontierWorldStateCodec.readString(input));
        return switch (kind) {
            case 0 -> new ResourceSitePreparationJob(id, site, intent);
            case 1 -> new ResourceSiteHarvestJob(id, new SubjectId(FrontierWorldStateCodec.readString(input)), site, new SubjectId(FrontierWorldStateCodec.readString(input)),
                    new SubjectId(FrontierWorldStateCodec.readString(input)), readOutputSlot(input), intent);
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
