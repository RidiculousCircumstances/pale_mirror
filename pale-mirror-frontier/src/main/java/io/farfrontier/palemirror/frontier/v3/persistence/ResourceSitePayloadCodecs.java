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
    static PayloadCodec harvestStarted() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_started"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestJob job = ((ResourceSiteHarvestStarted) payload).job();
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, job.id()); FrontierWorldPayloadCodecs.writeSubject(output, job.taskId());
                    FrontierWorldPayloadCodecs.writeSubject(output, job.siteId()); FrontierWorldPayloadCodecs.writeSubject(output, job.workerId());
                    FrontierWorldPayloadCodecs.writeSubject(output, job.outputItemId()); FrontierWorldPayloadCodecs.writeSubject(output, job.outputSlot().containerId());
                    output.writeByte(job.outputSlot().slot()); FrontierWorldPayloadCodecs.writeString(output, job.intentId().value());
                    output.writeByte(job.progress().completedCropSlots()); output.writeByte(job.progress().pendingCropSlotIndex());
                    TraversalTopologyStateCodec.write(output, job.traversal()); output.writeShort(job.traversalCursor());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                    SubjectId id = FrontierWorldPayloadCodecs.readSubject(input).value(), task = FrontierWorldPayloadCodecs.readSubject(input).value();
                    SubjectId site = FrontierWorldPayloadCodecs.readSubject(input).value(), worker = FrontierWorldPayloadCodecs.readSubject(input).value();
                    SubjectId output = FrontierWorldPayloadCodecs.readSubject(input).value(), depot = FrontierWorldPayloadCodecs.readSubject(input).value();
                    int slot = input.readUnsignedByte(); String intent = FrontierWorldPayloadCodecs.readString(input);
                    int completed = input.readUnsignedByte(), pending = input.readByte(); TraversalTopology topology = TraversalTopologyStateCodec.read(input); int cursor = input.readUnsignedShort();
                    return new ResourceSiteHarvestStarted(new ResourceSiteHarvestJob(id, task, site, worker, output,
                            new InventoryCustody.ContainerSlot(depot, slot), new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(intent),
                            new ResourceSiteHarvestProgress(completed, pending), topology, cursor));
                });
            }
        };
    }
    static PayloadCodec harvestTraversalAdvanced() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_traversal_advanced"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestTraversalAdvanced advanced = (ResourceSiteHarvestTraversalAdvanced) payload;
                return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                    FrontierWorldPayloadCodecs.writeSubject(output, advanced.jobId()); output.writeShort(advanced.nextCursor());
                });
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResourceSiteHarvestTraversalAdvanced(
                        FrontierWorldPayloadCodecs.readSubject(input).value(), input.readUnsignedShort()));
            }
        };
    }
    static PayloadCodec harvestCropPrepared() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_crop_prepared"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestCropPrepared prepared = (ResourceSiteHarvestCropPrepared) payload; byte[] job = bytes(prepared.jobId().value());
                return ByteBuffer.allocate(1 + job.length + 1).put((byte) job.length).put(job).put((byte) prepared.cropSlotIndex()).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes); String job = read(input);
                if (input.remaining() != 1) throw new IllegalArgumentException("malformed resource-site harvest crop preparation payload");
                return new ResourceSiteHarvestCropPrepared(new SubjectId(job), Byte.toUnsignedInt(input.get()));
            }
        };
    }
    static PayloadCodec harvestProgressed() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvest_progressed"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvestProgressed progressed = (ResourceSiteHarvestProgressed) payload;
                byte[] job = bytes(progressed.jobId().value());
                return ByteBuffer.allocate(1 + job.length + 1).put((byte) job.length).put(job).put((byte) progressed.completedCropSlots()).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes); String job = read(input);
                if (!input.hasRemaining()) throw new IllegalArgumentException("truncated resource-site harvest progress payload");
                int completed = Byte.toUnsignedInt(input.get()); if (input.hasRemaining()) throw new IllegalArgumentException("trailing resource-site harvest progress payload");
                return new ResourceSiteHarvestProgressed(new SubjectId(job), completed);
            }
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
                byte[] site = conflict.siteId().value().getBytes(StandardCharsets.UTF_8), cause = conflict.cause().getBytes(StandardCharsets.UTF_8);
                if (site.length == 0 || cause.length == 0 || site.length > 255 || cause.length > 255) throw new IllegalArgumentException("resource-site conflict encoding is invalid");
                return ByteBuffer.allocate(2 + site.length + cause.length + Integer.BYTES * 3).put((byte) site.length).put(site)
                        .putInt(conflict.position().x()).putInt(conflict.position().y()).putInt(conflict.position().z()).put((byte) cause.length).put(cause).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes); String site = read(input);
                if (input.remaining() < Integer.BYTES * 3 + 1) throw new IllegalArgumentException("truncated resource-site conflict payload");
                BlockPosition position = new BlockPosition(input.getInt(), input.getInt(), input.getInt()); String cause = read(input);
                if (input.hasRemaining()) throw new IllegalArgumentException("trailing resource-site conflict payload");
                return new ResourceSiteConflictObserved(new SubjectId(site), position, cause);
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
