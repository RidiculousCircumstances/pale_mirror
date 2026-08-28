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
}
