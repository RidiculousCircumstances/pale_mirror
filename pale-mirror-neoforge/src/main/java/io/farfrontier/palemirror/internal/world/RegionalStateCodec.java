package io.farfrontier.palemirror.internal.world;

import java.util.EnumMap;
import java.util.Map;

import io.farfrontier.palemirror.domain.CommunityPlaceBinding;
import io.farfrontier.palemirror.domain.CrisisState;
import io.farfrontier.palemirror.domain.EvidenceReliability;
import io.farfrontier.palemirror.domain.GuardCapability;
import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.domain.ObservationFreshness;
import io.farfrontier.palemirror.domain.OccupancyState;
import io.farfrontier.palemirror.domain.OperationalState;
import io.farfrontier.palemirror.domain.RecognitionState;
import io.farfrontier.palemirror.domain.ResourceAccount;
import io.farfrontier.palemirror.domain.ResourceAvailability;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.RouteContract;
import io.farfrontier.palemirror.domain.RouteContractStatus;
import io.farfrontier.palemirror.domain.RouteProvider;
import io.farfrontier.palemirror.domain.SettlementCommunity;
import io.farfrontier.palemirror.domain.SettlementEconomy;
import io.farfrontier.palemirror.domain.SettlementPlace;
import io.farfrontier.palemirror.domain.SettlementPolicy;
import io.farfrontier.palemirror.domain.SettlementSecurity;
import io.farfrontier.palemirror.domain.SiteAffiliation;
import io.farfrontier.palemirror.domain.SiteAffiliationRole;
import io.farfrontier.palemirror.domain.SiteCapability;
import io.farfrontier.palemirror.domain.SiteCapabilityType;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.StructuralIntegrity;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldSite;
import io.farfrontier.palemirror.domain.WorldSiteType;
import io.farfrontier.palemirror.domain.WorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Exact persistence for the First Living Settlement actor model. */
final class RegionalStateCodec {
    private RegionalStateCodec() { }

    static void write(CompoundTag tag, WorldState state) {
        tag.put("communities", list(state.communities().stream().map(RegionalStateCodec::writeCommunity).toList()));
        tag.put("places", list(state.places().stream().map(RegionalStateCodec::writePlace).toList()));
        tag.put("communityPlaceBindings", list(state.communityPlaceBindings().stream().map(RegionalStateCodec::writeBinding).toList()));
        tag.put("economies", list(state.economies().stream().map(RegionalStateCodec::writeEconomy).toList()));
        tag.put("securities", list(state.securities().stream().map(RegionalStateCodec::writeSecurity).toList()));
        tag.put("settlementPolicies", list(state.settlementPolicies().stream().map(RegionalStateCodec::writePolicy).toList()));
        tag.put("worldSites", list(state.sites().stream().map(RegionalStateCodec::writeSite).toList()));
        tag.put("siteAffiliations", list(state.siteAffiliations().stream().map(RegionalStateCodec::writeAffiliation).toList()));
        tag.put("siteCapabilities", list(state.siteCapabilities().stream().map(RegionalStateCodec::writeCapability).toList()));
        tag.put("routeContracts", list(state.routeContracts().stream().map(RegionalStateCodec::writeContract).toList()));
        tag.put("livingRegions", list(state.livingRegions().stream().map(RegionalStateCodec::writeRegion).toList()));
    }

    static void read(CompoundTag tag, WorldState state) {
        for (Tag value : tag.getList("communities", Tag.TAG_COMPOUND)) state.putCommunity(readCommunity((CompoundTag) value));
        for (Tag value : tag.getList("places", Tag.TAG_COMPOUND)) state.putPlace(readPlace((CompoundTag) value));
        for (Tag value : tag.getList("communityPlaceBindings", Tag.TAG_COMPOUND)) state.putCommunityPlaceBinding(readBinding((CompoundTag) value));
        for (Tag value : tag.getList("economies", Tag.TAG_COMPOUND)) state.putEconomy(readEconomy((CompoundTag) value));
        for (Tag value : tag.getList("securities", Tag.TAG_COMPOUND)) state.putSecurity(readSecurity((CompoundTag) value));
        for (Tag value : tag.getList("settlementPolicies", Tag.TAG_COMPOUND)) state.putSettlementPolicy(readPolicy((CompoundTag) value));
        for (Tag value : tag.getList("worldSites", Tag.TAG_COMPOUND)) state.putSite(readSite((CompoundTag) value));
        for (Tag value : tag.getList("siteAffiliations", Tag.TAG_COMPOUND)) state.putSiteAffiliation(readAffiliation((CompoundTag) value));
        for (Tag value : tag.getList("siteCapabilities", Tag.TAG_COMPOUND)) state.putSiteCapability(readCapability((CompoundTag) value));
        for (Tag value : tag.getList("routeContracts", Tag.TAG_COMPOUND)) state.putRouteContract(readContract((CompoundTag) value));
        for (Tag value : tag.getList("livingRegions", Tag.TAG_COMPOUND)) state.putLivingRegion(readRegion((CompoundTag) value));
    }

    private static CompoundTag writeCommunity(SettlementCommunity value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", value.id().value());
        tag.putInt("population", value.population());
        tag.putBoolean("rationing", value.rationing());
        tag.putBoolean("supplyRequested", value.supplyRequested());
        tag.putString("crisis", value.crisisState().name());
        tag.putInt("stableSupplySteps", value.stableSupplySteps());
        return tag;
    }

    private static SettlementCommunity readCommunity(CompoundTag tag) {
        return new SettlementCommunity(id(tag, "id"), tag.getInt("population"), tag.getBoolean("rationing"),
                tag.getBoolean("supplyRequested"), CrisisState.valueOf(tag.getString("crisis")),
                tag.getInt("stableSupplySteps"));
    }

    private static CompoundTag writePlace(SettlementPlace value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", value.id().value());
        tag.putString("recognition", value.recognition().name());
        tag.putString("freshness", value.observationFreshness().name());
        tag.putString("integrity", value.structuralIntegrity().name());
        tag.putString("occupancy", value.occupancy().name());
        tag.putString("reliability", value.lastReliability().name());
        tag.putString("lastObservationId", value.lastObservationId());
        return tag;
    }

    private static SettlementPlace readPlace(CompoundTag tag) {
        return new SettlementPlace(id(tag, "id"), RecognitionState.valueOf(tag.getString("recognition")),
                ObservationFreshness.valueOf(tag.getString("freshness")), StructuralIntegrity.valueOf(tag.getString("integrity")),
                OccupancyState.valueOf(tag.getString("occupancy")), EvidenceReliability.valueOf(tag.getString("reliability")),
                tag.getString("lastObservationId"));
    }

    private static CompoundTag writeBinding(CommunityPlaceBinding value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("community", value.communityId().value());
        tag.putString("place", value.placeId().value());
        return tag;
    }

    private static CommunityPlaceBinding readBinding(CompoundTag tag) {
        return new CommunityPlaceBinding(id(tag, "community"), id(tag, "place"));
    }

    private static CompoundTag writeEconomy(SettlementEconomy value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("community", value.communityId().value());
        ListTag accounts = new ListTag();
        value.accounts().forEach((kind, account) -> {
            CompoundTag item = new CompoundTag();
            item.putString("resource", kind.name());
            item.putInt("capacity", account.capacity());
            item.putInt("stock", account.stock());
            item.putInt("production", account.production());
            item.putInt("incomingFlow", account.incomingFlow());
            item.putInt("baseConsumption", account.baseConsumption());
            item.putInt("rationedConsumption", account.rationedConsumption());
            item.putInt("effectiveConsumption", account.effectiveConsumption());
            item.putInt("actualConsumption", account.actualConsumption());
            item.putInt("netFlow", account.netFlow());
            item.putString("availability", account.availability().name());
            accounts.add(item);
        });
        tag.put("accounts", accounts);
        return tag;
    }

    private static SettlementEconomy readEconomy(CompoundTag tag) {
        Map<ResourceKind, ResourceAccount> accounts = new EnumMap<>(ResourceKind.class);
        for (Tag value : tag.getList("accounts", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) value;
            accounts.put(ResourceKind.valueOf(item.getString("resource")), new ResourceAccount(item.getInt("capacity"),
                    item.getInt("stock"), item.getInt("production"), item.getInt("baseConsumption"),
                    item.getInt("rationedConsumption"), item.getInt("incomingFlow"), item.getInt("effectiveConsumption"),
                    item.getInt("actualConsumption"), item.getInt("netFlow"),
                    ResourceAvailability.valueOf(item.getString("availability"))));
        }
        return new SettlementEconomy(id(tag, "community"), accounts);
    }

    private static CompoundTag writeSecurity(SettlementSecurity value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("community", value.communityId().value());
        tag.putInt("baseDefence", value.baseDefence());
        tag.putInt("defenceReadiness", value.defenceReadiness());
        tag.putInt("registeredGuards", value.registeredGuards());
        tag.putString("guardCapability", value.guardCapability().name());
        return tag;
    }

    private static SettlementSecurity readSecurity(CompoundTag tag) {
        return new SettlementSecurity(id(tag, "community"), tag.getInt("baseDefence"), tag.getInt("defenceReadiness"),
                tag.getInt("registeredGuards"), GuardCapability.valueOf(tag.getString("guardCapability")));
    }

    private static CompoundTag writePolicy(SettlementPolicy value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("community", value.communityId().value());
        tag.putLong("rationReserveSteps", value.rationReserveSteps());
        tag.putLong("requestReserveSteps", value.requestReserveSteps());
        tag.putInt("defenceLossPerUnavailableStep", value.defenceLossPerUnavailableStep());
        tag.putInt("stableStepsToRecover", value.stableStepsToRecover());
        return tag;
    }

    private static SettlementPolicy readPolicy(CompoundTag tag) {
        return new SettlementPolicy(id(tag, "community"), tag.getLong("rationReserveSteps"),
                tag.getLong("requestReserveSteps"), tag.getInt("defenceLossPerUnavailableStep"),
                tag.getInt("stableStepsToRecover"));
    }

    private static CompoundTag writeSite(WorldSite value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", value.id().value());
        tag.putString("type", value.type().name());
        tag.putString("operationalState", value.operationalState().name());
        return tag;
    }

    private static WorldSite readSite(CompoundTag tag) {
        return new WorldSite(id(tag, "id"), WorldSiteType.valueOf(tag.getString("type")),
                OperationalState.valueOf(tag.getString("operationalState")));
    }

    private static CompoundTag writeAffiliation(SiteAffiliation value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("site", value.siteId().value());
        tag.putString("object", value.objectId().value());
        tag.putString("role", value.role().name());
        return tag;
    }

    private static SiteAffiliation readAffiliation(CompoundTag tag) {
        return new SiteAffiliation(id(tag, "site"), id(tag, "object"), SiteAffiliationRole.valueOf(tag.getString("role")));
    }

    private static CompoundTag writeCapability(SiteCapability value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("site", value.siteId().value());
        tag.putString("type", value.type().name());
        if (value.resource() != null) tag.putString("resource", value.resource().name());
        tag.putInt("capacity", value.capacity());
        return tag;
    }

    private static SiteCapability readCapability(CompoundTag tag) {
        ResourceKind resource = tag.contains("resource", Tag.TAG_STRING) ? ResourceKind.valueOf(tag.getString("resource")) : null;
        return new SiteCapability(id(tag, "site"), SiteCapabilityType.valueOf(tag.getString("type")), resource,
                tag.getInt("capacity"));
    }

    private static CompoundTag writeContract(RouteContract value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", value.id().value());
        tag.putString("originEndpoint", value.originEndpoint().value());
        tag.putString("destinationEndpoint", value.destinationEndpoint().value());
        tag.putString("provider", value.provider().name());
        tag.putString("resource", value.resource().name());
        tag.putInt("nominalCapacity", value.nominalCapacity());
        tag.putLong("currentWindowSteps", value.currentWindowSteps());
        tag.putLong("expiryWindowSteps", value.expiryWindowSteps());
        tag.putInt("validatedCapacity", value.validatedCapacity());
        tag.putLong("lastSuccessfulValidationStep", value.lastSuccessfulValidationStep());
        tag.putString("lastObservationId", value.lastObservationId());
        tag.putString("status", value.status().name());
        return tag;
    }

    private static RouteContract readContract(CompoundTag tag) {
        return new RouteContract(id(tag, "id"), id(tag, "originEndpoint"), id(tag, "destinationEndpoint"),
                RouteProvider.valueOf(tag.getString("provider")), ResourceKind.valueOf(tag.getString("resource")),
                tag.getInt("nominalCapacity"), tag.getLong("currentWindowSteps"), tag.getLong("expiryWindowSteps"),
                tag.getInt("validatedCapacity"), tag.getLong("lastSuccessfulValidationStep"),
                tag.getString("lastObservationId"), RouteContractStatus.valueOf(tag.getString("status")));
    }

    private static CompoundTag writeRegion(LivingRegionState value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", value.id());
        tag.putString("community", value.communityId().value());
        tag.putString("place", value.placeId().value());
        tag.putString("primaryFacility", value.primaryFacilityId().value());
        tag.putString("alternateFacility", value.alternateFacilityId().value());
        tag.putString("primaryRoute", value.primaryRouteId().value());
        tag.putString("alternateRoute", value.alternateRouteId().value());
        tag.putLong("incidentDelaySteps", value.incidentDelaySteps());
        if (value.primaryAudience() != null) tag.putString("audience", value.primaryAudience().value());
        tag.putString("recognition", value.recognition().name());
        tag.putLong("discoveredAtStep", value.discoveredAtStep());
        return tag;
    }

    private static LivingRegionState readRegion(CompoundTag tag) {
        StoryAudienceId audience = tag.contains("audience", Tag.TAG_STRING) ? new StoryAudienceId(tag.getString("audience")) : null;
        return new LivingRegionState(tag.getString("id"), id(tag, "community"), id(tag, "place"),
                id(tag, "primaryFacility"), id(tag, "alternateFacility"), id(tag, "primaryRoute"), id(tag, "alternateRoute"),
                tag.getLong("incidentDelaySteps"), audience, RecognitionState.valueOf(tag.getString("recognition")),
                tag.getLong("discoveredAtStep"));
    }

    private static ListTag list(java.util.List<CompoundTag> values) {
        ListTag list = new ListTag();
        list.addAll(values);
        return list;
    }

    private static WorldObjectId id(CompoundTag tag, String key) { return new WorldObjectId(tag.getString(key)); }
}
