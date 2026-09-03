package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One bounded exact group selected from cocoon custody for a current hive task.
 *
 * <p>This record owns neither health nor positions. {@link ActorLocation} remains the only
 * body/vitality authority, while {@link BioformLifecycle} remains the cocoon-custody authority.
 * The mobilization only records why these exact dormant identities may progress through the
 * durable physical release boundary.</p>
 */
public record HiveMobilization(SubjectId id, SubjectId hiveId, SubjectId nestId, SubjectId taskId,
                               SubjectId settlementId, List<SubjectId> memberIds,
                               List<SubjectId> releasedMemberIds, Optional<SubjectId> releasingMemberId,
                               HiveMobilizationStatus status, Optional<HiveMobilizationConflictReason> conflictReason,
                               long startedAt) {
    public static final int MAX_MEMBERS = 12;

    public HiveMobilization {
        Objects.requireNonNull(id, "hive mobilization id");
        Objects.requireNonNull(hiveId, "hive mobilization hive");
        Objects.requireNonNull(nestId, "hive mobilization nest");
        Objects.requireNonNull(taskId, "hive mobilization task");
        Objects.requireNonNull(settlementId, "hive mobilization settlement");
        memberIds = List.copyOf(Objects.requireNonNull(memberIds, "hive mobilization members"));
        releasedMemberIds = List.copyOf(Objects.requireNonNull(releasedMemberIds, "released hive mobilization members"));
        releasingMemberId = Objects.requireNonNull(releasingMemberId, "releasing hive mobilization member");
        Objects.requireNonNull(status, "hive mobilization status");
        conflictReason = Objects.requireNonNull(conflictReason, "hive mobilization conflict reason");
        if (memberIds.isEmpty() || memberIds.size() > MAX_MEMBERS || memberIds.stream().distinct().count() != memberIds.size()) {
            throw new IllegalArgumentException("hive mobilization must retain one to " + MAX_MEMBERS + " distinct exact members");
        }
        if (releasedMemberIds.stream().distinct().count() != releasedMemberIds.size()
                || !memberIds.subList(0, releasedMemberIds.size()).equals(releasedMemberIds)) {
            throw new IllegalArgumentException("released hive mobilization members must be one ordered exact prefix");
        }
        if (startedAt < 0L) throw new IllegalArgumentException("hive mobilization start must be non-negative");
        if (status == HiveMobilizationStatus.CONFLICT != conflictReason.isPresent()) {
            throw new IllegalArgumentException("only a conflicted hive mobilization retains a conflict reason");
        }
        if (status == HiveMobilizationStatus.RELEASING != releasingMemberId.isPresent()) {
            throw new IllegalArgumentException("only a releasing mobilization retains its one exact in-flight occupant");
        }
        Optional<SubjectId> expectedReleasingMember = releasedMemberIds.size() == memberIds.size()
                ? Optional.empty() : Optional.of(memberIds.get(releasedMemberIds.size()));
        if (releasingMemberId.isPresent() && !expectedReleasingMember.equals(releasingMemberId)) {
            throw new IllegalArgumentException("releasing hive mobilization member is not the exact next cocoon occupant");
        }
        if (status == HiveMobilizationStatus.ASSEMBLING && releasedMemberIds.size() != memberIds.size()) {
            throw new IllegalArgumentException("assembly begins only after every selected cocoon is physically released");
        }
    }

    public HiveMobilization(SubjectId id, SubjectId hiveId, SubjectId nestId, SubjectId taskId, SubjectId settlementId,
                            List<SubjectId> memberIds, HiveMobilizationStatus status, long startedAt) {
        this(id, hiveId, nestId, taskId, settlementId, memberIds, List.of(), Optional.empty(), status, Optional.empty(), startedAt);
    }

    public HiveMobilization startRelease() {
        if (status != HiveMobilizationStatus.WAKING || releasedMemberIds.size() == memberIds.size()) {
            throw new IllegalArgumentException("only a waking mobilization with an unreleased cocoon may begin physical release");
        }
        return new HiveMobilization(id, hiveId, nestId, taskId, settlementId, memberIds, releasedMemberIds,
                nextUnreleasedMember(), HiveMobilizationStatus.RELEASING, Optional.empty(), startedAt);
    }

    public HiveMobilization confirmRelease(SubjectId memberId) {
        if (status != HiveMobilizationStatus.RELEASING || !releasingMemberId.equals(Optional.of(memberId))) {
            throw new IllegalArgumentException("only the exact in-flight cocoon release may be confirmed");
        }
        List<SubjectId> released = new java.util.ArrayList<>(releasedMemberIds); released.add(memberId);
        HiveMobilizationStatus next = released.size() == memberIds.size() ? HiveMobilizationStatus.ASSEMBLING : HiveMobilizationStatus.WAKING;
        return new HiveMobilization(id, hiveId, nestId, taskId, settlementId, memberIds, released, Optional.empty(), next, Optional.empty(), startedAt);
    }

    public HiveMobilization conflict(HiveMobilizationConflictReason reason) {
        Objects.requireNonNull(reason, "hive mobilization conflict reason");
        if (status == HiveMobilizationStatus.ASSEMBLING || status == HiveMobilizationStatus.CONFLICT) {
            throw new IllegalArgumentException("only an unconfirmed cocoon release may conflict");
        }
        return new HiveMobilization(id, hiveId, nestId, taskId, settlementId, memberIds, releasedMemberIds, Optional.empty(),
                HiveMobilizationStatus.CONFLICT, Optional.of(reason), startedAt);
    }

    private Optional<SubjectId> nextUnreleasedMember() {
        return releasedMemberIds.size() == memberIds.size() ? Optional.empty() : Optional.of(memberIds.get(releasedMemberIds.size()));
    }
}
