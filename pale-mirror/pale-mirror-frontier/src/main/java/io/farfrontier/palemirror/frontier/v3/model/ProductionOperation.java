package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;

/** One station-owned operation. An order refers to it; the resident does not own its clock. */
public record ProductionOperation(Id id, SubjectId stationId, SubjectId orderId,
                                  ProductionRecipe.Id recipeId, InputCustody inputCustody, SubjectId outputId,
                                  int inputQuantity, int outputQuantity, long committedHeat,
                                  Optional<WorkProgress> progress, boolean transformed, Initiator initiator) {
    public sealed interface Initiator permits OrderInitiator, PlayerInitiator { }
    public record OrderInitiator(ProductionOrderKind kind, SubjectId orderId) implements Initiator {
        public OrderInitiator { Objects.requireNonNull(kind); Objects.requireNonNull(orderId); }
    }
    public enum PlayerAttendanceIntent {
        RUN_REQUESTED(1), PAUSED(2);
        private final int wireTag;
        PlayerAttendanceIntent(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static PlayerAttendanceIntent fromWireTag(int tag) {
            return switch (tag) {
                case 1 -> RUN_REQUESTED; case 2 -> PAUSED;
                default -> throw new IllegalArgumentException("unknown player station attendance intent");
            };
        }
    }
    /** Player consent is independent of the current, expiring physical attendance lease. */
    public record PlayerInitiator(java.util.UUID playerId, PlayerAttendanceIntent attendanceIntent) implements Initiator {
        public PlayerInitiator { Objects.requireNonNull(playerId); Objects.requireNonNull(attendanceIntent); }
    }
    public record Id(SubjectId value) {
        public Id { Objects.requireNonNull(value, "production operation identity"); }
    }
    public sealed interface InputCustody permits ExactInput, FungibleInput { }
    public record ExactInput(SubjectId itemId) implements InputCustody {
        public ExactInput { Objects.requireNonNull(itemId); }
    }
    public record FungibleInput(SubjectId accountId, Map<SubjectId, Integer> lots,
                                Map<SubjectId, Integer> claims) implements InputCustody {
        public FungibleInput {
            Objects.requireNonNull(accountId); lots = Map.copyOf(Objects.requireNonNull(lots));
            claims = Map.copyOf(Objects.requireNonNull(claims));
            if (lots.isEmpty() || lots.size() > 64 || claims.size() > 64
                    || lots.values().stream().anyMatch(value -> value < 1 || value > 64)
                    || claims.values().stream().anyMatch(value -> value < 1 || value > 64)
                    || !claims.isEmpty() && claims.values().stream().mapToInt(Integer::intValue).sum()
                        != lots.values().stream().mapToInt(Integer::intValue).sum())
                throw new IllegalArgumentException("station operation needs its exact bounded input allocation");
        }
    }
    public ProductionOperation {
        Objects.requireNonNull(id); Objects.requireNonNull(stationId); Objects.requireNonNull(orderId);
        Objects.requireNonNull(recipeId); Objects.requireNonNull(inputCustody); Objects.requireNonNull(outputId);
        Objects.requireNonNull(initiator);
        progress = Objects.requireNonNull(progress);
        if (inputQuantity < 1 || inputQuantity > 64 || outputQuantity < 1 || outputQuantity > 64
                || committedHeat < 0 || committedHeat > 1_000_000_000L
                || inputCustody instanceof FungibleInput fungible
                    && fungible.lots().values().stream().mapToInt(Integer::intValue).sum() != inputQuantity
                || transformed && progress.filter(WorkProgress::complete).isEmpty()
                || initiator instanceof PlayerInitiator player && player.attendanceIntent() == PlayerAttendanceIntent.PAUSED
                    && progress.filter(WorkProgress::running).isPresent()
                || initiator instanceof OrderInitiator order && !order.orderId().equals(orderId))
            throw new IllegalArgumentException("invalid bounded station operation");
    }
    public ProductionOperation withProgress(WorkProgress value) {
        if (transformed) throw new IllegalArgumentException("transformed operation cannot accrue work");
        return new ProductionOperation(id, stationId, orderId, recipeId, inputCustody, outputId, inputQuantity,
                outputQuantity, committedHeat, Optional.of(value), false, initiator);
    }
    public boolean complete() { return progress.filter(WorkProgress::complete).isPresent(); }
    public ProductionOperation withPlayerAttendanceIntent(PlayerAttendanceIntent intent) {
        if (!(initiator instanceof PlayerInitiator player) || transformed)
            throw new IllegalArgumentException("attendance consent belongs only to an unfinished player operation");
        return new ProductionOperation(id, stationId, orderId, recipeId, inputCustody, outputId, inputQuantity,
                outputQuantity, committedHeat, progress, false, new PlayerInitiator(player.playerId(), intent));
    }
    public SubjectId outputClaimId() { return outputClaimId(id); }
    public static SubjectId outputClaimId(Id id) { return new SubjectId("claim:station-output-" + id.value().value().replace(':', '-')); }
    public ProductionOperation confirmTransformation() {
        if (!complete() || transformed) throw new IllegalArgumentException("station transformation has no unconsumed completion");
        return new ProductionOperation(id, stationId, orderId, recipeId, inputCustody, outputId, inputQuantity,
                outputQuantity, committedHeat, progress, true, initiator);
    }
}
