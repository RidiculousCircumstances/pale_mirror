package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.domain.CommunityPlaceBinding;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.GuardCapability;
import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.domain.OperationalState;
import io.farfrontier.palemirror.domain.PopulationGroup;
import io.farfrontier.palemirror.domain.RecognitionState;
import io.farfrontier.palemirror.domain.ResourceAccount;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.RouteContract;
import io.farfrontier.palemirror.domain.RouteContractStatus;
import io.farfrontier.palemirror.domain.RouteProvider;
import io.farfrontier.palemirror.domain.SettlementAuthorityProfile;
import io.farfrontier.palemirror.domain.SettlementCohort;
import io.farfrontier.palemirror.domain.SettlementCommunity;
import io.farfrontier.palemirror.domain.SettlementDevelopment;
import io.farfrontier.palemirror.domain.SettlementDevelopmentPolicy;
import io.farfrontier.palemirror.domain.SettlementEconomy;
import io.farfrontier.palemirror.domain.SettlementPlace;
import io.farfrontier.palemirror.domain.SettlementPolicy;
import io.farfrontier.palemirror.domain.SettlementSecurity;
import io.farfrontier.palemirror.domain.SiteAffiliation;
import io.farfrontier.palemirror.domain.SiteAffiliationRole;
import io.farfrontier.palemirror.domain.SiteCapability;
import io.farfrontier.palemirror.domain.SiteCapabilityType;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldSite;
import io.farfrontier.palemirror.domain.WorldSiteType;
import io.farfrontier.palemirror.internal.content.CampaignRegionDefinition;
import io.farfrontier.palemirror.internal.content.CampaignRegionDefinitions;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** Accepts immutable visual genesis facts and creates canonical PM state exactly once. */
public final class AuthoredRegionRegistrar {
    private AuthoredRegionRegistrar() { }

    public static boolean discover(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
        var provider = PaleMirrorVisuals.provider().orElse(null);
        if (provider == null) return false;
        boolean changed = false;
        for (AuthoredRegionSeed seed : provider.discoverAuthoredRegions(server.overworld()).stream()
                .sorted(java.util.Comparator.comparing(AuthoredRegionSeed::planId)).toList()) {
            if (!seed.dimensionId().equals(server.overworld().dimension().location().toString())) continue;
            if (data.worldState().livingRegion(seed.planId()).isPresent()) continue;
            register(data, commands, seed);
            changed = true;
        }
        return changed;
    }

    static void register(PaleMirrorSavedData data, DomainCommandProcessor commands, AuthoredRegionSeed seed) {
        RegionBindings ids = RegionBindings.forAuthored(seed.planId());
        if (data.campaignRegions().containsKey(ids.regionId())) return;
        CampaignRegionDefinition definition = CampaignRegionDefinitions.require(CampaignRegionBootstrapper.IRON_FRONTIER_DEFINITION);
        if (!definition.id().toString().equals(seed.archetypeId())) {
            throw new IllegalStateException("Unsupported authored archetype " + seed.archetypeId());
        }
        int population = seed.residents().size();
        int demand = scale(definition.ironDemand(), population, definition.population());
        int initialStock = Math.max(demand * 4, scale(definition.initialIronStock(), population, definition.population()));
        int capacity = Math.max(initialStock, scale(definition.ironStockCapacity(), population, definition.population()));
        WorldObjectId placeId = new WorldObjectId(ids.regionId() + "_place");
        LivingRegionState region = new LivingRegionState(ids.regionId(), ids.communityId(), placeId,
                ids.primaryMineId(), ids.alternateMineId(), ids.primaryRouteId(), ids.alternateRouteId(),
                definition.crisisDelaySteps(), null, RecognitionState.DISCOVERED, -1, true);
        FacilityState primary = new FacilityState(ids.primaryMineId(), definition.infectionSource(),
                definition.ironProduction(), Integer.MAX_VALUE, 0);
        FacilityState alternate = new FacilityState(ids.alternateMineId(), definition.infectionSource(),
                definition.ironProduction(), Integer.MAX_VALUE, 0);
        SettlementCommunity community = new SettlementCommunity(ids.communityId());
        SettlementPlace place = new SettlementPlace(placeId);
        SettlementEconomy economy = new SettlementEconomy(ids.communityId(), Map.of(ResourceKind.IRON,
                new ResourceAccount(capacity, initialStock, 0, demand,
                        Math.min(demand, scale(definition.rationedIronDemand(), population, definition.population())))));
        int guards = (int) seed.residents().stream().filter(value -> value.cohort().equals("GUARDS")).count();
        SettlementSecurity security = new SettlementSecurity(ids.communityId(), definition.defence(), definition.defence(),
                guards, guards > 0 ? GuardCapability.PRESENT : GuardCapability.ABSENT);
        SettlementPolicy policy = new SettlementPolicy(ids.communityId(), definition.rationReserveSteps(),
                definition.requestReserveSteps(), definition.defenceLossPerUnavailableStep(),
                definition.stableStepsToRecover(), definition.evacuationDefenceThreshold(),
                definition.emergencyGraceSteps(), definition.evacuationDurationSteps());
        List<WorldSite> sites = List.of(
                new WorldSite(ids.primaryDispatchSiteId(), WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL),
                new WorldSite(ids.alternateDispatchSiteId(), WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL),
                new WorldSite(ids.receivingSiteId(), WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL));
        List<SiteAffiliation> affiliations = List.of(
                new SiteAffiliation(ids.primaryDispatchSiteId(), ids.primaryMineId(), SiteAffiliationRole.SUPPLIER),
                new SiteAffiliation(ids.alternateDispatchSiteId(), ids.alternateMineId(), SiteAffiliationRole.SUPPLIER),
                new SiteAffiliation(ids.receivingSiteId(), ids.communityId(), SiteAffiliationRole.RECIPIENT));
        List<SiteCapability> capabilities = List.of(
                new SiteCapability(ids.primaryDispatchSiteId(), SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction()),
                new SiteCapability(ids.alternateDispatchSiteId(), SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction()),
                new SiteCapability(ids.receivingSiteId(), SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction()));
        List<RouteContract> routes = List.of(
                new RouteContract(ids.primaryRouteId(), ids.primaryDispatchSiteId(), ids.receivingSiteId(),
                        RouteProvider.VANILLA_MINECART, ResourceKind.IRON, definition.ironProduction(),
                        definition.routeCurrentWindowSteps(), definition.routeExpiryWindowSteps(), RouteContractStatus.PLANNED),
                new RouteContract(ids.alternateRouteId(), ids.alternateDispatchSiteId(), ids.receivingSiteId(),
                        RouteProvider.CREATE, ResourceKind.IRON, definition.ironProduction(),
                        definition.routeCurrentWindowSteps(), definition.routeExpiryWindowSteps(), RouteContractStatus.PLANNED));
        PopulationGroup residents = PopulationGroup.residents(ids.regionId() + ":residents", ids.communityId(), placeId,
                cohortCounts(seed));
        commands.execute(data.worldState(), new DomainCommand.RegisterLivingRegion(region, List.of(primary, alternate),
                community, place, new CommunityPlaceBinding(ids.communityId(), placeId), economy, security, policy,
                sites, affiliations, capabilities, routes, List.of(residents)));
        commands.execute(data.worldState(), new DomainCommand.RegisterSettlementAuthorityProfile(
                SettlementAuthorityProfile.pmManaged(ids.communityId())));
        data.worldState().putSettlementDevelopment(new SettlementDevelopment(ids.communityId(), 25, 0,
                population, Math.max(population, 56), 0));
        data.worldState().putDevelopmentPolicy(SettlementDevelopmentPolicy.defaults(ids.communityId()));
        data.campaignRegions().put(ids.regionId(), record(seed, placeId, definition));
        data.setDirty();
    }

    private static CampaignRegionRecord record(AuthoredRegionSeed seed, WorldObjectId placeId,
                                               CampaignRegionDefinition definition) {
        return new CampaignRegionRecord(seed.planId(), definition.id().toString(), Math.max(2, definition.version()),
                displayName(seed.planId()), seed.dimensionId(), placeId, block(seed.anchor()), block(seed.primaryMine()),
                block(seed.alternateMine()), block(seed.freightGate()), block(seed.receivingDepot()), null, null,
                CampaignRegionPresentationStatus.PLANNED, "", 0, -1, -1, 0, 0, "", "");
    }

    private static Map<SettlementCohort, Integer> cohortCounts(AuthoredRegionSeed seed) {
        Map<SettlementCohort, Integer> result = new EnumMap<>(SettlementCohort.class);
        seed.residents().forEach(value -> result.merge(SettlementCohort.valueOf(value.cohort()), 1, Integer::sum));
        return result;
    }

    private static int scale(int value, int population, int baseline) {
        return (value * population + baseline - 1) / baseline;
    }

    private static BlockPos block(VisualPoint point) { return new BlockPos(point.x(), point.y(), point.z()); }
    private static String displayName(String planId) {
        String suffix = planId.substring(planId.lastIndexOf('_') + 1).toUpperCase(java.util.Locale.ROOT);
        return "Frontier " + suffix;
    }
}
