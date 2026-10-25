package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import java.util.*;
import java.util.stream.Collectors;

/** Bounded read-only forest/work view. Never computes routes or starts an economic review. */
final class FrontierV3ForestryDiagnostic {
    private FrontierV3ForestryDiagnostic() { }
    static String render(String id, FrontierScheduleView checkpoint, FrontierWorldState state) {
        var home = FrontierV3DiagnosticJson.subject(id).orElse(null);
        var settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(home)).findFirst().orElse(null);
        if (settlement == null) return FrontierV3DiagnosticJson.unavailable("forest", id, checkpoint, "not_found");
        var known = ForestKnowledge.knownBy(state, home); var rules = state.bootstrap().ruleset().forestry();
        var available = known.stream().filter(header -> state.forest().find(header.tree()).map(tree -> tree.remaining() > 0).orElse(true)).toList();
        String stocks = rules.harvests().stream().map(TreeHarvestProfile::outputItem).distinct().sorted().map(kind -> "{\"item\":" + string(kind)
                + ",\"held\":" + ForestryStockDemand.held(state, home, kind) + ",\"requested\":"
                + ForestryStockDemand.requested(state, home, kind, Optional.empty()) + "}").collect(Collectors.joining(",", "[", "]"));
        String jobs = state.forest().work().values().stream().filter(job -> job.home().equals(home)).sorted(Comparator.comparing(ForestryWork::id))
                .map(job -> job(state, checkpoint, job)).collect(Collectors.joining(",", "[", "]"));
        var actors = state.forest().work().values().stream().filter(job -> job.home().equals(home)).map(job -> job.execution().actorId()).sorted().toList();
        var target = state.forest().work().values().stream().filter(job -> job.home().equals(home)).sorted(Comparator.comparing(ForestryWork::id))
                .flatMap(job -> job.target().stream()).findFirst();
        String workSource = target.map(value -> FrontierV3DiagnosticJson.position(state.forest().require(value.tree()).declaration().origin()))
                .orElseGet(() -> available.isEmpty() ? "null" : FrontierV3DiagnosticJson.position(available.getFirst().origin()));
        boolean workersCold = actors.stream().allMatch(actor -> ActorExecutionCoordinator.coldAvailable(state, actor));
        long liveSources = known.stream().filter(header -> ForestrySourceCustody.blocksCold(state, header.tree())).count();
        boolean hasWood = rules.harvests().stream().anyMatch(profile -> ForestryStockDemand.held(state, home, profile.outputItem()) > 0);
        boolean supplied = rules.harvests().stream().allMatch(profile -> ForestryStockDemand.held(state, home, profile.outputItem()) >= rules.reserveItems());
        String first = available.isEmpty() ? "null" : FrontierV3DiagnosticJson.position(available.getFirst().origin());
        return FrontierV3DiagnosticJson.base("forest", id, checkpoint) + ",\"status\":\"ok\",\"enabled\":" + ForestryStockDemand.enabled(state, home)
                + ",\"knownTrees\":" + known.size() + ",\"availableTrees\":" + available.size() + ",\"firstTree\":" + first
                + ",\"exhaustedTrees\":" + (known.size()-available.size()) + ",\"activeWorkers\":" + actors.size()
                + ",\"hasPendingEffects\":" + state.forest().work().values().stream().anyMatch(job -> job.home().equals(home) && job.pending().isPresent())
                + ",\"workSource\":" + workSource + ",\"workersCold\":" + workersCold + ",\"liveSources\":" + liveSources
                + ",\"hasWood\":" + hasWood + ",\"reserveSupplied\":" + supplied
                + ",\"reserveTarget\":" + rules.reserveItems() + ",\"resumeBelow\":" + rules.resumeBelowItems() + ",\"stocks\":" + stocks
                + ",\"workers\":" + FrontierV3DiagnosticJson.strings(actors.stream().map(SubjectId::value).toList()) + ",\"jobs\":" + jobs + "}";
    }
    private static String job(FrontierWorldState state, FrontierScheduleView checkpoint, ForestryWork job) {
        var tool = state.inventory().items().get(job.toolId()); var target = job.target();
        var lease = target.map(value -> state.replicaCustody().custodyByScope().get(ForestrySourceCustody.scope(value.tree())));
        var ambient = state.ambientLeases().get(job.execution().actorId());
        return "{\"id\":" + string(job.id().value()) + ",\"actor\":" + string(job.execution().actorId().value())
                + ",\"phase\":" + string(job.phase().name()) + ",\"revision\":" + job.revision()
                + ",\"position\":" + FrontierV3DiagnosticJson.position(state.actorLocations().get(job.execution().actorId()).body())
                + ",\"station\":" + (job.phase() == ForestryWork.Phase.SELECT_TREE ? "null"
                    : FrontierV3DiagnosticJson.position(job.movementOrder().legalStations().getFirst().standingBody()))
                + ",\"tree\":" + target.map(value -> string(value.tree().id().value().toString())).orElse("null")
                + ",\"sourcePosition\":" + target.map(value -> FrontierV3DiagnosticJson.position(state.forest().require(value.tree()).declaration().origin())).orElse("null")
                + ",\"sourceAuthority\":" + lease.map(value -> string(value.status().name())).orElse("null")
                + ",\"carried\":" + ForestryWorkAuthority.carried(state, job) + ",\"tool\":" + string(job.toolId().value())
                + ",\"toolRemaining\":" + (tool == null ? 0 : tool.condition().remaining())
                + ",\"moving\":" + state.actorMovements().containsKey(job.execution().actorId())
                + ",\"physical\":" + (ambient == null ? "null" : string(ambient.status().name()))
                + ",\"pendingEffect\":" + job.pending().isPresent() + ",\"appliedChildren\":" + job.appliedEffects()
                + ",\"plannedChildren\":" + job.felling().map(value -> value.batch().effects().size()).orElse(0)
                + ",\"completedMilliWork\":" + job.labour().map(value -> value.completedAt(checkpoint.instant().ticks())).orElse(0L)
                + ",\"requiredMilliWork\":" + job.labour().map(WorkProgress::requiredMilliWork).orElse(0L) + "}";
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
}
