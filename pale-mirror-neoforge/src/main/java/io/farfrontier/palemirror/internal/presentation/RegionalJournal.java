package io.farfrontier.palemirror.internal.presentation;

import java.util.function.Function;

import io.farfrontier.palemirror.domain.CommunityPlaceBinding;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.ScenarioStatus;
import io.farfrontier.palemirror.domain.SettlementCommunity;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.CampaignRegionBootstrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

/** Read-only causal projection. It owns neither settlement facts nor scenario progression. */
public final class RegionalJournal {
    private RegionalJournal() { }

    public static WrittenBookContent ledger(PaleMirrorSavedData data, ServerPlayer player, StoryAudienceId audience) {
        var region = data.worldState().livingRegions().stream().findFirst().orElse(null);
        if (region == null) return book(java.util.List.of(Component.literal("No living region has been recognized yet.\n\n"
                + "Explore established villages. Pale Mirror requires stable evidence before it recognizes a community.")));
        SettlementCommunity community = data.worldState().community(region.communityId()).orElse(null);
        if (community == null) return book(java.util.List.of(Component.literal("Regional records are incomplete.")));
        var economy = data.worldState().economy(community.id()).orElseThrow();
        var iron = economy.require(ResourceKind.IRON);
        var security = data.worldState().security(community.id()).orElseThrow();
        String reserve = iron.reserveSteps().isPresent() ? iron.reserveSteps().getAsLong() + " steps" : "stable";
        java.util.List<Component> pages = new java.util.ArrayList<>();
        pages.add(Component.literal("IRONHILL\nRegional Ledger\n\nPopulation: " + data.worldState().population(community.id())
                + "\nIron: " + iron.stock() + "/" + iron.capacity() + "\nNet flow: " + signed(iron.netFlow())
                + "/step\nReserve: " + reserve + "\nDefence: " + security.defenceReadiness() + "/" + security.baseDefence()
                + "\nCondition: " + community.crisisState()));
        String cause = data.worldState().routeContracts().stream().filter(route -> route.destinationEndpoint()
                        .equals(CampaignRegionBootstrapper.IRONHILL_RECEIVING_SITE))
                .map(route -> route.id().equals(CampaignRegionBootstrapper.MINE17_ROUTE) ? "Mine17 legacy line: "
                        + route.status() + " / " + route.freshness(data.worldState().simulationStep())
                        : "Red Valley alternative: " + route.status() + " / " + route.freshness(data.worldState().simulationStep()))
                .reduce((a, b) -> a + "\n" + b).orElse("No route contracts are known.");
        pages.add(Component.literal("WHY IT MATTERS\n\nMine17 supplies strategic iron. The settlement consumes reserves whenever its route cannot deliver.\n\n"
                + cause + "\n\nFalling reserves cause rationing, weaker defence and eventually evacuation."));
        MutableComponent responses = Component.literal("AVAILABLE RESPONSES\n\n1. Clear Mine17 and let the legacy line recover.\n\n"
                + "2. Validate a Create route from Red Valley.\n\n3. Preserve people through evacuation.\n\nIgnoring the crisis remains a choice; the world keeps its outcome.");
        data.worldState().emergencyWindow(community.id()).filter(window ->
                window.state() == io.farfrontier.palemirror.domain.EmergencyWindowState.OPEN).ifPresent(window ->
                responses.append(Component.literal("\n\n[Begin evacuation]").withStyle(Style.EMPTY.withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/pale_mirror settlement evacuate " + community.id().value())))));
        pages.add(responses);
        String places = data.worldRegistry().find(region.placeId()).map(value -> "Ironhill: " + coords(value.anchor())).orElse("Ironhill: unknown")
                + "\n" + data.worldRegistry().find(region.primaryFacilityId()).map(value -> "Mine17 chamber: " + coords(value.anchor())).orElse("Mine17: not represented")
                + "\n" + data.worldRegistry().find(region.alternateFacilityId()).map(value -> "Red Valley: " + coords(value.anchor())).orElse("Red Valley: not represented");
        pages.add(Component.literal("PLACES\n\n" + places + "\n\nCoordinates are evidence-backed physical anchors; the ledger never teleports or force-loads them."));
        var commissioning = data.campaignCommissioning().get(region.id());
        pages.add(Component.literal("LEGACY FREIGHT\n\n" + (commissioning == null ? "Railway commissioning has not begun."
                : "State: " + commissioning.status() + "\nBaseline arrivals: " + commissioning.baselineArrivals()
                + "\nTrain: " + (commissioning.nativeTrainReference().isBlank() ? "not commissioned" : "registered")
                + (commissioning.diagnostic().isBlank() ? "" : "\nIssue: " + commissioning.diagnostic()))
                + "\n\nThe train is representative. Canonical cargo remains abstract and cannot be duplicated through its wagons."));
        java.util.List<io.farfrontier.palemirror.domain.DomainEvent> relevantEvents = data.worldState().history().stream()
                .filter(event -> event.subject().equals(community.id()) || event.subject().equals(region.primaryFacilityId())).toList();
        String events = relevantEvents.stream().skip(Math.max(0, relevantEvents.size() - 8L))
                .map(event -> "Step " + event.simulationStep() + ": " + readable(event.type().name()))
                .reduce((a, b) -> a + "\n" + b).orElse("No significant events yet.");
        pages.add(Component.literal("RECENT HISTORY\n\n" + events));
        data.worldState().scenarios().stream().filter(value -> value.audience().equals(audience) && !value.status().isTerminal())
                .findFirst().ifPresent(scenario -> {
                    MutableComponent page = Component.literal("ACTIVE STORY\n\nStage: " + scenario.status()
                            + "\nOutcome: " + (scenario.resolutionOutcome().isBlank() ? "undecided" : scenario.resolutionOutcome()));
                    if (scenario.status() == ScenarioStatus.OFFERED) page.append(Component.literal("\n\n[Accept request]")
                            .withStyle(Style.EMPTY.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/pale_mirror scenario accept " + scenario.id()))));
                    pages.add(page);
                });
        return book(pages);
    }

    private static WrittenBookContent book(java.util.List<Component> pages) {
        return new WrittenBookContent(Filterable.passThrough("Regional Ledger"), "Pale Mirror", 0,
                pages.stream().map(Filterable::passThrough).toList(), true);
    }

    private static String coords(BlockPos position) { return position.getX() + ", " + position.getY() + ", " + position.getZ(); }
    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
    private static String readable(String value) { return value.toLowerCase(java.util.Locale.ROOT).replace('_', ' '); }

    public static String explainSettlement(PaleMirrorSavedData data, String requestedId) {
        try {
            WorldObjectId requested = new WorldObjectId(requestedId);
            SettlementCommunity community = data.worldState().community(requested).orElseGet(() ->
                    data.worldState().bindingForPlace(requested).flatMap(binding -> data.worldState().community(binding.communityId()))
                            .orElse(null));
            if (community == null) return "Unknown settlement community/place " + requestedId;
            CommunityPlaceBinding binding = data.worldState().communityPlaceBinding(community.id()).orElseThrow();
            var place = data.worldState().place(binding.placeId()).orElseThrow();
            var iron = data.worldState().economy(community.id()).orElseThrow().require(ResourceKind.IRON);
            var security = data.worldState().security(community.id()).orElseThrow();
            String reserve = iron.reserveSteps().isPresent() ? Long.toString(iron.reserveSteps().getAsLong()) : "unbounded";
            String routes = data.worldState().routeContracts().stream().filter(route -> data.worldState()
                            .siteAffiliations(route.destinationEndpoint(), io.farfrontier.palemirror.domain.SiteAffiliationRole.RECIPIENT)
                            .stream().anyMatch(value -> value.objectId().equals(community.id())))
                    .map(route -> route.id().value() + "=" + route.status() + "/" + route.health(data.worldState().simulationStep())
                            + "/" + route.freshness(data.worldState().simulationStep()) + ":"
                            + route.transferableCapacity(data.worldState().simulationStep()))
                    .sorted().reduce((left, right) -> left + ", " + right).orElse("none");
            String development = data.worldState().settlementDevelopment(community.id())
                    .map(value -> "prosperity:" + value.prosperity() + ",pressure:" + value.developmentPressure()
                            + ",housing:" + data.worldState().population(community.id()) + "/" + value.housingCapacity()
                            + ",growth:" + value.stableGrowthSteps()).orElse("unavailable");
            String authority = data.worldState().settlementAuthorityProfile(community.id())
                    .map(profile -> profile.profileId() + profile.fields()).orElse("unregistered");
            return "community=" + community.id().value() + ", place=" + place.id().value() + "["
                    + place.recognition() + "/" + place.observationFreshness() + "/" + place.lastReliability() + "]"
                    + ", population=" + data.worldState().population(community.id()) + ", iron=" + iron.stock() + "/" + iron.capacity()
                    + " flow=" + iron.netFlow() + " reserve=" + reserve + " availability=" + iron.availability()
                    + ", policy=rationing:" + community.rationing() + ",supplyRequested:" + community.supplyRequested()
                    + ",crisis:" + community.crisisState() + ", defence=" + security.defenceReadiness() + "/"
                    + security.baseDefence() + " guards=" + security.guardCapability() + ":" + security.registeredGuards()
                    + ", populationGroups=" + data.worldState().populationGroups(community.id()).stream()
                    .map(group -> group.id() + ":" + group.disposition() + "=" + group.size()).toList()
                    + ", emergency=" + data.worldState().emergencyWindow(community.id())
                    .map(window -> window.state() + "@" + window.deadlineStep()).orElse("none")
                    + ", development=" + development
                    + ", authority=" + authority
                    + ", contracts=" + routes;
        } catch (IllegalArgumentException ignored) {
            return "Invalid settlement id " + requestedId;
        }
    }

    public static String timeline(PaleMirrorSavedData data, String objectId) {
        try {
            WorldObjectId subject = new WorldObjectId(objectId);
            String events = data.worldState().history().stream().filter(event -> event.subject().equals(subject))
                    .map(event -> event.simulationStep() + ":" + event.type() + "(" + event.causationId() + ")")
                    .reduce((left, right) -> left + " -> " + right).orElse("none");
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
        var region = data.worldState().livingRegions().stream().filter(candidate ->
                data.worldRegistry().find(candidate.placeId()).filter(entry -> entry.anchor().equals(position)
                        && entry.dimensionId().equals(player.serverLevel().dimension().location().toString())).isPresent())
                .findFirst().orElse(null);
        if (region == null) return false;
        SettlementCommunity community = data.worldState().community(region.communityId()).orElse(null);
        if (community == null) return false;
        var iron = data.worldState().economy(community.id()).orElseThrow().require(ResourceKind.IRON);
        var security = data.worldState().security(community.id()).orElseThrow();
        String reserve = iron.reserveSteps().isPresent() ? Long.toString(iron.reserveSteps().getAsLong()) : "∞";
        player.sendSystemMessage(Component.literal("Ironhill — population " + data.worldState().population(community.id()) + ", defence "
                + security.defenceReadiness() + "/" + security.baseDefence() + ", iron " + iron.stock() + "/"
                + iron.capacity() + ", flow " + iron.netFlow() + ", reserve " + reserve + ", "
                + iron.availability() + ", policy " + community.crisisState()));
        data.worldState().settlementDevelopment(community.id()).ifPresent(value -> player.sendSystemMessage(Component.literal(
                "Development — prosperity " + value.prosperity() + ", pressure " + value.developmentPressure()
                        + ", housing " + data.worldState().population(community.id()) + "/" + value.housingCapacity()
                        + ", growth " + value.stableGrowthSteps() + ".")));
        data.worldState().settlementAuthorityProfile(community.id()).ifPresent(profile -> {
            if (!profile.relocationAllowed()) player.sendSystemMessage(Component.literal(
                    "This society remains native-owned: Pale Mirror can reconcile its condition and external supply, but cannot evacuate, ruin, grow, or construct it."));
        });
        AdapterRegistry.scenarioJournalCommand().ifPresent(command -> player.sendSystemMessage(
                action("[Open regional journal]", command).append(Component.literal(" — presentation only; PM owns state."))));
        data.worldState().emergencyWindow(community.id()).filter(window ->
                window.state() == io.farfrontier.palemirror.domain.EmergencyWindowState.OPEN).ifPresent(window ->
                player.sendSystemMessage(action("[Begin evacuation]", "/pale_mirror settlement evacuate " + community.id().value())
                        .append(Component.literal(" — intervention window closes at simulation step " + window.deadlineStep() + "."))));
        scenarioLine(data, player, region.communityId(), audiences.apply(player));
        return true;
    }

    private static void scenarioLine(PaleMirrorSavedData data, ServerPlayer player, WorldObjectId communityId,
                                     StoryAudienceId audience) {
        var scenario = data.worldState().scenarios().stream().filter(value -> value.audience().equals(audience)
                && value.target().equals(communityId) && !value.status().isTerminal()).findFirst().orElse(null);
        if (scenario == null) {
            SettlementCommunity community = data.worldState().community(communityId).orElseThrow();
            player.sendSystemMessage(Component.literal(community.supplyRequested()
                    ? "Ironhill is rationing supplies and seeking another route."
                    : "The council is monitoring its mine supply line."));
            return;
        }
        if (scenario.status() == ScenarioStatus.OFFERED) {
            player.sendSystemMessage(action("[Accept Ironhill crisis]", "/pale_mirror scenario accept " + scenario.id())
                    .append(Component.literal(" — assess Mine17 and the alternate supply line.")));
            return;
        }
        if (scenario.status() == ScenarioStatus.RESPOND) {
            player.sendSystemMessage(Component.literal("Responses: clear Mine17; or validate a Create train between Red Valley Dispatch and Ironhill Receiving."));
            return;
        }
        player.sendSystemMessage(Component.literal("Ironhill scenario: " + scenario.status() + ", outcome="
                + scenario.resolutionOutcome() + "."));
    }

    private static MutableComponent action(String text, String command) {
        return Component.literal(text).setStyle(Style.EMPTY.withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
}
