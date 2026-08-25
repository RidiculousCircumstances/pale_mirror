package io.farfrontier.palemirror.frontier.reference;

import static io.farfrontier.palemirror.frontier.reference.ReferenceOperationRules.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical owner of the launch-side of Python {@code OperationManager}.
 *
 * <p>It has one all-or-nothing admission boundary: validity, capacity,
 * personnel, doctrine and every contributor's stock are checked before it
 * moves a named resident or removes one unit of supply.  The subsequent field
 * arrival/engagement phase is deliberately owned by the FieldWarfare port and
 * is added with that phase rather than emulating it in the legacy runtime.</p>
 */
public final class ReferenceOperationManager {
    private final ArrayList<ReferenceOperation> active = new ArrayList<>();
    private final ArrayList<ReferenceOperation> completed = new ArrayList<>();
    private int nextId = 1;

    public List<ReferenceOperation> active() { return List.copyOf(active); }
    /** Source-owned terminal operation records; they are not a second custody ledger. */
    public List<ReferenceOperation> completed() { return List.copyOf(completed); }
    public int nextId() { return nextId; }
    List<ReferenceOperation> mutableActive() { return active; }
    List<ReferenceOperation> mutableCompleted() { return completed; }

    /** Advance source operations after infection movement for the current simulation day. */
    public void step(ReferenceWorld world) { ReferenceOperationExecution.step(this, world); }

    /** Synchronise a strategic colony operation to its infection-owned swarm. */
    public void syncInfectionSwarms(ReferenceWorld world) { ReferenceOperationExecution.syncInfectionSwarms(this, world); }

    /** Apply patrol interception without granting patrols ownership of the swarm. */
    public void detectAndIntercept(ReferenceWorld world) { ReferenceOperationExecution.detectAndIntercept(this, world); }

    ReferenceCasualtyResult applyDiscreteCasualties(ReferenceWorld world, ReferenceOperation operation,
                                                    double killed, double wounded, String cause) {
        return ReferenceOperationExecution.applyDiscreteCasualties(world, operation, killed, wounded, cause);
    }

    public List<ReferenceOperation> activeFor(ReferenceAgentRef agent) {
        ReferenceAgentRef required = Objects.requireNonNull(agent, "agent");
        return active.stream().filter(operation -> operation.owner().equals(required)).toList();
    }

    public Map<ReferenceResource, Double> requirements(ReferenceOperationKind kind) {
        return immutableResources(baseRequirements(Objects.requireNonNull(kind, "kind"), 1.0d));
    }

    /** Exact source target lookup, including the route-index midpoint contract. */
    public static Optional<ReferencePoint> targetPosition(ReferenceWorld world, ReferenceTargetRef target) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceTargetRef value = Objects.requireNonNull(target, "target");
        if (value.kind() == ReferenceTargetKind.SETTLEMENT && value.id() != null) {
            ReferenceSettlement settlement = required.settlements().get(value.id());
            return settlement == null ? Optional.empty() : Optional.of(new ReferencePoint(settlement.x(), settlement.y()));
        }
        if (value.kind() == ReferenceTargetKind.SITE && value.id() != null) {
            ReferenceResourceSite site = required.resourceSites().get(value.id());
            return site == null ? Optional.empty() : Optional.of(new ReferencePoint(site.x(), site.y()));
        }
        if (value.kind() == ReferenceTargetKind.NEST && value.id() != null) {
            ReferenceHiveOrgan organ = required.infection().organs().get(value.id());
            return organ == null ? Optional.empty() : Optional.of(new ReferencePoint(organ.x(), organ.y()));
        }
        if (value.kind() == ReferenceTargetKind.ROUTE && value.id() != null) {
            List<ReferenceRoute> routes = required.trade().routes();
            if (value.id() < 0 || value.id() >= routes.size()) return Optional.empty();
            ReferenceRoute route = routes.get(value.id());
            ReferenceSettlement a = required.settlements().get(route.a());
            ReferenceSettlement b = required.settlements().get(route.b());
            return a == null || b == null ? Optional.empty() : Optional.of(new ReferencePoint((a.x() + b.x()) / 2.0d, (a.y() + b.y()) / 2.0d));
        }
        if (value.kind() == ReferenceTargetKind.FIELD_POST && value.id() != null) {
            ReferenceFieldPost post = required.field().posts().get(value.id());
            return post == null ? Optional.empty() : Optional.of(new ReferencePoint(post.x(), post.y()));
        }
        return value.x() == null || value.y() == null ? Optional.empty() : Optional.of(new ReferencePoint(value.x(), value.y()));
    }

    /** Source {@code requirements_for}; null means no legal operation can form. */
    public Map<ReferenceResource, Double> requirementsFor(ReferenceWorld world, ReferenceOperationKind kind, int leaderId,
                                                           ReferenceTargetRef target) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceSettlement settlement = required.settlements().get(leaderId);
        Optional<ReferencePoint> targetPosition = targetPosition(required, target);
        if (settlement == null || targetPosition.isEmpty()) return null;
        double personnel = personnelFor(settlement, kind);
        if (personnel <= 0.0d) return null;
        EnumMap<ReferenceResource, Double> values = baseRequirements(kind, required.profile().personScale());
        double distance = Math.hypot(targetPosition.get().x() - settlement.x(), targetPosition.get().y() - settlement.y());
        int travelDays = Math.max(1, (int) (distance / HUMAN_SPEED_OFFROAD) + 1);
        int provisionsDays = Math.max(MINIMUM_SUPPLY_DAYS, travelDays * 2);
        values.put(ReferenceResource.FOOD, Math.max(values.getOrDefault(ReferenceResource.FOOD, 0.0d), personnel * FOOD_PER_PERSON_DAY * provisionsDays));
        values.put(ReferenceResource.MEDICINE, Math.max(values.getOrDefault(ReferenceResource.MEDICINE, 0.0d), personnel * MEDICINE_PER_PERSON_DAY * provisionsDays));
        return immutableResources(values);
    }

    /**
     * Start a human operation after checking every contributor.  {@code null}
     * is the visible rejected command result; all settlement ledgers remain
     * unchanged on that branch.
     */
    public ReferenceOperation launchHuman(ReferenceWorld world, ReferenceOperationKind kind, int leaderId,
                                          ReferenceTargetRef target) {
        return launchHuman(world, kind, leaderId, target, null, null, null, null, Map.of());
    }

    public ReferenceOperation launchHuman(ReferenceWorld world, ReferenceOperationKind kind, int leaderId,
                                          ReferenceTargetRef target, Integer objectiveId,
                                          Map<Integer, ? extends Map<ReferenceResource, Double>> contributors,
                                          Map<ReferenceResource, Double> cargo,
                                          Map<Integer, ? extends Map<ReferenceResource, Double>> cargoContributors,
                                          Map<String, Object> details) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceOperationKind operationKind = Objects.requireNonNull(kind, "kind");
        ReferenceTargetRef operationTarget = Objects.requireNonNull(target, "target");
        ReferenceSettlement leader = required.settlements().get(leaderId);
        if (leader == null || !leader.alive()) return null;
        ReferenceAgentRef owner = new ReferenceAgentRef(ReferenceAgentKind.SETTLEMENT, leaderId);
        if (!canLaunchKind(owner, operationKind) || targetPosition(required, operationTarget).isEmpty()) return null;
        Map<ReferenceResource, Double> requirements = requirementsFor(required, operationKind, leaderId, operationTarget);
        if (requirements == null) return null;
        Map<Integer, Map<ReferenceResource, Double>> requirementContributors = contributors == null
                ? Map.of(leaderId, requirements) : copyResourceMatrix(contributors);
        if (!contributorsSatisfyRequirements(requirementContributors, requirements)) return null;
        if (!allContributorsCanPay(required, requirementContributors)) return null;
        Map<ReferenceResource, Double> requestedCargo = cargo == null ? Map.of() : Map.copyOf(cargo);
        Map<Integer, Map<ReferenceResource, Double>> cargoByContributor;
        if (cargoContributors == null) {
            cargoByContributor = Map.of(leaderId, requestedCargo);
        } else {
            LinkedHashMap<Integer, Map<ReferenceResource, Double>> merged = new LinkedHashMap<>(copyResourceMatrix(cargoContributors));
            EnumMap<ReferenceResource, Double> leaderCargo = enumResources(merged.getOrDefault(leaderId, Map.of()));
            requestedCargo.forEach((resource, amount) -> leaderCargo.merge(resource, amount, Double::sum));
            merged.put(leaderId, leaderCargo);
            cargoByContributor = merged;
        }
        if (!allContributorsCanPay(required, cargoByContributor)) return null;

        LinkedHashMap<Integer, Double> personnel = personnelByContributor(required, operationKind, leaderId, requirementContributors);
        if (personnel == null) return null;
        LinkedHashMap<Integer, EnumMap<ReferenceHumanUnitKind, Double>> compositions = compositions(required, operationKind, personnel);
        if (compositions == null) return null;

        // The only first mutation: named resident custody.  Stock removals are
        // still delayed until all deployments have succeeded.
        LinkedHashMap<Integer, List<String>> residentIds = new LinkedHashMap<>();
        try {
            for (Map.Entry<Integer, EnumMap<ReferenceHumanUnitKind, Double>> entry : compositions.entrySet()) {
                ReferenceSettlement contributor = required.settlements().get(entry.getKey());
                if (!contributor.discretePeople()) continue;
                Map<String, List<String>> deployed = contributor.deployPeople(nextId, stringComposition(entry.getValue()));
                residentIds.put(entry.getKey(), flatten(deployed));
                personnel.put(entry.getKey(), (double) residentIds.get(entry.getKey()).size());
            }
        } catch (RuntimeException rejected) {
            // Deployment preconditions were checked by the ledger. A failure is
            // an invariant breach rather than permission to reserve partial stock.
            throw new IllegalStateException("operation admission lost named-person custody before stock reservation", rejected);
        }

        EnumMap<ReferenceResource, Double> supplies = new EnumMap<>(ReferenceResource.class);
        LinkedHashMap<Integer, Map<ReferenceResource, Double>> actualContributors = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<ReferenceResource, Double>> entry : requirementContributors.entrySet()) {
            ReferenceSettlement contributor = required.settlements().get(entry.getKey());
            EnumMap<ReferenceResource, Double> actual = new EnumMap<>(ReferenceResource.class);
            entry.getValue().forEach((resource, amount) -> {
                double removed = contributor.remove(resource, amount);
                actual.put(resource, removed);
                supplies.merge(resource, removed, Double::sum);
            });
            actualContributors.put(entry.getKey(), actual);
        }
        EnumMap<ReferenceResource, Double> actualCargo = new EnumMap<>(ReferenceResource.class);
        for (Map.Entry<Integer, Map<ReferenceResource, Double>> entry : cargoByContributor.entrySet()) {
            ReferenceSettlement contributor = required.settlements().get(entry.getKey());
            Map<ReferenceResource, Double> actual = actualContributors.computeIfAbsent(entry.getKey(), ignored -> new EnumMap<>(ReferenceResource.class));
            entry.getValue().forEach((resource, amount) -> {
                double removed = contributor.remove(resource, amount);
                actualCargo.merge(resource, removed, Double::sum);
                actual.merge(resource, removed, Double::sum);
            });
        }
        for (Map.Entry<Integer, Double> entry : personnel.entrySet()) {
            if (!required.settlements().get(entry.getKey()).discretePeople()) {
                ReferenceSettlement contributor = required.settlements().get(entry.getKey());
                contributor.mobilizedPersonnel(contributor.mobilizedPersonnel() + entry.getValue());
            }
        }

        ReferenceOperation operation = new ReferenceOperation(nextId, ReferenceAgentKind.SETTLEMENT, operationKind, owner,
                operationTarget, required.day(), leader.x(), leader.y(), leader.x(), leader.y());
        operation.personnel(sum(personnel));
        operation.committedPersonnel(operation.personnel());
        operation.power(powerFor(combinedRoles(compositions), supplies));
        operation.supplies(supplies);
        operation.contributors(actualContributors);
        operation.personnelBySettlement(personnel);
        operation.unitCompositionBySettlement(compositions);
        operation.residentIdsBySettlement(residentIds);
        operation.objectiveId(objectiveId);
        List<ReferencePoint> path = humanWaypoints(required, leaderId, operationTarget);
        operation.waypoints(path);
        operation.returnWaypoints(returnWaypoints(path, leader));
        operation.cargo(actualCargo);
        operation.origin(new ReferenceTargetRef(ReferenceTargetKind.SETTLEMENT, leaderId));
        operation.details(details == null ? Map.of() : details);
        nextId++;
        active.add(operation);
        required.marketWorld().event("D" + required.day() + ": " + leader.name() + " launched " + operationKind.id()
                + " operation " + operation.id() + " toward " + operationTarget.key());
        return operation;
    }

    /** Source preview used by AI before it commits a raid from an existing post. */
    public ReferenceOperationPreview previewFromPost(ReferenceWorld world, ReferenceOperationKind kind, int postId,
                                                      ReferenceTargetRef target, Set<Integer> participantIds) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceFieldPost post = required.field().posts().get(postId);
        if (post == null || targetPosition(required, target).isEmpty()
                || (post.status() != ReferenceFieldPostStatus.ACTIVE && post.status() != ReferenceFieldPostStatus.ISOLATED)
                || personnelRule(kind) == null) return null;
        LinkedHashMap<Integer, Double> personnel = new LinkedHashMap<>();
        LinkedHashMap<Integer, EnumMap<ReferenceHumanUnitKind, Double>> compositions = new LinkedHashMap<>();
        for (int settlementId : participantIds.stream().sorted().toList()) {
            ReferenceSettlement settlement = required.settlements().get(settlementId);
            double garrison = post.garrisonBySettlement().getOrDefault(settlementId, 0.0d);
            if (settlement == null || !settlement.alive() || garrison <= 0.0d) continue;
            PersonnelRule rule = personnelRule(kind);
            double minimum = settlement.discretePeople() ? grayboxPersonnelMinimum(kind) : rule.minimum();
            double committed = Math.min(garrison, Math.max(minimum, settlement.population() * rule.populationRatio()));
            EnumMap<ReferenceHumanUnitKind, Double> composition = compositionFor(settlement, kind, committed);
            if (composition == null) return null;
            personnel.put(settlementId, committed);
            compositions.put(settlementId, composition);
        }
        double total = sum(personnel);
        if (total <= 0.0d) return null;
        EnumMap<ReferenceResource, Double> requirements = postRequirements(required, kind, post, target, total);
        if (!hasStock(post.stock(), requirements)) return null;
        Map<ReferenceHumanUnitKind, Double> roles = combinedRoles(compositions);
        return new ReferenceOperationPreview(total, powerFor(roles, requirements), roles);
    }

    /**
     * Exact launch side of Python {@code launch_from_post}.  A post is the
     * custody origin: people leave its named garrison and return there unless
     * a later withdrawal explicitly sends them home.
     */
    public ReferenceOperation launchFromPost(ReferenceWorld world, ReferenceOperationKind kind, int leaderId, int postId,
                                             ReferenceTargetRef target, Integer campaignId, Set<Integer> participantIds,
                                             Map<String, Object> details) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceSettlement leader = required.settlements().get(leaderId);
        ReferenceFieldPost post = required.field().posts().get(postId);
        if (leader == null || post == null || targetPosition(required, target).isEmpty()
                || (post.status() != ReferenceFieldPostStatus.ACTIVE && post.status() != ReferenceFieldPostStatus.ISOLATED)
                || !canLaunchKind(new ReferenceAgentRef(ReferenceAgentKind.SETTLEMENT, leaderId), kind)
                || personnelRule(kind) == null) return null;
        Set<Integer> participants = participantIds == null ? Set.of(leaderId) : participantIds;
        LinkedHashMap<Integer, Double> personnel = new LinkedHashMap<>();
        for (int settlementId : participants.stream().sorted().toList()) {
            ReferenceSettlement participant = required.settlements().get(settlementId);
            double garrison = post.garrisonBySettlement().getOrDefault(settlementId, 0.0d);
            if (participant == null || !participant.alive() || garrison <= 0.0d) continue;
            PersonnelRule rule = personnelRule(kind);
            double minimum = participant.discretePeople() ? grayboxPersonnelMinimum(kind) : rule.minimum();
            personnel.put(settlementId, Math.min(garrison, Math.max(minimum, participant.population() * rule.populationRatio())));
        }
        double total = sum(personnel);
        if (total <= 0.0d) return null;
        LinkedHashMap<Integer, EnumMap<ReferenceHumanUnitKind, Double>> compositions = compositions(required, kind, personnel);
        if (compositions == null) return null;
        LinkedHashMap<Integer, List<String>> residentIds = new LinkedHashMap<>();
        for (Map.Entry<Integer, EnumMap<ReferenceHumanUnitKind, Double>> entry : compositions.entrySet()) {
            ReferenceSettlement participant = required.settlements().get(entry.getKey());
            if (!participant.discretePeople()) continue;
            Map<String, List<String>> deployed = participant.deployPeopleFromFieldPost(nextId, post.id(), stringComposition(entry.getValue()),
                    post.residentIdsBySettlement().getOrDefault(entry.getKey(), List.of()));
            List<String> ids = flatten(deployed);
            residentIds.put(entry.getKey(), ids);
            personnel.put(entry.getKey(), (double) ids.size());
            List<String> retained = new ArrayList<>(post.mutableResidentIdsBySettlement().getOrDefault(entry.getKey(), List.of()));
            retained.removeAll(ids);
            post.mutableResidentIdsBySettlement().put(entry.getKey(), retained);
            post.mutableGarrisonBySettlement().put(entry.getKey(), (double) retained.size());
        }
        total = sum(personnel);
        EnumMap<ReferenceResource, Double> requirements = postRequirements(required, kind, post, target, total);
        if (!hasStock(post.stock(), requirements)) {
            restorePostResidents(required, post, residentIds);
            return null;
        }
        EnumMap<ReferenceResource, Double> supplies = new EnumMap<>(ReferenceResource.class);
        for (Map.Entry<ReferenceResource, Double> requirement : requirements.entrySet()) {
            double value = Math.min(post.stock(requirement.getKey()), requirement.getValue());
            supplies.put(requirement.getKey(), value);
            post.stock(requirement.getKey(), post.stock(requirement.getKey()) - value);
        }
        for (Map.Entry<Integer, Double> entry : personnel.entrySet()) {
            if (!required.settlements().get(entry.getKey()).discretePeople()) {
                post.mutableGarrisonBySettlement().merge(entry.getKey(), -entry.getValue(), Double::sum);
            }
        }
        ReferencePoint destination = targetPosition(required, target).orElseThrow();
        ReferenceOperation operation = new ReferenceOperation(nextId, ReferenceAgentKind.SETTLEMENT, kind,
                new ReferenceAgentRef(ReferenceAgentKind.SETTLEMENT, leaderId), target, required.day(), post.x(), post.y(), post.x(), post.y());
        operation.personnel(total);
        operation.committedPersonnel(total);
        operation.power(powerFor(combinedRoles(compositions), supplies));
        operation.supplies(supplies);
        LinkedHashMap<Integer, Map<ReferenceResource, Double>> contributors = new LinkedHashMap<>();
        personnel.keySet().forEach(settlementId -> contributors.put(settlementId, new EnumMap<>(supplies)));
        operation.contributors(contributors);
        operation.personnelBySettlement(personnel);
        operation.unitCompositionBySettlement(compositions);
        operation.residentIdsBySettlement(residentIds);
        operation.waypoints(List.of(destination));
        operation.returnWaypoints(List.of(new ReferencePoint(post.x(), post.y())));
        operation.origin(new ReferenceTargetRef(ReferenceTargetKind.FIELD_POST, post.id()));
        LinkedHashMap<String, Object> operationDetails = new LinkedHashMap<>();
        if (campaignId != null) operationDetails.put("campaign_id", campaignId);
        if (kind == ReferenceOperationKind.WITHDRAW) operationDetails.put("withdraw_to_home", true);
        if (details != null) operationDetails.putAll(details);
        operation.details(operationDetails);
        nextId++;
        active.add(operation);
        required.marketWorld().event("D" + required.day() + ": field post " + post.id() + " launched " + kind.id() + " operation " + operation.id());
        return operation;
    }

    /** Infection strategists call this only after their own source action creates the swarm/project. */
    public ReferenceOperation recordInfectionOperation(ReferenceWorld world, ReferenceOperationKind kind, int colonyId,
                                                        ReferenceTargetRef target, double x, double y, double power,
                                                        Integer objectiveId) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceOperation operation = new ReferenceOperation(nextId++, ReferenceAgentKind.COLONY, Objects.requireNonNull(kind, "kind"),
                new ReferenceAgentRef(ReferenceAgentKind.COLONY, colonyId), Objects.requireNonNull(target, "target"),
                required.day(), x, y, x, y);
        operation.power(power);
        operation.objectiveId(objectiveId);
        active.add(operation);
        return operation;
    }

    public double supportFor(int settlementId) {
        return active.stream().filter(operation -> operation.side() == ReferenceAgentKind.SETTLEMENT
                        && operation.status() == ReferenceOperationStatus.ON_STATION
                        && operation.target().kind() == ReferenceTargetKind.SETTLEMENT
                        && Objects.equals(operation.target().id(), settlementId)
                        && stationKind(operation.kind()))
                .mapToDouble(ReferenceOperation::power).sum();
    }

    public Map<ReferenceHumanUnitKind, Double> rolesSupporting(int settlementId) {
        EnumMap<ReferenceHumanUnitKind, Double> result = new EnumMap<>(ReferenceHumanUnitKind.class);
        for (ReferenceOperation operation : active) {
            if (operation.side() == ReferenceAgentKind.SETTLEMENT && operation.status() == ReferenceOperationStatus.ON_STATION
                    && operation.target().kind() == ReferenceTargetKind.SETTLEMENT && Objects.equals(operation.target().id(), settlementId)
                    && stationKind(operation.kind())) {
                rolesFor(operation).forEach((role, amount) -> result.merge(role, amount, Double::sum));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** Exact operation-side supply arithmetic; false visibly advances exhaustion. */
    public boolean consumeSupplies(ReferenceOperation operation) {
        ReferenceOperation required = Objects.requireNonNull(operation, "operation");
        double logistics = rolesFor(required).getOrDefault(ReferenceHumanUnitKind.LOGISTICS, 0.0d);
        double efficiency = 1.0d - logistics / Math.max(1.0d, required.personnel()) * ROLE_LOGISTICS_SUPPLY_MULTIPLIER;
        double food = required.personnel() * FOOD_PER_PERSON_DAY * efficiency;
        double medicine = required.personnel() * MEDICINE_PER_PERSON_DAY * efficiency;
        boolean hasFood = required.mutableSupplies().getOrDefault(ReferenceResource.FOOD, 0.0d) + 1.0e-9d >= food;
        boolean hasMedicine = required.mutableSupplies().getOrDefault(ReferenceResource.MEDICINE, 0.0d) + 1.0e-9d >= medicine;
        if (hasFood) required.mutableSupplies().merge(ReferenceResource.FOOD, -food, Double::sum);
        if (hasMedicine) required.mutableSupplies().merge(ReferenceResource.MEDICINE, -medicine, Double::sum);
        if (hasFood && hasMedicine) { required.unsuppliedDays(0); return true; }
        required.unsuppliedDays(required.unsuppliedDays() + 1);
        required.power(required.power() * (1.0d - EXHAUSTION_POWER_LOSS));
        return false;
    }

    /** Exact source movement primitive; arrival snaps rather than accumulating coordinate drift. */
    public boolean moveTowards(ReferenceOperation operation, double targetX, double targetY, double speed) {
        ReferenceOperation required = Objects.requireNonNull(operation, "operation");
        double dx = targetX - required.x();
        double dy = targetY - required.y();
        double distance = Math.hypot(dx, dy);
        if (distance <= Math.max(ARRIVAL_DISTANCE, speed)) { required.position(targetX, targetY); return true; }
        required.position(required.x() + dx / distance * speed, required.y() + dy / distance * speed);
        return false;
    }

    public boolean moveAlong(ReferenceOperation operation, List<ReferencePoint> waypoints, boolean roadSpeed, double speedMultiplier) {
        ReferenceOperation required = Objects.requireNonNull(operation, "operation");
        List<ReferencePoint> points = Objects.requireNonNull(waypoints, "waypoints");
        if (required.waypointIndex() >= points.size()) return true;
        ReferencePoint target = points.get(required.waypointIndex());
        double speed = (roadSpeed ? HUMAN_SPEED_ROAD : HUMAN_SPEED_OFFROAD) * speedMultiplier;
        if (moveTowards(required, target.x(), target.y(), speed)) required.waypointIndex(required.waypointIndex() + 1);
        return required.waypointIndex() >= points.size();
    }

    private boolean canLaunchKind(ReferenceAgentRef owner, ReferenceOperationKind kind) {
        List<ReferenceOperation> current = activeFor(owner);
        if (kind.isFieldOperation()) {
            long fieldActive = current.stream().filter(operation -> operation.kind().isFieldOperation()
                    && !(operation.resolved() && operation.personnel() <= 0.01d)).count();
            return fieldActive < MAX_ACTIVE_FIELD_PER_SETTLEMENT;
        }
        return current.stream().filter(operation -> !operation.kind().isFieldOperation()).count() < MAX_ACTIVE_PER_SETTLEMENT;
    }

    private static List<ReferencePoint> humanWaypoints(ReferenceWorld world, int leaderId, ReferenceTargetRef target) {
        ReferencePoint position = targetPositionStatic(world, target);
        if (position == null) return List.of();
        int destinationId;
        if (target.kind() == ReferenceTargetKind.SETTLEMENT && target.id() != null) destinationId = target.id();
        else destinationId = world.settlements().values().stream().filter(ReferenceSettlement::alive)
                .min(java.util.Comparator.comparingDouble(settlement -> Math.hypot(settlement.x() - position.x(), settlement.y() - position.y())))
                .map(ReferenceSettlement::id).orElse(leaderId);
        ReferenceTradePath path = world.trade().shortestPath(leaderId, destinationId, ReferenceResource.FOOD, world.settlements(), world.day());
        ArrayList<ReferencePoint> points = new ArrayList<>();
        if (path != null) path.nodes().subList(1, path.nodes().size()).forEach(node -> { ReferenceSettlement settlement = world.settlements().get(node); points.add(new ReferencePoint(settlement.x(), settlement.y())); });
        if (points.isEmpty() || !points.getLast().equals(position)) points.add(position);
        return List.copyOf(points);
    }

    private static ReferencePoint targetPositionStatic(ReferenceWorld world, ReferenceTargetRef target) {
        return targetPosition(world, target).orElse(null);
    }

    private static void restorePostResidents(ReferenceWorld world, ReferenceFieldPost post, Map<Integer, List<String>> residents) {
        for (Map.Entry<Integer, List<String>> entry : residents.entrySet()) {
            ReferenceSettlement participant = world.settlements().get(entry.getKey());
            participant.assignPeopleToFieldPost(entry.getValue(), post.id());
            ArrayList<String> retained = new ArrayList<>(post.mutableResidentIdsBySettlement().getOrDefault(entry.getKey(), List.of()));
            retained.addAll(entry.getValue());
            retained.sort(String::compareTo);
            ArrayList<String> unique = new ArrayList<>();
            String prior = null;
            for (String id : retained) { if (!id.equals(prior)) unique.add(id); prior = id; }
            post.mutableResidentIdsBySettlement().put(entry.getKey(), unique);
            post.mutableGarrisonBySettlement().put(entry.getKey(), (double) unique.size());
        }
    }

    private static List<ReferencePoint> returnWaypoints(List<ReferencePoint> path, ReferenceSettlement leader) {
        ArrayList<ReferencePoint> result = new ArrayList<>();
        for (int index = path.size() - 2; index >= 0; index--) result.add(path.get(index));
        result.add(new ReferencePoint(leader.x(), leader.y()));
        return List.copyOf(result);
    }

    private static EnumMap<ReferenceResource, Double> postRequirements(ReferenceWorld world, ReferenceOperationKind kind,
                                                                         ReferenceFieldPost post, ReferenceTargetRef target, double personnel) {
        EnumMap<ReferenceResource, Double> values = baseRequirements(kind, world.profile().personScale());
        ReferencePoint position = targetPositionStatic(world, target);
        double distance = Math.hypot(position.x() - post.x(), position.y() - post.y());
        int days = Math.max(MINIMUM_SUPPLY_DAYS, (int) (distance / HUMAN_SPEED_OFFROAD) + 2);
        values.put(ReferenceResource.FOOD, Math.max(values.getOrDefault(ReferenceResource.FOOD, 0.0d), personnel * FOOD_PER_PERSON_DAY * days));
        values.put(ReferenceResource.MEDICINE, Math.max(values.getOrDefault(ReferenceResource.MEDICINE, 0.0d), personnel * MEDICINE_PER_PERSON_DAY * days));
        return values;
    }

    private static boolean contributorsSatisfyRequirements(Map<Integer, Map<ReferenceResource, Double>> contributors,
                                                            Map<ReferenceResource, Double> requirements) {
        for (Map.Entry<ReferenceResource, Double> requirement : requirements.entrySet()) {
            double supplied = contributors.values().stream().mapToDouble(values -> values.getOrDefault(requirement.getKey(), 0.0d)).sum();
            if (supplied + 1.0e-9d < requirement.getValue()) return false;
        }
        return true;
    }

    private static boolean allContributorsCanPay(ReferenceWorld world, Map<Integer, Map<ReferenceResource, Double>> contributors) {
        for (Map.Entry<Integer, Map<ReferenceResource, Double>> entry : contributors.entrySet()) {
            ReferenceSettlement settlement = world.settlements().get(entry.getKey());
            if (settlement == null || !settlement.alive()) return false;
            for (Map.Entry<ReferenceResource, Double> value : entry.getValue().entrySet()) if (settlement.amount(value.getKey()) + 1.0e-9d < value.getValue()) return false;
        }
        return true;
    }

    private static boolean hasStock(Map<ReferenceResource, Double> stock, Map<ReferenceResource, Double> requirements) {
        return requirements.entrySet().stream().allMatch(entry -> stock.getOrDefault(entry.getKey(), 0.0d) + 1.0e-9d >= entry.getValue());
    }

}
