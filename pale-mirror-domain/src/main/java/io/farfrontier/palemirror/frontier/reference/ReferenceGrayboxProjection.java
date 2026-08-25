package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds the sole read-only input accepted by the source-parity graybox.
 *
 * <p>This boundary intentionally describes every domain process with semantic
 * identities and colour tokens, but contains neither blocks nor entities. A
 * presentation adapter may defer unloaded work; it may never replace, combine
 * or invent the canonical records represented here.</p>
 */
public final class ReferenceGrayboxProjection {
    private ReferenceGrayboxProjection() { }

    public static ReferenceGrayboxSnapshot from(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceGrayboxLayout.requireSupported(required);
        ReferenceWorldView view = ReferenceWorldView.from(required);
        Map<Integer, ReferenceGrayboxLayout.Rectangle> settlementAreas = settlementAreas(required);
        Map<String, ReferenceGrayboxLayout.Rectangle> sectorAreas = sectorAreas(required.v2());
        Map<Integer, ReferenceGrayboxLayout.Point> operationPositions = operationPositions(required, sectorAreas);
        Map<Integer, ReferenceGrayboxLayout.Point> postPositions = postPositions(required);

        List<ReferenceGrayboxSnapshot.Cell> cells = cells(view);
        List<ReferenceGrayboxSnapshot.Settlement> settlements = settlements(required, settlementAreas);
        List<ReferenceGrayboxSnapshot.Facility> facilities = facilities(required, settlementAreas);
        List<ReferenceGrayboxSnapshot.ResourceSite> sites = sites(required);
        List<ReferenceGrayboxSnapshot.Route> routes = routes(required);
        List<ReferenceGrayboxSnapshot.HiveOrgan> organs = organs(required);
        List<ReferenceGrayboxSnapshot.Bioform> bioforms = bioforms(required);
        List<ReferenceGrayboxSnapshot.Resident> residents = residents(required, settlementAreas, operationPositions, postPositions);
        List<ReferenceGrayboxSnapshot.FieldPost> posts = posts(required);
        List<ReferenceGrayboxSnapshot.FieldLink> links = links(required);
        List<ReferenceGrayboxSnapshot.Activity> activities = activities(required, sectorAreas);
        List<ReferenceGrayboxSnapshot.Cargo> cargoes = cargoes(required, operationPositions, postPositions);
        List<ReferenceGrayboxSnapshot.Interaction> interactions = interactions(facilities, sites, routes, organs, posts, links, cargoes);
        List<ReferenceGrayboxSnapshot.Sector> sectors = sectors(required, sectorAreas);
        List<ReferenceGrayboxSnapshot.Chrysalis> chrysalises = chrysalises(required, sectorAreas);
        List<ReferenceGrayboxSnapshot.Readout> readouts = ReferenceGrayboxReadouts.from(required, settlementAreas);
        List<String> events = required.events();
        String stateRevision = stateRevision(required.day(), required.profile().id(), cells, settlements, facilities, sites, routes, organs,
                bioforms, residents, posts, links, activities, cargoes, interactions, sectors, chrysalises, readouts, events);
        return new ReferenceGrayboxSnapshot(required.day(), required.profile().id(), stateRevision, ReferenceGrayboxLayout.bounds(), cells,
                settlements, facilities, sites, routes, organs, bioforms, residents, posts, links, activities, cargoes, interactions, sectors,
                chrysalises, readouts, events);
    }

    private static List<ReferenceGrayboxSnapshot.Cell> cells(ReferenceWorldView view) {
        return view.cells().stream().map(cell -> new ReferenceGrayboxSnapshot.Cell(cell.x(), cell.y(),
                ReferenceGrayboxLayout.cell(cell.x(), cell.y()), cell.infection(), cell.organicMass(), cell.moisture(), cell.signal(),
                cellColour(cell.infection(), cell.signal()))).toList();
    }

    private static List<ReferenceGrayboxSnapshot.Settlement> settlements(
            ReferenceWorld world, Map<Integer, ReferenceGrayboxLayout.Rectangle> areas
    ) {
        List<ReferenceGrayboxSnapshot.Settlement> result = new ArrayList<>();
        for (ReferenceSettlement settlement : sorted(world.settlements().values(), ReferenceSettlement::id)) {
            ReferenceCivicLedger civic = world.v2().civics().get(settlement.id());
            if (civic == null) throw new IllegalStateException("settlement has no V2 civic ledger: " + settlement.id());
            result.add(new ReferenceGrayboxSnapshot.Settlement(settlement.id(), settlement.name(), settlement.x(), settlement.y(),
                    requiredArea(areas, settlement.id()), settlement.alive(), settlement.population(), settlement.integrity(), settlement.threat(),
                    settlement.illnessBurden(), civic.state().id(), civic.foodReserveDays(), civic.rationFraction(),
                    settlementColour(settlement, civic)));
        }
        return List.copyOf(result);
    }

    private static List<ReferenceGrayboxSnapshot.Facility> facilities(
            ReferenceWorld world, Map<Integer, ReferenceGrayboxLayout.Rectangle> areas
    ) {
        List<ReferenceGrayboxSnapshot.Facility> result = new ArrayList<>();
        for (ReferenceSettlement settlement : sorted(world.settlements().values(), ReferenceSettlement::id)) {
            ReferenceGrayboxLayout.Rectangle area = requiredArea(areas, settlement.id());
            addFacility(result, settlement, area, "civic_hall", settlement.integrity());
            addFacility(result, settlement, area, "workshop", settlement.facilities().workshop());
            addFacility(result, settlement, area, "armory", settlement.facilities().armory());
            addFacility(result, settlement, area, "clinic", settlement.facilities().clinic());
            addFacility(result, settlement, area, "warehouse", stockTotal(settlement));
            addFacility(result, settlement, area, "housing", settlement.population());
            addFacility(result, settlement, area, "fortification", settlement.facilities().fortification());
        }
        return List.copyOf(result);
    }

    private static void addFacility(List<ReferenceGrayboxSnapshot.Facility> result, ReferenceSettlement settlement,
                                    ReferenceGrayboxLayout.Rectangle area, String kind, double level) {
        result.add(new ReferenceGrayboxSnapshot.Facility("settlement:" + settlement.id() + ":facility:" + kind, settlement.id(), kind,
                ReferenceGrayboxLayout.facility(area, kind), level, "facility." + kind));
    }

    private static List<ReferenceGrayboxSnapshot.ResourceSite> sites(ReferenceWorld world) {
        return sorted(world.resourceSites().values(), ReferenceResourceSite::id).stream().map(site -> new ReferenceGrayboxSnapshot.ResourceSite(
                site.id(), site.kind().name().toLowerCase(java.util.Locale.ROOT), site.ownerId() == null ? -1 : site.ownerId(),
                ReferenceGrayboxLayout.site(site.x(), site.y()), site.capacity(), site.condition(), site.contamination(), siteColour(site))).toList();
    }

    private static List<ReferenceGrayboxSnapshot.Route> routes(ReferenceWorld world) {
        List<ReferenceGrayboxSnapshot.Route> result = new ArrayList<>();
        for (ReferenceRoute route : sorted(world.trade().routes(), item -> item.key().lowerSettlementId())) {
            ReferenceSettlement a = world.settlements().get(route.a());
            ReferenceSettlement b = world.settlements().get(route.b());
            if (a == null || b == null) throw new IllegalStateException("route endpoint is absent: " + route.key());
            boolean quarantined = world.day() <= route.quarantineUntil();
            boolean disrupted = world.day() <= route.disruptionUntil();
            result.add(new ReferenceGrayboxSnapshot.Route("route:" + route.key().lowerSettlementId() + ":" + route.key().upperSettlementId(),
                    route.a(), route.b(), ReferenceGrayboxLayout.centre(a.x(), a.y()), ReferenceGrayboxLayout.centre(b.x(), b.y()),
                    route.capacityOn(world.day()), route.risk(), route.infectionOn(world.day()), quarantined, disrupted,
                    quarantined ? "route.quarantined" : disrupted ? "route.disrupted" : "route.open"));
        }
        return List.copyOf(result);
    }

    private static List<ReferenceGrayboxSnapshot.HiveOrgan> organs(ReferenceWorld world) {
        return sorted(world.infection().organs().values(), ReferenceHiveOrgan::id).stream().map(organ -> new ReferenceGrayboxSnapshot.HiveOrgan(
                organ.id(), organ.kind().name().toLowerCase(java.util.Locale.ROOT), ReferenceGrayboxLayout.organ(organ.x(), organ.y()),
                organ.biomass(), organ.vitality(), organ.feral(), organ.feral() ? "organ.feral" : "organ." + organ.kind().name().toLowerCase(java.util.Locale.ROOT))).toList();
    }

    private static List<ReferenceGrayboxSnapshot.Bioform> bioforms(ReferenceWorld world) {
        List<ReferenceGrayboxSnapshot.Bioform> result = new ArrayList<>();
        for (ReferenceSwarm swarm : sorted(world.infection().swarms(), ReferenceSwarm::id)) {
            ReferenceGrayboxLayout.Point anchor = ReferenceGrayboxLayout.position(swarm.x(), swarm.y());
            int localOrdinal = 0;
            for (Map.Entry<ReferenceBioformKind, List<String>> entry : swarm.bioformIds().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ReferenceBioformKind::id))).toList()) {
                for (String bioformId : entry.getValue()) {
                    result.add(new ReferenceGrayboxSnapshot.Bioform(bioformId,
                            swarm.id(), entry.getKey().id(), ReferenceGrayboxLayout.actorSlot(anchor, localOrdinal++), swarm.phase().id(),
                            swarm.feral(), "bioform." + entry.getKey().id()));
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<ReferenceGrayboxSnapshot.Resident> residents(
            ReferenceWorld world, Map<Integer, ReferenceGrayboxLayout.Rectangle> settlementAreas,
            Map<Integer, ReferenceGrayboxLayout.Point> operationPositions, Map<Integer, ReferenceGrayboxLayout.Point> postPositions
    ) {
        Map<String, List<ResidentDraft>> grouped = new TreeMap<>();
        for (ReferenceSettlement settlement : sorted(world.settlements().values(), ReferenceSettlement::id)) {
            ReferenceResidentLedger ledger = settlement.residents();
            if (ledger == null) throw new IllegalStateException("graybox settlement has no resident ledger: " + settlement.id());
            for (String residentId : ledger.livingIds()) {
                ReferenceResident resident = ledger.resident(residentId);
                ReferenceGrayboxLayout.Point anchor = residentAnchor(resident, settlementAreas, operationPositions, postPositions);
                String owner = resident.location().name() + ":" + (resident.locationRef() == null ? resident.homeSettlementId() : resident.locationRef());
                grouped.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(new ResidentDraft(resident, anchor));
            }
        }
        List<ReferenceGrayboxSnapshot.Resident> result = new ArrayList<>();
        for (List<ResidentDraft> group : grouped.values()) {
            group.sort(Comparator.comparing(item -> item.resident().id()));
            for (int index = 0; index < group.size(); index++) {
                ReferenceResident resident = group.get(index).resident();
                result.add(new ReferenceGrayboxSnapshot.Resident(resident.id(), resident.homeSettlementId(), resident.occupation(),
                        resident.economicClass(), resident.location().name().toLowerCase(java.util.Locale.ROOT), resident.locationRef(),
                        resident.condition().name().toLowerCase(java.util.Locale.ROOT), resident.deploymentRole(),
                        ReferenceGrayboxLayout.actorSlot(group.get(index).anchor(), index), residentColour(resident)));
            }
        }
        return List.copyOf(result);
    }

    private static List<ReferenceGrayboxSnapshot.FieldPost> posts(ReferenceWorld world) {
        return sorted(world.field().posts().values(), ReferenceFieldPost::id).stream().map(post -> new ReferenceGrayboxSnapshot.FieldPost(post.id(),
                post.campaignId(), post.kind().id(), post.status().id(), ReferenceGrayboxLayout.fieldPost(post.x(), post.y()), post.integrity(),
                wholePeople(post.garrison(), "field post garrison", post.id()), wholePeople(post.wounded(), "field post wounded", post.id()),
                post.modules().stream().map(ReferenceFieldModuleKind::id).sorted().toList(), "post." + post.kind().id())).toList();
    }

    private static List<ReferenceGrayboxSnapshot.FieldLink> links(ReferenceWorld world) {
        List<ReferenceGrayboxSnapshot.FieldLink> result = new ArrayList<>();
        for (ReferenceFieldLink link : sorted(world.field().links().values(), ReferenceFieldLink::id)) {
            ReferenceFieldPost a = world.field().posts().get(link.aPostId());
            ReferenceFieldPost b = world.field().posts().get(link.bPostId());
            if (a == null || b == null) throw new IllegalStateException("field link endpoint is absent: " + link.id());
            result.add(new ReferenceGrayboxSnapshot.FieldLink(link.id(), link.campaignId(), link.kind().id(), link.aPostId(), link.bPostId(),
                    link.status(), link.integrity(), ReferenceGrayboxLayout.fieldLinkSlots(ReferenceGrayboxLayout.centre(a.x(), a.y()),
                    ReferenceGrayboxLayout.centre(b.x(), b.y())), fieldLinkColour(link)));
        }
        return List.copyOf(result);
    }

    private static List<ReferenceGrayboxSnapshot.Activity> activities(ReferenceWorld world,
                                                                        Map<String, ReferenceGrayboxLayout.Rectangle> sectorAreas) {
        List<ReferenceGrayboxSnapshot.Activity> result = new ArrayList<>();
        for (ReferenceOperation operation : sorted(allOperations(world), ReferenceOperation::id)) result.add(operationActivity(operation));
        for (ReferenceFieldCampaign campaign : sorted(world.field().campaigns().values(), ReferenceFieldCampaign::id)) {
            result.add(new ReferenceGrayboxSnapshot.Activity("field-campaign:" + campaign.id(), "field_campaign", campaign.kind().id(),
                    campaign.phase().id(), ReferenceGrayboxLayout.position(campaign.targetX(), campaign.targetY()), campaign.expectedPersonnel(),
                    campaign.risk(), fieldCampaignTerminal(campaign.phase()), "activity.field_campaign." + campaign.phase().id()));
        }
        for (ReferenceFrontCampaign campaign : sorted(world.v2().frontCampaigns().values(), ReferenceFrontCampaign::id)) {
            ReferenceGrayboxLayout.Rectangle sector = sectorAreas.get(campaign.targetSector());
            if (sector == null) throw new IllegalStateException("front campaign has no target sector: " + campaign.id());
            result.add(new ReferenceGrayboxSnapshot.Activity("front-campaign:" + campaign.id(), "front_campaign", campaign.kind().id(),
                    campaign.phase().id(), new ReferenceGrayboxLayout.Point(sector.centreX(), sector.centreZ()), campaign.personnel(), campaign.risk(),
                    campaign.phase().terminal(), "activity.front_campaign." + campaign.phase().id()));
        }
        return allocateActivitySlots(result);
    }

    /** Separates concurrent source activities without moving either one out of its canonical logical cell. */
    private static List<ReferenceGrayboxSnapshot.Activity> allocateActivitySlots(List<ReferenceGrayboxSnapshot.Activity> activities) {
        Map<ReferenceGrayboxLayout.Point, List<ReferenceGrayboxSnapshot.Activity>> grouped = new LinkedHashMap<>();
        activities.forEach(activity -> grouped.computeIfAbsent(activity.position(), ignored -> new ArrayList<>()).add(activity));
        List<ReferenceGrayboxSnapshot.Activity> result = new ArrayList<>(activities.size());
        for (List<ReferenceGrayboxSnapshot.Activity> group : grouped.values()) {
            group.sort(Comparator.comparing(ReferenceGrayboxSnapshot.Activity::id));
            if (group.size() > 49) throw new IllegalStateException("graybox cell exceeds activity presentation slots");
            for (int index = 0; index < group.size(); index++) {
                ReferenceGrayboxSnapshot.Activity activity = group.get(index);
                result.add(new ReferenceGrayboxSnapshot.Activity(activity.id(), activity.family(), activity.kind(), activity.phase(),
                        ReferenceGrayboxLayout.activitySlot(activity.position(), index), activity.personnel(), activity.indicator(),
                        activity.terminal(), activity.colour()));
            }
        }
        return List.copyOf(result);
    }

    private static ReferenceGrayboxSnapshot.Activity operationActivity(ReferenceOperation operation) {
        boolean terminal = operation.status() == ReferenceOperationStatus.COMPLETED || operation.status() == ReferenceOperationStatus.ABORTED;
        return new ReferenceGrayboxSnapshot.Activity("operation:" + operation.id(), "operation", operation.kind().id(), operation.status().id(),
                ReferenceGrayboxLayout.position(operation.x(), operation.y()), operation.personnel(), operation.unsuppliedDays(), terminal,
                "activity.operation." + operation.status().id());
    }

    private static List<ReferenceGrayboxSnapshot.Cargo> cargoes(
            ReferenceWorld world, Map<Integer, ReferenceGrayboxLayout.Point> operationPositions,
            Map<Integer, ReferenceGrayboxLayout.Point> postPositions
    ) {
        List<ReferenceGrayboxSnapshot.Cargo> result = new ArrayList<>();
        Map<ReferenceGrayboxLayout.Point, Integer> usedSlots = new LinkedHashMap<>();
        for (ReferenceOperation operation : sorted(world.operations().active(), ReferenceOperation::id)) {
            ReferenceGrayboxLayout.Point anchor = requiredPoint(operationPositions, operation.id(), "operation", "cargo");
            for (ReferenceResource resource : ReferenceResource.values()) {
                double quantity = operation.cargo().getOrDefault(resource, 0.0d);
                if (quantity <= 0.0d) continue;
                String resourceId = resource.name().toLowerCase(java.util.Locale.ROOT);
                int ordinal = usedSlots.merge(anchor, 1, Integer::sum) - 1;
                result.add(new ReferenceGrayboxSnapshot.Cargo("operation:" + operation.id() + ":cargo:" + resourceId, "operation", operation.id(),
                        resourceId, quantity, ReferenceGrayboxLayout.cargo(anchor, ordinal), "cargo." + resourceId));
            }
        }
        for (ReferenceFieldPost post : sorted(world.field().posts().values(), ReferenceFieldPost::id)) {
            ReferenceGrayboxLayout.Point anchor = requiredPoint(postPositions, post.id(), "field post", "cargo");
            int ordinal = 0;
            for (ReferenceResource resource : ReferenceResource.values()) {
                double quantity = post.stock(resource);
                if (quantity <= 0.0d) continue;
                String resourceId = resource.name().toLowerCase(java.util.Locale.ROOT);
                result.add(new ReferenceGrayboxSnapshot.Cargo("field_post:" + post.id() + ":cargo:" + resourceId, "field_post", post.id(),
                        resourceId, quantity, ReferenceGrayboxLayout.fieldPostCargo(post.x(), post.y(), ordinal++), "cargo." + resourceId));
            }
            if (ordinal > 16) throw new IllegalStateException("field post exceeds graybox cargo slots: " + post.id());
        }
        return List.copyOf(result);
    }

    private static List<ReferenceGrayboxSnapshot.Interaction> interactions(
            List<ReferenceGrayboxSnapshot.Facility> facilities,
            List<ReferenceGrayboxSnapshot.ResourceSite> sites,
            List<ReferenceGrayboxSnapshot.Route> routes,
            List<ReferenceGrayboxSnapshot.HiveOrgan> organs,
            List<ReferenceGrayboxSnapshot.FieldPost> posts,
            List<ReferenceGrayboxSnapshot.FieldLink> links,
            List<ReferenceGrayboxSnapshot.Cargo> cargoes
    ) {
        List<ReferenceGrayboxSnapshot.Interaction> result = new ArrayList<>();
        for (ReferenceGrayboxSnapshot.Facility facility : facilities) {
            if (facility.level() <= 0.0d || !Set.of("civic_hall", "workshop", "armory", "clinic", "fortification").contains(facility.kind())) continue;
            result.add(interaction("facility:" + facility.id(), facility.id(), "facility_damaged", facility.level(), 1,
                    ReferenceGrayboxLayout.interactionSlots(facility.rectangle(), 16), facility.colour()));
        }
        for (ReferenceGrayboxSnapshot.ResourceSite site : sites) if (site.condition() > 0.0d) {
            result.add(interaction("resource-site:" + site.id(), "site:" + site.id(), "site_damaged", site.condition(), 1,
                    ReferenceGrayboxLayout.interactionSlots(site.rectangle(), 8), site.colour()));
        }
        for (ReferenceGrayboxSnapshot.Route route : routes) if (route.capacity() > 0.0d) {
            result.add(interaction("route:" + route.id(), route.id(), "route_damaged", route.capacity(), 3,
                    ReferenceGrayboxLayout.routeSlots(route.start(), route.end()), route.colour()));
        }
        for (ReferenceGrayboxSnapshot.HiveOrgan organ : organs) if (organ.vitality() > 0.0d) {
            result.add(interaction("hive-organ:" + organ.id(), "organ:" + organ.id(), "organ_damaged", organ.vitality(), 2,
                    ReferenceGrayboxLayout.interactionSlots(organ.rectangle(), 16), organ.colour()));
        }
        for (ReferenceGrayboxSnapshot.FieldPost post : posts) if (post.integrity() > 0.0d
                && Set.of("building", "active", "isolated").contains(post.status())) {
            result.add(interaction("field-post:" + post.id(), "field_post:" + post.id(), "field_post_damaged", post.integrity(), 4,
                    ReferenceGrayboxLayout.interactionSlots(post.rectangle(), 16), post.colour()));
        }
        for (ReferenceGrayboxSnapshot.FieldLink link : links) if (link.integrity() > 0.0d && !link.status().equals("destroyed")) {
            result.add(interaction("field-link:" + link.id(), "field_link:" + link.id(), "field_link_damaged", link.integrity(), 2,
                    link.slots(), link.colour()));
        }
        for (ReferenceGrayboxSnapshot.Cargo cargo : cargoes) {
            String factKind = cargo.ownerKind().equals("operation") ? "operation_cargo_lost" : "field_post_cargo_lost";
            int slots = cargo.ownerKind().equals("operation") ? 4 : 1;
            result.add(interaction("cargo:" + cargo.id(), cargo.id(), factKind, cargo.quantity(), 1,
                    ReferenceGrayboxLayout.interactionSlots(cargo.rectangle(), slots), cargo.colour()));
        }
        return List.copyOf(result);
    }

    private static ReferenceGrayboxSnapshot.Interaction interaction(String id, String subjectId, String kind, double weight, int yOffset,
                                                                      List<ReferenceGrayboxLayout.Point> slots, String colour) {
        return new ReferenceGrayboxSnapshot.Interaction(id, subjectId, kind, weight, yOffset, slots, colour);
    }

    private static boolean fieldCampaignTerminal(ReferenceCampaignPhase phase) {
        return phase == ReferenceCampaignPhase.COMPLETE || phase == ReferenceCampaignPhase.FAILED;
    }

    private static List<ReferenceGrayboxSnapshot.Sector> sectors(ReferenceWorld world,
                                                                   Map<String, ReferenceGrayboxLayout.Rectangle> sectorAreas) {
        List<ReferenceGrayboxSnapshot.Sector> result = new ArrayList<>();
        for (ReferenceV2OperationalSector sector : sorted(world.v2().sectors().values(), item -> item.key())) {
            ReferenceV2SectorControl control = world.v2().sectorControl().get(sector.key());
            if (control == null) throw new IllegalStateException("sector has no control record: " + sector.key());
            result.add(new ReferenceGrayboxSnapshot.Sector(sector.key(), requiredArea(sectorAreas, sector.key()), control.state().id(),
                    sector.infection(), sector.sporeLoad(), sector.humanAccess(), sector.hiveInfluence(), control.supplied(),
                    "sector." + control.state().id()));
        }
        return List.copyOf(result);
    }

    private static List<ReferenceGrayboxSnapshot.Chrysalis> chrysalises(ReferenceWorld world,
                                                                          Map<String, ReferenceGrayboxLayout.Rectangle> sectorAreas) {
        List<ReferenceGrayboxSnapshot.Chrysalis> result = new ArrayList<>();
        for (ReferenceNeuralChrysalis chrysalis : sorted(world.v2().chrysalises().values(), ReferenceNeuralChrysalis::organId)) {
            ReferenceGrayboxLayout.Rectangle sector = requiredArea(sectorAreas, chrysalis.sectorKey());
            result.add(new ReferenceGrayboxSnapshot.Chrysalis(chrysalis.organId(), chrysalis.sectorKey(), chrysalis.daysRemaining(),
                    chrysalis.biomassCommitted(), chrysalis.status(), sector, "chrysalis." + chrysalis.status()));
        }
        return List.copyOf(result);
    }


    private static Map<Integer, ReferenceGrayboxLayout.Rectangle> settlementAreas(ReferenceWorld world) {
        Map<Integer, ReferenceGrayboxLayout.Rectangle> result = new LinkedHashMap<>();
        for (ReferenceSettlement settlement : sorted(world.settlements().values(), ReferenceSettlement::id)) {
            result.put(settlement.id(), ReferenceGrayboxLayout.settlement(settlement.x(), settlement.y()));
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, ReferenceGrayboxLayout.Point> operationPositions(
            ReferenceWorld world, Map<String, ReferenceGrayboxLayout.Rectangle> sectorAreas
    ) {
        Map<Integer, ReferenceGrayboxLayout.Point> result = new LinkedHashMap<>();
        for (ReferenceOperation operation : allOperations(world)) result.put(operation.id(), ReferenceGrayboxLayout.position(operation.x(), operation.y()));
        for (ReferenceFrontCampaign campaign : world.v2().frontCampaigns().values()) {
            if (campaign.residentIdsBySettlement().isEmpty()) continue;
            ReferenceGrayboxLayout.Rectangle sector = requiredArea(sectorAreas, campaign.targetSector());
            result.put(1_000_000 + campaign.id(), centre(sector));
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, ReferenceGrayboxLayout.Point> postPositions(ReferenceWorld world) {
        Map<Integer, ReferenceGrayboxLayout.Point> result = new LinkedHashMap<>();
        for (ReferenceFieldPost post : world.field().posts().values()) result.put(post.id(), ReferenceGrayboxLayout.centre(post.x(), post.y()));
        return Map.copyOf(result);
    }

    private static Map<String, ReferenceGrayboxLayout.Rectangle> sectorAreas(ReferenceV2State v2) {
        Map<String, ReferenceGrayboxLayout.Rectangle> result = new LinkedHashMap<>();
        for (ReferenceV2OperationalSector sector : v2.sectors().values()) {
            int minX = sector.cells().stream().mapToInt(ReferenceGridPosition::x).min().orElseThrow();
            int maxX = sector.cells().stream().mapToInt(ReferenceGridPosition::x).max().orElseThrow();
            int minY = sector.cells().stream().mapToInt(ReferenceGridPosition::y).min().orElseThrow();
            int maxY = sector.cells().stream().mapToInt(ReferenceGridPosition::y).max().orElseThrow();
            ReferenceGrayboxLayout.Rectangle first = ReferenceGrayboxLayout.cell(minX, minY);
            result.put(sector.key(), new ReferenceGrayboxLayout.Rectangle(first.x(), first.z(),
                    (maxX - minX + 1) * ReferenceGrayboxLayout.BLOCKS_PER_CELL,
                    (maxY - minY + 1) * ReferenceGrayboxLayout.BLOCKS_PER_CELL));
        }
        return Map.copyOf(result);
    }

    private static ReferenceGrayboxLayout.Point residentAnchor(ReferenceResident resident,
                                                                 Map<Integer, ReferenceGrayboxLayout.Rectangle> settlementAreas,
                                                                 Map<Integer, ReferenceGrayboxLayout.Point> operations,
                                                                 Map<Integer, ReferenceGrayboxLayout.Point> posts) {
        return switch (resident.location()) {
            case SETTLEMENT -> centre(requiredArea(settlementAreas, resident.homeSettlementId()));
            case OPERATION -> requiredPoint(operations, resident.locationRef(), "operation", resident.id());
            case FIELD_POST -> requiredPoint(posts, resident.locationRef(), "field post", resident.id());
        };
    }

    private static List<ReferenceOperation> allOperations(ReferenceWorld world) {
        List<ReferenceOperation> result = new ArrayList<>(world.operations().active());
        result.addAll(world.operations().completed());
        return result;
    }

    private static double stockTotal(ReferenceSettlement settlement) {
        return settlement.stock().values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static int wholeBioforms(double value, int swarmId, ReferenceBioformKind kind) {
        return whole(value, "swarm " + swarmId + " " + kind.id());
    }

    private static int wholePeople(double value, String owner, int id) {
        return whole(value, owner + " " + id);
    }

    private static int whole(double value, String source) {
        if (!Double.isFinite(value) || value < 0.0d || Math.abs(value - Math.rint(value)) > 1.0e-9d || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("graybox requires whole discrete entities for " + source + ": " + value);
        }
        return (int) Math.rint(value);
    }

    private static String cellColour(double infection, double signal) {
        if (infection >= 0.70d) return signal > 0.01d ? "cell.signal_severe" : "cell.feral_severe";
        if (infection >= 0.28d) return signal > 0.01d ? "cell.signal_active" : "cell.feral_active";
        return infection > 0.01d ? "cell.trace" : "cell.neutral";
    }

    private static String settlementColour(ReferenceSettlement settlement, ReferenceCivicLedger civic) {
        return !settlement.alive() ? "settlement.collapsed" : "settlement." + civic.state().id();
    }

    private static String siteColour(ReferenceResourceSite site) {
        return site.condition() <= 0.02d ? "site.disabled" : site.contamination() >= 0.45d ? "site.contaminated" : "site." + site.kind().name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String residentColour(ReferenceResident resident) {
        return resident.condition() == ReferenceResidentCondition.WOUNDED ? "resident.wounded" : "resident." + resident.occupation();
    }

    private static String fieldLinkColour(ReferenceFieldLink link) {
        return "link." + link.kind().id() + "." + link.status();
    }

    /**
     * A materialization revision is an exact hash of the immutable graybox
     * input, not a second mutable version ledger.  It works for the discrete
     * profile even while the source-profile persistence codec deliberately
     * rejects that separate state shape.
     */
    private static String stateRevision(int day, String profileId, List<?>... components) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("schema", "frontier_graybox_projection_v1");
        state.put("day", day);
        state.put("profile", profileId);
        for (int index = 0; index < components.length; index++) {
            state.put("component_" + index, components[index].stream().map(Object::toString).toList());
        }
        return ReferenceV2PublicSnapshot.sha256(state);
    }

    private static ReferenceGrayboxLayout.Point centre(ReferenceGrayboxLayout.Rectangle rectangle) {
        return new ReferenceGrayboxLayout.Point(rectangle.centreX(), rectangle.centreZ());
    }

    private static <K> ReferenceGrayboxLayout.Rectangle requiredArea(Map<K, ReferenceGrayboxLayout.Rectangle> values, K key) {
        ReferenceGrayboxLayout.Rectangle area = values.get(key);
        if (area == null) throw new IllegalStateException("graybox area is missing for " + key);
        return area;
    }

    private static ReferenceGrayboxLayout.Point requiredPoint(Map<Integer, ReferenceGrayboxLayout.Point> values, Integer key,
                                                               String owner, String residentId) {
        ReferenceGrayboxLayout.Point point = key == null ? null : values.get(key);
        if (point == null) throw new IllegalStateException("resident " + residentId + " has no live " + owner + " owner");
        return point;
    }

    private static <T, U extends Comparable<? super U>> List<T> sorted(Iterable<T> values, java.util.function.Function<T, U> key) {
        List<T> result = new ArrayList<>();
        values.forEach(result::add);
        result.sort(Comparator.comparing(key));
        return result;
    }

    private record ResidentDraft(ReferenceResident resident, ReferenceGrayboxLayout.Point anchor) { }
}
