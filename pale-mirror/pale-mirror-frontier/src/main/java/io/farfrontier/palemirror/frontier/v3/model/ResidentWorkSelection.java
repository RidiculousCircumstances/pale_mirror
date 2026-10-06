package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Optional;

/** Read-only shared selection. Refused candidates acquire no job, resource or body claim. */
public final class ResidentWorkSelection {
    private final List<ResidentWorkAvailabilityPort> opportunities;
    public ResidentWorkSelection(List<ResidentWorkAvailabilityPort> opportunities) {
        this.opportunities = List.copyOf(opportunities);
        if (this.opportunities.stream().map(ResidentWorkAvailabilityPort::kind).distinct().count() != this.opportunities.size())
            throw new IllegalArgumentException("work preference composition has competing family opportunity owners");
    }

    /** Common selection never interprets concrete jobs, crop phases or station phases. */
    public <T> Optional<ResidentWorkOffer<T>> offer(FrontierWorldState state, ResidentProfile resident,
                                                          long atTick, ResidentWorkProvider<T> provider) {
        if (atTick < 0) throw new IllegalArgumentException("work selection needs canonical time");
        if (!resident.equals(state.humanPopulation().resident(resident.id()))
                || !SettlementWorkPolicy.permissions(state, resident.settlementId()).permits(provider.kind(), resident.id())
                || resident.capability(provider.capability()) <= 0
                || !SettlementWorkforce.availableForNewAssignment(state, resident)
                || !HumanAssignmentProjection.compile(state).idle(resident.id())
                || !ResidentActivityCoordinator.mayStartOrdinaryWork(state, resident.id(), atTick)
                || !ActorExecutionCoordinator.ordinaryWorkAdmission(state, resident.id()).permitted()
                || !preferred(state, resident, provider.kind(), atTick))
            return Optional.empty();
        return discover(state, resident, atTick, provider);
    }

    private static <T> Optional<ResidentWorkOffer<T>> discover(FrontierWorldState state, ResidentProfile resident,
                                                              long atTick, ResidentWorkProvider<T> provider) {
        var offered = provider.discover(state, resident, atTick);
        offered.ifPresent(value -> {
            if (value.kind() != provider.kind() || !value.residentId().equals(resident.id()))
                throw new IllegalArgumentException("work provider returned a foreign permission or resident");
        });
        return offered;
    }

    public <T> List<ResidentWorkOffer<T>> offers(FrontierWorldState state, SubjectId settlementId,
                                                       long atTick, ResidentWorkProvider<T> provider) {
        return eligible(state, settlementId, provider.kind(), provider.capability(), atTick).stream()
                .flatMap(resident -> discover(state, resident, atTick, provider).stream()).toList();
    }

    /** Candidate capability order is retained; activity and physical authority are checked per candidate. */
    public List<ResidentProfile> eligible(FrontierWorldState state, SubjectId settlementId,
                                                 ResidentWorkKind work, HumanCapability capability, long atTick) {
        if (atTick < 0L) throw new IllegalArgumentException("work selection needs canonical time");
        return SettlementWorkforce.candidates(state, settlementId, work, capability).stream()
                .filter(resident -> ResidentActivityCoordinator.mayStartOrdinaryWork(state, resident.id(), atTick))
                .filter(resident -> ActorExecutionCoordinator.ordinaryWorkAdmission(state, resident.id()).permitted())
                .filter(resident -> preferred(state, resident, work, atTick))
                .toList();
    }

    /** Only executable higher-priority opportunities win; an unavailable station cannot trap a resident. */
    private boolean preferred(FrontierWorldState state, ResidentProfile resident, ResidentWorkKind requested, long atTick) {
        var policy = SettlementWorkPolicy.permissions(state, resident.settlementId());
        int priority = policy.priority(requested, resident.id());
        return opportunities.stream().filter(port -> port.kind() != requested && policy.permits(port.kind(), resident.id())
                        && policy.priority(port.kind(), resident.id()) < priority
                        && resident.capability(port.capability()) > 0)
                .noneMatch(port -> port.available(state, resident, atTick));
    }

}
