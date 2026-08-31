package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Exact reducer owner for company, market and production facts. */
final class FrontierEconomyProcessModule implements FrontierWorldProcessModule {
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case CompanyRegistered registered -> CompanyFoundationProcess.reduce(state, event.subject(), registered);
            case EmploymentContractOpened opened -> CompanyFoundationProcess.reduceEmployment(state, event.subject(), opened);
            case EmploymentContractTerminated terminated -> CompanyFoundationProcess.reduceEmploymentTermination(state, event.subject(), terminated);
            case MarketDemandOpened opened -> MarketClearingProcess.reduceOpened(state, event.subject(), opened);
            case MarketQuotePublished published -> MarketClearingProcess.reduceQuote(state, event.subject(), event.instant().ticks(), published);
            case MarketWorkOrderAccepted accepted -> MarketClearingProcess.reduceAccepted(state, event.subject(), event.instant().ticks(), accepted);
            case MarketWorkOrderCancelled cancelled -> MarketClearingProcess.reduceWorkOrderCancelled(state, event.subject(), cancelled);
            case MarketDemandExpired expired -> MarketClearingProcess.reduceExpired(state, event.subject(), event.instant().ticks(), expired);
            case MarketDemandCancelled cancelled -> MarketClearingProcess.reduceCancelled(state, event.subject(), cancelled);
            case ProductionStarted started -> ProductionProcess.reduceStarted(state, event.subject(), started);
            case ProductionCompleted completed -> ProductionProcess.reduceCompleted(state, event.subject(), completed);
            case ProductionBlocked blocked -> ProductionProcess.reduceBlocked(state, event.subject(), blocked);
            case ProductionInterrupted interrupted -> ProductionProcess.reduceInterrupted(state, event.subject(), event.instant().ticks(), interrupted);
            default -> throw new IllegalArgumentException("economy process does not own event: " + event.payload().type());
        };
    }
}
