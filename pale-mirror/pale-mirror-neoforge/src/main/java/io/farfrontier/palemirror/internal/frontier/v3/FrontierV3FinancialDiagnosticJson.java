package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FinancialAccountReport;

/** Actual cash since account registration; not profit, conditional revenue or capped sale history. */
final class FrontierV3FinancialDiagnosticJson {
    static String render(FinancialAccountReport report) {
        return "{\"scope\":\"SINCE_ACCOUNT_REGISTRATION\",\"openingBalanceRaw\":" + report.turnover().openingBalance().raw()
                + ",\"receivedRaw\":" + report.turnover().received().raw() + ",\"paidRaw\":" + report.turnover().paid().raw()
                + ",\"balanceRaw\":" + report.balance().raw() + ",\"creditLimitRaw\":" + report.creditLimit().raw()
                + ",\"reservedPaymentsRaw\":" + report.reservedPayments().raw() + ",\"unusedBudgetsRaw\":" + report.unusedBudgets().raw()
                + ",\"availableToCommitRaw\":" + report.availableToCommit().raw() + "}";
    }
    private FrontierV3FinancialDiagnosticJson() { }
}
