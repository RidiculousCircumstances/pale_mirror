package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Named recovery boundaries stamp this before an exact physical intent becomes restart-unknown. */
public enum PhysicalIntentRecoveryDiagnosticProducer {
    DECONTAMINATION(PhysicalIntentLifecycleOwner.DECONTAMINATION),
    PRODUCTION_WORK(PhysicalIntentLifecycleOwner.PRODUCTION_WORK),
    ENGINEERING_WORKSITE(PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE),
    SETTLEMENT_SERVICE_WORK(PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_WORK),
    SETTLEMENT_SERVICE_DECONTAMINATION(PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_DECONTAMINATION),
    HIVE_MOBILIZATION(PhysicalIntentLifecycleOwner.HIVE_MOBILIZATION),
    HIVE_NUTRIENT_TRANSFER(PhysicalIntentLifecycleOwner.HIVE_NUTRIENT_TRANSFER),
    HIVE_GROWTH(PhysicalIntentLifecycleOwner.HIVE_GROWTH),
    ROUTE_ENGAGEMENT(PhysicalIntentLifecycleOwner.ROUTE_ENGAGEMENT),
    SETTLEMENT_ASSAULT(PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT),
    POPULATION_MIGRATION(PhysicalIntentLifecycleOwner.POPULATION_MIGRATION),
    MEDICAL_TREATMENT(PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT),
    SETTLEMENT_PROVISION(PhysicalIntentLifecycleOwner.SETTLEMENT_PROVISION),
    ROUTE_OPERATION(PhysicalIntentLifecycleOwner.ROUTE_OPERATION),
    RESOURCE_SITE_PREPARATION(PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION),
    RESOURCE_SITE_HARVEST(PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST),
    NO_PHYSICAL_CAPABILITY(null);

    private final PhysicalIntentLifecycleOwner owner;
    PhysicalIntentRecoveryDiagnosticProducer(PhysicalIntentLifecycleOwner owner) { this.owner = owner; }
    public PhysicalIntentLifecycleOwner owner() { return owner; }

    public DiagnosticTuple stamp(PhysicalIntent intent) {
        if (owner == null || intent.lifecycleOwner() != owner) throw new IllegalArgumentException("recovery diagnostic producer has a mismatched physical lifecycle owner");
        SubjectId exactIntent = new SubjectId(intent.id().value());
        return new DiagnosticTuple(DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED,
                DiagnosticCategory.RECOVERY_UNKNOWN,
                new DiagnosticOwner(DiagnosticOwnerKind.PHYSICAL_INTENT, exactIntent),
                new DiagnosticSubject(DiagnosticSubjectKind.PHYSICAL_EFFECT, exactIntent),
                DiagnosticDisposition.INSPECT);
    }
}
