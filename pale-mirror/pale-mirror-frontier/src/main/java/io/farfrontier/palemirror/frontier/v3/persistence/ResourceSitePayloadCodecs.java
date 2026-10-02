package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Payload codec registry fragment for the resource-site COLD process. */
final class ResourceSitePayloadCodecs {
    private ResourceSitePayloadCodecs() { }
    static PayloadCodec growthAdvanced() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_growth_advanced"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteGrowthAdvanced advanced = (ResourceSiteGrowthAdvanced) payload;
                byte[] site = advanced.siteId().value().getBytes(StandardCharsets.UTF_8);
                if (site.length == 0 || site.length > 255) throw new IllegalArgumentException("resource-site id encoding is invalid");
                return ByteBuffer.allocate(1 + site.length + Long.BYTES + Integer.BYTES).put((byte) site.length).put(site)
                        .putLong(advanced.growthEpoch()).putInt(advanced.growthStage()).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                if (bytes.length < 1 + Long.BYTES + Integer.BYTES) throw new IllegalArgumentException("truncated resource-site growth payload");
                ByteBuffer input = ByteBuffer.wrap(bytes); int length = Byte.toUnsignedInt(input.get());
                if (length == 0 || bytes.length != 1 + length + Long.BYTES + Integer.BYTES) throw new IllegalArgumentException("malformed resource-site growth payload");
                byte[] site = new byte[length]; input.get(site);
                return new ResourceSiteGrowthAdvanced(new SubjectId(new String(site, StandardCharsets.UTF_8)), input.getLong(), input.getInt());
            }
        };
    }
    static PayloadCodec preparationStarted() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_preparation_started"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSitePreparationJob job = ((ResourceSitePreparationStarted) payload).job();
                byte[] id = job.id().value().getBytes(StandardCharsets.UTF_8), site = job.siteId().value().getBytes(StandardCharsets.UTF_8), intent = job.intentId().value().getBytes(StandardCharsets.UTF_8);
                if (id.length > 255 || site.length > 255 || intent.length > 255) throw new IllegalArgumentException("resource-site preparation encoding is invalid");
                return ByteBuffer.allocate(3 + id.length + site.length + intent.length).put((byte) id.length).put(id).put((byte) site.length).put(site).put((byte) intent.length).put(intent).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes); if (input.remaining() < 3) throw new IllegalArgumentException("truncated resource-site preparation payload");
                String id = read(input), site = read(input), intent = read(input); if (input.hasRemaining()) throw new IllegalArgumentException("trailing resource-site preparation payload");
                return new ResourceSitePreparationStarted(new ResourceSitePreparationJob(new SubjectId(id), new SubjectId(site), new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(intent)));
            }
        };
    }
    static PayloadCodec harvestWorkChanged() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_work_changed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                var value = (ResourceSiteHarvestWorkChanged) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                    FrontierWorldPayloadCodecs.writeSubject(out, value.siteId());
                    FrontierWorldPayloadCodecs.writeSubject(out, value.jobId());
                    FrontierWorldPayloadCodecs.writeSubject(out, value.workerId());
                    out.writeLong(value.epoch()); out.writeLong(value.layoutRevision()); out.writeLong(value.cellId().value());
                    out.writeByte(value.operation().wireTag()); out.writeLong(value.atTick());
                    WorkStateCodec.writeProgress(out, value.previous()); WorkStateCodec.writeProgress(out, java.util.Optional.of(value.next()));
                    FrontierWorldPayloadCodecs.writeString(out, value.scheduleId().value()); out.writeLong(value.dueAt());
                    out.writeBoolean(value.hotLeaseId().isPresent());
                    if (value.hotLeaseId().isPresent()) FrontierWorldPayloadCodecs.writeString(out, value.hotLeaseId().orElseThrow().value());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ResourceSiteHarvestWorkChanged(
                        FrontierWorldPayloadCodecs.readSubject(in).value(), FrontierWorldPayloadCodecs.readSubject(in).value(),
                        FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong(), in.readLong(),
                        new ResourceFieldLayout.CellId(in.readLong()), WorkOperation.fromWireTag(in.readUnsignedByte()), in.readLong(),
                        WorkStateCodec.readProgress(in), WorkStateCodec.readProgress(in).orElseThrow(),
                        new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(FrontierWorldPayloadCodecs.readString(in)), in.readLong(),
                        in.readBoolean() ? java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(
                                FrontierWorldPayloadCodecs.readString(in))) : java.util.Optional.empty()));
            }
        };
    }
    static PayloadCodec harvestStarted() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_started"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestJob job = ((ResourceSiteHarvestStarted) payload).job();
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, job.id()); FrontierWorldPayloadCodecs.writeSubject(output, job.taskId());
                    FrontierWorldPayloadCodecs.writeSubject(output, job.siteId()); FrontierWorldPayloadCodecs.writeSubject(output, job.workerId());
                    FrontierWorldPayloadCodecs.writeSubject(output, job.actorAccountId());
                    FrontierWorldPayloadCodecs.writeSubject(output, job.depotAccountId());
                    FrontierWorldPayloadCodecs.writeSubject(output, job.outputItemId()); FrontierWorldPayloadCodecs.writeSubject(output, job.outputSlot().containerId());
                    output.writeByte(job.outputSlot().slot()); FrontierWorldPayloadCodecs.writeString(output, job.intentId().value());
                    output.writeInt(job.progress().totalCropSlots()); output.writeInt(job.progress().completedCropSlots());
                    output.writeInt(job.progress().pendingCropSlotIndex());
                    output.writeInt(job.progress().selectedCropSlotIndex());
                    output.writeInt(job.progress().lastCompletedCropSlotIndex());
                    WorkStateCodec.writeProgress(output, job.progress().work());
                    output.writeInt(job.deliveredYieldQuantity());
                    output.writeInt(job.harvestedYieldQuantity());
                    output.writeLong(job.target().layoutRevision());
                    output.writeLong(job.target().cellId().value());
                    output.writeLong(job.target().generation());
                    output.writeBoolean(job.returningForBatch());
                    output.writeBoolean(job.batchSuccessorSlot().isPresent());
                    if (job.batchSuccessorSlot().isPresent()) output.writeByte(job.batchSuccessorSlot().orElseThrow().slot());
                    output.writeBoolean(job.lastConfirmedBatch().isPresent());
                    if (job.lastConfirmedBatch().isPresent()) {
                        ResourceSiteHarvestBatchDelivered batch = job.lastConfirmedBatch().orElseThrow();
                        output.writeInt(batch.deliveredYieldBefore());
                        PhysicalEffectObservationPayloadCodec.write(output, batch.receipt());
                    }
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                    SubjectId id = FrontierWorldPayloadCodecs.readSubject(input).value(), task = FrontierWorldPayloadCodecs.readSubject(input).value();
                    SubjectId site = FrontierWorldPayloadCodecs.readSubject(input).value(), worker = FrontierWorldPayloadCodecs.readSubject(input).value();
                    SubjectId actorAccount = FrontierWorldPayloadCodecs.readSubject(input).value();
                    SubjectId depotAccount = FrontierWorldPayloadCodecs.readSubject(input).value();
                    SubjectId output = FrontierWorldPayloadCodecs.readSubject(input).value(), depot = FrontierWorldPayloadCodecs.readSubject(input).value();
                    int slot = input.readUnsignedByte(); String intent = FrontierWorldPayloadCodecs.readString(input);
                    int total = input.readInt(), completed = input.readInt(), pending = input.readInt();
                    int selected = input.readInt(), lastCompleted = input.readInt();
                    var workProgress = WorkStateCodec.readProgress(input);
                    int delivered = input.readInt(), harvested = input.readInt();
                    var target = new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldWorkTarget(site,
                            input.readLong(), new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout.CellId(input.readLong()), input.readLong());
                    boolean returningForBatch = input.readBoolean();
                    java.util.Optional<InventoryCustody.ContainerSlot> successorSlot = input.readBoolean()
                            ? java.util.Optional.of(new InventoryCustody.ContainerSlot(depot, input.readUnsignedByte()))
                            : java.util.Optional.empty();
                    java.util.Optional<ResourceSiteHarvestBatchDelivered> lastBatch = java.util.Optional.empty();
                    if (input.readBoolean()) {
                        int before = input.readInt();
                        PhysicalEffectObservation observation = PhysicalEffectObservationPayloadCodec.read(input);
                        if (!(observation instanceof ResourceSiteHarvestDeliveryObservation receipt))
                            throw new IllegalArgumentException("field start has a foreign batch receipt");
                        lastBatch = java.util.Optional.of(new ResourceSiteHarvestBatchDelivered(receipt, before));
                    }
                    return new ResourceSiteHarvestStarted(new ResourceSiteHarvestJob(id, task, site, worker, actorAccount, depotAccount, output,
                            new InventoryCustody.ContainerSlot(depot, slot), new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(intent),
                            new ResourceSiteHarvestProgress(total, completed, pending, selected, lastCompleted, workProgress), delivered,
                            returningForBatch, successorSlot, lastBatch, java.util.Optional.empty(), harvested, target));
                });
            }
        };
    }
    static PayloadCodec harvestColdTraversalAdvanced() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_cold_traversal_advanced"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestColdTraversalAdvanced advanced = (ResourceSiteHarvestColdTraversalAdvanced) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, advanced.jobId());
                    FrontierWorldPayloadCodecs.writeSubject(output, advanced.workerId());
                    output.writeInt(advanced.nextCursor());
                    FrontierWorldPayloadCodecs.writeString(output, advanced.coldScheduleId().value());
                    output.writeLong(advanced.coldDueAt());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                    SubjectId job = FrontierWorldPayloadCodecs.readSubject(input).value();
                    SubjectId worker = FrontierWorldPayloadCodecs.readSubject(input).value();
                    int nextCursor = input.readInt();
                    if (input.available() == 0)
                        throw new IllegalArgumentException("pre-return harvest traversal requires an explicit migration");
                    return new ResourceSiteHarvestColdTraversalAdvanced(job, worker, nextCursor,
                            new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(FrontierWorldPayloadCodecs.readString(input)),
                            input.readLong());
                });
            }
        };
    }
    static PayloadCodec harvestColdGoalAdvanced() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_cold_goal_advanced"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestColdGoalAdvanced advanced = (ResourceSiteHarvestColdGoalAdvanced) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, advanced.jobId());
                    FrontierWorldPayloadCodecs.writeSubject(output, advanced.workerId());
                    output.writeLong(advanced.layoutRevision());
                    output.writeInt(advanced.nextWorkSlot());
                    output.writeInt(advanced.kind().wireTag());
                    FrontierWorldPayloadCodecs.writeBody(output, advanced.nextBody());
                    FrontierWorldPayloadCodecs.writeString(output, advanced.coldScheduleId().value());
                    output.writeLong(advanced.coldDueAt());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                        new ResourceSiteHarvestColdGoalAdvanced(
                                FrontierWorldPayloadCodecs.readSubject(input).value(),
                                FrontierWorldPayloadCodecs.readSubject(input).value(),
                                input.readLong(), input.readInt(),
                                ResourceSiteHarvestGoal.Kind.requireWireTag(input.readInt()),
                                FrontierWorldPayloadCodecs.readBody(input),
                                new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(FrontierWorldPayloadCodecs.readString(input)),
                                input.readLong()));
            }
        };
    }
    static PayloadCodec harvestColdGoalHeld() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_cold_goal_held"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestColdGoalHeld held = (ResourceSiteHarvestColdGoalHeld) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, held.siteId());
                    FrontierWorldPayloadCodecs.writeSubject(output, held.jobId());
                    FrontierWorldPayloadCodecs.writeSubject(output, held.workerId());
                    output.writeLong(held.layoutRevision());
                    output.writeInt(held.nextWorkSlot());
                    output.writeInt(held.kind().wireTag());
                    output.writeInt(held.target().x()); output.writeInt(held.target().y()); output.writeInt(held.target().z());
                    output.writeByte(held.reason().wireTag());
                    FrontierWorldPayloadCodecs.writeString(output, held.coldScheduleId().value());
                    output.writeLong(held.coldDueAt());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResourceSiteHarvestColdGoalHeld(
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        input.readLong(), input.readInt(), ResourceSiteHarvestGoal.Kind.requireWireTag(input.readInt()),
                        SurfaceAnchor.at(input.readInt(), input.readInt(), input.readInt()),
                        ResourceSiteHarvestNavigationBlock.Reason.requireWireTag(input.readUnsignedByte()),
                        new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(FrontierWorldPayloadCodecs.readString(input)),
                        input.readLong()));
            }
        };
    }
    static PayloadCodec harvestReturned() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_returned"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestReturned returned = (ResourceSiteHarvestReturned) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, returned.jobId());
                    FrontierWorldPayloadCodecs.writeSubject(output, returned.workerId());
                    FrontierWorldPayloadCodecs.writeString(output, returned.coldScheduleId().value());
                    output.writeLong(returned.coldDueAt());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResourceSiteHarvestReturned(
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(FrontierWorldPayloadCodecs.readString(input)),
                        input.readLong()));
            }
        };
    }
    static PayloadCodec harvestSegmentRenewed() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_segment_renewed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestSegmentRenewed renewed = (ResourceSiteHarvestSegmentRenewed) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, renewed.siteId());
                    FrontierWorldPayloadCodecs.writeSubject(output, renewed.jobId());
                    FrontierWorldPayloadCodecs.writeSubject(output, renewed.workerId());
                    output.writeInt(renewed.nextCropSlot());
                    FrontierWorldPayloadCodecs.writeString(output, renewed.coldScheduleId().value());
                    output.writeLong(renewed.coldDueAt());
                    output.writeBoolean(renewed.hotLeaseId().isPresent());
                    if (renewed.hotLeaseId().isPresent())
                        FrontierWorldPayloadCodecs.writeString(output, renewed.hotLeaseId().orElseThrow().value());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResourceSiteHarvestSegmentRenewed(
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(), input.readInt(),
                        new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(FrontierWorldPayloadCodecs.readString(input)),
                        input.readLong(), input.readBoolean()
                        ? java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(
                        FrontierWorldPayloadCodecs.readString(input))) : java.util.Optional.empty()));
            }
        };
    }
    static PayloadCodec workAccessObserved() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_field_work_access_observed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceFieldWorkAccessObserved observed = (ResourceFieldWorkAccessObserved) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, observed.siteId());
                    output.writeLong(observed.epoch()); output.writeLong(observed.layoutRevision());
                    output.writeLong(observed.cellId().value());
                    output.writeInt(observed.headroom().x()); output.writeInt(observed.headroom().y());
                    output.writeInt(observed.headroom().z());
                    output.writeBoolean(observed.blocked());
                    FrontierWorldPayloadCodecs.writeString(output, observed.observedBlock());
                    output.writeBoolean(observed.hotLeaseId().isPresent());
                    if (observed.hotLeaseId().isPresent())
                        FrontierWorldPayloadCodecs.writeString(output, observed.hotLeaseId().orElseThrow().value());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                        new ResourceFieldWorkAccessObserved(FrontierWorldPayloadCodecs.readSubject(input).value(),
                                input.readLong(), input.readLong(), new ResourceFieldLayout.CellId(input.readLong()),
                                new BlockPosition(input.readInt(), input.readInt(), input.readInt()),
                                input.readBoolean(), FrontierWorldPayloadCodecs.readString(input),
                                input.readBoolean() ? java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(
                                        FrontierWorldPayloadCodecs.readString(input))) : java.util.Optional.empty()));
            }
        };
    }
    static PayloadCodec harvestBlockedCellSkipped() {
        return harvestCellSkipped(false);
    }
    static PayloadCodec harvestImmatureCellSkipped() {
        return harvestCellSkipped(true);
    }
    private static PayloadCodec harvestCellSkipped(boolean immature) {
        return new PayloadCodec() {
            @Override public String type() { return immature ? "frontier.resource_site_harvest_immature_cell_skipped"
                    : "frontier.resource_site_harvest_blocked_cell_skipped"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestCellSkip skipped = (ResourceSiteHarvestCellSkip) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, skipped.siteId());
                    FrontierWorldPayloadCodecs.writeSubject(output, skipped.jobId());
                    FrontierWorldPayloadCodecs.writeSubject(output, skipped.workerId());
                    output.writeLong(skipped.layoutRevision());
                    output.writeInt(skipped.cellIds().size());
                    for (ResourceFieldLayout.CellId id : skipped.cellIds()) output.writeLong(id.value());
                    FrontierWorldPayloadCodecs.writeString(output, skipped.coldScheduleId().value());
                    output.writeLong(skipped.coldDueAt());
                    output.writeBoolean(skipped.hotLeaseId().isPresent());
                    if (skipped.hotLeaseId().isPresent())
                        FrontierWorldPayloadCodecs.writeString(output, skipped.hotLeaseId().orElseThrow().value());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                    var site = FrontierWorldPayloadCodecs.readSubject(input).value();
                    var job = FrontierWorldPayloadCodecs.readSubject(input).value();
                    var worker = FrontierWorldPayloadCodecs.readSubject(input).value();
                    long revision = input.readLong();
                    int count = input.readInt();
                    if (count < 1 || count > ResourceFieldLayout.MAX_CELLS)
                        throw new java.io.IOException("blocked field cell prefix exceeds layout bound");
                    var ids = new java.util.ArrayList<ResourceFieldLayout.CellId>(count);
                    for (int index = 0; index < count; index++) ids.add(new ResourceFieldLayout.CellId(input.readLong()));
                    var schedule = new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(FrontierWorldPayloadCodecs.readString(input));
                    long dueAt = input.readLong();
                    var lease = input.readBoolean()
                                    ? java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(
                                    FrontierWorldPayloadCodecs.readString(input))) : java.util.Optional.<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId>empty();
                    return immature ? new ResourceSiteHarvestImmatureCellSkipped(site, job, worker, revision, ids, schedule, dueAt, lease)
                            : new ResourceSiteHarvestBlockedCellSkipped(site, job, worker, revision, ids, schedule, dueAt, lease);
                });
            }
        };
    }
    static PayloadCodec harvestTargetRetargeted() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_target_retargeted"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestTargetRetargeted value = (ResourceSiteHarvestTargetRetargeted) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, value.siteId());
                    FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
                    FrontierWorldPayloadCodecs.writeSubject(output, value.workerId());
                    output.writeLong(value.layoutRevision());
                    output.writeInt(value.fromSlot());
                    output.writeInt(value.toSlot());
                    FrontierWorldPayloadCodecs.writeString(output, value.coldScheduleId().value());
                    output.writeLong(value.coldDueAt());
                    output.writeBoolean(value.hotLeaseId().isPresent());
                    if (value.hotLeaseId().isPresent())
                        FrontierWorldPayloadCodecs.writeString(output, value.hotLeaseId().orElseThrow().value());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResourceSiteHarvestTargetRetargeted(
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        input.readLong(), input.readInt(), input.readInt(),
                        new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(FrontierWorldPayloadCodecs.readString(input)),
                        input.readLong(), input.readBoolean()
                        ? java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(
                                FrontierWorldPayloadCodecs.readString(input))) : java.util.Optional.empty()));
            }
        };
    }
    static PayloadCodec harvestRouteBlocked() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_route_blocked"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestRouteBlocked value = (ResourceSiteHarvestRouteBlocked) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, value.siteId());
                    FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
                    FrontierWorldPayloadCodecs.writeSubject(output, value.workerId());
                    writeNavigationBlock(output, value.block());
                    FrontierWorldPayloadCodecs.writeString(output, value.leaseId().value());
                    FrontierWorldPayloadCodecs.writeString(output, value.coldScheduleId().value());
                    output.writeLong(value.coldDueAt());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResourceSiteHarvestRouteBlocked(
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(), readNavigationBlock(input),
                        new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)),
                        new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(FrontierWorldPayloadCodecs.readString(input)),
                        input.readLong()));
            }
        };
    }
    static PayloadCodec harvestRouteCleared() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_route_cleared"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestRouteCleared value = (ResourceSiteHarvestRouteCleared) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, value.siteId());
                    FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
                    FrontierWorldPayloadCodecs.writeSubject(output, value.workerId());
                    writeNavigationBlock(output, value.expected());
                    FrontierWorldPayloadCodecs.writeString(output, value.leaseId().value());
                    FrontierWorldPayloadCodecs.writeString(output, value.coldScheduleId().value());
                    output.writeLong(value.coldDueAt());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResourceSiteHarvestRouteCleared(
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(), readNavigationBlock(input),
                        new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)),
                        new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(FrontierWorldPayloadCodecs.readString(input)),
                        input.readLong()));
            }
        };
    }
    private static void writeNavigationBlock(java.io.DataOutputStream output,
                                             ResourceSiteHarvestNavigationBlock block) throws java.io.IOException {
        output.writeInt(block.target().x()); output.writeInt(block.target().y()); output.writeInt(block.target().z());
        output.writeLong(block.layoutRevision());
        output.writeByte(block.reason().wireTag());
    }
    private static ResourceSiteHarvestNavigationBlock readNavigationBlock(java.io.DataInputStream input)
            throws java.io.IOException {
        return new ResourceSiteHarvestNavigationBlock(SurfaceAnchor.at(input.readInt(), input.readInt(), input.readInt()),
                input.readLong(), ResourceSiteHarvestNavigationBlock.Reason.requireWireTag(input.readUnsignedByte()));
    }
    static PayloadCodec harvestBatchDelivered() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_batch_delivered"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestBatchDelivered delivered = (ResourceSiteHarvestBatchDelivered) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    output.writeInt(delivered.deliveredYieldBefore());
                    PhysicalEffectObservationPayloadCodec.write(output, delivered.receipt());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                    int before = input.readInt();
                    PhysicalEffectObservation observation = PhysicalEffectObservationPayloadCodec.read(input);
                    if (!(observation instanceof ResourceSiteHarvestDeliveryObservation receipt))
                        throw new IllegalArgumentException("field batch WAL has a foreign physical receipt");
                    return new ResourceSiteHarvestBatchDelivered(receipt, before);
                });
            }
        };
    }
    static PayloadCodec harvestBatchPrepared() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_batch_prepared"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestBatchPrepared prepared = (ResourceSiteHarvestBatchPrepared) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, prepared.siteId());
                    FrontierWorldPayloadCodecs.writeSubject(output, prepared.jobId());
                    FrontierWorldPayloadCodecs.writeString(output, prepared.leaseId().value());
                    output.writeInt(prepared.deliveredYieldBefore());
                    FrontierWorldPayloadCodecs.writeSubject(output, prepared.nextOutputSlot().containerId());
                    output.writeByte(prepared.nextOutputSlot().slot());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResourceSiteHarvestBatchPrepared(
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)),
                        input.readInt(), new InventoryCustody.ContainerSlot(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), input.readUnsignedByte())));
            }
        };
    }
    static PayloadCodec harvestHotTraversalAdvanced() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_hot_traversal_advanced"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestHotTraversalAdvanced advanced = (ResourceSiteHarvestHotTraversalAdvanced) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, advanced.jobId());
                    FrontierWorldPayloadCodecs.writeString(output, advanced.leaseId().value());
                    FrontierWorldPayloadCodecs.writeSubject(output, advanced.workerId());
                    FrontierWorldPayloadCodecs.writeBody(output, advanced.observedWorker());
                    output.writeInt(advanced.nextCursor());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResourceSiteHarvestHotTraversalAdvanced(
                        FrontierWorldPayloadCodecs.readSubject(input).value(),
                        new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)),
                        FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldPayloadCodecs.readBody(input), input.readInt()));
            }
        };
    }
    static PayloadCodec harvestHotGoalArrived() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_hot_goal_arrived"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestHotGoalArrived arrived = (ResourceSiteHarvestHotGoalArrived) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, arrived.jobId());
                    FrontierWorldPayloadCodecs.writeString(output, arrived.leaseId().value());
                    FrontierWorldPayloadCodecs.writeSubject(output, arrived.workerId());
                    output.writeLong(arrived.layoutRevision());
                    output.writeInt(arrived.nextWorkSlot());
                    output.writeByte(arrived.kind().wireTag());
                    FrontierWorldPayloadCodecs.writeBody(output, arrived.observedWorker());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                        new ResourceSiteHarvestHotGoalArrived(
                                FrontierWorldPayloadCodecs.readSubject(input).value(),
                                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(
                                        FrontierWorldPayloadCodecs.readString(input)),
                                FrontierWorldPayloadCodecs.readSubject(input).value(),
                                input.readLong(), input.readInt(),
                                ResourceSiteHarvestGoal.Kind.requireWireTag(input.readUnsignedByte()),
                                FrontierWorldPayloadCodecs.readBody(input)));
            }
        };
    }
    static PayloadCodec harvestHotTransitObserved() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_hot_transit_observed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestHotTransitObserved observed = (ResourceSiteHarvestHotTransitObserved) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, observed.jobId());
                    FrontierWorldPayloadCodecs.writeString(output, observed.leaseId().value());
                    FrontierWorldPayloadCodecs.writeSubject(output, observed.workerId());
                    output.writeLong(observed.layoutRevision());
                    output.writeInt(observed.nextWorkSlot());
                    output.writeByte(observed.kind().wireTag());
                    FrontierWorldPayloadCodecs.writeBody(output, observed.observedWorker());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                        new ResourceSiteHarvestHotTransitObserved(
                                FrontierWorldPayloadCodecs.readSubject(input).value(),
                                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(
                                        FrontierWorldPayloadCodecs.readString(input)),
                                FrontierWorldPayloadCodecs.readSubject(input).value(),
                                input.readLong(), input.readInt(),
                                ResourceSiteHarvestGoal.Kind.requireWireTag(input.readUnsignedByte()),
                                FrontierWorldPayloadCodecs.readBody(input)));
            }
        };
    }
    static PayloadCodec harvestCropPrepared() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_crop_prepared"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestCropPrepared prepared = (ResourceSiteHarvestCropPrepared) payload; byte[] job = bytes(prepared.jobId().value());
                return ByteBuffer.allocate(1 + job.length + Integer.BYTES + Long.BYTES).put((byte) job.length).put(job)
                        .putInt(prepared.cropSlotIndex()).putLong(prepared.generation()).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes); String job = read(input);
                if (input.remaining() != Integer.BYTES + Long.BYTES) throw new IllegalArgumentException("malformed resource-site harvest crop preparation payload");
                return new ResourceSiteHarvestCropPrepared(new SubjectId(job), input.getInt(), input.getLong());
            }
        };
    }
    static PayloadCodec harvestProgressed() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_progressed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestProgressed progressed = (ResourceSiteHarvestProgressed) payload;
                byte[] site = bytes(progressed.siteId().value());
                byte[] job = bytes(progressed.jobId().value());
                byte[] schedule = bytes(progressed.coldScheduleId().value());
                byte[] handActor = progressed.observedHand().map(hand -> bytes(hand.address().actorId().value())).orElse(new byte[0]);
                int handBytes = handActor.length == 0 ? 0 : 1 + handActor.length + Long.BYTES * 3 + 1;
                ByteBuffer encoded = ByteBuffer.allocate(1 + site.length + Long.BYTES + 1 + job.length + Integer.BYTES
                                + Long.BYTES * 4 + 1 + 1 + schedule.length + 1 + handBytes)
                        .put((byte) site.length).put(site).putLong(progressed.epoch())
                        .put((byte) job.length).put(job).putInt(progressed.completedCropSlots())
                        .putLong(progressed.layoutRevision()).putLong(progressed.cellId().value()).putLong(progressed.generation())
                        .put((byte) workOutcomeTag(progressed.outcome()))
                        .put((byte) schedule.length).put(schedule).putLong(progressed.coldDueAt())
                        .put((byte) (handActor.length == 0 ? 0 : 1));
                progressed.observedHand().ifPresent(hand -> encoded.put((byte) handActor.length).put(handActor)
                        .putLong(hand.address().entityId().getMostSignificantBits())
                        .putLong(hand.address().entityId().getLeastSignificantBits())
                        .putLong(hand.authorityEpoch()).put((byte) hand.quantity()));
                return encoded.array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes); String site = read(input);
                if (input.remaining() < Long.BYTES + 1) throw new IllegalArgumentException("truncated resource-site harvest owner epoch");
                long epoch = input.getLong(); String job = read(input);
                if (input.remaining() < Integer.BYTES) throw new IllegalArgumentException("truncated resource-site harvest progress payload");
                int completed = input.getInt();
                if (input.remaining() < Long.BYTES * 3 + 2) throw new IllegalArgumentException("truncated resource-site cell work receipt");
                long revision = input.getLong();
                var cellId = new ResourceFieldLayout.CellId(input.getLong());
                long generation = input.getLong();
                var outcome = workOutcome(Byte.toUnsignedInt(input.get()));
                String schedule = read(input);
                if (input.remaining() < Long.BYTES + 1) throw new IllegalArgumentException("malformed resource-site harvest progress payload");
                long dueAt = input.getLong(); int handTag = Byte.toUnsignedInt(input.get());
                java.util.Optional<ResourceSiteHarvestProgressed.HandObservation> hand;
                if (handTag == 0) {
                    if (input.hasRemaining()) throw new IllegalArgumentException("trailing COLD harvest hand bytes");
                    hand = java.util.Optional.empty();
                } else if (handTag == 1) {
                    String actor = read(input);
                    if (input.remaining() != Long.BYTES * 3 + 1)
                        throw new IllegalArgumentException("malformed HOT harvest hand bytes");
                    var entity = new java.util.UUID(input.getLong(), input.getLong());
                    hand = java.util.Optional.of(new ResourceSiteHarvestProgressed.HandObservation(
                            new PhysicalStackAddress.ActorHand(new SubjectId(actor), entity), input.getLong(),
                            Byte.toUnsignedInt(input.get())));
                } else throw new IllegalArgumentException("unknown harvest hand observation tag");
                return new ResourceSiteHarvestProgressed(new SubjectId(site), epoch, new SubjectId(job), completed, revision, cellId, generation, outcome,
                        new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(schedule), dueAt, hand);
            }
        };
    }
    private static int workOutcomeTag(ResourceFieldCycle.WorkOutcome outcome) {
        return switch (outcome) {
            case HARVESTED -> 1; case PLANTED -> 2; case TILLED_AND_PLANTED -> 3;
            case SKIPPED_IMMATURE -> 4; case SKIPPED_BLOCKED -> 5;
        };
    }
    private static ResourceFieldCycle.WorkOutcome workOutcome(int tag) {
        return switch (tag) {
            case 1 -> ResourceFieldCycle.WorkOutcome.HARVESTED;
            case 2 -> ResourceFieldCycle.WorkOutcome.PLANTED;
            case 3 -> ResourceFieldCycle.WorkOutcome.TILLED_AND_PLANTED;
            case 4 -> ResourceFieldCycle.WorkOutcome.SKIPPED_IMMATURE;
            case 5 -> ResourceFieldCycle.WorkOutcome.SKIPPED_BLOCKED;
            default -> throw new IllegalArgumentException("unknown resource-site cell work outcome wire tag " + tag);
        };
    }
    static PayloadCodec cellObserved() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_field_cell_observed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceFieldCellObserved observed = (ResourceFieldCellObserved) payload;
                byte[] site = bytes(observed.siteId().value()), cause = bytes(observed.causationId());
                return ByteBuffer.allocate(1 + site.length + Long.BYTES * 3 + 8 + 1 + cause.length)
                        .put((byte) site.length).put(site).putLong(observed.epoch()).putLong(observed.layoutRevision()).putLong(observed.cellId().value())
                        .put((byte) soilTag(observed.before().soil())).put((byte) cropTag(observed.before().crop()))
                        .put((byte) observed.before().growthStage())
                        .put((byte) soilTag(observed.after().soil())).put((byte) cropTag(observed.after().crop()))
                        .put((byte) observed.after().growthStage())
                        .put((byte) changeTag(observed.change())).put((byte) sourceTag(observed.source()))
                        .put((byte) cause.length).put(cause).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes);
                String site = read(input);
                if (input.remaining() < Long.BYTES * 3 + 9)
                    throw new IllegalArgumentException("truncated resource-field cell observation");
                long epoch = input.getLong(), revision = input.getLong();
                ResourceFieldLayout.CellId cellId = new ResourceFieldLayout.CellId(input.getLong());
                var before = new ResourceFieldPhysicalSurface.Condition(soil(input.get()), crop(input.get()), Byte.toUnsignedInt(input.get()));
                var after = new ResourceFieldPhysicalSurface.Condition(soil(input.get()), crop(input.get()), Byte.toUnsignedInt(input.get()));
                var change = change(input.get()); var source = source(input.get());
                String cause = read(input);
                if (input.hasRemaining()) throw new IllegalArgumentException("trailing resource-field cell observation");
                return new ResourceFieldCellObserved(new SubjectId(site), epoch, revision, cellId, before, after, change, source, cause);
            }
        };
    }
    static PayloadCodec worldChangeHeld() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_field_world_change_held"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return cellObserved().encode(((ResourceFieldWorldChangeHeld) payload).observation());
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return new ResourceFieldWorldChangeHeld((ResourceFieldCellObserved) cellObserved().decode(bytes));
            }
        };
    }
    static PayloadCodec worldChangeAcknowledged() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_field_world_change_acknowledged"; }
            @Override public byte[] encode(FrontierPayload payload) {
                var acknowledged = (ResourceFieldWorldChangeAcknowledged) payload;
                byte[] observation = cellObserved().encode(acknowledged.observation());
                byte[] encoded = java.util.Arrays.copyOf(observation, observation.length + 1);
                encoded[observation.length] = (byte) (acknowledged.physical().equals(acknowledged.observation().before()) ? 1 : 2);
                return encoded;
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                if (bytes.length < 2) throw new IllegalArgumentException("truncated world field acknowledgement");
                var observation = (ResourceFieldCellObserved) cellObserved().decode(java.util.Arrays.copyOf(bytes, bytes.length - 1));
                var physical = switch (Byte.toUnsignedInt(bytes[bytes.length - 1])) {
                    case 1 -> observation.before();
                    case 2 -> observation.after();
                    default -> throw new IllegalArgumentException("unknown world field acknowledgement outcome");
                };
                return new ResourceFieldWorldChangeAcknowledged(observation, physical);
            }
        };
    }
    static PayloadCodec foreignChangeHeld() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_field_foreign_change_held"; }
            @Override public byte[] encode(FrontierPayload payload) {
                var held = (ResourceFieldForeignChangeHeld) payload;
                byte[] site = bytes(held.siteId().value()), cause = bytes(held.causationId());
                return ByteBuffer.allocate(1 + site.length + Long.BYTES * 3 + 6 + 1 + cause.length)
                        .put((byte) site.length).put(site).putLong(held.epoch()).putLong(held.layoutRevision())
                        .putLong(held.cellId().value()).put(foreignCellBytes(held.before()))
                        .put((byte) cause.length).put(cause).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes);
                String site = read(input);
                if (input.remaining() < Long.BYTES * 3 + 7)
                    throw new IllegalArgumentException("truncated foreign field hold");
                long epoch = input.getLong(), revision = input.getLong(), cell = input.getLong();
                var before = readForeignCell(input);
                String cause = read(input);
                if (input.hasRemaining()) throw new IllegalArgumentException("trailing foreign field hold");
                return new ResourceFieldForeignChangeHeld(new SubjectId(site), epoch, revision,
                        new ResourceFieldLayout.CellId(cell), before, cause);
            }
        };
    }

    static PayloadCodec foreignCellObserved() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_field_foreign_cell_observed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                var observed = (ResourceFieldForeignCellObserved) payload;
                byte[] held = foreignChangeHeld().encode(observed.hold());
                byte[] soil = bytes(observed.soilBlockName()), crop = bytes(observed.cropBlockName());
                return ByteBuffer.allocate(Integer.BYTES + held.length + 6 + 1 + soil.length + 1 + crop.length)
                        .putInt(held.length).put(held).put(foreignCellBytes(observed.after()))
                        .put((byte) soil.length).put(soil).put((byte) crop.length).put(crop).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes);
                if (input.remaining() < Integer.BYTES) throw new IllegalArgumentException("truncated foreign field observation");
                int length = input.getInt();
                if (length < 1 || length > 600 || input.remaining() < length + 8)
                    throw new IllegalArgumentException("invalid held foreign field cause length");
                byte[] held = new byte[length]; input.get(held);
                var after = readForeignCell(input);
                String soil = read(input), crop = read(input);
                if (input.hasRemaining()) throw new IllegalArgumentException("trailing foreign field observation");
                return new ResourceFieldForeignCellObserved((ResourceFieldForeignChangeHeld) foreignChangeHeld().decode(held),
                        after, soil, crop);
            }
        };
    }

    static PayloadCodec foreignChangeAcknowledged() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_field_foreign_change_acknowledged"; }
            @Override public byte[] encode(FrontierPayload payload) {
                var acknowledged = (ResourceFieldForeignChangeAcknowledged) payload;
                byte[] held = foreignChangeHeld().encode(acknowledged.hold());
                return ByteBuffer.allocate(Integer.BYTES + held.length + 6)
                        .putInt(held.length).put(held).put(foreignCellBytes(acknowledged.physical())).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes);
                if (input.remaining() < Integer.BYTES) throw new IllegalArgumentException("truncated foreign field acknowledgement");
                int length = input.getInt();
                if (length < 1 || length > 600 || input.remaining() != length + 6)
                    throw new IllegalArgumentException("invalid held foreign field acknowledgement length");
                byte[] held = new byte[length]; input.get(held);
                return new ResourceFieldForeignChangeAcknowledged(
                        (ResourceFieldForeignChangeHeld) foreignChangeHeld().decode(held), readForeignCell(input));
            }
        };
    }

    private static byte[] foreignCellBytes(ResourceFieldCycle.CellState cell) {
        return new byte[] {(byte) switch (cell.soil()) {
            case FARMLAND -> 1; case DIRT -> 2; case OBSTRUCTED -> 3;
            case UNKNOWN -> throw new IllegalArgumentException("unknown foreign field soil");
        }, (byte) switch (cell.crop()) {
            case ABSENT -> 1; case GROWING -> 2; case MATURE -> 3; case OBSTRUCTED -> 4;
            case UNKNOWN -> throw new IllegalArgumentException("unknown foreign field crop");
        }, (byte) cell.growthStage(), (byte) (cell.accounted() ? 1 : 0), (byte) (cell.yielded() ? 1 : 0),
                (byte) (cell.workAccessBlocked() ? 1 : 0)};
    }

    private static ResourceFieldCycle.CellState readForeignCell(ByteBuffer input) {
        if (input.remaining() < 6) throw new IllegalArgumentException("truncated foreign field cell");
        var soil = switch (Byte.toUnsignedInt(input.get())) {
            case 1 -> ResourceFieldCycle.Soil.FARMLAND; case 2 -> ResourceFieldCycle.Soil.DIRT;
            case 3 -> ResourceFieldCycle.Soil.OBSTRUCTED;
            default -> throw new IllegalArgumentException("unknown foreign field soil tag");
        };
        var crop = switch (Byte.toUnsignedInt(input.get())) {
            case 1 -> ResourceFieldCycle.Crop.ABSENT; case 2 -> ResourceFieldCycle.Crop.GROWING;
            case 3 -> ResourceFieldCycle.Crop.MATURE; case 4 -> ResourceFieldCycle.Crop.OBSTRUCTED;
            default -> throw new IllegalArgumentException("unknown foreign field crop tag");
        };
        int stage = Byte.toUnsignedInt(input.get());
        int accounted = Byte.toUnsignedInt(input.get()), yielded = Byte.toUnsignedInt(input.get());
        int blocked = Byte.toUnsignedInt(input.get());
        if (accounted > 1 || yielded > 1 || blocked > 1) throw new IllegalArgumentException("invalid foreign field work flags");
        return new ResourceFieldCycle.CellState(soil, crop, stage, accounted == 1, yielded == 1, blocked == 1);
    }
    static PayloadCodec playerBreakPrepared() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_field_player_break_prepared"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceFieldPlayerBreakPrepared prepared = (ResourceFieldPlayerBreakPrepared) payload;
                byte[] site = bytes(prepared.siteId().value()), action = bytes(prepared.actionId());
                return ByteBuffer.allocate(1 + site.length + Long.BYTES * 5 + 3 + 1 + action.length)
                        .put((byte) site.length).put(site).putLong(prepared.epoch()).putLong(prepared.layoutRevision()).putLong(prepared.cellId().value())
                        .put((byte) soilTag(prepared.before().soil())).put((byte) cropTag(prepared.before().crop()))
                        .put((byte) prepared.before().growthStage())
                        .putLong(prepared.playerId().getMostSignificantBits()).putLong(prepared.playerId().getLeastSignificantBits())
                        .put((byte) action.length).put(action).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes); String site = read(input);
                if (input.remaining() < Long.BYTES * 5 + 4)
                    throw new IllegalArgumentException("truncated prepared field break");
                long epoch = input.getLong(), revision = input.getLong(); var cellId = new ResourceFieldLayout.CellId(input.getLong());
                var before = new ResourceFieldPhysicalSurface.Condition(soil(input.get()), crop(input.get()), Byte.toUnsignedInt(input.get()));
                var player = new java.util.UUID(input.getLong(), input.getLong());
                String action = read(input);
                if (input.hasRemaining()) throw new IllegalArgumentException("trailing prepared field break");
                return new ResourceFieldPlayerBreakPrepared(new SubjectId(site), epoch, revision, cellId, before, player, action);
            }
        };
    }
    private static int soilTag(ResourceFieldCycle.Soil soil) {
        return switch (soil) { case FARMLAND -> 1; case DIRT -> 2; default -> throw new IllegalArgumentException("unowned field soil cannot enter an exact observation"); };
    }
    private static ResourceFieldCycle.Soil soil(byte tag) {
        return switch (Byte.toUnsignedInt(tag)) {
            case 1 -> ResourceFieldCycle.Soil.FARMLAND;
            case 2 -> ResourceFieldCycle.Soil.DIRT;
            default -> throw new IllegalArgumentException("unknown field observation soil wire tag");
        };
    }
    private static int cropTag(ResourceFieldCycle.Crop crop) {
        return switch (crop) {
            case ABSENT -> 1; case GROWING -> 2; case MATURE -> 3;
            default -> throw new IllegalArgumentException("unowned field crop cannot enter an exact observation");
        };
    }
    private static ResourceFieldCycle.Crop crop(byte tag) {
        return switch (Byte.toUnsignedInt(tag)) {
            case 1 -> ResourceFieldCycle.Crop.ABSENT;
            case 2 -> ResourceFieldCycle.Crop.GROWING;
            case 3 -> ResourceFieldCycle.Crop.MATURE;
            default -> throw new IllegalArgumentException("unknown field observation crop wire tag");
        };
    }
    private static int changeTag(ResourceFieldCellObserved.Change change) {
        return switch (change) { case CROP_REMOVED -> 1; case SOIL_BECAME_DIRT -> 2; case UNCHANGED -> 3; case CROP_REPLANTED -> 4; case CROP_GROWN -> 5; };
    }
    private static ResourceFieldCellObserved.Change change(byte tag) {
        return switch (Byte.toUnsignedInt(tag)) {
            case 1 -> ResourceFieldCellObserved.Change.CROP_REMOVED;
            case 2 -> ResourceFieldCellObserved.Change.SOIL_BECAME_DIRT;
            case 3 -> ResourceFieldCellObserved.Change.UNCHANGED;
            case 4 -> ResourceFieldCellObserved.Change.CROP_REPLANTED;
            case 5 -> ResourceFieldCellObserved.Change.CROP_GROWN;
            default -> throw new IllegalArgumentException("unknown field observation change wire tag");
        };
    }
    private static int sourceTag(ResourceFieldCellObserved.Source source) {
        return switch (source) { case PLAYER -> 1; case WORLD -> 2; };
    }
    private static ResourceFieldCellObserved.Source source(byte tag) {
        return switch (Byte.toUnsignedInt(tag)) {
            case 1 -> ResourceFieldCellObserved.Source.PLAYER;
            case 2 -> ResourceFieldCellObserved.Source.WORLD;
            default -> throw new IllegalArgumentException("unknown field observation source wire tag");
        };
    }
    static PayloadCodec prepared() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_prepared"; }
            @Override public byte[] encode(FrontierPayload payload) { return preparationBytes(((ResourceSitePrepared) payload).job()); }
            @Override public FrontierPayload decode(byte[] bytes) { return new ResourceSitePrepared(readPreparation(bytes)); }
        };
    }
    private static byte[] preparationBytes(ResourceSitePreparationJob job) {
        byte[] id = bytes(job.id().value()), site = bytes(job.siteId().value()), intent = bytes(job.intentId().value());
        return ByteBuffer.allocate(3 + id.length + site.length + intent.length).put((byte) id.length).put(id).put((byte) site.length).put(site).put((byte) intent.length).put(intent).array();
    }
    private static ResourceSitePreparationJob readPreparation(byte[] bytes) {
        ByteBuffer input = ByteBuffer.wrap(bytes); if (input.remaining() < 3) throw new IllegalArgumentException("truncated resource-site preparation payload");
        String id = read(input), site = read(input), intent = read(input); if (input.hasRemaining()) throw new IllegalArgumentException("trailing resource-site preparation payload");
        return new ResourceSitePreparationJob(new SubjectId(id), new SubjectId(site), new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(intent));
    }
    static PayloadCodec conflictObserved() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_conflict_observed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteConflictObserved conflict = (ResourceSiteConflictObserved) payload;
                byte[] site = conflict.siteId().value().getBytes(StandardCharsets.UTF_8);
                if (site.length == 0 || site.length > 255) throw new IllegalArgumentException("resource-site conflict encoding is invalid");
                return ByteBuffer.allocate(2 + site.length + Integer.BYTES * 3).put((byte) site.length).put(site)
                        .putInt(conflict.position().x()).putInt(conflict.position().y()).putInt(conflict.position().z())
                        .put((byte) conflict.producer().wireTag()).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes); String site = read(input);
                if (input.remaining() < Integer.BYTES * 3 + 1) throw new IllegalArgumentException("truncated resource-site conflict payload");
                BlockPosition position = new BlockPosition(input.getInt(), input.getInt(), input.getInt()); int producer = Byte.toUnsignedInt(input.get());
                if (input.hasRemaining()) throw new IllegalArgumentException("trailing resource-site conflict payload");
                return new ResourceSiteConflictObserved(new SubjectId(site), position, ResourceSiteDiagnosticProducer.requireWireTag(producer));
            }
        };
    }
    private static String read(ByteBuffer input) {
        if (!input.hasRemaining()) throw new IllegalArgumentException("truncated resource-site preparation payload"); int length = Byte.toUnsignedInt(input.get());
        if (length == 0 || input.remaining() < length) throw new IllegalArgumentException("malformed resource-site preparation payload"); byte[] value = new byte[length]; input.get(value); return new String(value, StandardCharsets.UTF_8);
    }
    private static byte[] bytes(String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length == 0 || encoded.length > 255) throw new IllegalArgumentException("resource-site payload identity is invalid");
        return encoded;
    }
}
