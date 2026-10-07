package io.farfrontier.palemirror.frontier.v3.api;

/** Owning-thread retention pressure, not a reservation or permission to perform a physical effect. */
public record CommandAdmissionCapacity(int availableReceipts, int maximumReceipts,
                                       int availableTransactions, int maximumTransactions) {
    public CommandAdmissionCapacity {
        if (maximumReceipts < 1 || maximumTransactions < 1 || availableReceipts < 0
                || availableReceipts > maximumReceipts || availableTransactions < 0
                || availableTransactions > maximumTransactions)
            throw new IllegalArgumentException("invalid command admission capacity");
    }
    public int availableCommands() { return Math.min(availableReceipts, availableTransactions); }
    /** A depleted recovered ledger must regain a causal batch margin before an executor starts. */
    public boolean hasCausalHeadroom() {
        return availableReceipts >= Math.max(1, maximumReceipts / 4)
                && availableTransactions >= Math.max(1, maximumTransactions / 4);
    }
    /** Optional notifications cannot consume the final quarter reserved for causal observations. */
    public int optionalCommands(int safetyMaximum) {
        if (safetyMaximum < 0) throw new IllegalArgumentException("negative notification bound");
        return Math.max(0, Math.min(safetyMaximum, Math.min(
                availableReceipts - Math.max(1, maximumReceipts / 4),
                availableTransactions - Math.max(1, maximumTransactions / 4))));
    }
}
