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
                               SubjectId settlementId, HiveSettlementKnowledge.Sighting sighting, SubjectId overseerId, List<SubjectId> memberIds,
                               List<SubjectId> releasedMemberIds, Optional<SubjectId> releasingMemberId,
                               Optional<HiveTaskAssembly> assembly, Optional<HiveReturnAssembly> returnAssembly,
                               HiveMobilizationStatus status, Optional<HiveMobilizationConflictReason> conflictReason,
                               Optional<HiveAssemblyBlockage> assemblyBlockage, long startedAt) {
    public static final int MAX_MEMBERS = 12;

    /** The task-derived parent survives the lifecycle handoff without a second roster record. */
    public SubjectId expeditionId() { return HiveExpeditionIdentity.forTask(taskId); }

    public HiveMobilization {
        Objects.requireNonNull(id, "hive mobilization id");
        Objects.requireNonNull(hiveId, "hive mobilization hive");
        Objects.requireNonNull(nestId, "hive mobilization nest");
        Objects.requireNonNull(taskId, "hive mobilization task");
        Objects.requireNonNull(settlementId, "hive mobilization settlement");
        Objects.requireNonNull(sighting, "hive mobilization sighting");
        Objects.requireNonNull(overseerId, "hive mobilization overseer");
        memberIds = List.copyOf(Objects.requireNonNull(memberIds, "hive mobilization members"));
        releasedMemberIds = List.copyOf(Objects.requireNonNull(releasedMemberIds, "released hive mobilization members"));
        releasingMemberId = Objects.requireNonNull(releasingMemberId, "releasing hive mobilization member");
        assembly = Objects.requireNonNull(assembly, "hive mobilization assembly");
        returnAssembly = Objects.requireNonNull(returnAssembly, "hive mobilization return assembly");
        Objects.requireNonNull(status, "hive mobilization status");
        conflictReason = Objects.requireNonNull(conflictReason, "hive mobilization conflict reason");
        assemblyBlockage = Objects.requireNonNull(assemblyBlockage, "hive mobilization assembly blockage");
        if (memberIds.isEmpty() || memberIds.size() > MAX_MEMBERS || memberIds.stream().distinct().count() != memberIds.size()) {
            throw new IllegalArgumentException("hive mobilization must retain one to " + MAX_MEMBERS + " distinct exact members");
        }
        if (!memberIds.contains(overseerId)) {
            throw new IllegalArgumentException("hive mobilization overseer must be one exact member");
        }
        if (!settlementId.equals(sighting.settlementId())) {
            throw new IllegalArgumentException("hive mobilization sighting must retain its exact settlement");
        }
        if (releasedMemberIds.stream().distinct().count() != releasedMemberIds.size()
                || !memberIds.subList(0, releasedMemberIds.size()).equals(releasedMemberIds)) {
            throw new IllegalArgumentException("released hive mobilization members must be one ordered exact prefix");
        }
        if (startedAt < 0L) throw new IllegalArgumentException("hive mobilization start must be non-negative");
        if (status == HiveMobilizationStatus.CONFLICT != conflictReason.isPresent()) {
            throw new IllegalArgumentException("only a conflicted hive mobilization retains a conflict reason");
        }
        if (assemblyBlockage.isPresent() != (status == HiveMobilizationStatus.CONFLICT
                && conflictReason.orElseThrow() == HiveMobilizationConflictReason.ASSEMBLY_PATH_BLOCKED)) {
            throw new IllegalArgumentException("only an assembly path conflict retains its exact blocked edge");
        }
        if (status == HiveMobilizationStatus.RELEASING != releasingMemberId.isPresent()) {
            throw new IllegalArgumentException("only a releasing mobilization retains its one exact in-flight occupant");
        }
        Optional<SubjectId> expectedReleasingMember = releasedMemberIds.size() == memberIds.size()
                ? Optional.empty() : Optional.of(memberIds.get(releasedMemberIds.size()));
        if (releasingMemberId.isPresent() && !expectedReleasingMember.equals(releasingMemberId)) {
            throw new IllegalArgumentException("releasing hive mobilization member is not the exact next cocoon occupant");
        }
        if ((status == HiveMobilizationStatus.ASSEMBLING || status == HiveMobilizationStatus.DEPARTED
                || status == HiveMobilizationStatus.RETURNING || status == HiveMobilizationStatus.COMPLETED) && assembly.isEmpty()
                || status != HiveMobilizationStatus.ASSEMBLING && status != HiveMobilizationStatus.DEPARTED
                && status != HiveMobilizationStatus.RETURNING && status != HiveMobilizationStatus.COMPLETED
                && status != HiveMobilizationStatus.CONFLICT && assembly.isPresent()) {
            throw new IllegalArgumentException("only an assembling, departed or conflicted complete mobilization retains one exact assembly plan");
        }
        if (assembly.isPresent() && (!releasedMemberIds.equals(memberIds) || !assembly.orElseThrow().members().keySet().equals(java.util.Set.copyOf(memberIds)))) {
            throw new IllegalArgumentException("hive assembly must retain every and only physically released member");
        }
        if (returnAssembly.isPresent() && (status != HiveMobilizationStatus.RETURNING && status != HiveMobilizationStatus.COMPLETED
                || !memberIds.containsAll(returnAssembly.orElseThrow().members().keySet())
                || returnAssembly.orElseThrow().nestId().equals(nestId) == false)) {
            throw new IllegalArgumentException("only a returning/completed parent may retain exact surviving return cursors");
        }
        if (status == HiveMobilizationStatus.RETURNING && returnAssembly.isEmpty()) {
            throw new IllegalArgumentException("a returning parent requires its exact surviving-member cursors");
        }
        if (assemblyBlockage.isPresent()) {
            HiveAssemblyBlockage blockage = assemblyBlockage.orElseThrow();
            HiveTaskAssembly.Member member = assembly.orElseThrow().members().get(blockage.actorId());
            if (member == null || member.arrived() || member.cursor() != blockage.expectedCursor()
                    || !member.nextSurface().equals(blockage.target())) {
                throw new IllegalArgumentException("hive assembly blockage is not the exact retained next edge");
            }
        }
    }

    public HiveMobilization(SubjectId id, SubjectId hiveId, SubjectId nestId, SubjectId taskId, HiveSettlementKnowledge.Sighting sighting,
                            SubjectId overseerId, List<SubjectId> memberIds, HiveMobilizationStatus status, long startedAt) {
        this(id, hiveId, nestId, taskId, sighting.settlementId(), sighting, overseerId, memberIds, List.of(), Optional.empty(), Optional.empty(), Optional.empty(), status, Optional.empty(), Optional.empty(), startedAt);
    }

    /** Convenience constructor for ordinary non-assembly fixtures. */
    public HiveMobilization(SubjectId id, SubjectId hiveId, SubjectId nestId, SubjectId taskId, HiveSettlementKnowledge.Sighting sighting,
                            SubjectId overseerId, List<SubjectId> memberIds, List<SubjectId> releasedMemberIds,
                            Optional<SubjectId> releasingMemberId, HiveMobilizationStatus status,
                            Optional<HiveMobilizationConflictReason> conflictReason, long startedAt) {
        this(id, hiveId, nestId, taskId, sighting.settlementId(), sighting, overseerId, memberIds, releasedMemberIds, releasingMemberId,
                Optional.empty(), Optional.empty(), status, conflictReason, Optional.empty(), startedAt);
    }

    public HiveMobilization startRelease() {
        if (status != HiveMobilizationStatus.WAKING || releasedMemberIds.size() == memberIds.size()) {
            throw new IllegalArgumentException("only a waking mobilization with an unreleased cocoon may begin physical release");
        }
        return new HiveMobilization(id, hiveId, nestId, taskId, settlementId, sighting, overseerId, memberIds, releasedMemberIds,
                nextUnreleasedMember(), Optional.empty(), Optional.empty(), HiveMobilizationStatus.RELEASING, Optional.empty(), Optional.empty(), startedAt);
    }

    public HiveMobilization confirmRelease(SubjectId memberId, Optional<HiveTaskAssembly> completedAssembly) {
        completedAssembly = Objects.requireNonNull(completedAssembly, "completed hive assembly");
        if (status != HiveMobilizationStatus.RELEASING || !releasingMemberId.equals(Optional.of(memberId))) {
            throw new IllegalArgumentException("only the exact in-flight cocoon release may be confirmed");
        }
        List<SubjectId> released = new java.util.ArrayList<>(releasedMemberIds); released.add(memberId);
        boolean complete = released.size() == memberIds.size();
        if (complete != completedAssembly.isPresent()) {
            throw new IllegalArgumentException("only the final cocoon release may retain a complete hive assembly");
        }
        HiveMobilizationStatus next = complete ? HiveMobilizationStatus.ASSEMBLING : HiveMobilizationStatus.WAKING;
        return new HiveMobilization(id, hiveId, nestId, taskId, settlementId, sighting, overseerId, memberIds, released, Optional.empty(), completedAssembly, Optional.empty(), next, Optional.empty(), Optional.empty(), startedAt);
    }

    /** Advances exactly one already retained assembly cursor without changing its port or roster. */
    public HiveMobilization advanceAssembly(SubjectId memberId) {
        if (status != HiveMobilizationStatus.ASSEMBLING) {
            throw new IllegalArgumentException("only an assembling mobilization may advance its retained cursor");
        }
        return new HiveMobilization(id, hiveId, nestId, taskId, settlementId, sighting, overseerId, memberIds, releasedMemberIds,
                Optional.empty(), Optional.of(assembly.orElseThrow().advance(memberId)), Optional.empty(), status, Optional.empty(), Optional.empty(), startedAt);
    }

    /** Transfers only a complete retained group to the operation that selected it. */
    public HiveMobilization depart() {
        if (status != HiveMobilizationStatus.ASSEMBLING || !assembly.orElseThrow().complete()) {
            throw new IllegalArgumentException("only a complete exact assembly may depart");
        }
        return new HiveMobilization(id, hiveId, nestId, taskId, settlementId, sighting, overseerId, memberIds, releasedMemberIds,
                Optional.empty(), assembly, Optional.empty(), HiveMobilizationStatus.DEPARTED, Optional.empty(), Optional.empty(), startedAt);
    }

    /** The parent closes only after its retained child reports one terminal result. */
    public HiveMobilization complete() {
        if (status != HiveMobilizationStatus.DEPARTED && status != HiveMobilizationStatus.RETURNING) {
            throw new IllegalArgumentException("only a departed or returned expedition parent may complete");
        }
        if (status == HiveMobilizationStatus.RETURNING && !returnAssembly.orElseThrow().complete()) {
            throw new IllegalArgumentException("a returning expedition cannot complete before every retained survivor reaches home");
        }
        return new HiveMobilization(id, hiveId, nestId, taskId, settlementId, sighting, overseerId, memberIds, releasedMemberIds,
                Optional.empty(), assembly, returnAssembly, HiveMobilizationStatus.COMPLETED, Optional.empty(), Optional.empty(), startedAt);
    }

    /**
     * The resolved child has no authority to release surviving bodies. It first gives the
     * parent one exact homeward cursor for each survivor; only those cursors may complete it.
     */
    public HiveMobilization beginReturn(HiveReturnAssembly retainedReturn) {
        retainedReturn = Objects.requireNonNull(retainedReturn, "retained hive return");
        if (status != HiveMobilizationStatus.DEPARTED || retainedReturn.complete()) {
            throw new IllegalArgumentException("only a departed parent with unfinished surviving return may begin return");
        }
        return new HiveMobilization(id, hiveId, nestId, taskId, settlementId, sighting, overseerId, memberIds, releasedMemberIds,
                Optional.empty(), assembly, Optional.of(retainedReturn), HiveMobilizationStatus.RETURNING,
                Optional.empty(), Optional.empty(), startedAt);
    }

    /** Advances one observed exact survivor edge and closes the parent only at home. */
    public HiveMobilization advanceReturn(SubjectId memberId) {
        if (status != HiveMobilizationStatus.RETURNING) {
            throw new IllegalArgumentException("only a returning parent may advance homeward");
        }
        HiveReturnAssembly next = returnAssembly.orElseThrow().advance(memberId);
        HiveMobilizationStatus nextStatus = next.complete() ? HiveMobilizationStatus.COMPLETED : HiveMobilizationStatus.RETURNING;
        return new HiveMobilization(id, hiveId, nestId, taskId, settlementId, sighting, overseerId, memberIds, releasedMemberIds,
                Optional.empty(), assembly, Optional.of(next), nextStatus, Optional.empty(), Optional.empty(), startedAt);
    }

    public HiveMobilization conflict(HiveMobilizationConflictReason reason) {
        return conflict(reason, Optional.empty());
    }

    public HiveMobilization conflict(HiveMobilizationConflictReason reason, Optional<HiveAssemblyBlockage> blockage) {
        Objects.requireNonNull(reason, "hive mobilization conflict reason");
        blockage = Objects.requireNonNull(blockage, "hive mobilization assembly blockage");
        if (status == HiveMobilizationStatus.CONFLICT) {
            throw new IllegalArgumentException("a conflicted mobilization cannot conflict again");
        }
        return new HiveMobilization(id, hiveId, nestId, taskId, settlementId, sighting, overseerId, memberIds, releasedMemberIds, Optional.empty(), assembly,
                Optional.empty(), HiveMobilizationStatus.CONFLICT, Optional.of(reason), blockage, startedAt);
    }

    private Optional<SubjectId> nextUnreleasedMember() {
        return releasedMemberIds.size() == memberIds.size() ? Optional.empty() : Optional.of(memberIds.get(releasedMemberIds.size()));
    }
}
