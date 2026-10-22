package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GrayboxExecutor.ProjectionCost;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GrayboxExecutor.ProjectionWorkSnapshot;

/** Noncanonical attribution counters; the executor owns their runtime lifetime. */
final class FrontierV3ProjectionWork {
    long visibilityAdmissions, visibilityPreparationWaits, visibilityCellProbes, visibilityWrites;
    int compatibilityChecks, freshnessConstructions, planCompilations, providerAcquisitions, pointQueries, siblingHiveFenceRetentions;
    String lastTrigger = "UNINITIALIZED", lastDisposition = "NONE";
    String lastCompileTrigger = "NONE";
    ProjectionCost cursorPath = ProjectionCost.empty(), firstVisibilityPath = ProjectionCost.empty();
    ProjectionCost stagingRetirementPath = ProjectionCost.empty(), deferredProjectionPath = ProjectionCost.empty();
    void lastRefresh(String trigger, String disposition) {
        lastTrigger = trigger; lastDisposition = disposition;
        if ("COMPILE_STABLE_BASELINE".equals(disposition)) lastCompileTrigger = trigger;
    }
    void cursorPath(long elapsedNanos) { cursorPath = cursorPath.observe(elapsedNanos); }
    void firstVisibilityPath(long elapsedNanos) { firstVisibilityPath = firstVisibilityPath.observe(elapsedNanos); }
    void stagingRetirementPath(long elapsedNanos) { stagingRetirementPath = stagingRetirementPath.observe(elapsedNanos); }
    void deferredProjectionPath(long elapsedNanos) { deferredProjectionPath = deferredProjectionPath.observe(elapsedNanos); }
    ProjectionWorkSnapshot snapshot() {
        return new ProjectionWorkSnapshot(compatibilityChecks, freshnessConstructions, planCompilations,
                providerAcquisitions, pointQueries, siblingHiveFenceRetentions, lastTrigger, lastDisposition,
                lastCompileTrigger, cursorPath, firstVisibilityPath, stagingRetirementPath, deferredProjectionPath);
    }
}
