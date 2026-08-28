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
    private static String read(ByteBuffer input) {
        if (!input.hasRemaining()) throw new IllegalArgumentException("truncated resource-site preparation payload"); int length = Byte.toUnsignedInt(input.get());
        if (length == 0 || input.remaining() < length) throw new IllegalArgumentException("malformed resource-site preparation payload"); byte[] value = new byte[length]; input.get(value); return new String(value, StandardCharsets.UTF_8);
    }
}
