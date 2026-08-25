package io.farfrontier.palemirror.frontier.reference;

import java.util.List;
import java.util.Objects;

/** Immutable, complete presentation input for one source-parity graybox frame. */
public record ReferenceGrayboxSnapshot(
        int day,
        String profileId,
        String stateRevision,
        ReferenceGrayboxLayout.Bounds bounds,
        List<Cell> cells,
        List<Settlement> settlements,
        List<Facility> facilities,
        List<ResourceSite> resourceSites,
        List<Route> routes,
        List<HiveOrgan> hiveOrgans,
        List<Bioform> bioforms,
        List<Resident> residents,
        List<FieldPost> fieldPosts,
        List<FieldLink> fieldLinks,
        List<Activity> activities,
        List<Cargo> cargoes,
        List<Interaction> interactions,
        List<Sector> sectors,
        List<Chrysalis> chrysalises,
        List<String> events
) {
    public ReferenceGrayboxSnapshot {
        if (day < 0) throw new IllegalArgumentException("graybox day must not be negative");
        profileId = required(profileId, "profileId");
        stateRevision = required(stateRevision, "stateRevision");
        if (!stateRevision.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("graybox state revision must be SHA-256 hex");
        bounds = Objects.requireNonNull(bounds, "bounds");
        cells = copied(cells, "cells");
        settlements = copied(settlements, "settlements");
        facilities = copied(facilities, "facilities");
        resourceSites = copied(resourceSites, "resourceSites");
        routes = copied(routes, "routes");
        hiveOrgans = copied(hiveOrgans, "hiveOrgans");
        bioforms = copied(bioforms, "bioforms");
        residents = copied(residents, "residents");
        fieldPosts = copied(fieldPosts, "fieldPosts");
        fieldLinks = copied(fieldLinks, "fieldLinks");
        activities = copied(activities, "activities");
        cargoes = copied(cargoes, "cargoes");
        interactions = copied(interactions, "interactions");
        sectors = copied(sectors, "sectors");
        chrysalises = copied(chrysalises, "chrysalises");
        events = copied(events, "events");
        if (cells.size() != ReferenceWorldConfig.SOURCE_WIDTH * ReferenceWorldConfig.SOURCE_HEIGHT) {
            throw new IllegalArgumentException("graybox snapshot must contain every logical cell");
        }
    }

    public record Cell(int x, int y, ReferenceGrayboxLayout.Rectangle rectangle, double infection,
                       double organicMass, double moisture, double signal, String colour) {
        public Cell { rectangle = Objects.requireNonNull(rectangle, "rectangle"); colour = required(colour, "colour"); }
    }

    public record Settlement(int id, String name, int logicalX, int logicalY, ReferenceGrayboxLayout.Rectangle rectangle,
                             boolean alive, double population, double integrity, double threat, double illnessBurden,
                             String civicState, double foodReserveDays, double rationFraction, String colour) {
        public Settlement {
            name = required(name, "name");
            rectangle = Objects.requireNonNull(rectangle, "rectangle");
            civicState = required(civicState, "civicState");
            colour = required(colour, "colour");
        }
    }

    public record Facility(String id, int settlementId, String kind, ReferenceGrayboxLayout.Rectangle rectangle,
                           double level, String colour) {
        public Facility { id = required(id, "id"); kind = required(kind, "kind"); rectangle = Objects.requireNonNull(rectangle, "rectangle"); colour = required(colour, "colour"); }
    }

    public record ResourceSite(int id, String kind, int ownerSettlementId, ReferenceGrayboxLayout.Rectangle rectangle,
                               double capacity, double condition, double contamination, String colour) {
        public ResourceSite { kind = required(kind, "kind"); rectangle = Objects.requireNonNull(rectangle, "rectangle"); colour = required(colour, "colour"); }
    }

    public record Route(String id, int settlementA, int settlementB, ReferenceGrayboxLayout.Point start,
                        ReferenceGrayboxLayout.Point end, double capacity, double risk, double infection,
                        boolean quarantined, boolean disrupted, String colour) {
        public Route { id = required(id, "id"); start = Objects.requireNonNull(start, "start"); end = Objects.requireNonNull(end, "end"); colour = required(colour, "colour"); }
    }

    public record HiveOrgan(int id, String kind, ReferenceGrayboxLayout.Rectangle rectangle, double biomass,
                            double vitality, boolean feral, String colour) {
        public HiveOrgan { kind = required(kind, "kind"); rectangle = Objects.requireNonNull(rectangle, "rectangle"); colour = required(colour, "colour"); }
    }

    /** One descriptor is exactly one source discrete bioform and one future managed Zombie. */
    public record Bioform(String id, int swarmId, String kind, ReferenceGrayboxLayout.Point position,
                          String phase, boolean feral, String colour) {
        public Bioform {
            id = required(id, "id");
            kind = required(kind, "kind");
            position = Objects.requireNonNull(position, "position");
            phase = required(phase, "phase");
            colour = required(colour, "colour");
        }
    }

    /** One descriptor is exactly one source resident and one future managed Villager. */
    public record Resident(String id, int homeSettlementId, String occupation, String economicClass,
                           String location, Integer locationId, String condition, String deploymentRole,
                           ReferenceGrayboxLayout.Point position, String colour) {
        public Resident {
            id = required(id, "id");
            occupation = required(occupation, "occupation");
            economicClass = required(economicClass, "economicClass");
            location = required(location, "location");
            condition = required(condition, "condition");
            position = Objects.requireNonNull(position, "position");
            colour = required(colour, "colour");
        }
    }

    public record FieldPost(int id, int campaignId, String kind, String status, ReferenceGrayboxLayout.Rectangle rectangle,
                            double integrity, int garrison, int wounded, List<String> modules, String colour) {
        public FieldPost {
            kind = required(kind, "kind");
            status = required(status, "status");
            rectangle = Objects.requireNonNull(rectangle, "rectangle");
            modules = copied(modules, "modules");
            colour = required(colour, "colour");
        }
    }

    public record FieldLink(int id, int campaignId, String kind, int postA, int postB, String status, String colour) {
        public FieldLink { kind = required(kind, "kind"); status = required(status, "status"); colour = required(colour, "colour"); }
    }

    /** Operation/campaign record used for readable labels and phase-coloured overlays. */
    public record Activity(String id, String family, String kind, String phase, ReferenceGrayboxLayout.Point position,
                           double personnel, double indicator, boolean terminal, String colour) {
        public Activity {
            id = required(id, "id");
            family = required(family, "family");
            kind = required(kind, "kind");
            phase = required(phase, "phase");
            position = Objects.requireNonNull(position, "position");
            colour = required(colour, "colour");
        }
    }

    /** One resource-specific pallet owned by an exact source operation or field post. */
    public record Cargo(String id, String ownerKind, int ownerId, String resource, double quantity,
                        ReferenceGrayboxLayout.Rectangle rectangle, String colour) {
        public Cargo {
            id = required(id, "id");
            ownerKind = required(ownerKind, "ownerKind");
            if (!(ownerKind.equals("operation") || ownerKind.equals("field_post"))) {
                throw new IllegalArgumentException("graybox cargo owner kind is invalid");
            }
            if (ownerId < 1) throw new IllegalArgumentException("graybox cargo owner ID is invalid");
            resource = required(resource, "resource");
            if (!Double.isFinite(quantity) || quantity <= 0.0d) throw new IllegalArgumentException("cargo quantity must be positive and finite");
            rectangle = Objects.requireNonNull(rectangle, "rectangle");
            colour = required(colour, "colour");
        }

        /** Compatibility constructor for an operation-owned pallet. */
        public Cargo(String id, int operationId, String resource, double quantity,
                     ReferenceGrayboxLayout.Rectangle rectangle, String colour) {
            this(id, "operation", operationId, resource, quantity, rectangle, colour);
        }
    }

    /**
     * Exact source fact distributed across visible physical slots.
     *
     * <p>Slots are part of this immutable projection, so NeoForge has no
     * latitude to choose a different weight, semantic owner, or interaction
     * geometry for an ordinary graybox block break.</p>
     */
    public record Interaction(String id, String subjectId, String kind, double totalWeight, int yOffset,
                              List<ReferenceGrayboxLayout.Point> slots, String colour) {
        public Interaction {
            id = required(id, "id");
            subjectId = required(subjectId, "subjectId");
            kind = required(kind, "kind");
            if (!Double.isFinite(totalWeight) || totalWeight <= 0.0d) {
                throw new IllegalArgumentException("interaction total weight must be positive and finite");
            }
            if (yOffset < 0 || yOffset > 4) throw new IllegalArgumentException("interaction y offset is invalid");
            slots = copied(slots, "slots");
            if (slots.isEmpty() || slots.size() > 64 || slots.stream().distinct().count() != slots.size()) {
                throw new IllegalArgumentException("interaction slots are invalid");
            }
            colour = required(colour, "colour");
        }
    }

    public record Sector(String key, ReferenceGrayboxLayout.Rectangle rectangle, String control, double infection,
                         double sporeLoad, double humanAccess, double hiveInfluence, boolean supplied, String colour) {
        public Sector { key = required(key, "key"); rectangle = Objects.requireNonNull(rectangle, "rectangle"); control = required(control, "control"); colour = required(colour, "colour"); }
    }

    public record Chrysalis(int organId, String sectorKey, int daysRemaining, double biomassCommitted, String status,
                            ReferenceGrayboxLayout.Rectangle rectangle, String colour) {
        public Chrysalis {
            sectorKey = required(sectorKey, "sectorKey");
            status = required(status, "status");
            rectangle = Objects.requireNonNull(rectangle, "rectangle");
            colour = required(colour, "colour");
        }
    }

    private static String required(String value, String name) { return Objects.requireNonNull(value, name); }
    private static <T> List<T> copied(List<T> values, String name) { return List.copyOf(Objects.requireNonNull(values, name)); }
}
