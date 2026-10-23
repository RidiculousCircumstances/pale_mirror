package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Finite-source owner declares present products. Owning a mine never creates demand for its output. */
final class ExtractionReserveSupply implements ReserveSupplyPort {
    @Override public Key key() { return Key.EXTRACTION; }
    private static final ImmutableKeyedInputView<SubjectId, Facts> VIEWS = new ImmutableKeyedInputView<>(256);
    @Override public Facts read(FrontierWorldState state, SubjectId home) {
        return VIEWS.get(state.bootstrap(), home, List.of(state.extractionSites().deposits(), state.humanPopulation().schedule(home)), () -> project(state, home));
    }
    private static Facts project(FrontierWorldState state, SubjectId home) {
        var timing = new TreeMap<String, Timing>();
        for (var deposit : state.extractionSites().deposits().values()) {
            if (!deposit.site().settlementId().equals(home)) continue;
            var materials = deposit.site().layout().cells().stream()
                    .filter(c -> deposit.cells().get(c.id()).disposition() == ExtractionDeposit.Disposition.PRESENT)
                    .map(ExtractionLayout.Cell::definition).distinct().toList();
            for (var material : materials) {
                long work = state.bootstrap().ruleset().workCatalog().require(material.before().kind(), WorkOperation.EXTRACT).workUnits();
                long lead = Math.addExact(state.humanPopulation().schedule(home).dayTicks(), work);
                for (var output : material.coldOutput()) timing.merge(output.itemKind(), new Timing(lead, TimingBasis.LOCAL_ESTIMATE),
                        (a, b) -> a.leadTicks() <= b.leadTicks() ? a : b);
            }
        }
        return new Facts(Map.of(), List.of(), timing);
    }
}
