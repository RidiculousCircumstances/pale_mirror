package io.farfrontier.palemirror.frontier.reference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rebuilds one complete graybox world from a validated source-shaped document. */
final class ReferenceGrayboxWorldHydrator {
    private ReferenceGrayboxWorldHydrator() { }

    static ReferenceWorld restore(byte[] document) {
        return restore(ReferenceGrayboxStateDocument.decode(document));
    }

    static ReferenceWorld restore(Map<String, Object> document) {
        Map<String, Object> envelope = ReferenceGrayboxStateReader.envelope(document);
        Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(envelope.get("reference_state"));
        Prepared prepared = prepare(reference, envelope.get("bioform_identities"));
        ReferenceWorld restored = new ReferenceWorld(prepared.root.config());
        apply(prepared, restored);
        validateCrossOwnerReferences(restored);
        restored.assertProfileInvariants();
        String expected = ReferenceV2PublicSnapshot.canonicalJson(envelope);
        String actual = ReferenceV2PublicSnapshot.canonicalJson(ReferenceGrayboxCanonicalState.capture(restored));
        if (!expected.equals(actual)) throw new IllegalArgumentException("graybox hydration does not reproduce the canonical source state");
        return restored;
    }

    private static Prepared prepare(Map<String, Object> reference, Object encodedBioformIdentities) {
        ReferenceGrayboxStateRoot.State root = ReferenceGrayboxStateRoot.read(reference);
        PythonRandom populationRng = new PythonRandom(0L);
        populationRng.restore(root.populationRng());
        LinkedHashMap<Integer, ReferenceSettlement> settlements = ReferenceGrayboxStateSettlements.restore(reference.get("settlements"),
                root.config().profile(), populationRng);
        LinkedHashMap<Integer, ReferenceResourceSite> sites = ReferenceGrayboxStateResourceSites.restore(reference.get("resource_sites"), settlements);
        ReferenceGrayboxStateTrade.State trade = ReferenceGrayboxStateTrade.read(reference.get("trade"), root.config().profile(), settlements);
        ReferenceGrayboxStateMarket.State market = ReferenceGrayboxStateMarket.read(reference.get("market"), root.config().profile(), settlements, sites,
                reference.get("resource_sites"), root.day());
        ReferenceGrayboxStateInfection.State infection = ReferenceGrayboxStateInfection.read(reference.get("infection"), root.day());
        Map<Integer, Map<ReferenceBioformKind, List<String>>> bioformIdentities = ReferenceGrayboxStateInfection.bioformIdentities(encodedBioformIdentities,
                infection.swarms());
        ReferenceGrayboxStateOperations.State operations = ReferenceGrayboxStateOperations.read(reference.get("operations"));
        ReferenceGrayboxStateField.State field = ReferenceGrayboxStateField.read(reference.get("field"));
        ReferenceGrayboxStateV2.State v2 = ReferenceGrayboxStateV2.read(reference.get("v2"));
        ReferenceGrayboxStateDiagnostics.State diagnostics = ReferenceGrayboxStateDiagnostics.read(reference.get("diagnostics"), settlements);
        return new Prepared(root, settlements, sites, trade, market, infection, bioformIdentities, operations, field, v2, diagnostics);
    }

    private static void apply(Prepared prepared, ReferenceWorld world) {
        world.rng().restore(prepared.root.rng());
        world.populationRng().restore(prepared.root.populationRng());
        world.marketWorld().settlements().clear();
        prepared.settlements.values().stream().sorted(java.util.Comparator.comparingInt(ReferenceSettlement::id)).forEach(settlement -> {
            settlement.populationRng(world.populationRng());
            world.marketWorld().addSettlement(settlement);
        });
        world.marketWorld().resourceSites().clear();
        prepared.sites.values().stream().sorted(java.util.Comparator.comparingInt(ReferenceResourceSite::id))
                .forEach(world.marketWorld()::addResourceSite);
        prepared.trade.applyTo(world.trade());
        prepared.market.applyTo(world.microeconomy(), world.marketWorld().settlements().keySet());
        prepared.infection.applyTo(world.infection(), prepared.bioformIdentities);
        prepared.operations.applyTo(world.operations());
        prepared.field.applyTo(world.field());
        prepared.v2.applyTo(world.v2());
        prepared.diagnostics.applyTo(world.diagnostics());
        world.day(prepared.root.day());
        world.marketWorld().restoreEvents(prepared.root.events());
    }

    private static void validateCrossOwnerReferences(ReferenceWorld world) {
        Map<Integer, ReferenceSettlement> settlements = world.settlements();
        Map<Integer, ReferenceResourceSite> sites = world.resourceSites();
        Map<Integer, ReferenceCompany> companies = world.microeconomy().companies();
        for (ReferenceResourceSite site : sites.values()) {
            if (site.ownerId() != null && !settlements.containsKey(site.ownerId())) throw new IllegalArgumentException("site owner is absent");
            if (site.operatorCompanyId() != null && !companies.containsKey(site.operatorCompanyId())) throw new IllegalArgumentException("site operator is absent");
        }
        validateOperations(world, world.operations().active());
        validateField(world);
        validateV2(world, settlements, sites, companies);
    }

    private static void validateOperations(ReferenceWorld world, List<ReferenceOperation> operations) {
        for (ReferenceOperation operation : operations) {
            if (operation.owner().kind() == ReferenceAgentKind.SETTLEMENT && !world.settlements().containsKey(operation.owner().id())) {
                throw new IllegalArgumentException("active operation settlement owner is absent");
            }
            if (operation.owner().kind() == ReferenceAgentKind.COLONY && !world.infection().organs().containsKey(operation.owner().id())) {
                throw new IllegalArgumentException("active operation colony owner is absent");
            }
            validateTarget(world, operation.target());
            if (operation.origin() != null) validateTarget(world, operation.origin());
            if (operation.linkedSwarmId() != null && world.infection().swarms().stream().noneMatch(swarm -> swarm.id() == operation.linkedSwarmId())) {
                throw new IllegalArgumentException("active operation swarm is absent");
            }
            if (operation.engagementId() != null && !world.field().engagements().containsKey(operation.engagementId())) {
                throw new IllegalArgumentException("active operation engagement is absent");
            }
        }
    }

    private static void validateTarget(ReferenceWorld world, ReferenceTargetRef target) {
        if (target.id() == null) return;
        boolean present = switch (target.kind()) {
            case SETTLEMENT -> world.settlements().containsKey(target.id());
            case SITE -> world.resourceSites().containsKey(target.id());
            case NEST -> world.infection().organs().containsKey(target.id());
            case FIELD_POST -> world.field().posts().containsKey(target.id());
            case ROUTE, CELL -> true;
        };
        if (!present) throw new IllegalArgumentException("active operation target is absent");
    }

    private static void validateField(ReferenceWorld world) {
        for (ReferenceFieldPost post : world.field().posts().values()) {
            if (!world.settlements().containsKey(post.leaderId())) throw new IllegalArgumentException("field post leader is absent");
            for (int settlement : post.contributors()) if (!world.settlements().containsKey(settlement)) throw new IllegalArgumentException("field post contributor is absent");
        }
        for (ReferenceFieldCampaign campaign : world.field().campaigns().values()) {
            if (!world.settlements().containsKey(campaign.leaderId())) throw new IllegalArgumentException("field campaign leader is absent");
            for (int settlement : campaign.contributors()) if (!world.settlements().containsKey(settlement)) throw new IllegalArgumentException("field campaign contributor is absent");
        }
        for (ReferenceFieldEngagement engagement : world.field().engagements().values()) {
            if (engagement.operationId() != null && world.operations().active().stream().noneMatch(item -> item.id() == engagement.operationId())) {
                throw new IllegalArgumentException("field engagement operation is absent");
            }
            if (engagement.nestId() != null && !world.infection().organs().containsKey(engagement.nestId())) throw new IllegalArgumentException("field engagement nest is absent");
            if (engagement.swarmId() != null && world.infection().swarms().stream().noneMatch(item -> item.id() == engagement.swarmId())) {
                throw new IllegalArgumentException("field engagement swarm is absent");
            }
        }
    }

    private static void validateV2(
            ReferenceWorld world,
            Map<Integer, ReferenceSettlement> settlements,
            Map<Integer, ReferenceResourceSite> sites,
            Map<Integer, ReferenceCompany> companies
    ) {
        ReferenceV2State v2 = world.v2();
        for (ReferenceCoalitionCharter charter : v2.charters().values()) {
            requireSettlement(settlements, charter.leaderId(), "charter leader");
            for (int member : charter.members()) requireSettlement(settlements, member, "charter member");
        }
        for (ReferenceRouteInsurance insurance : v2.routeInsurance().values()) {
            if (world.trade().routes().stream().noneMatch(route -> route.key().equals(insurance.routeKey()))) throw new IllegalArgumentException("route insurance route is absent");
            requireSettlement(settlements, insurance.underwriterId(), "route insurance underwriter");
        }
        for (ReferenceProcurementOrder order : v2.procurements().values()) requireSettlement(settlements, order.settlementId(), "procurement settlement");
        for (ReferenceCompensationClaim claim : v2.compensation().values()) {
            requireSettlement(settlements, claim.settlementId(), "compensation settlement");
            if (!companies.containsKey(claim.companyId())) throw new IllegalArgumentException("compensation company is absent");
        }
        for (ReferenceCivicSiteProject project : v2.civicSiteProjects()) {
            requireSettlement(settlements, project.settlementId(), "civic project settlement");
            if (!sites.containsKey(project.siteId())) throw new IllegalArgumentException("civic project site is absent");
        }
        for (ReferenceNeuralChrysalis chrysalis : v2.chrysalises().values()) {
            ReferenceHiveOrgan organ = world.infection().organs().get(chrysalis.organId());
            if (organ == null || !v2.sectorKeyAt(organ.x(), organ.y()).equals(chrysalis.sectorKey())) throw new IllegalArgumentException("chrysalis organ is absent or misplaced");
        }
        for (ReferenceFrontCampaign campaign : v2.frontCampaigns().values()) {
            requireSettlement(settlements, campaign.leaderId(), "front campaign leader");
            for (int contributor : campaign.contributors()) requireSettlement(settlements, contributor, "front campaign contributor");
            if (!campaign.phase().terminal() && campaign.fieldCampaignId() != null && !world.field().campaigns().containsKey(campaign.fieldCampaignId())) {
                throw new IllegalArgumentException("front campaign field campaign is absent");
            }
            if (!campaign.phase().terminal() && campaign.postId() != null && !world.field().posts().containsKey(campaign.postId())) {
                throw new IllegalArgumentException("front campaign post is absent");
            }
        }
    }

    private static void requireSettlement(Map<Integer, ReferenceSettlement> settlements, int id, String label) {
        if (!settlements.containsKey(id)) throw new IllegalArgumentException(label + " is absent");
    }

    private record Prepared(
            ReferenceGrayboxStateRoot.State root,
            LinkedHashMap<Integer, ReferenceSettlement> settlements,
            LinkedHashMap<Integer, ReferenceResourceSite> sites,
            ReferenceGrayboxStateTrade.State trade,
            ReferenceGrayboxStateMarket.State market,
            ReferenceGrayboxStateInfection.State infection,
            Map<Integer, Map<ReferenceBioformKind, List<String>>> bioformIdentities,
            ReferenceGrayboxStateOperations.State operations,
            ReferenceGrayboxStateField.State field,
            ReferenceGrayboxStateV2.State v2,
            ReferenceGrayboxStateDiagnostics.State diagnostics
    ) { }
}
