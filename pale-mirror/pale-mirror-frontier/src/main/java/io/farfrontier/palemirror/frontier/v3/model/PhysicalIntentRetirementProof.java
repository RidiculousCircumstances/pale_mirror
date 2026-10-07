package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Owner-produced, durable account for one terminal physical-intent event.
 *
 * <p>This is evidence, not a relationship index or schedule.  Exact relationship facts are
 * copied only into the event which retires their intent; the authoritative aggregates and the
 * engine queue remain the sources of truth.  A checked-none value is typed and subsequently
 * verified against the same transaction, rather than being an explanatory string.</p>
 */
public record PhysicalIntentRetirementProof(
        PhysicalIntentLifecycleOwner owner,
        PhysicalIntentId intentId,
        RelationObligation relations,
        ScheduleObligation continuation,
        SubjectObligation leaseOrCarrier,
        SubjectObligation commitment,
        LateDisposition lateDisposition
) {
    public PhysicalIntentRetirementProof {
        Objects.requireNonNull(owner, "retirement proof owner");
        Objects.requireNonNull(intentId, "retirement proof intent");
        Objects.requireNonNull(relations, "retirement proof relations");
        Objects.requireNonNull(continuation, "retirement proof continuation");
        Objects.requireNonNull(leaseOrCarrier, "retirement proof lease/carrier");
        Objects.requireNonNull(commitment, "retirement proof commitment");
        Objects.requireNonNull(lateDisposition, "retirement proof late disposition");
    }

    public enum Absence { NO_APPLICABLE_RELATION, NO_ENGINE_CONTINUATION, NO_LEASE_OR_CARRIER, NO_RESOURCE_COMMITMENT }
    public enum LateDisposition { REJECT_STALE_ONCE, RETAIN_AMBIGUOUS_RECOVERY, NO_PHYSICAL_INPUT }

    /** Stable proof-envelope tags; never Java enum ordinals or names. */
    public static String entityKindTag(FrontierDomainRelationships.EntityKind kind) { return "rel.entity." + switch (kind) {
        case OBJECTIVE -> "objective"; case TASK -> "task"; case MARKET_DEMAND -> "market-demand"; case MARKET_QUOTE -> "market-quote";
        case MARKET_ORDER -> "market-order"; case FINANCIAL_RESERVATION -> "financial-reservation"; case PRODUCTION_JOB -> "production-job";
        case FINANCIAL_BUDGET -> "financial-budget";
        case GOODS_ORDER -> "goods-order"; case GOODS_CONTRACT -> "goods-contract";
        case ECONOMIC_ACCOUNT -> "economic-account"; case CONTAINER -> "container";
        case SHIPMENT -> "shipment";
        case UNIT_GROUP -> "unit-group";
        case TRANSPORT_MISSION -> "transport-mission";
        case ACTOR -> "actor";
        case HIVE_GROWTH_JOB -> "hive-growth-job";
        case RESOURCE_SITE -> "resource-site"; case RESOURCE_HARVEST_JOB -> "resource-harvest-job"; case RESIDENT -> "resident";
        case EXACT_ITEM -> "exact-item"; case RESOURCE_LOT -> "resource-lot"; case RESOURCE_ACCOUNT -> "resource-account"; case RESOURCE_CLAIM -> "resource-claim";
        case PROVISION_CYCLE -> "provision-cycle"; case PROVISION_ALLOCATION -> "provision-allocation"; case SCENE_LEASE -> "scene-lease";
        case AMBIENT_LEASE -> "ambient-lease"; case CARRIER_EVIDENCE -> "carrier-evidence";
        case CARGO -> "cargo"; case SERVICE_WORK -> "service-work";
        case HIVE_MOBILIZATION -> "hive-mobilization"; case BIOFORM -> "bioform"; case STRUCTURE -> "structure"; }; }
    public static FrontierDomainRelationships.EntityKind entityKindFromTag(String tag) {
        for (FrontierDomainRelationships.EntityKind kind : FrontierDomainRelationships.EntityKind.values()) if (entityKindTag(kind).equals(tag)) return kind;
        throw new IllegalArgumentException("unknown retirement relationship entity tag");
    }
    public static String lifecycleTag(FrontierDomainRelationships.Lifecycle lifecycle) { return switch (lifecycle) {
        case ACTIVE -> "rel.lifecycle.active"; case TERMINAL_RETAINED -> "rel.lifecycle.terminal-retained";
        case TERMINAL_COMPACTED -> "rel.lifecycle.terminal-compacted"; case PREPARED -> "rel.lifecycle.prepared"; case OBSERVED -> "rel.lifecycle.observed"; }; }
    public static FrontierDomainRelationships.Lifecycle lifecycleFromTag(String tag) {
        for (FrontierDomainRelationships.Lifecycle lifecycle : FrontierDomainRelationships.Lifecycle.values()) if (lifecycleTag(lifecycle).equals(tag)) return lifecycle;
        throw new IllegalArgumentException("unknown retirement relationship lifecycle tag");
    }

    public sealed interface RelationObligation permits ExactRelations, CheckedNoRelations { }
    public record ExactRelations(List<FrontierDomainRelationships.Edge> value) implements RelationObligation {
        public ExactRelations {
            value = List.copyOf(Objects.requireNonNull(value, "exact retirement relations"));
            if (value.isEmpty() || value.stream().distinct().count() != value.size()) {
                throw new IllegalArgumentException("exact retirement relations must be nonempty and duplicate-free");
            }
        }
    }
    public record CheckedNoRelations(Absence absence) implements RelationObligation {
        public CheckedNoRelations {
            if (absence != Absence.NO_APPLICABLE_RELATION) throw new IllegalArgumentException("invalid relation checked-none disposition");
        }
    }

    public sealed interface ScheduleObligation permits ExactSchedule, CheckedNoSchedule { }
    public record ExactSchedule(ScheduleId value) implements ScheduleObligation {
        public ExactSchedule { Objects.requireNonNull(value, "exact retirement schedule"); }
    }
    public record CheckedNoSchedule(Absence absence) implements ScheduleObligation {
        public CheckedNoSchedule {
            if (absence != Absence.NO_ENGINE_CONTINUATION) throw new IllegalArgumentException("invalid schedule checked-none disposition");
        }
    }

    public sealed interface SubjectObligation permits ExactSubject, CheckedNoSubject { }
    public record ExactSubject(SubjectId value) implements SubjectObligation {
        public ExactSubject { Objects.requireNonNull(value, "exact retirement subject"); }
    }
    public record CheckedNoSubject(Absence absence) implements SubjectObligation {
        public CheckedNoSubject {
            if (absence != Absence.NO_LEASE_OR_CARRIER && absence != Absence.NO_RESOURCE_COMMITMENT) {
                throw new IllegalArgumentException("invalid subject checked-none disposition");
            }
        }
    }

    public static Optional<PhysicalIntentRetirementProof> none() { return Optional.empty(); }
}
