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
import io.farfrontier.palemirror.internal.world.RegionBindings;
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
        var region = regionFor(data, player.serverLevel().dimension().location().toString(), player.blockPosition(), audience);
        if (region == null) return book(java.util.List.of(Component.literal("No living region has been recognized yet.\n\n"
                + "Explore established villages. Pale Mirror requires stable evidence before it recognizes a community.")));
        String name = regionName(data, region.id());
        SettlementCommunity community = data.worldState().community(region.communityId()).orElse(null);
        if (community == null) return book(java.util.List.of(Component.literal("Regional records are incomplete.")));
        var economy = data.worldState().economy(community.id()).orElseThrow();
        var iron = economy.require(ResourceKind.IRON);
        var security = data.worldState().security(community.id()).orElseThrow();
        String reserve = iron.reserveSteps().isPresent() ? iron.reserveSteps().getAsLong() + " steps" : "stable";
        java.util.List<Component> pages = new java.util.ArrayList<>();
        pages.add(Component.literal(name.toUpperCase(java.util.Locale.ROOT) + "\nRegional Ledger\n\nPopulation: " + data.worldState().population(community.id())
                + "\nIron: " + iron.stock() + "/" + iron.capacity() + "\nNet flow: " + signed(iron.netFlow())
                + "/step\nReserve: " + reserve + "\nDefence: " + security.defenceReadiness() + "/" + security.baseDefence()
                + "\nCondition: " + community.crisisState()));
        String cause = data.worldState().routeContracts().stream().filter(route -> route.destinationEndpoint()
                        .equals(RegionBindings.fromRegionId(region.id()).receivingSiteId()))
                .map(route -> route.id().equals(region.primaryRouteId()) ? "Legacy minecart line: "
                        + route.status() + " / " + route.freshness(data.worldState().simulationStep())
                        : "Alternate Create line: " + route.status() + " / " + route.freshness(data.worldState().simulationStep()))
                .reduce((a, b) -> a + "\n" + b).orElse("No route contracts are known.");
        pages.add(Component.literal("WHY IT MATTERS\n\nThe primary mine supplies strategic iron. The settlement consumes reserves whenever its route cannot deliver.\n\n"
                + cause + "\n\nFalling reserves cause rationing, weaker defence and eventually evacuation."));
        MutableComponent responses = Component.literal("AVAILABLE RESPONSES\n\n1. Clear the infected mine and let the legacy line recover.\n\n"
                + "2. Validate a Create route from the alternate source.\n\n3. Preserve people through evacuation.\n\nIgnoring the crisis remains a choice; the world keeps its outcome.");
        boolean responding = data.worldState().hasRespondingScenario(audience, community.id(),
                io.farfrontier.palemirror.domain.ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS);
        data.worldState().emergencyWindow(community.id()).filter(window -> responding &&
                window.state() == io.farfrontier.palemirror.domain.EmergencyWindowState.OPEN).ifPresent(window -> {
            MutableComponent prepare = Component.literal("\n\n[Prepare refugee site]").withStyle(Style.EMPTY.withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/pale_mirror settlement prepare_refugee_site " + community.id().value())));
            responses.append(prepare);
            if (preparedShelter(data, community.id())) {
                MutableComponent begin = Component.literal("\n[Begin prepared evacuation]").withStyle(Style.EMPTY.withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/pale_mirror settlement evacuate " + community.id().value())));
                responses.append(begin);
            } else {
                responses.append(Component.literal("\nPlace the issued anchor before evacuation can begin."));
            }
        });
        pages.add(responses);
        String places = data.worldRegistry().find(region.placeId()).map(value -> name + ": " + coords(value.anchor())).orElse(name + ": unknown")
                + "\n" + data.worldRegistry().find(region.primaryFacilityId()).map(value -> "Primary mine chamber: " + coords(value.anchor())).orElse("Primary mine: not represented")
                + "\n" + data.worldRegistry().find(region.alternateFacilityId()).map(value -> "Alternate source: " + coords(value.anchor())).orElse("Alternate source: not represented");
        pages.add(Component.literal("PLACES\n\n" + places + "\n\nCoordinates are evidence-backed physical anchors; the ledger never teleports or force-loads them."));
        var minecart = data.vanillaMinecartRoutes().get(region.id());
        var commissioning = data.campaignCommissioning().get(region.id());
        String freight = minecart != null ? "VANILLA FREIGHT\n\nState: " + minecart.status()
                + "\nSegments: " + minecart.completedSegmentCount() + "/" + minecart.segmentCount()
                + "\nCarrier: " + (minecart.representativeCartId() == null ? "awaiting loaded yard" : "registered")
                + (minecart.diagnostic().isBlank() ? "" : "\nIssue: " + minecart.diagnostic())
                + "\n\nThis is a narrow legacy minecart line. Its cargo is abstract and cannot be duplicated from the cart."
                : commissioning == null ? "LEGACY FREIGHT\n\nRailway commissioning has not begun."
                : "LEGACY FREIGHT\n\nState: " + commissioning.status() + "\nBaseline arrivals: " + commissioning.baselineArrivals()
                + "\nConstruction: " + commissioning.constructionPolicy() + "\nSegments: "
                + commissioning.completedSegments() + "/" + commissioning.totalSegments() + "\nTrain: "
                + (commissioning.nativeTrainReference().isBlank() ? "not commissioned" : "registered")
                + (commissioning.diagnostic().isBlank() ? "" : "\nIssue: " + commissioning.diagnostic())
                + "\n\nThe train is representative. Canonical cargo remains abstract and cannot be duplicated through its wagons.";
        pages.add(Component.literal(freight));
        java.util.List<io.farfrontier.palemirror.domain.DomainEvent> relevantEvents = data.worldState().history().stream()
                .filter(event -> event.subject().equals(community.id()) || event.subject().equals(region.primaryFacilityId())).toList();
        String events = relevantEvents.stream().skip(Math.max(0, relevantEvents.size() - 8L))
                .map(event -> "Step " + event.simulationStep() + ": " + readable(event.type().name()))
                .reduce((a, b) -> a + "\n" + b).orElse("No significant events yet.");
        pages.add(Component.literal("RECENT HISTORY\n\n" + events));
        data.worldState().scenarios().stream().filter(value -> value.audience().equals(audience) && !value.status().isTerminal())
                .findFirst().ifPresent(scenario -> {
                    MutableComponent page = Component.literal("ACTIVE STORY\n\n" + scenarioTitle(scenario)
                            + "\n\nStage: " + scenario.status()
                            + "\nOutcome: " + (scenario.resolutionOutcome().isBlank() ? "undecided" : scenario.resolutionOutcome()));
                    if (scenario.status() == ScenarioStatus.OFFERED) {
                        MutableComponent accept = Component.literal("\n\n[Accept request]").withStyle(Style.EMPTY.withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/pale_mirror scenario accept " + scenario.id())));
                        MutableComponent decline = Component.literal("\n[Decline request]").withStyle(Style.EMPTY.withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/pale_mirror scenario decline " + scenario.id())));
                        page.append(accept).append(decline);
                    }
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
                    .map(window -> window.state() + ":remaining=" + window.remainingGraceSteps()).orElse("none")
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
        player.sendSystemMessage(Component.literal(regionName(data, region.id()) + " — population " + data.worldState().population(community.id()) + ", defence "
                + security.defenceReadiness() + "/" + security.baseDefence() + ", iron " + iron.stock() + "/"
                + iron.capacity() + ", flow " + iron.netFlow() + ", reserve " + reserve + ", "
                + iron.availability() + ", policy " + community.crisisState()));
        data.worldState().settlementDevelopment(community.id()).ifPresent(value -> player.sendSystemMessage(Component.literal(
                "Development investment — prosperity " + value.prosperity() + ", pressure " + value.developmentPressure()
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
                player.sendSystemMessage(action("[Prepare refugee site]", "/pale_mirror settlement prepare_refugee_site " + community.id().value())
                        .append(Component.literal(" — place the anchor, then begin evacuation; "
                                + window.remainingGraceSteps() + " active steps remain."))));
        scenarioLine(data, player, region.communityId(), audiences.apply(player), regionName(data, region.id()));
        return true;
    }

    private static void scenarioLine(PaleMirrorSavedData data, ServerPlayer player, WorldObjectId communityId,
                                     StoryAudienceId audience, String name) {
        var scenario = data.worldState().scenarios().stream().filter(value -> value.audience().equals(audience)
                && value.target().equals(communityId) && !value.status().isTerminal()).findFirst().orElse(null);
        if (scenario == null) {
            SettlementCommunity community = data.worldState().community(communityId).orElseThrow();
            player.sendSystemMessage(Component.literal(community.supplyRequested()
                    ? name + " is rationing supplies and seeking another route."
                    : "The council is monitoring its mine supply line."));
            return;
        }
        if (scenario.status() == ScenarioStatus.OFFERED) {
            player.sendSystemMessage(action("[Accept: " + scenarioTitle(scenario) + "]", "/pale_mirror scenario accept " + scenario.id())
                    .append(Component.literal(" — " + scenarioPrompt(scenario, name))));
            return;
        }
        if (scenario.status() == ScenarioStatus.RESPOND) {
            player.sendSystemMessage(Component.literal(scenarioPrompt(scenario, name)));
            return;
        }
        player.sendSystemMessage(Component.literal(scenarioTitle(scenario) + ": " + scenario.status() + ", outcome="
                + scenario.resolutionOutcome() + "."));
    }

    private static MutableComponent action(String text, String command) {
        return Component.literal(text).setStyle(Style.EMPTY.withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static io.farfrontier.palemirror.domain.LivingRegionState regionFor(PaleMirrorSavedData data,
            String dimensionId, BlockPos position, StoryAudienceId audience) {
        var local = data.worldState().livingRegions().stream().filter(candidate -> data.worldRegistry().find(candidate.placeId())
                .filter(entry -> entry.dimensionId().equals(dimensionId)
                        && entry.anchor().distSqr(position) <= 256L * 256L).isPresent()).findFirst();
        if (local.isPresent()) return local.get();
        return data.worldState().livingRegions().stream().filter(candidate -> data.worldState()
                        .regionKnowledge(audience, candidate.id())
                        .filter(knowledge -> knowledge.knows(io.farfrontier.palemirror.domain.KnownRegionalFeature.SETTLEMENT))
                        .isPresent())
                .sorted(java.util.Comparator.comparing(io.farfrontier.palemirror.domain.LivingRegionState::id)).findFirst().orElse(null);
    }

    private static String regionName(PaleMirrorSavedData data, String regionId) {
        return java.util.Optional.ofNullable(data.campaignRegions().get(regionId)).map(io.farfrontier.palemirror.internal.world.CampaignRegionRecord::displayName)
                .orElse("Iron Frontier");
    }

    private static boolean preparedShelter(PaleMirrorSavedData data, WorldObjectId communityId) {
        int population = data.worldState().population(communityId);
        return data.worldState().siteCapabilities().stream()
                .filter(capability -> capability.type() == io.farfrontier.palemirror.domain.SiteCapabilityType.SHELTER)
                .filter(capability -> capability.capacity() >= population)
                .filter(capability -> data.worldState().siteAffiliations(capability.siteId(),
                        io.farfrontier.palemirror.domain.SiteAffiliationRole.RECIPIENT).stream()
                        .anyMatch(affiliation -> affiliation.objectId().equals(communityId)))
                .anyMatch(capability -> data.worldState().site(capability.siteId()).map(site ->
                        site.operationalState() == io.farfrontier.palemirror.domain.OperationalState.OPERATIONAL).orElse(false));
    }

    private static String scenarioTitle(io.farfrontier.palemirror.domain.ScenarioInstance scenario) {
        return switch (scenario.archetype()) {
            case INVESTIGATION_RECOVERY -> "Mine recovery";
            case SETTLEMENT_SUPPLY_CRISIS -> "Settlement supply crisis";
            case DEVELOPMENT_OPPORTUNITY -> "Recovery investment";
            case RESETTLEMENT_OPPORTUNITY -> "Return home";
        };
    }

    private static String scenarioPrompt(io.farfrontier.palemirror.domain.ScenarioInstance scenario, String name) {
        return switch (scenario.archetype()) {
            case INVESTIGATION_RECOVERY -> "Find and clear the infection threatening this site.";
            case SETTLEMENT_SUPPLY_CRISIS -> "Clear the primary mine, validate an alternate Create train, or prepare evacuation for " + name + ".";
            case DEVELOPMENT_OPPORTUNITY -> "Approve the community's storehouse recovery investment.";
            case RESETTLEMENT_OPPORTUNITY -> "Approve the community's safe return from the refugee site.";
        };
    }
}
