package io.farfrontier.palemirror.frontier.v3.model;

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
                byte[][] values = { bytes(job.id().value()), bytes(job.taskId().value()), bytes(job.siteId().value()), bytes(job.workerId().value()), bytes(job.outputItemId().value()),
                        bytes(job.outputSlot().containerId().value()), bytes(job.intentId().value()) };
                int size = Integer.BYTES + 7; for (byte[] value : values) size = Math.addExact(size, value.length);
                return ByteBuffer.allocate(size).put((byte) values[0].length).put(values[0]).put((byte) values[1].length).put(values[1])
                        .put((byte) values[2].length).put(values[2]).put((byte) values[3].length).put(values[3]).put((byte) values[4].length).put(values[4])
                        .put((byte) values[5].length).put(values[5]).putInt(job.outputSlot().slot()).put((byte) values[6].length).put(values[6]).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes); String id = read(input), task = read(input), site = read(input), worker = read(input), output = read(input), depot = read(input);
                if (input.remaining() < Integer.BYTES + 1) throw new IllegalArgumentException("truncated resource-site harvest payload");
                int slot = input.getInt(); String intent = read(input); if (input.hasRemaining()) throw new IllegalArgumentException("trailing resource-site harvest payload");
                return new ResourceSiteHarvestStarted(new ResourceSiteHarvestJob(new SubjectId(id), new SubjectId(task), new SubjectId(site), new SubjectId(worker), new SubjectId(output),
                        new InventoryCustody.ContainerSlot(new SubjectId(depot), slot), new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(intent)));
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
    static PayloadCodec harvested() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resource_site_harvested"; }
            @Override public byte[] encode(FrontierPayload payload) {
                ResourceSiteHarvested harvested = (ResourceSiteHarvested) payload; ResourceSiteHarvestJob job = harvested.job(); ExactItemStack output = harvested.output();
                byte[][] values = { bytes(job.id().value()), bytes(job.taskId().value()), bytes(job.siteId().value()), bytes(job.workerId().value()), bytes(job.outputItemId().value()),
                        bytes(job.outputSlot().containerId().value()), bytes(job.intentId().value()), bytes(output.id().value()), bytes(output.economicOwnerId().value()), bytes(output.itemKind()) };
                int size = Integer.BYTES * 2 + 10; for (byte[] value : values) size = Math.addExact(size, value.length);
                return ByteBuffer.allocate(size).put((byte) values[0].length).put(values[0]).put((byte) values[1].length).put(values[1]).put((byte) values[2].length).put(values[2])
                        .put((byte) values[3].length).put(values[3]).put((byte) values[4].length).put(values[4]).put((byte) values[5].length).put(values[5]).putInt(job.outputSlot().slot())
                        .put((byte) values[6].length).put(values[6]).put((byte) values[7].length).put(values[7]).put((byte) values[8].length).put(values[8])
                        .put((byte) values[9].length).put(values[9]).putInt(output.count()).array();
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                ByteBuffer input = ByteBuffer.wrap(bytes); String id = read(input), task = read(input), site = read(input), worker = read(input), outputId = read(input), depot = read(input);
                if (input.remaining() < Integer.BYTES + 1) throw new IllegalArgumentException("truncated resource-site harvested payload");
                int slot = input.getInt(); String intent = read(input), itemId = read(input), owner = read(input), kind = read(input);
                if (input.remaining() != Integer.BYTES) throw new IllegalArgumentException("malformed resource-site harvested payload");
                int count = input.getInt(); ResourceSiteHarvestJob job = new ResourceSiteHarvestJob(new SubjectId(id), new SubjectId(task), new SubjectId(site), new SubjectId(worker), new SubjectId(outputId),
                        new InventoryCustody.ContainerSlot(new SubjectId(depot), slot), new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(intent));
                return new ResourceSiteHarvested(job, new ExactItemStack(new SubjectId(itemId), new SubjectId(owner), kind, count, job.outputSlot()));
            }
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
