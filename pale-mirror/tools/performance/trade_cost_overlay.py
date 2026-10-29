"""Read-only source instrumentation for the manually invoked offline diagnostic.

Only generated copies under build/ change. Each selected method's original body
is enclosed in try-with-resources; return/throw/control flow stay intact. Missing
or ambiguous anchors fail instead of instrumenting a guessed method. No agent or
instrumentation enters the production classes/JAR.
"""
from pathlib import Path
import hashlib
import json
import re
import sys


TARGETS = {
    "process/GoodsTripPlanning.java": [
        ("private static java.util.function.Supplier<Result> prepareInScope(", "proposals", "PROPOSALS"),
        ("private static Optional<Candidate> reserveSelected(", "reserve_candidate", "RESERVATION"),
        ("private static Optional<Candidate> smaller(", "smaller", "SMALLER"),
        ("private static ProcurementSelection.Offer offer(", "candidate_offer", ""),
        ("static TripDispatchPolicy.Cause dispatchCause(", "dispatch_policy", ""),
    ],
    "process/ShipmentRetention.java": [("static Result collect(", "retention", "RETENTION")],
    "process/GoodsTripAssembly.java": [
        ("long now, long forecastDuration) {", "assembly", "ASSEMBLY"),
        ("private static Optional<TransportMissionStarted> declaration(", "expedition_declaration", ""),
    ],
    "process/GoodsOrderMatching.java": [("Set<String> excludedCommodities, Optional<Endpoints> endpoints, FixedScalar unreservedDeliveryExpense) {", "matching", "")],
    "model/GoodsTradeStateSupport.java": [
        ("public static FrontierWorldState reserve(", "reserve_write", ""),
        ("public static boolean receivingCapacity(", "receiving_capacity", ""),
    ],
    "model/FrontierWorldState.java": [("public FrontierWorldState withChanges(", "draft_update", "")],
    "model/SettlementEconomicInputChanges.java": [("public static FrontierWorldState retain(", "input_invalidation", "")],
    "model/GoodsParticipantAssessment.java": [("private static Assessment published(", "assessment", "")],
    "model/GoodsParticipantView.java": [
        ("public static GoodsParticipantView observe(", "current_view", ""),
        ("private static GoodsParticipantView project(", "project_current_view", ""),
    ],
    "model/GoodsResourceValuation.java": [("private static Map<String, FixedScalar> published(", "replacement_value", "")],
    "model/GoodsTransportOptions.java": [("public static List<Option> forLoad(", "transport_options", "")],
    "model/ResidentWorkSelection.java": [("public List<ResidentProfile> eligible(", "eligible_crew", "")],
    "model/expedition/ExpeditionCrewAdmission.java": [
        ("public static List<SubjectId> select(", "select_provisioned_crew", ""),
        ("private static Map<String, FoodStock> stocks(", "crew_food_stock", ""),
        ("private static List<SubjectId> ranked(", "rank_provisioned_crew", ""),
    ],
    "model/market/ProcurementSelection.java": [("public Optional<SubjectId> select(List<Requirement> requirements, List<Offer> offers, long labourValuePerTick) {", "ranking", "RANKING")],
}

TRACE = "io.farfrontier.palemirror.frontier.v3.diagnostic.TradeCostTrace"
# Observations preserve original boolean values and short-circuit evaluation;
# no business predicate is called twice or moved before another predicate.
OBSERVATIONS = {
    "process/GoodsTripPlanning.java": [
        ("candidates.add(new Proposal(seller, endpoints, mode, ranked.offer(), ranked.timing()));",
         f'{TRACE}.count("proposal_created"); candidates.add(new Proposal(seller, endpoints, mode, ranked.offer(), ranked.timing()));'),
        ("if (parcels.isEmpty()) return Optional.empty();",
         f'if (parcels.isEmpty()) {{ {TRACE}.count("candidate_empty"); return Optional.empty(); }}\n        {TRACE}.count("candidate_reserved");'),
    ],
    "process/GoodsTripAssembly.java": [
        ("if (people.isEmpty()) return Optional.empty();",
         f'if (people.isEmpty()) {{ {TRACE}.count("no_eligible_crew"); return Optional.empty(); }}'),
        ("if (selected.isEmpty()) continue;",
         f'if (selected.isEmpty()) {{ {TRACE}.count("crew_selection_rejected"); continue; }}\n            {TRACE}.count("crew_selected");'),
        ("if (prepared.isPresent()) return prepared;",
         f'if (prepared.isPresent()) {{ {TRACE}.count("declaration_ready"); return prepared; }}'),
    ],
    "process/GoodsOrderMatching.java": [
        ("return new Search(Optional.empty(), disposition, checked);",
         f'{TRACE}.count("empty_match:" + disposition); return new Search(Optional.empty(), disposition, checked);'),
        ('return new Search(Optional.empty(), "SEARCH_BUDGET; last=" + disposition, checked);',
         f'{{ {TRACE}.count("match_search_budget"); return new Search(Optional.empty(), "SEARCH_BUDGET; last=" + disposition, checked); }}'),
    ],
    "model/expedition/ExpeditionCrewAdmission.java": [
        ("if (permitted.test(List.copyOf(proposed))) crew.add(id);",
         f'if ({TRACE}.flag("local_labour_permission", permitted.test(List.copyOf(proposed)))) crew.add(id);'),
        ("if (crew.size() == count && canSupply(state, sponsor, crew, asset, duration, now, availableStock)) return List.copyOf(crew);",
         f'if ({TRACE}.flag("crew_size_sufficient", crew.size() == count) && '
         f'{TRACE}.flag("crew_provisions_sufficient", canSupply(state, sponsor, crew, asset, duration, now, availableStock))) return List.copyOf(crew);'),
    ],
}

# Java comments and quoted literals cannot contribute method-body braces.
TOKENS = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[{}]')


def enclose(source, anchor, name, phase):
    if source.count(anchor) != 1:
        raise ValueError(f"Expected one method anchor: {anchor!r}")
    opening = source.index("{", source.index(anchor))
    depth = 0
    for token in TOKENS.finditer(source, opening):
        if token.group() == "{":
            depth += 1
        elif token.group() == "}":
            depth -= 1
            if depth == 0:
                close = token.start()
                prefix = ('\n        try (var __tradeCostSpan = '
                          'io.farfrontier.palemirror.frontier.v3.diagnostic.TradeCostTrace.enter('
                          f'"{name}", "{phase}")) {{')
                return source[:opening + 1] + prefix + source[opening + 1:close] + "\n        }\n    " + source[close:]
    raise ValueError(f"Unclosed method: {anchor!r}")


def main():
    source_root, output = map(Path, sys.argv[1:])
    if "build" not in output.resolve().parts or source_root.resolve() in output.resolve().parents:
        raise ValueError("Instrumentation output must be outside source, under build/")
    package = Path("io/farfrontier/palemirror/frontier/v3")
    manifest = {}
    for relative, targets in TARGETS.items():
        source = source_root / package / relative
        original = source.read_text()
        result = original
        for before, after in OBSERVATIONS.get(relative, []):
            if result.count(before) != 1:
                raise ValueError(f"Expected one observation anchor in {relative}: {before!r}")
            result = result.replace(before, after)
        for anchor, name, phase in targets:
            result = enclose(result, anchor, name, phase)
        destination = output / package / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(result)
        manifest[relative] = {"source_sha256": hashlib.sha256(original.encode()).hexdigest(),
                              "overlay_sha256": hashlib.sha256(result.encode()).hexdigest(),
                              "spans": [name for _, name, _ in targets]}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Prepared {len(TARGETS)} method-observation copies; production source unchanged")


if __name__ == "__main__":
    main()
