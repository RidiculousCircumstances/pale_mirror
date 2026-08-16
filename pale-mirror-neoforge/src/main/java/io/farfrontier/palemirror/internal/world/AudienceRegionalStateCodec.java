package io.farfrontier.palemirror.internal.world;

import java.util.EnumSet;

import io.farfrontier.palemirror.domain.AudienceRegionAccess;
import io.farfrontier.palemirror.domain.AudienceRegionKnowledge;
import io.farfrontier.palemirror.domain.AudienceRegionReachability;
import io.farfrontier.palemirror.domain.KnownRegionalFeature;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Persistence detail for audience knowledge/access, separate from the settlement aggregate codec. */
final class AudienceRegionalStateCodec {
    private AudienceRegionalStateCodec() { }

    static CompoundTag writeKnowledge(AudienceRegionKnowledge value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("audience", value.audience().value());
        tag.putString("region", value.regionId());
        ListTag features = new ListTag();
        value.features().stream().sorted().forEach(feature -> {
            CompoundTag item = new CompoundTag();
            item.putString("feature", feature.name());
            features.add(item);
        });
        tag.put("features", features);
        tag.putLong("supplyChainDiscoveredAtStep", value.supplyChainDiscoveredAtStep());
        return tag;
    }

    static AudienceRegionKnowledge readKnowledge(CompoundTag tag) {
        EnumSet<KnownRegionalFeature> features = EnumSet.noneOf(KnownRegionalFeature.class);
        for (Tag value : tag.getList("features", Tag.TAG_COMPOUND)) {
            features.add(KnownRegionalFeature.valueOf(((CompoundTag) value).getString("feature")));
        }
        return new AudienceRegionKnowledge(new StoryAudienceId(tag.getString("audience")), tag.getString("region"),
                features, tag.getLong("supplyChainDiscoveredAtStep"));
    }

    static CompoundTag writeAccess(AudienceRegionAccess value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("audience", value.audience().value());
        tag.putString("region", value.regionId());
        tag.putString("reachability", value.reachability().name());
        tag.putBoolean("online", value.online());
        tag.putLong("observedAtStep", value.observedAtStep());
        tag.putString("observationId", value.observationId());
        return tag;
    }

    static AudienceRegionAccess readAccess(CompoundTag tag) {
        return new AudienceRegionAccess(new StoryAudienceId(tag.getString("audience")), tag.getString("region"),
                AudienceRegionReachability.valueOf(tag.getString("reachability")), tag.getBoolean("online"),
                tag.getLong("observedAtStep"), tag.getString("observationId"));
    }
}
