package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.model.ValuationProbeScenarios.*;

/** Source-compatible with the pre-boundary production JAR, so the SAME public
 * consumer and immutable branches compare actual old/new code in separate JVMs.
 * No engine, background threads, canonical save, native outcome or timing assertion. */
public final class FrontierV3OwnerFactsDiagnostic {
    private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: snapshot.bin output-directory");
        var snapshot = FrontierPersistenceCodec.decodeSnapshot(Files.readAllBytes(Path.of(args[0])));
        var state = new FrontierWorldStateCodec().decode(snapshot.checkpoint().canonicalState());
        var participants = state.companies().goodsTrade().participants().participants().values().stream()
                .filter(p -> p.policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT).sorted(Comparator.comparing(p -> p.party().id())).toList();
        if (participants.size() != 100) throw new IllegalArgumentException("requires the retained 100-town checkpoint");
        long tick = snapshot.checkpoint().instant().ticks();
        var samples = new ArrayList<Sample>(); var moving = state;
        for (int i = 0; i < 20; i++) {
            moving = move(moving, participants.get(1).settlementId()); samples.add(new Sample("movement-" + i, moving));
        }
        samples.addAll(scenarios(state, participants.getFirst(), participants.get(1)));
        var output = Path.of(args[1]); Files.createDirectories(output);
        var rows = new ArrayList<String>(); rows.add("towns,round,queries,wall_ns,cpu_ns,allocated_bytes");
        var answers = new ArrayList<String>(); answers.add("scenario\tparticipant\titem\traw_price");
        for (int towns : new int[]{1, 100}) {
            var selected = participants.subList(0, towns); int repetitions = towns == 1 ? 100 : 1;
            measure(selected, samples, repetitions, tick, -1, null);
            for (int round = 0; round < 3; round++)
                rows.add(measure(selected, samples, repetitions, tick, round, towns == 100 && round == 0 ? answers : null));
        }
        Files.write(output.resolve("measurements.csv"), rows); Files.write(output.resolve("answers.tsv"), answers);
        var stock = new ArrayList<String>(); stock.add("scenario\tparticipant\titem\tavailable");
        var claims = new ArrayList<String>(); claims.add("scenario\titem");
        for (var sample : samples) {
            WorkEquipmentClaimComposition.reserved(sample.state()).stream().sorted()
                    .forEach(id -> claims.add(sample.name() + "\t" + id.value()));
            for (var p : participants) for (var commodity : sample.state().bootstrap().ruleset().goodsTrade().policies().get(p.policy()))
                stock.add(sample.name() + "\t" + p.party().id().value() + "\t" + commodity.itemKind() + "\t"
                        + GoodsParticipantStorage.unclaimed(sample.state(), p, commodity.itemKind()));
        }
        Files.write(output.resolve("stock.tsv"), stock); Files.write(output.resolve("claims.tsv"), claims);
        Files.writeString(output.resolve("identity.txt"), "snapshotSha256=" + HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(args[0]))))
                + "\ntick=" + tick + "\ncoveredWalSequence=" + snapshot.coveredWalSequence()
                + "\njava=" + System.getProperty("java.runtime.version") + "\nmethod=public GoodsResourceValuation.replacement for all declared commodities\n");
    }
    private static String measure(List<GoodsParticipant> participants, List<Sample> samples, int repetitions,
                                  long tick, int round, List<String> answers) {
        var values = new ArrayList<Map<String, FixedScalar>>();
        long cpu = THREADS.getCurrentThreadCpuTime(), bytes = THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId());
        long start = System.nanoTime();
        for (int repetition = 0; repetition < repetitions; repetition++) for (var sample : samples) for (var participant : participants) {
            var vector = new TreeMap<String, FixedScalar>();
            for (var commodity : sample.state().bootstrap().ruleset().goodsTrade().policies().get(participant.policy()))
                vector.put(commodity.itemKind(), GoodsResourceValuation.replacement(sample.state(), participant, commodity.itemKind(), tick));
            values.add(Map.copyOf(vector));
        }
        long wall = System.nanoTime() - start, cpuNanos = THREADS.getCurrentThreadCpuTime() - cpu;
        long allocated = THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes;
        if (answers != null) {
            int index = 0;
            for (int repetition = 0; repetition < repetitions; repetition++) for (var sample : samples) for (var participant : participants) {
                var vector = values.get(index++);
                for (var kind : vector.keySet().stream().sorted().toList()) answers.add(sample.name() + "\t" + participant.party().id().value()
                        + "\t" + kind + "\t" + vector.get(kind).raw());
            }
        }
        var row = participants.size() + "," + round + "," + values.size() + "," + wall + "," + cpuNanos + "," + allocated;
        if (round >= 0) System.out.println(row); return row;
    }
    private FrontierV3OwnerFactsDiagnostic() { }
}
