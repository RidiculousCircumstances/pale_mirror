package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.server.level.ServerLevel;

/** Resource-site owner's bounded retirement service. It never loads chunks or actuates a body/block. */
final class FrontierV3ResourceFieldAcceptanceExecutor {
    private FrontierV3ResourceFieldAcceptanceExecutor() { }
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var checkpoint = runtime.canonicalState().orElseThrow();
        for (var site : checkpoint.state().resourceSites().sites().values().stream()
                .sorted(java.util.Comparator.comparing(ResourceSiteLifecycle::siteId)).toList()) {
            for (var job : site.harvestJobs().values().stream()
                    .sorted(java.util.Comparator.comparing(ResourceSiteHarvestJob::id)).toList()) {
                if (job.progress().acceptance().isEmpty()) continue;
                var accepted = job.progress().acceptance().orElseThrow();
                var ledger = FrontierV3ResourceSiteLedger.get(level);
                if (!(ledger.fieldClaim(job.siteId()) instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                        || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE)
                    throw new IllegalStateException("accepted field work lost its physical owner: site=" + job.siteId()
                            + ";job=" + job.id() + ";cell=" + accepted.receipt().cellId());
                var retired = owner.witness().retireWork(accepted);
                if (retired != owner.witness()) {
                    ledger.replaceFieldClaim(owner, owner.withWitness(retired));
                    // The exact seal must survive a crash before the canonical acknowledgement.
                    ledger.persist(level);
                }
                CommandId id = new CommandId("executor:field-work-ack-r" + checkpoint.revision().value());
                var result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(),
                        checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id),
                        new ResourceSiteHarvestWorkAcknowledged(accepted))).orElseThrow(
                                () -> new IllegalStateException("field acknowledgement runtime is inactive"));
                if (result instanceof CommandResult.Rejected rejected)
                    throw new IllegalStateException("persisted field retirement cannot acknowledge its retained receipt: site="
                            + job.siteId() + ";job=" + job.id() + ";cell=" + accepted.receipt().cellId()
                            + ";rejection=" + rejected.rejection());
                return true;
            }
        }
        return false;
    }
}
