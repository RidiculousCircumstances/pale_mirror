package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Optional;

/** Read-only shared selection. Refused candidates acquire no job, resource or body claim. */
public final class ResidentWorkSelection {
    private ResidentWorkSelection() { }

    /** Common selection never interprets concrete jobs, crop phases or station phases. */
    public static <T> Optional<ResidentWorkOffer<T>> offer(FrontierWorldState state, ResidentProfile resident,
                                                          long atTick, ResidentWorkProvider<T> provider) {
        if (!eligible(state, resident.settlementId(), provider.kind(), provider.capability(), atTick).contains(resident))
            return Optional.empty();
        var offered = provider.discover(state, resident, atTick);
        offered.ifPresent(value -> {
            if (value.kind() != provider.kind() || !value.residentId().equals(resident.id()))
                throw new IllegalArgumentException("work provider returned a foreign permission or resident");
        });
        return offered;
    }

    public static <T> List<ResidentWorkOffer<T>> offers(FrontierWorldState state, SubjectId settlementId,
                                                       long atTick, ResidentWorkProvider<T> provider) {
        return eligible(state, settlementId, provider.kind(), provider.capability(), atTick).stream()
                .flatMap(resident -> offer(state, resident, atTick, provider).stream()).toList();
    }

    /** Candidate capability order is retained; activity and physical authority are checked per candidate. */
    public static List<ResidentProfile> eligible(FrontierWorldState state, SubjectId settlementId,
                                                 ResidentWorkKind work, HumanCapability capability, long atTick) {
        if (atTick < 0L) throw new IllegalArgumentException("work selection needs canonical time");
        return SettlementWorkforce.candidates(state, settlementId, work, capability).stream()
                .filter(resident -> ResidentActivityCoordinator.mayStartOrdinaryWork(state, resident.id(), atTick))
                .filter(resident -> ActorExecutionCoordinator.ordinaryWorkAdmission(state, resident.id()).permitted())
                .toList();
    }

}
