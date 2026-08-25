package io.farfrontier.palemirror.frontier;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bounded identity and participant ownership for autonomous human campaigns. */
final class FrontierCampaignRegistry {
    private static final int TERMINAL_RETENTION_DAYS = 14;
    private static final int MAX_ACTIVE_CAMPAIGNS = 2;
    private final Map<String, FrontierCampaign> values = new LinkedHashMap<>();

    Collection<FrontierCampaign> values() { return List.copyOf(values.values()); }
    Optional<FrontierCampaign> find(String id) { return Optional.ofNullable(values.get(id)); }

    void put(FrontierWorldState state, FrontierCampaign campaign) {
        FrontierSettlement leader = state.settlement(campaign.leaderSettlementId()).orElseThrow(() ->
                new IllegalArgumentException("campaign leader is unknown"));
        FrontierHive hive = state.hive(campaign.targetHiveId()).orElseThrow(() ->
                new IllegalArgumentException("campaign hive is unknown"));
        FrontierHiveOrgan target = state.hiveOrgan(campaign.targetOrganId()).orElseThrow(() ->
                new IllegalArgumentException("campaign target organ is unknown"));
        if (!target.hiveId().equals(hive.id()) || target.kind() != FrontierHiveOrganKind.CORE
                || !FrontierCampaign.idFor(leader.id(), hive.id(), campaign.startedDay()).equals(campaign.id())
                || campaign.startedDay() > state.day() || !state.inBounds(campaign.position())) {
            throw new IllegalArgumentException("invalid campaign identity or target " + campaign.id());
        }
        for (String contributorId : campaign.contributorSettlementIds()) {
            if (!state.settlement(contributorId).isPresent()) throw new IllegalArgumentException("campaign contributor is unknown");
        }
        for (String participantId : campaign.participantIds()) {
            FrontierResident participant = state.resident(participantId).orElseThrow(() ->
                    new IllegalArgumentException("campaign participant is unknown " + participantId));
            if (participant.role() != FrontierResidentRole.GUARD || !campaign.contributorSettlementIds().contains(participant.settlementId())
                    || state.residentAssignedToFieldOperation(participantId) || assigned(participantId)) {
                throw new IllegalArgumentException("campaign participant is unavailable " + participantId);
            }
        }
        if (campaign.contributorSettlementIds().stream().anyMatch(id -> campaign.participantIds().stream().map(state::resident)
                .flatMap(Optional::stream).noneMatch(value -> value.settlementId().equals(id)))) {
            throw new IllegalArgumentException("every campaign contributor needs a real guard");
        }
        if (values.putIfAbsent(campaign.id(), campaign) != null) throw new IllegalArgumentException("duplicate campaign " + campaign.id());
        long active = values.values().stream().filter(value -> !value.terminal()).count();
        boolean duplicateLeader = values.values().stream().anyMatch(value -> !value.id().equals(campaign.id()) && !value.terminal()
                && value.leaderSettlementId().equals(campaign.leaderSettlementId()));
        boolean duplicateHive = values.values().stream().anyMatch(value -> !value.id().equals(campaign.id()) && !value.terminal()
                && value.targetHiveId().equals(campaign.targetHiveId()));
        if (active > MAX_ACTIVE_CAMPAIGNS || duplicateLeader || duplicateHive) {
            values.remove(campaign.id());
            throw new IllegalArgumentException("campaign exceeds bounded frontier commitment");
        }
    }

    boolean assigned(String residentId) {
        return values.values().stream().anyMatch(campaign -> !campaign.terminal() && campaign.participantIds().contains(residentId));
    }

    boolean deployed(String residentId) {
        return values.values().stream().anyMatch(campaign -> !campaign.terminal()
                && campaign.phase() != FrontierCampaign.Phase.RECON && campaign.phase() != FrontierCampaign.Phase.ASSEMBLE
                && campaign.participantIds().contains(residentId));
    }

    int livingParticipants(FrontierWorldState state, FrontierCampaign campaign) {
        return (int) campaign.participantIds().stream().map(state::resident).flatMap(Optional::stream)
                .filter(FrontierResident::alive).count();
    }

    List<FrontierCampaign> failWithoutLivingParticipants(FrontierWorldState state) {
        return failBelowLivingParticipants(state, 1);
    }

    List<FrontierCampaign> failBelowLivingParticipants(FrontierWorldState state, int minimum) {
        if (minimum < 1) throw new IllegalArgumentException("minimum participants must be positive");
        return values.values().stream().filter(value -> !value.terminal())
                .filter(value -> livingParticipants(state, value) < minimum)
                .filter(value -> value.fail(state.day())).toList();
    }

    List<FrontierCampaign> beginWithdrawForHive(String hiveId) {
        return beginWithdrawForHiveExcept(hiveId, null);
    }

    List<FrontierCampaign> beginWithdrawForHiveExcept(String hiveId, String exemptCampaignId) {
        return values.values().stream().filter(value -> value.targetHiveId().equals(hiveId))
                .filter(value -> !value.id().equals(exemptCampaignId)).filter(FrontierCampaign::beginWithdraw).toList();
    }

    void compact(long day) {
        values.values().stream().filter(FrontierCampaign::terminal).filter(value -> day - value.finishedDay() > TERMINAL_RETENTION_DAYS)
                .sorted(Comparator.comparing(FrontierCampaign::id)).map(FrontierCampaign::id).toList().forEach(values::remove);
    }
}
