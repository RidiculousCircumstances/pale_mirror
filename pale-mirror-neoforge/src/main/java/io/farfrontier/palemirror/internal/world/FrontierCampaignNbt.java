package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.FrontierCampaign;
import io.farfrontier.palemirror.frontier.FrontierPoint;
import io.farfrontier.palemirror.frontier.FrontierStateHydration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Complete persisted ledger for one bounded human coalition campaign. */
final class FrontierCampaignNbt {
    private FrontierCampaignNbt() { }

    static void write(CompoundTag tag, Iterable<FrontierCampaign> values) {
        ListTag campaigns = new ListTag();
        for (FrontierCampaign value : values) {
            CompoundTag campaign = new CompoundTag();
            campaign.putString("id", value.id());
            campaign.putString("leader", value.leaderSettlementId());
            campaign.putString("hive", value.targetHiveId());
            campaign.putString("organ", value.targetOrganId());
            campaign.putString("kind", value.kind().name());
            campaign.putString("phase", value.phase().name());
            campaign.putInt("x", value.position().x());
            campaign.putInt("z", value.position().z());
            campaign.put("contributors", strings(value.contributorSettlementIds()));
            campaign.put("participants", strings(value.participantIds()));
            campaign.putLong("startedDay", value.startedDay());
            campaign.putInt("transitDays", value.transitDays());
            campaign.putInt("transitProgress", value.transitProgress());
            campaign.putInt("phaseDays", value.phaseDays());
            campaign.putInt("supplyRiskPermille", value.supplyRiskPermille());
            campaign.putInt("supplyReadinessPermille", value.supplyReadinessPermille());
            campaign.putLong("finishedDay", value.finishedDay());
            campaign.putLong("revision", value.revision());
            campaigns.add(campaign);
        }
        tag.put("campaigns", campaigns);
    }

    static List<FrontierStateHydration.CampaignState> read(CompoundTag tag) {
        if (!tag.contains("campaigns", Tag.TAG_LIST)) throw new IllegalStateException("incomplete Frontier campaign ledger");
        List<FrontierStateHydration.CampaignState> values = new ArrayList<>();
        for (Tag value : tag.getList("campaigns", Tag.TAG_COMPOUND)) {
            CompoundTag campaign = (CompoundTag) value;
            try {
                require(campaign, "id", Tag.TAG_STRING); require(campaign, "leader", Tag.TAG_STRING);
                require(campaign, "hive", Tag.TAG_STRING); require(campaign, "organ", Tag.TAG_STRING);
                require(campaign, "kind", Tag.TAG_STRING); require(campaign, "phase", Tag.TAG_STRING);
                require(campaign, "x", Tag.TAG_INT); require(campaign, "z", Tag.TAG_INT);
                require(campaign, "contributors", Tag.TAG_LIST); require(campaign, "participants", Tag.TAG_LIST);
                require(campaign, "startedDay", Tag.TAG_LONG); require(campaign, "transitDays", Tag.TAG_INT);
                require(campaign, "transitProgress", Tag.TAG_INT); require(campaign, "phaseDays", Tag.TAG_INT);
                require(campaign, "supplyRiskPermille", Tag.TAG_INT); require(campaign, "supplyReadinessPermille", Tag.TAG_INT);
                require(campaign, "finishedDay", Tag.TAG_LONG); require(campaign, "revision", Tag.TAG_LONG);
                values.add(new FrontierStateHydration.CampaignState(campaign.getString("id"), campaign.getString("leader"),
                        campaign.getString("hive"), campaign.getString("organ"), FrontierCampaign.Kind.valueOf(campaign.getString("kind")),
                        FrontierCampaign.Phase.valueOf(campaign.getString("phase")), new FrontierPoint(campaign.getInt("x"), campaign.getInt("z")),
                        strings(campaign.getList("contributors", Tag.TAG_STRING)), strings(campaign.getList("participants", Tag.TAG_STRING)),
                        campaign.getLong("startedDay"), campaign.getInt("transitDays"), campaign.getInt("transitProgress"),
                        campaign.getInt("phaseDays"), campaign.getInt("supplyRiskPermille"), campaign.getInt("supplyReadinessPermille"),
                        campaign.getLong("finishedDay"), campaign.getLong("revision")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid Frontier campaign ledger", invalid);
            }
        }
        return List.copyOf(values);
    }

    private static ListTag strings(List<String> values) {
        ListTag result = new ListTag();
        values.forEach(value -> result.add(StringTag.valueOf(value)));
        return result;
    }
    private static List<String> strings(ListTag values) { return values.stream().map(Tag::getAsString).toList(); }
    private static void require(CompoundTag value, String key, int type) {
        if (!value.contains(key, type)) throw new IllegalArgumentException("missing campaign " + key);
    }
}
