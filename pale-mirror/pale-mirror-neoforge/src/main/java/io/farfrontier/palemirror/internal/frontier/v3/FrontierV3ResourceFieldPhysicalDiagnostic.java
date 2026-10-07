package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** No-load read of the physical field owner; this grants no projection or repair authority. */
final class FrontierV3ResourceFieldPhysicalDiagnostic {
    private FrontierV3ResourceFieldPhysicalDiagnostic() { }

    static String render(CheckpointImage checkpoint, FrontierWorldState state,
                         FrontierV3ResourceSiteLedger ledger, String id,
                         net.minecraft.server.level.ServerLevel level,
                         FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        SubjectId siteId;
        try { siteId = new SubjectId(id); }
        catch (IllegalArgumentException invalid) {
            return FrontierV3DiagnosticJson.unavailable("field_physical", id, checkpoint, "invalid_site");
        }
        if (!state.resourceSiteDescriptors().containsKey(siteId))
            return FrontierV3DiagnosticJson.unavailable("field_physical", id, checkpoint, "not_found");
        var claim = ledger.fieldClaim(siteId);
        if (claim == null)
            return FrontierV3DiagnosticJson.base("field_physical", id, checkpoint)
                    + ",\"status\":\"UNCLAIMED\",\"claimKind\":\"NONE\"}";
        String base = FrontierV3DiagnosticJson.base("field_physical", id, checkpoint)
                + ",\"status\":\"" + claim.status().name() + "\",\"claimKind\":\"";
        if (claim instanceof FrontierV3ResourceSiteLedger.FieldInitialization initial)
            return base + "INITIALIZATION\",\"initialWrites\":" + initial.cursor().nextWrite()
                    + ",\"initialWriteCount\":" + initial.cursor().writeCount()
                    + ",\"initialPrepared\":" + initial.cursor().prepared() + "}";
        var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) claim;
        var cycle = state.resourceSites().cycle(siteId);
        if (!owner.witness().matchesLayout(siteId, cycle.layout()))
            return base + "OWNERSHIP\",\"epoch\":" + owner.witness().epoch()
                    + ",\"layoutRevision\":" + owner.witness().layoutRevision()
                    + ",\"layoutCurrent\":false,\"worldChangePending\":"
                    + (ledger.fieldWorldChange(siteId) != null)
                    + ",\"foreignChangePending\":" + (ledger.fieldForeignChange(siteId) != null) + "}";
        var first = cycle.layout().cells().getFirst().id();
        String mismatch = "null";
        int mismatches = 0;
        for (var cell : cycle.layout().cells()) {
            var retained = owner.witness().cell(cell.id());
            var target = io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface.Condition.of(cycle.cell(cell.id()));
            if (retained.pending().isEmpty() && retained.committed().equals(target)) continue;
            mismatches++;
            if (!mismatch.equals("null")) continue;
            var reading = FrontierV3ResourceFieldObservation.read(level, cell, "diagnostic:field-mismatch");
            mismatch = "{\"cell\":" + cell.id().value()
                    + ",\"committed\":\"" + FrontierV3DiagnosticJson.quote(retained.committed().toString())
                    + "\",\"canonical\":\"" + FrontierV3DiagnosticJson.quote(target.toString())
                    + "\",\"physical\":\"" + FrontierV3DiagnosticJson.quote(reading.toString())
                    + "\",\"pending\":" + retained.pending().isPresent() + "}";
        }
        var firstClaim = owner.witness().cell(first);
        var committed = firstClaim.committed();
        var canonical = cycle.cell(first);
        boolean canonicalForeign = canonical.soil() == io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle.Soil.OBSTRUCTED
                || canonical.crop() == io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle.Crop.OBSTRUCTED;
        boolean current = owner.witness().matchesCycle(cycle)
                && !canonicalForeign && firstClaim.pending().isEmpty() && firstClaim.foreign().isEmpty()
                && committed.equals(io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface.Condition.of(
                        canonical));
        String foreign = firstClaim.foreign().map(incident ->
                ",\"firstCellForeign\":true,\"foreignSoilName\":\""
                        + FrontierV3DiagnosticJson.quote(incident.observedSoil().getString("Name"))
                        + "\",\"foreignCropName\":\""
                        + FrontierV3DiagnosticJson.quote(incident.observedCrop().getString("Name")) + "\"")
                .orElse(",\"firstCellForeign\":false");
        return base + "OWNERSHIP\",\"epoch\":" + owner.witness().epoch()
                + ",\"layoutRevision\":" + owner.witness().layoutRevision()
                + ",\"layoutCurrent\":true"
                + ",\"firstCellCurrent\":" + current
                + ",\"firstCellCommitted\":{\"soil\":\"" + committed.soil().name()
                + "\",\"crop\":\"" + committed.crop().name()
                + "\",\"stage\":" + committed.growthStage() + "}"
                + ",\"firstCellCanonical\":{\"soil\":\"" + canonical.soil().name()
                + "\",\"crop\":\"" + canonical.crop().name()
                + "\",\"stage\":" + canonical.growthStage() + "}"
                + foreign
                + ",\"projectionLoaded\":" + FrontierV3ResourceSiteExecutor.loaded(level, state.resourceSite(siteId))
                + ",\"projectionDemanded\":" + FrontierV3GrayboxExecutor.resourceSiteProjectionDemanded(runtime, level, state.resourceSite(siteId))
                + ",\"restartPending\":" + FrontierV3ResourceSiteExecutor.RECOVERY_SITES.getOrDefault(runtime, java.util.Set.of()).contains(siteId)
                + ",\"mismatchedCells\":" + mismatches + ",\"firstMismatch\":" + mismatch
                + ",\"worldChangePending\":" + (ledger.fieldWorldChange(siteId) != null)
                + ",\"foreignChangePending\":" + (ledger.fieldForeignChange(siteId) != null) + "}";
    }
}
