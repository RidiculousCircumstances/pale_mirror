package io.farfrontier.palemirror.internal.presentation;

import java.util.function.Function;

import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.domain.LivingRegionStatus;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/** Read-only player/admin presentation derived from PM state; it does not own scenarios or settlement facts. */
public final class RegionalJournal {
    private RegionalJournal() { }

    public static String explainSettlement(PaleMirrorSavedData data, String settlementId) {
        try {
            WorldObjectId id = new WorldObjectId(settlementId);
            var settlement = data.worldState().settlement(id).orElse(null);
            if (settlement == null) return "Unknown settlement " + settlementId;
            String routes = data.worldState().routes().stream().filter(route -> route.destination().equals(id))
                    .map(route -> route.id().value() + "=" + route.status() + ":" + route.transferableCapacity())
                    .sorted().reduce((left, right) -> left + ", " + right).orElse("none");
            String region = data.worldState().livingRegions().stream().filter(value -> value.settlementId().equals(id))
                    .map(value -> value.id() + "=" + value.status()).findFirst().orElse("none");
            return "settlement=" + id.value() + ", population=" + settlement.population() + ", defence="
                    + settlement.currentDefense() + "/" + settlement.baseDefense() + ", status=" + settlement.status()
                    + ", iron=" + settlement.stock(ResourceKind.IRON) + "/" + settlement.stockCapacity(ResourceKind.IRON)
                    + ", routes=" + routes + ", region=" + region;
        } catch (IllegalArgumentException ignored) {
            return "Invalid settlement id " + settlementId;
        }
    }

    public static String timeline(PaleMirrorSavedData data, String objectId) {
        try {
            WorldObjectId subject = new WorldObjectId(objectId);
            String events = data.worldState().history().stream().filter(event -> event.subject().equals(subject))
                    .map(event -> event.simulationStep() + ":" + event.type()).reduce((left, right) -> left + " -> " + right)
                    .orElse("none");
            return "timeline=" + subject.value() + ": " + events;
        } catch (IllegalArgumentException ignored) {
            return "Invalid world object id " + objectId;
        }
    }

    public static boolean present(PaleMirrorSavedData data, ServerPlayer player, BlockPos position,
                                  Function<ServerPlayer, StoryAudienceId> audiences) {
        if (!player.serverLevel().getBlockState(position).is(Blocks.LECTERN)
                && !player.serverLevel().getBlockState(position).is(Blocks.BELL)
                && !player.serverLevel().getBlockState(position).is(net.minecraft.tags.BlockTags.BEDS)) return false;
        LivingRegionState region = data.worldState().livingRegions().stream().filter(candidate ->
                data.worldRegistry().find(candidate.settlementId()).filter(entry -> entry.anchor().equals(position)
                        && entry.dimensionId().equals(player.serverLevel().dimension().location().toString())).isPresent())
                .findFirst().orElse(null);
        if (region == null) return false;
        var settlement = data.worldState().settlement(region.settlementId()).orElse(null);
        if (settlement == null) return false;
        player.sendSystemMessage(Component.literal("Ironhill — population " + settlement.population() + ", defence "
                + settlement.currentDefense() + "/" + settlement.baseDefense() + ", iron "
                + settlement.stock(ResourceKind.IRON) + "/" + settlement.stockCapacity(ResourceKind.IRON) + ", " + settlement.status()));
        AdapterRegistry.scenarioJournalCommand().ifPresent(command -> player.sendSystemMessage(
                action("[Open regional journal]", command).append(Component.literal(" — presentation only; PM owns state."))));
        scenarioLine(data, player, region, audiences.apply(player));
        return true;
    }

    private static void scenarioLine(PaleMirrorSavedData data, ServerPlayer player, LivingRegionState region,
                                     StoryAudienceId audience) {
        var scenario = data.worldState().scenarios().stream().filter(value -> value.audience().equals(audience)
                && value.target().equals(region.settlementId()) && !value.status().isTerminal()).findFirst().orElse(null);
        if (scenario == null) {
            player.sendSystemMessage(Component.literal(region.status() == LivingRegionStatus.DISCOVERED
                    ? "The council is watching the mine supply line." : "No active Ironhill decision."));
            return;
        }
        if (scenario.status() == ScenarioStatus.OFFERED) {
            player.sendSystemMessage(action("[Accept Ironhill crisis]", "/pale_mirror scenario accept " + scenario.id())
                    .append(Component.literal(" — assess the loss of Mine17 and choose a response.")));
            return;
        }
        if (scenario.status() == ScenarioStatus.RESPOND) {
            player.sendSystemMessage(Component.literal("Responses: clear Mine17; build a Create train between Red Valley Dispatch and "
                    + "Ironhill Receiving; or ").append(action("[evacuate residents]", "/pale_mirror scenario evacuate " + scenario.id())));
            return;
        }
        player.sendSystemMessage(Component.literal("Ironhill scenario: " + scenario.status() + "."));
    }

    private static MutableComponent action(String text, String command) {
        return Component.literal(text).setStyle(Style.EMPTY.withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
}
