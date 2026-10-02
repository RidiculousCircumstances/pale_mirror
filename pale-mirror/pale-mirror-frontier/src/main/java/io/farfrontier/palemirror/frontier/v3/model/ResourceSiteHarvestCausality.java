package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Small retained account of a terminal field harvest.  It records facts admitted at the
 * COLD boundary; it is neither a scheduler nor a replacement for the physical receipt owner.
 */
public record ResourceSiteHarvestCausality(String coldScheduleId, long coldDueAt,
                                           List<SceneLeaseId> hotLeaseIds, PhysicalIntentId intentId,
                                           String expectedPhysical, String observedPhysical,
                                           String reconciliation, RetainedDiagnosticTrace trace) {
    private static final int MAX = 512;

    public ResourceSiteHarvestCausality {
        coldScheduleId = field(coldScheduleId, "harvest causality cold schedule");
        hotLeaseIds = List.copyOf(Objects.requireNonNull(hotLeaseIds, "harvest causality HOT leases").stream()
                .sorted(Comparator.comparing(SceneLeaseId::value)).toList());
        if (hotLeaseIds.size() > 32 || hotLeaseIds.stream().distinct().count() != hotLeaseIds.size()) {
            throw new IllegalArgumentException("harvest causality HOT lease retention is invalid");
        }
        intentId = Objects.requireNonNull(intentId, "harvest causality intent");
        expectedPhysical = field(expectedPhysical, "harvest causality expected physical");
        observedPhysical = field(observedPhysical, "harvest causality observed physical");
        reconciliation = field(reconciliation, "harvest causality reconciliation");
        trace = Objects.requireNonNull(trace, "harvest causality trace");
        if ((coldScheduleId.equals("not_captured")) != (coldDueAt == -1L)) {
            throw new IllegalArgumentException("harvest causality schedule evidence is incomplete");
        }
        if (coldDueAt < -1L) throw new IllegalArgumentException("harvest causality due instant is invalid");
    }

    public static ResourceSiteHarvestCausality notCaptured(ResourceSiteHarvestJob job) {
        return notCaptured(job.intentId(), job.outputItemId(), job.outputSlot())
                .withTrace(RetainedDiagnosticTrace.pending(job, "not_captured"));
    }
    public static ResourceSiteHarvestCausality notCaptured(PhysicalIntentId intentId, io.farfrontier.palemirror.frontier.v3.api.SubjectId outputItemId,
                                                           InventoryCustody.ContainerSlot outputSlot) {
        return new ResourceSiteHarvestCausality("not_captured", -1L, List.of(), intentId, expected(intentId, outputItemId, outputSlot),
                "not_observed", "not_captured", new RetainedDiagnosticTrace("resource-site-harvest:" + intentId.value(), "not_captured", "not_captured", "not_captured", -1, -1, "not_observed", "not_captured", "not_captured", -1, -1));
    }

    public static ResourceSiteHarvestCausality captured(FrontierWorldState state, ResourceSiteHarvestJob job,
                                                         ScheduledAction action, boolean receiptPending) {
        Objects.requireNonNull(state, "harvest causality state");
        Objects.requireNonNull(job, "harvest causality job");
        Objects.requireNonNull(action, "harvest causality action");
        if (!action.subject().equals(job.siteId())) throw new IllegalArgumentException("harvest causality has a foreign COLD site owner");
        List<SceneLeaseId> hotLeases = state.sceneLeases().values().stream()
                .filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                .filter(lease -> FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id())
                        && FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId()))
                .map(SceneLease::id).toList();
        return new ResourceSiteHarvestCausality(action.id().value(), action.dueAt().ticks(), hotLeases, job.intentId(), expected(job),
                receiptPending ? "not_observed" : "not_applicable_intent_composed",
                receiptPending ? "pending_exact_physical_receipt" : "composed_cold_receipt", RetainedDiagnosticTrace.pending(job, action.kind()));
    }

    public ResourceSiteHarvestCausality confirmed(String observation) {
        return new ResourceSiteHarvestCausality(coldScheduleId, coldDueAt, hotLeaseIds, intentId, expectedPhysical,
                field(observation, "harvest causality confirmation"), "confirmed_exact_physical_receipt", trace);
    }

    public boolean completeForColdHotReceipt() {
        return !coldScheduleId.equals("not_captured") && !hotLeaseIds.isEmpty()
                && reconciliation.equals("confirmed_exact_physical_receipt") && trace.complete();
    }
    public ResourceSiteHarvestCausality withTrace(RetainedDiagnosticTrace next) {
        return new ResourceSiteHarvestCausality(coldScheduleId, coldDueAt, hotLeaseIds, intentId, expectedPhysical, observedPhysical, reconciliation, next);
    }

    private static String expected(ResourceSiteHarvestJob job) {
        return expected(job.intentId(), job.outputItemId(), job.outputSlot());
    }
    private static String expected(PhysicalIntentId intentId, io.farfrontier.palemirror.frontier.v3.api.SubjectId outputItemId,
                                   InventoryCustody.ContainerSlot outputSlot) {
        return "intent=" + intentId.value() + ";output=" + outputItemId.value()
                + ";container=" + outputSlot.containerId().value() + ";slot=" + outputSlot.slot();
    }
    private static String field(String value, String label) {
        value = Objects.requireNonNull(value, label);
        if (value.isBlank() || value.length() > MAX) throw new IllegalArgumentException(label + " is invalid");
        return value;
    }
}
