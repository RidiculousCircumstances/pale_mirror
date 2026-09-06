package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Java counterpart of Python {@code simulation.views.WorldView}.
 *
 * <p>This is the full primitive system view that planners and materializers may
 * observe. It has no mutation methods and never exposes a live domain object.
 * {@link #canonicalProjection()} preserves the Python dataclass field shape
 * for source-parity fixtures.</p>
 */
public record ReferenceWorldView(
        int day,
        int width,
        int height,
        List<Settlement> settlements,
        List<Site> sites,
        List<Nest> nests,
        List<Swarm> swarms,
        List<Cell> cells,
        List<FieldPost> posts,
        List<Campaign> campaigns,
        int maximumSwarms,
        List<Sector> sectors,
        List<Chrysalis> chrysalises,
        double personScale,
        boolean discretePeople
) {
    public ReferenceWorldView {
        if (width < 1 || height < 1) throw new IllegalArgumentException("world-view dimensions must be positive");
        if (maximumSwarms < 0) throw new IllegalArgumentException("world-view maximum swarms must not be negative");
        settlements = List.copyOf(Objects.requireNonNull(settlements, "settlements"));
        sites = List.copyOf(Objects.requireNonNull(sites, "sites"));
        nests = List.copyOf(Objects.requireNonNull(nests, "nests"));
        swarms = List.copyOf(Objects.requireNonNull(swarms, "swarms"));
        cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        posts = List.copyOf(Objects.requireNonNull(posts, "posts"));
        campaigns = List.copyOf(Objects.requireNonNull(campaigns, "campaigns"));
        sectors = List.copyOf(Objects.requireNonNull(sectors, "sectors"));
        chrysalises = List.copyOf(Objects.requireNonNull(chrysalises, "chrysalises"));
        if (cells.size() != width * height) throw new IllegalArgumentException("world view must contain one cell per coordinate");
        if (!Double.isFinite(personScale) || personScale <= 0.0d) throw new IllegalArgumentException("world-view person scale must be positive and finite");
    }

    /** Build a deep primitive projection in the exact stable order used by Python {@code world_view}. */
    public static ReferenceWorldView from(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceInfectionModel infection = required.infection();
        ReferenceV2State v2 = required.v2();
        List<List<Double>> signal = infection.signalMap();
        List<Settlement> settlements = required.settlements().values().stream().sorted(Comparator.comparingInt(ReferenceSettlement::id))
                .map(ReferenceWorldView::settlement).toList();
        List<Site> sites = required.resourceSites().values().stream().sorted(Comparator.comparingInt(ReferenceResourceSite::id))
                .map(ReferenceWorldView::site).toList();
        List<Nest> nests = infection.organs().values().stream().sorted(Comparator.comparingInt(ReferenceHiveOrgan::id))
                .map(ReferenceWorldView::nest).toList();
        List<Swarm> swarms = infection.swarms().stream().sorted(Comparator.comparingInt(ReferenceSwarm::id))
                .map(ReferenceWorldView::swarm).toList();
        List<Cell> cells = new ArrayList<>(infection.width() * infection.height());
        for (int y = 0; y < infection.height(); y++) for (int x = 0; x < infection.width(); x++) {
            ReferenceEcosystemCell ecosystem = infection.ecosystem().cell(x, y);
            cells.add(new Cell(x, y, infection.infectionAt(x, y), ecosystem.organicMass(), ecosystem.moisture(), signal.get(y).get(x)));
        }
        List<FieldPost> posts = required.field().posts().values().stream().sorted(Comparator.comparingInt(ReferenceFieldPost::id))
                .map(ReferenceWorldView::post).toList();
        List<Campaign> campaigns = required.field().campaigns().values().stream().sorted(Comparator.comparingInt(ReferenceFieldCampaign::id))
                .map(ReferenceWorldView::campaign).toList();
        List<Sector> sectors = v2.sectors().values().stream().sorted(Comparator.comparingInt(ReferenceV2OperationalSector::y)
                        .thenComparingInt(ReferenceV2OperationalSector::x))
                .map(sector -> sector(sector, v2.sectorControl().get(sector.key()))).toList();
        List<Chrysalis> chrysalises = v2.chrysalises().values().stream().sorted(Comparator.comparingInt(ReferenceNeuralChrysalis::organId))
                .map(item -> new Chrysalis(item.organId(), item.sectorKey(), item.daysRemaining(), item.biomassCommitted(), item.status())).toList();
        return new ReferenceWorldView(required.day(), infection.width(), infection.height(), settlements, sites, nests, swarms, cells,
                posts, campaigns, ReferenceInfectionLimits.MAXIMUM_SWARMS, sectors, chrysalises,
                required.profile().personScale(), required.profile().discretePeople());
    }

    /** Python {@code dataclasses.asdict(world.view())} shape, using only JSON primitives. */
    public Map<String, Object> canonicalProjection() {
        return object("day", day, "width", width, "height", height,
                "settlements", settlements.stream().map(ReferenceWorldView::map).toList(),
                "sites", sites.stream().map(ReferenceWorldView::map).toList(),
                "nests", nests.stream().map(ReferenceWorldView::map).toList(),
                "swarms", swarms.stream().map(ReferenceWorldView::map).toList(),
                "cells", cells.stream().map(ReferenceWorldView::map).toList(),
                "posts", posts.stream().map(ReferenceWorldView::map).toList(),
                "campaigns", campaigns.stream().map(ReferenceWorldView::map).toList(),
                "maximum_swarms", maximumSwarms, "sectors", sectors.stream().map(ReferenceWorldView::map).toList(),
                "chrysalises", chrysalises.stream().map(ReferenceWorldView::map).toList(),
                "person_scale", personScale, "discrete_people", discretePeople);
    }

    private static Settlement settlement(ReferenceSettlement item) {
        LinkedHashMap<String, Double> stock = new LinkedHashMap<>();
        for (ReferenceResource resource : ReferenceResource.values()) stock.put(resource.name().toLowerCase(java.util.Locale.ROOT), item.amount(resource));
        return new Settlement(item.id(), item.x(), item.y(), item.alive(), item.population(), item.integrity(), item.threat(),
                item.illnessBurden(), item.defenceStrength(), item.doctrine(), stock, facilities(item.facilities()));
    }

    private static Map<String, Double> facilities(ReferenceFacilities item) {
        return Map.of("workshop", item.workshop(), "armory", item.armory(), "clinic", item.clinic(), "fortification", item.fortification());
    }

    private static Site site(ReferenceResourceSite item) {
        return new Site(item.id(), item.kind().name().toLowerCase(java.util.Locale.ROOT), item.x(), item.y(), item.ownerId(), item.capacity(),
                item.quality(), item.condition(), item.contamination(), item.operatorCompanyId());
    }

    private static Nest nest(ReferenceHiveOrgan item) {
        return new Nest(item.id(), item.x(), item.y(), item.kind().name().toLowerCase(java.util.Locale.ROOT), item.biomass(), item.samples(),
                item.vitality(), item.feral(), item.lastProjectDay());
    }

    private static Swarm swarm(ReferenceSwarm item) {
        LinkedHashMap<String, Double> composition = new LinkedHashMap<>();
        item.composition().entrySet().stream().filter(entry -> entry.getValue() > 0.0d).sorted(Map.Entry.comparingByKey())
                .forEach(entry -> composition.put(entry.getKey().id(), entry.getValue()));
        return new Swarm(item.id(), item.kind().id(), item.sourceOrganId(), item.x(), item.y(), composition, item.phase().id());
    }

    private static FieldPost post(ReferenceFieldPost item) {
        return new FieldPost(item.id(), item.kind().id(), item.x(), item.y(), item.campaignId(), item.leaderId(), item.status().id(),
                item.garrison(), item.isolationDays(), item.modules().stream().map(ReferenceFieldModuleKind::id).sorted().toList());
    }

    private static Campaign campaign(ReferenceFieldCampaign item) {
        return new Campaign(item.id(), item.kind().id(), item.leaderId(), item.contributors().stream().sorted().toList(), item.targetKind(),
                item.targetId(), item.targetX(), item.targetY(), item.phase().id(), item.postIds().stream().sorted().toList());
    }

    private static Sector sector(ReferenceV2OperationalSector item, ReferenceV2SectorControl control) {
        if (control == null) throw new IllegalStateException("operational sector has no control record: " + item.key());
        String routes = item.routeKeys().isEmpty() ? "—" : item.routeKeys().stream()
                .map(key -> key.lowerSettlementId() + "-" + key.upperSettlementId()).collect(java.util.stream.Collectors.joining(", "));
        return new Sector(item.key(), item.x(), item.y(), item.organicMass(), item.moisture(), item.scar(), item.infection(), item.sporeLoad(),
                item.humanAccess(), item.hiveInfluence(), item.infrastructureValue(), routes, control.state().id(), control.cordonStrength(),
                control.garrison(), control.supplied(), control.heldDays(), control.reason());
    }

    private static Map<String, Object> map(Settlement item) {
        return object("id", item.id(), "x", item.x(), "y", item.y(), "alive", item.alive(), "population", item.population(),
                "integrity", item.integrity(), "threat", item.threat(), "illness_burden", item.illnessBurden(), "defence", item.defence(),
                "doctrine", item.doctrine(), "stock", pairs(item.stock()), "facilities", pairs(item.facilities()));
    }

    private static Map<String, Object> map(Site item) {
        return object("id", item.id(), "kind", item.kind(), "x", item.x(), "y", item.y(), "owner_id", item.ownerId(), "capacity", item.capacity(),
                "quality", item.quality(), "condition", item.condition(), "contamination", item.contamination(), "operator_company_id", item.operatorCompanyId());
    }

    private static Map<String, Object> map(Nest item) {
        return object("id", item.id(), "x", item.x(), "y", item.y(), "kind", item.kind(), "biomass", item.biomass(), "samples", item.samples(),
                "vitality", item.vitality(), "feral", item.feral(), "last_project_day", item.lastProjectDay());
    }

    private static Map<String, Object> map(Swarm item) {
        return object("id", item.id(), "kind", item.kind(), "source_nest_id", item.sourceNestId(), "x", item.x(), "y", item.y(),
                "composition", pairs(item.composition()), "phase", item.phase());
    }

    private static Map<String, Object> map(Cell item) {
        return object("x", item.x(), "y", item.y(), "infection", item.infection(), "organic_mass", item.organicMass(), "moisture", item.moisture(), "signal", item.signal());
    }

    private static Map<String, Object> map(FieldPost item) {
        return object("id", item.id(), "kind", item.kind(), "x", item.x(), "y", item.y(), "campaign_id", item.campaignId(), "leader_id", item.leaderId(),
                "status", item.status(), "garrison", item.garrison(), "isolation_days", item.isolationDays(), "modules", item.modules());
    }

    private static Map<String, Object> map(Campaign item) {
        return object("id", item.id(), "kind", item.kind(), "leader_id", item.leaderId(), "contributors", item.contributors(), "target_kind", item.targetKind(),
                "target_id", item.targetId(), "target_x", item.targetX(), "target_y", item.targetY(), "phase", item.phase(), "post_ids", item.postIds());
    }

    private static Map<String, Object> map(Sector item) {
        return object("key", item.key(), "x", item.x(), "y", item.y(), "organic_mass", item.organicMass(), "moisture", item.moisture(), "scar", item.scar(),
                "infection", item.infection(), "spore_load", item.sporeLoad(), "human_access", item.humanAccess(), "hive_influence", item.hiveInfluence(),
                "infrastructure_value", item.infrastructureValue(), "route_summary", item.routeSummary(), "control", item.control(),
                "cordon_strength", item.cordonStrength(), "garrison", item.garrison(), "supplied", item.supplied(), "held_days", item.heldDays(),
                "control_reason", item.controlReason());
    }

    private static Map<String, Object> map(Chrysalis item) {
        return object("organ_id", item.organId(), "sector_key", item.sectorKey(), "days_remaining", item.daysRemaining(),
                "biomass_committed", item.biomassCommitted(), "status", item.status());
    }

    private static List<List<Object>> pairs(Map<String, Double> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> List.<Object>of(entry.getKey(), entry.getValue())).toList();
    }

    private static LinkedHashMap<String, Object> object(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("object entries must be pairs");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put((String) entries[index], entries[index + 1]);
        return result;
    }

    public record Settlement(int id, int x, int y, boolean alive, double population, double integrity, double threat, double illnessBurden,
                             double defence, String doctrine, Map<String, Double> stock, Map<String, Double> facilities) {
        public Settlement { doctrine = Objects.requireNonNull(doctrine, "doctrine"); stock = Map.copyOf(stock); facilities = Map.copyOf(facilities); }
    }
    public record Site(int id, String kind, int x, int y, Integer ownerId, double capacity, double quality, double condition, double contamination,
                       Integer operatorCompanyId) { public Site { kind = Objects.requireNonNull(kind, "kind"); } }
    public record Nest(int id, int x, int y, String kind, double biomass, double samples, double vitality, boolean feral, int lastProjectDay) {
        public Nest { kind = Objects.requireNonNull(kind, "kind"); }
    }
    public record Swarm(int id, String kind, Integer sourceNestId, double x, double y, Map<String, Double> composition, String phase) {
        public Swarm { kind = Objects.requireNonNull(kind, "kind"); composition = Map.copyOf(composition); phase = Objects.requireNonNull(phase, "phase"); }
    }
    public record Cell(int x, int y, double infection, double organicMass, double moisture, double signal) { }
    public record FieldPost(int id, String kind, int x, int y, int campaignId, int leaderId, String status, double garrison,
                            int isolationDays, List<String> modules) {
        public FieldPost {
            kind = Objects.requireNonNull(kind, "kind");
            status = Objects.requireNonNull(status, "status");
            modules = List.copyOf(modules);
        }
    }
    public record Campaign(int id, String kind, int leaderId, List<Integer> contributors, String targetKind, Integer targetId, int targetX,
                           int targetY, String phase, List<Integer> postIds) {
        public Campaign {
            kind = Objects.requireNonNull(kind, "kind");
            contributors = List.copyOf(contributors);
            targetKind = Objects.requireNonNull(targetKind, "targetKind");
            phase = Objects.requireNonNull(phase, "phase");
            postIds = List.copyOf(postIds);
        }
    }
    public record Sector(String key, int x, int y, double organicMass, double moisture, double scar, double infection, double sporeLoad,
                         double humanAccess, double hiveInfluence, double infrastructureValue, String routeSummary, String control,
                         double cordonStrength, double garrison, boolean supplied, int heldDays, String controlReason) {
        public Sector {
            key = Objects.requireNonNull(key, "key");
            routeSummary = Objects.requireNonNull(routeSummary, "routeSummary");
            control = Objects.requireNonNull(control, "control");
            controlReason = Objects.requireNonNull(controlReason, "controlReason");
        }
    }
    public record Chrysalis(int organId, String sectorKey, int daysRemaining, double biomassCommitted, String status) {
        public Chrysalis { sectorKey = Objects.requireNonNull(sectorKey, "sectorKey"); status = Objects.requireNonNull(status, "status"); }
    }
}
