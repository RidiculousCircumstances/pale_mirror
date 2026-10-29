package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.diagnostic.TradeCostTrace;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Same real trade call with unmodified or method-observed classes. Manual only;
 * no thresholds, fake route success, native writes or production dependency. */
public final class TradeCostAttribution {
    record Query(String workload, CommittedTradeCorpus.Frame frame, SubjectId party) { }
    private static String calculate(Query query) {
        var frame = query.frame(); var state = frame.state();
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(query.party()));
        PedestrianRoutePlanner pending = (g, s, t) -> new PedestrianRouteResult(
                PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "EXPERIMENT_NO_COMPLETED_ROUTE");
        var preparation = FrontierWorldState.duringUnpublishedTransition(() ->
                PedestrianRoutePlanning.prepare(pending, () -> GoodsTripPlanning.plan(state, participant, frame.tick())));
        var result = preparation.observation().result();
        var demands = preparation.demands().entrySet().stream().map(entry -> {
            var request = entry.getKey();
            return request.start() + "->" + request.target() + ":" + request.extent() + ":" + entry.getValue() + ":" + request.geometry().bounds();
        }).sorted().toList();
        // Declared inputs are identical captured frames. Do not serialize rebuilt
        // geometry wrapper identities into the cross-JVM semantic receipt.
        return query.workload() + "\t" + frame.revision() + "\t" + query.party().value() + "\t" + result.choice()
                + "\t" + result.decision() + "\t" + result.awaitingNavigation() + "\t" + result.events() + "\t" + demands;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: <captured corpus> <output>");
        var output = Path.of(args[1]); Files.createDirectories(output);
        var corpus = CommittedTradeCorpus.read(Path.of(args[0]));
        var frames = corpus.frames(); var last = frames.getLast();
        var participants = last.state().companies().goodsTrade().participants().participants().values().stream()
                .filter(p -> p.policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT).sorted(Comparator.comparing(p -> p.party().id())).toList();
        if (participants.size() != 100) throw new IllegalArgumentException("requires retained 100-town corpus");
        var queries = new ArrayList<Query>();
        for (var participant : participants) queries.add(new Query("all-100", last, participant.party().id()));
        var ticks = new TreeMap<Long, CommittedTradeCorpus.Frame>(); frames.forEach(frame -> ticks.put(frame.tick(), frame));
        for (var frame : ticks.values()) for (int index : new int[]{9, 20, 81, 8})
            queries.add(new Query("accepted-4x27", frame, participants.get(index).party().id()));
        var costs = new ArrayList<String>();
        costs.add("round,workload,revision,participant,category,name,calls,cpu_ns,self_cpu_ns,allocated_bytes,self_allocated_bytes,wall_ns");
        List<String> oracle = null;
        for (int round = -1; round < 2; round++) {
            var answers = new ArrayList<String>();
            for (var query : queries) {
                var measured = TradeCostTrace.measure(() -> calculate(query)); answers.add(measured.answer());
                if (round >= 0) {
                    append(costs, round, query, "method", measured.methods());
                    append(costs, round, query, "phase", measured.phases());
                    append(costs, round, query, "outcome", measured.outcomes());
                }
            }
            if (oracle == null) oracle = List.copyOf(answers);
            else if (!oracle.equals(answers)) throw new AssertionError("repeat changed semantic answers");
            if (round >= 0) Files.write(output.resolve("costs.csv"), costs);
            System.out.println("round=" + round + " decisions=" + answers.size() + " methods/phase rows=" + costs.size());
        }
        var answerText = String.join("\n", oracle) + "\n";
        Files.writeString(output.resolve("answers.tsv"), answerText);
        var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(answerText.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.writeString(output.resolve("identity.txt"), "overlay=" + Boolean.getBoolean("pale_mirror.tradeCost.overlay")
                + "\nsnapshot=" + corpus.snapshot() + "\ncovered=" + corpus.covered() + "\ntransactions=" + frames.size()
                + "\nreplay_ns=" + corpus.replayNanos() + "\nqueries_per_round=" + queries.size() + "\nanswer_sha256=" + hash
                + "\njava=" + System.getProperty("java.runtime.version") + "\nscope=offline incomplete-route planning; instrumented attribution is not native TPS or uninstrumented latency\n");
        System.out.println("answer_sha256=" + hash);
    }
    private static void append(List<String> rows, int round, Query query, String category, Map<String, TradeCostTrace.Cost> costs) {
        costs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var value = entry.getValue();
            rows.add(round + "," + query.workload() + "," + query.frame().revision() + "," + query.party().value()
                    + "," + category + "," + entry.getKey() + "," + value.calls() + "," + value.cpu() + "," + value.selfCpu()
                    + "," + value.bytes() + "," + value.selfBytes() + "," + value.wall());
        });
    }
    private TradeCostAttribution() { }
}
