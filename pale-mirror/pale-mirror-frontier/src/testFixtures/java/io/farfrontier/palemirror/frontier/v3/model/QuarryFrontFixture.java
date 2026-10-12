package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

/** Genesis inputs only: exact work permission and metabolism, never a mining or meal outcome. */
final class QuarryFrontFixture {
    static final SubjectId SETTLEMENT = new SubjectId("settlement:6");
    static final SubjectId WORKER = new SubjectId("resident:6-23");
    static final SubjectId PEER = new SubjectId("resident:6-1");
    static final int METABOLISM = 4_000;

    private QuarryFrontFixture() { }

    static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId world, long seed) {
        var base = FrontierWorldRuntimeDefinition.configuration(world, seed,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r7"));
        var state = base.initialState();
        var worker = state.humanPopulation().resident(WORKER);
        var peer = state.humanPopulation().resident(PEER);
        if (worker == null || peer == null || !worker.settlementId().equals(SETTLEMENT)
                || !peer.settlementId().equals(SETTLEMENT))
            throw new IllegalArgumentException("quarry-front fixture lacks its declared living workers");
        var previous = SettlementWorkPolicy.permissions(state, SETTLEMENT);
        var priorities = new EnumMap<ResidentWorkKind, Map<SubjectId, Integer>>(ResidentWorkKind.class);
        priorities.putAll(previous.priorities());
        int priority = state.bootstrap().ruleset().labour().entries().get(ResidentWorkKind.EXTRACTION).priority();
        priorities.put(ResidentWorkKind.EXTRACTION, Map.of(WORKER, priority, PEER, priority));
        var permissions = new ResidentWorkPermissions(priorities, previous.minimumLocalStaff());
        worker = worker.withCharacteristics(worker.characteristics().withBaseMetabolism(METABOLISM));
        state = state.withChanges(FrontierWorldStateUpdate.begin()
                .strategicPlans(state.strategicPlans().withWorkPermissions(SETTLEMENT, permissions))
                .humanPopulation(state.humanPopulation().withProfile(worker)));
        var schedules = new ArrayList<>(base.initialSchedules().stream().filter(action ->
                !action.subject().equals(WORKER) || !action.kind().equals(ResidentNeedProcess.REVIEW)).toList());
        long due = state.humanPopulation().nutrition(WORKER).nextThresholdTick(
                state.bootstrap().ruleset().residentLife(), worker.characteristics().effectiveMetabolismPermille(0));
        schedules.add(ResidentNeedProcess.review(WORKER, due));
        base.stateValidator().validateRecoveryInitial(state, schedules);
        return new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                schedules, base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
    }
}
