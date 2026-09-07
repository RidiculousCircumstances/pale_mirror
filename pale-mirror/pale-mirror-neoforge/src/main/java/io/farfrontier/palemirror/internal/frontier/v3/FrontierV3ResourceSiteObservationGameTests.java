package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePrepared;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePreparationStarted;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** Proves the server-thread admission that turns one owned player break into one durable local conflict. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ResourceSiteObservationGameTests {
    private FrontierV3ResourceSiteObservationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-resource-observation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void ownedPlayerBreakIsDurablyAcceptedBeforeThePhysicalMutation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SubjectId siteId = new SubjectId("site:1-wheat-field");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime = preparedRuntime();
        FrontierWorldState before = runtime.decodedState().orElseThrow();
        ResourceSite site = FrontierResourceSitePlan.compile(before.bootstrap()).get(siteId);
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        ledger.reserve(siteId, new PhysicalIntentId("intent:site-prepare-1-wheat-field"));
        ledger.activate(siteId);

        String cause = "player:resource-observation-game-test";
        helper.assertTrue(FrontierV3ResourceSiteExecutor.recordPlayerConflict(level, runtime, ledger, site, site.cropSlots().getLast(), cause),
                "an owned player break must first receive one durable canonical conflict receipt");
        helper.assertValueEqual(runtime.decodedState().orElseThrow().resourceSites().site(siteId).phase(), ResourceSitePhase.CONFLICT,
                "the accepted observation must move only its owning site to CONFLICT before Minecraft mutates the cell");
        helper.assertValueEqual(ledger.claim(siteId).status(), FrontierV3ResourceSiteLedger.Status.CONFLICT,
                "the physical ownership ledger must carry the same local conflict boundary");
        helper.assertTrue(FrontierV3DiagnosticTrace.latest(level.getServer(), cause)
                        .filter(entry -> entry.kind().equals("resource_site_conflict") && entry.subject().equals(siteId.value())).isPresent(),
                "the observer must expose the accepted receipt under the exact player correlation rather than a stale diagnostic epoch");
        runtime.shutdown();
        helper.succeed();
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> preparedRuntime() {
        WorldId world = new WorldId("frontier:resource-observation-game-test");
        FrontierEngineConfiguration<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> base =
                FrontierWorldRuntimeDefinition.configuration(world, 91L);
        SubjectId siteId = new SubjectId("site:1-wheat-field");
        FrontierWorldState initial = base.initialState();
        var preparation = ResourceSiteProcess.planPreparation(initial, ResourceSiteProcess.preparation(siteId, 4_000L));
        FrontierWorldState preparing = ResourceSiteProcess.reducePreparationStarted(initial, siteId,
                (ResourceSitePreparationStarted) preparation.getFirst().payload());
        FrontierWorldState prepared = ResourceSiteProcess.reducePrepared(preparing, siteId,
                (ResourceSitePrepared) preparation.get(1).payload());
        FrontierEngineConfiguration<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> configuration =
                new FrontierEngineConfiguration<>(base.worldId(), prepared, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(),
                        base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(),
                        base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
    }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) {
            throw new UnsupportedOperationException("GameTest does not compact");
        }
    }
}
