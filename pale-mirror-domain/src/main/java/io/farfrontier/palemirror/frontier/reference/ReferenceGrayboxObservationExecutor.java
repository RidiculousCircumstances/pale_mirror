package io.farfrontier.palemirror.frontier.reference;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Objects;

/** Applies graybox facts through the one owner that currently holds a named resident. */
final class ReferenceGrayboxObservationExecutor {
    private static final int FRONT_CAMPAIGN_OPERATION_OFFSET = 1_000_000;

    private ReferenceGrayboxObservationExecutor() { }

    static ReferenceGrayboxObservationOutcome apply(
            ReferenceWorld world,
            ReferenceGrayboxResidentObservation observation
    ) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceGrayboxResidentObservation event = Objects.requireNonNull(observation, "observation");
        ReferenceGrayboxLayout.requireSupported(required);
        String before = ReferenceGrayboxProjection.from(required).stateRevision();
        if (!before.equals(event.observedStateRevision())) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_STALE,
                "observation was made against an older graybox state", before);

        ResidentOwner owner = findResident(required, event.residentId());
        if (owner == null) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_UNKNOWN,
                "resident no longer exists in canonical state", before);
        boolean applied = switch (owner.resident().location()) {
            case SETTLEMENT -> applySettlement(owner, event.kind());
            case OPERATION -> applyOperation(required, owner, event.kind());
            case FIELD_POST -> applyFieldPost(required, owner, event.kind());
        };
        if (!applied) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT,
                "resident custody no longer accepts this observation", before);

        // The Python daily engine does this after combat/demography. A physical
        // named death happens between days, so reconcile immediately before
        // persisting an observable state with a company employee reference.
        required.microeconomy().reconcileIndividualEmployment(required.marketWorld());
        required.marketWorld().event("D" + required.day() + ": physical resident " + event.kind().name().toLowerCase()
                + " " + event.residentId() + " (" + event.eventId() + ")");
        required.assertProfileInvariants();
        String after = ReferenceGrayboxProjection.from(required).stateRevision();
        return outcome(event, ReferenceGrayboxObservationOutcome.Status.APPLIED, "canonical resident custody updated", after);
    }

    static ReferenceGrayboxObservationOutcome apply(
            ReferenceWorld world,
            ReferenceGrayboxBioformObservation observation
    ) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceGrayboxBioformObservation event = Objects.requireNonNull(observation, "observation");
        ReferenceGrayboxLayout.requireSupported(required);
        String before = ReferenceGrayboxProjection.from(required).stateRevision();
        if (!before.equals(event.observedStateRevision())) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_STALE,
                "observation was made against an older graybox state", before);
        boolean known = required.infection().swarms().stream().anyMatch(swarm -> swarm.hasExactBioform(event.bioformId()));
        if (!known) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_UNKNOWN,
                "bioform no longer exists in canonical state", before);
        if (!ReferenceBioformObservationMutation.killExact(required.infection(), event.bioformId())) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT,
                "bioform custody no longer accepts this observation", before);

        required.operations().syncInfectionSwarms(required);
        required.marketWorld().event("D" + required.day() + ": physical bioform killed " + event.bioformId() + " (" + event.eventId() + ")");
        required.assertProfileInvariants();
        String after = ReferenceGrayboxProjection.from(required).stateRevision();
        return outcome(event, ReferenceGrayboxObservationOutcome.Status.APPLIED, "canonical bioform custody updated", after);
    }

    static ReferenceGrayboxObservationOutcome apply(
            ReferenceWorld world,
            ReferenceGrayboxStructureObservation observation
    ) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceGrayboxStructureObservation event = Objects.requireNonNull(observation, "observation");
        ReferenceGrayboxLayout.requireSupported(required);
        String before = ReferenceGrayboxProjection.from(required).stateRevision();
        if (!before.equals(event.observedStateRevision())) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_STALE,
                "observation was made against an older graybox state", before);

        StructureMutation mutation = switch (event.kind()) {
            case FACILITY_DAMAGED -> damageFacility(required, event);
            case SITE_DAMAGED -> damageSite(required, event);
            case ROUTE_DAMAGED -> damageRoute(required, event);
            case ORGAN_DAMAGED -> damageOrgan(required, event);
            case OPERATION_CARGO_LOST -> loseOperationCargo(required, event);
            case FIELD_POST_CARGO_LOST -> loseFieldPostCargo(required, event);
            case FIELD_POST_DAMAGED -> damageFieldPost(required, event);
        };
        if (!mutation.applied()) return outcome(event, mutation.status(), mutation.reason(), before);

        required.marketWorld().event("D" + required.day() + ": physical " + event.kind().name().toLowerCase(Locale.ROOT)
                + " " + event.subjectId() + " weight=" + String.format(Locale.ROOT, "%.6f", mutation.appliedWeight())
                + " (" + event.eventId() + ")");
        required.assertProfileInvariants();
        String after = ReferenceGrayboxProjection.from(required).stateRevision();
        return outcome(event, ReferenceGrayboxObservationOutcome.Status.APPLIED, "canonical structure owner updated", after);
    }

    private static boolean applySettlement(ResidentOwner owner, ReferenceGrayboxResidentObservation.Kind kind) {
        return ReferenceResidentObservationMutation.apply(owner.settlement(), owner.resident().id(), kind);
    }

    private static boolean applyOperation(ReferenceWorld world, ResidentOwner owner, ReferenceGrayboxResidentObservation.Kind kind) {
        Integer reference = owner.resident().locationRef();
        if (reference == null) return false;
        if (reference >= FRONT_CAMPAIGN_OPERATION_OFFSET) {
            ReferenceFrontCampaign campaign = world.v2().frontCampaigns().get(reference - FRONT_CAMPAIGN_OPERATION_OFFSET);
            return campaign != null && ReferenceV2Frontier.applyExactResidentObservation(campaign, owner.settlement(),
                    owner.resident().id(), kind);
        }
        ReferenceOperation operation = world.operations().active().stream().filter(item -> item.id() == reference).findFirst().orElse(null);
        return operation != null && ReferenceOperationExecution.applyExactResidentObservation(world, operation, owner.settlement(),
                owner.resident().id(), kind);
    }

    private static boolean applyFieldPost(ReferenceWorld world, ResidentOwner owner, ReferenceGrayboxResidentObservation.Kind kind) {
        Integer reference = owner.resident().locationRef();
        ReferenceFieldPost post = reference == null ? null : world.field().posts().get(reference);
        return post != null && ReferenceFieldExecution.applyExactResidentObservation(post, owner.settlement(), owner.resident().id(), kind);
    }

    private static ResidentOwner findResident(ReferenceWorld world, String residentId) {
        ResidentOwner result = null;
        for (ReferenceSettlement settlement : world.settlements().values()) {
            ReferenceResident resident = settlement.residents() == null ? null : settlement.residents().resident(residentId);
            if (resident == null) continue;
            if (result != null) throw new IllegalStateException("resident has duplicate canonical ownership: " + residentId);
            result = new ResidentOwner(settlement, resident);
        }
        return result;
    }

    private static ReferenceGrayboxObservationOutcome outcome(
            ReferenceGrayboxResidentObservation event,
            ReferenceGrayboxObservationOutcome.Status status,
            String reason,
            String revision
    ) {
        return new ReferenceGrayboxObservationOutcome(event.eventId(), status, reason, revision);
    }

    private static ReferenceGrayboxObservationOutcome outcome(
            ReferenceGrayboxBioformObservation event,
            ReferenceGrayboxObservationOutcome.Status status,
            String reason,
            String revision
    ) {
        return new ReferenceGrayboxObservationOutcome(event.eventId(), status, reason, revision);
    }

    private static ReferenceGrayboxObservationOutcome outcome(
            ReferenceGrayboxStructureObservation event,
            ReferenceGrayboxObservationOutcome.Status status,
            String reason,
            String revision
    ) {
        return new ReferenceGrayboxObservationOutcome(event.eventId(), status, reason, revision);
    }

    private static StructureMutation damageFacility(ReferenceWorld world, ReferenceGrayboxStructureObservation event) {
        String[] parts = event.subjectId().split(":", -1);
        if (parts.length != 4 || !parts[0].equals("settlement") || !parts[2].equals("facility")) return unknown("invalid facility subject");
        Integer settlementId = integer(parts[1]);
        ReferenceSettlement settlement = settlementId == null ? null : world.settlements().get(settlementId);
        if (settlement == null) return unknown("unknown facility");
        double before;
        double after;
        switch (parts[3]) {
            case "civic_hall" -> {
                before = settlement.integrity();
                after = Math.max(0.0d, before - event.weight());
                settlement.integrity(after);
            }
            case "workshop" -> {
                before = settlement.facilities().workshop();
                after = Math.max(0.0d, before - event.weight());
                settlement.facilities().workshop(after);
            }
            case "armory" -> {
                before = settlement.facilities().armory();
                after = Math.max(0.0d, before - event.weight());
                settlement.facilities().armory(after);
            }
            case "clinic" -> {
                before = settlement.facilities().clinic();
                after = Math.max(0.0d, before - event.weight());
                settlement.facilities().clinic(after);
            }
            case "fortification" -> {
                before = settlement.facilities().fortification();
                after = Math.max(0.0d, before - event.weight());
                settlement.facilities().fortification(after);
            }
            default -> {
                return conflict("facility has no canonical damage owner");
            }
        }
        return applied(before - after);
    }

    private static StructureMutation damageSite(ReferenceWorld world, ReferenceGrayboxStructureObservation event) {
        Integer siteId = numericSubject(event.subjectId(), "site");
        ReferenceResourceSite site = siteId == null ? null : world.resourceSites().get(siteId);
        if (site == null) return unknown("unknown site");
        double before = site.condition();
        site.condition(Math.max(0.0d, before - event.weight()));
        world.refreshPrimaryCapacity();
        return applied(before - site.condition());
    }

    private static StructureMutation damageRoute(ReferenceWorld world, ReferenceGrayboxStructureObservation event) {
        String[] parts = event.subjectId().split(":", -1);
        if (parts.length != 3 || !parts[0].equals("route")) return unknown("invalid route subject");
        Integer first = integer(parts[1]);
        Integer second = integer(parts[2]);
        if (first == null || second == null) return unknown("invalid route subject");
        ReferenceRouteKey key = ReferenceRouteKey.between(first, second);
        ReferenceRoute route = world.trade().routes().stream().filter(item -> item.key().equals(key)).findFirst().orElse(null);
        if (route == null) return unknown("unknown route");
        double before = route.capacity();
        route.capacity(Math.max(0.0d, before - event.weight()));
        return applied(before - route.capacity());
    }

    private static StructureMutation damageOrgan(ReferenceWorld world, ReferenceGrayboxStructureObservation event) {
        Integer organId = numericSubject(event.subjectId(), "organ");
        ReferenceHiveOrgan organ = organId == null ? null : world.infection().organs().get(organId);
        if (organ == null) return unknown("unknown organ");
        double applied = Math.min(organ.vitality(), event.weight());
        organ.vitality(Math.max(0.0d, organ.vitality() - event.weight()));
        organ.biomass(Math.max(0.0d, organ.biomass() - applied * .32d));
        world.infection().recordDamage("combat", applied);
        world.infection().removeDestroyedOrgans();
        return applied(applied);
    }

    private static StructureMutation loseOperationCargo(ReferenceWorld world, ReferenceGrayboxStructureObservation event) {
        String[] parts = event.subjectId().split(":", -1);
        if (parts.length != 4 || !parts[0].equals("operation") || !parts[2].equals("cargo")) {
            return unknown("invalid operation cargo subject");
        }
        Integer operationId = integer(parts[1]);
        ReferenceResource resource;
        try {
            resource = ReferenceResource.valueOf(parts[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return unknown("invalid operation cargo subject");
        }
        ReferenceOperation operation = operationId == null ? null : world.operations().active().stream()
                .filter(item -> item.id() == operationId).findFirst().orElse(null);
        if (operation == null) return unknown("unknown active operation");
        EnumMap<ReferenceResource, Double> cargo = new EnumMap<>(ReferenceResource.class);
        cargo.putAll(operation.cargo());
        double before = cargo.getOrDefault(resource, 0.0d);
        cargo.put(resource, Math.max(0.0d, before - event.weight()));
        operation.cargo(cargo);
        return applied(before - cargo.get(resource));
    }

    private static StructureMutation loseFieldPostCargo(ReferenceWorld world, ReferenceGrayboxStructureObservation event) {
        String[] parts = event.subjectId().split(":", -1);
        if (parts.length != 4 || !parts[0].equals("field_post") || !parts[2].equals("cargo")) {
            return unknown("invalid field post cargo subject");
        }
        Integer postId = integer(parts[1]);
        ReferenceResource resource;
        try {
            resource = ReferenceResource.valueOf(parts[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return unknown("invalid field post cargo subject");
        }
        ReferenceFieldPost post = postId == null ? null : world.field().posts().get(postId);
        if (post == null) return unknown("unknown field post");
        double before = post.stock(resource);
        double after = Math.max(0.0d, before - event.weight());
        post.stock(resource, after);
        return applied(before - after);
    }

    private static StructureMutation damageFieldPost(ReferenceWorld world, ReferenceGrayboxStructureObservation event) {
        Integer postId = numericSubject(event.subjectId(), "field_post");
        if (postId == null) return unknown("invalid field post subject");
        ReferenceFieldWarfare.MaterializedPostDamage result = world.field().applyMaterializedPostDamage(world, postId, event.weight());
        if (result.applied()) return applied(result.appliedWeight());
        return result.reason().equals("unknown field post") ? unknown(result.reason()) : conflict(result.reason());
    }

    private static Integer numericSubject(String subject, String prefix) {
        String[] parts = subject.split(":", -1);
        return parts.length == 2 && parts[0].equals(prefix) ? integer(parts[1]) : null;
    }

    private static Integer integer(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static StructureMutation applied(double weight) {
        return weight > 0.0d ? new StructureMutation(true, ReferenceGrayboxObservationOutcome.Status.APPLIED, "applied", weight)
                : conflict("target has no remaining weight");
    }

    private static StructureMutation unknown(String reason) {
        return new StructureMutation(false, ReferenceGrayboxObservationOutcome.Status.REJECTED_UNKNOWN, reason, 0.0d);
    }

    private static StructureMutation conflict(String reason) {
        return new StructureMutation(false, ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT, reason, 0.0d);
    }

    private record ResidentOwner(ReferenceSettlement settlement, ReferenceResident resident) { }
    private record StructureMutation(boolean applied, ReferenceGrayboxObservationOutcome.Status status, String reason, double appliedWeight) { }
}
