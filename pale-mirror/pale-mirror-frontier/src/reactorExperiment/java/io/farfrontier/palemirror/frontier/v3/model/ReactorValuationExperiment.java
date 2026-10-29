package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import reactor.core.scheduler.Schedulers;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline comparison of the ACTUAL installed-cost/replacement function on a native checkpoint.
 * No engine, WAL replay, native acceptance, world writes or production cache replacement. */
public final class ReactorValuationExperiment {
    private static final MethodHandle ASSESS;
    private static final ImmutableInputView<ImmutableResultView<SubjectId, Map<String, FixedScalar>>> CURRENT_VALUES;
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static {
        try {
            ASSESS = MethodHandles.privateLookupIn(GoodsResourceValuation.class, MethodHandles.lookup())
                    .findStatic(GoodsResourceValuation.class, "assess", MethodType.methodType(Map.class, FrontierWorldState.class, GoodsParticipant.class));
            CURRENT_VALUES = (ImmutableInputView<ImmutableResultView<SubjectId, Map<String, FixedScalar>>>)
                    MethodHandles.privateLookupIn(GoodsResourceValuation.class, MethodHandles.lookup())
                            .findStaticVarHandle(GoodsResourceValuation.class, "VALUES", ImmutableInputView.class).get();
        } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }
    record Sample(String name, FrontierWorldState state) { }
    record Stored(ValuationExperimentInputs.Basis basis, Map<String, FixedScalar> value) { }
    record Prepared(FrontierWorldState state, GoodsParticipant participant, ValuationExperimentInputs.Basis basis) { }
    private static Map<String, FixedScalar> compute(FrontierWorldState state, GoodsParticipant participant) {
        try { return (Map<String, FixedScalar>) ASSESS.invokeExact(state, participant); }
        catch (Throwable failure) { throw new IllegalStateException("real valuation failed", failure); }
    }
    private static Map<String, FixedScalar> current(FrontierWorldState state, GoodsParticipant participant, long tick) {
        if (tick < 0) throw new IllegalArgumentException("negative valuation instant");
        // The unmodified CURRENT production memo and function. Read its whole vector
        // in the same one lookup as replacement(), excluding an artificial per-item
        // API loop/map reconstruction from A's measured cost. No production API edit.
        return CURRENT_VALUES.get(List.of(state), () -> new ImmutableResultView<>(256))
                .get(List.of(participant), participant.party().id(), () -> compute(state, participant));
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: <immutable snapshot.bin> <output directory>");
        var image = FrontierPersistenceCodec.decodeSnapshot(Files.readAllBytes(Path.of(args[0])));
        var state = new FrontierWorldStateCodec().decode(image.checkpoint().canonicalState());
        long tick = image.checkpoint().instant().ticks(); var output = Path.of(args[1]); Files.createDirectories(output);
        var participants = state.companies().goodsTrade().participants().participants().values().stream()
                .filter(p -> p.policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT).sorted(Comparator.comparing(p -> p.party().id())).toList();
        check(participants.size() == 100, "experiment requires the retained 100-town checkpoint");
        System.out.println("snapshot tick=" + tick + " walCovered=" + image.coveredWalSequence()
                + " towns=" + participants.size() + " residents=" + state.humanPopulation().residents().size());
        var projection = new ValuationExperimentInputs(); var home = participants.getFirst();
        var scenarios = scenarios(state, home, participants.get(1));
        var checks = new ArrayList<String>(); var base = projection.capture(state, home);
        var baseValue = current(state, home, tick);
        check(baseValue.size() > 1, "must exercise more than bread");
        for (var sample : scenarios) {
            var basis = projection.capture(sample.state(), home);
            var actual = current(sample.state(), home, tick);
            actual.forEach((kind, price) -> check(price.equals(GoodsResourceValuation.replacement(sample.state(), home, kind, tick)),
                    "current cache vector differs from public replacement API"));
            check(actual.equals(compute(sample.state(), home)), sample.name() + " direct/current mismatch");
            if (sample.name().startsWith("irrelevant")) {
                check(base.equals(basis), sample.name() + " changed local inputs");
                check(baseValue.equals(actual), sample.name() + " changed valuation");
            } else if (!sample.name().equals("base")) check(!base.equals(basis), sample.name() + " did not invalidate inputs");
            checks.add(sample.name() + ": basisChanged=" + !base.equals(basis) + ", resultChanged=" + !baseValue.equals(actual));
        }
        concurrency(state, home, scenarios, checks);
        Files.write(output.resolve("semantic-checks.txt"), checks);
        if (Boolean.getBoolean("pale_mirror.reactorExperiment.checksOnly")) {
            checks.forEach(System.out::println); return;
        }
        var samples = new ArrayList<Sample>(); var moving = state;
        for (int i = 0; i < 20; i++) {
            moving = move(moving, participants.get(1).settlementId()); samples.add(new Sample("movement-" + i, moving));
        }
        // Branch changes use named domain update boundaries; these are OFFLINE counterfactuals,
        // not forged native outcomes or edited checkpoint files.
        samples.addAll(scenarios);
        var csv = new ArrayList<String>();
        csv.add("mode,towns,round,queries,assessment_requests,downstream_changes,wall_ns,cpu_ns,allocated_bytes,projection_ns,equality_ns");
        for (int towns : new int[]{1, 100}) {
            var selected = participants.subList(0, towns);
            for (String mode : List.of("A", "B", "C")) measure(mode, selected, samples, tick, -1); // same-path warmup
            for (int round = 0; round < 3; round++) {
                var order = new ArrayList<>(List.of("A", "B", "C")); Collections.rotate(order, round);
                for (String mode : order) {
                    csv.add(measure(mode, selected, samples, tick, round));
                    Files.write(output.resolve("measurements.csv"), csv);
                }
            }
        }
        Files.write(output.resolve("semantic-checks.txt"), checks);
        var prepared = new ArrayList<Prepared>();
        for (var sample : samples.subList(0, 20)) for (var p : participants)
            prepared.add(new Prepared(sample.state(), p, projection.capture(sample.state(), p)));
        var kernel = new ArrayList<String>(); kernel.add("mode,round,queries,calls,wall_ns,cpu_ns,allocated_bytes");
        for (String mode : List.of("B", "C")) preparedMeasure(mode, prepared, -1);
        for (int round = 0; round < 3; round++) {
            var order = round % 2 == 0 ? List.of("B", "C") : List.of("C", "B");
            for (String mode : order) kernel.add(preparedMeasure(mode, prepared, round));
        }
        Files.write(output.resolve("prepared-inputs.csv"), kernel);
        Files.writeString(output.resolve("identity.txt"), "snapshot=" + Path.of(args[0]).toAbsolutePath() + "\n"
                + "sha256=" + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(args[0]))))
                + "\ntick=" + tick + "\ncoveredWalSequence=" + image.coveredWalSequence()
                + "\njava=" + System.getProperty("java.runtime.version") + "\nreactor=3.8.8\n"
                + "scope=exact checkpoint image only; subsequent WAL NOT replayed; offline immutable branches\n");
        checks.forEach(System.out::println);
    }
    private static String preparedMeasure(String mode, List<Prepared> prepared, int round) {
        var memo = new HashMap<ValuationExperimentInputs.Query, Stored>(); long calls = 0; long checksum = 0;
        long tid = Thread.currentThread().threadId(), cpu = THREADS.getCurrentThreadCpuTime();
        long allocated = THREADS.getThreadAllocatedBytes(tid), start = System.nanoTime();
        try (var reactor = new ReactorValuationProbe<Map<String, FixedScalar>>(128, 2, 256, null)) {
            long witness = 0;
            for (int sweep = 0; sweep < 20; sweep++) for (var input : prepared) {
                Map<String, FixedScalar> value;
                if (mode.equals("B")) {
                    var entry = memo.get(input.basis().query());
                    if (entry == null || !entry.basis().equals(input.basis())) {
                        entry = new Stored(input.basis(), compute(input.state(), input.participant()));
                        memo.put(input.basis().query(), entry); calls++;
                    }
                    value = entry.value();
                } else {
                    var reply = reactor.request(input.basis(), ++witness, () -> compute(input.state(), input.participant())).join();
                    check(reply.status() == ReactorValuationProbe.Status.VALUE, "prepared result unavailable"); value = reply.value();
                }
                checksum += value.size(); // consume real vectors without adding a full-map hash workload
            }
            long wall = System.nanoTime() - start, cpuNanos = THREADS.getCurrentThreadCpuTime() - cpu;
            long bytes = THREADS.getThreadAllocatedBytes(tid) - allocated;
            if (mode.equals("C")) {
                calls = reactor.calls();
            }
            check(checksum > 0, "unused prepared results");
            String row = mode + "," + round + "," + prepared.size() * 20L + "," + calls + "," + wall + "," + cpuNanos + "," + bytes;
            if (round >= 0) System.out.println("prepared " + row); return row;
        }
    }
    private static String measure(String mode, List<GoodsParticipant> participants, List<Sample> samples, long tick, int round) {
        var projection = new ValuationExperimentInputs(); var memo = new HashMap<ValuationExperimentInputs.Query, Stored>();
        var delivered = new HashMap<ValuationExperimentInputs.Query, Map<String, FixedScalar>>();
        var trace = new ArrayList<Map<String, FixedScalar>>();
        int repetitions = participants.size() == 1 ? 100 : 1;
        long calls = 0, changes = 0, projectionTime = 0, equalityTime = 0, witness = 0;
        long tid = Thread.currentThread().threadId(), cpu = THREADS.getCurrentThreadCpuTime();
        long allocated = THREADS.getThreadAllocatedBytes(tid), start = System.nanoTime();
        try (var reactor = new ReactorValuationProbe<Map<String, FixedScalar>>(128, 2, 256, null)) {
            for (int repetition = 0; repetition < repetitions; repetition++) for (var sample : samples) for (var p : participants) {
                var state = sample.state(); var query = ValuationExperimentInputs.Query.of(p);
                Map<String, FixedScalar> value;
                if (mode.equals("A")) {
                    value = current(state, p, tick); calls++; // one current-snapshot top-level memo miss per unique sample/party
                } else {
                    long before = System.nanoTime(); var basis = projection.capture(state, p);
                    projectionTime += System.nanoTime() - before;
                    if (mode.equals("B")) {
                        before = System.nanoTime(); var entry = memo.get(query);
                        boolean equal = entry != null && entry.basis().equals(basis);
                        equalityTime += System.nanoTime() - before;
                        if (!equal) { entry = new Stored(basis, compute(state, p)); memo.put(query, entry); calls++; }
                        value = entry.value();
                    } else {
                        var reply = reactor.request(basis, ++witness, () -> compute(state, p)).join();
                        check(reply.status() == ReactorValuationProbe.Status.VALUE, "synchronous experiment lost a value"); value = reply.value();
                    }
                }
                trace.add(value);
                var previous = delivered.put(query, value); if (!value.equals(previous)) changes++;
            }
            long wall = System.nanoTime() - start;
            long cpuNanos = THREADS.getCurrentThreadCpuTime() - cpu;
            long bytes = THREADS.getThreadAllocatedBytes(tid) - allocated;
            if (mode.equals("C")) {
                String stats = reactor.statistics();
                calls = reactor.calls();
                if (round >= 0) System.out.println("C lifecycle towns=" + participants.size() + " " + stats);
            }
            int index = 0;
            for (int repetition = 0; repetition < repetitions; repetition++) for (var sample : samples) for (var p : participants)
                check(trace.get(index++).equals(current(sample.state(), p, tick)), mode + " intermediate result mismatch at " + sample.name());
            String row = mode + "," + participants.size() + "," + round + "," + (long) participants.size() * samples.size() * repetitions
                    + "," + calls + "," + changes + "," + wall + "," + cpuNanos + "," + bytes + "," + projectionTime + "," + equalityTime;
            if (round >= 0) System.out.println(row); return row;
        }
    }
    private static List<Sample> scenarios(FrontierWorldState state, GoodsParticipant home, GoodsParticipant foreign) {
        var samples = new ArrayList<Sample>(); samples.add(new Sample("base", state));
        samples.add(new Sample("irrelevant-resident-movement", move(state, foreign.settlementId())));
        samples.add(new Sample("irrelevant-foreign-stock", removeOne(state, foreign)));
        samples.add(new Sample("local-stock", removeOne(state, home)));
        var resident = state.humanPopulation().residentsIn(home.settlementId()).getFirst();
        var modifiers = resident.characteristics().workModifiers();
        var changed = modifiers.with(new ResidentWorkModifiers.Modifier(new SubjectId("modifier:reactor-experiment"), HumanCapability.INDUSTRY, 500));
        samples.add(new Sample("local-workforce", state.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(
                state.humanPopulation().changeWorkModifiers(resident.id(), modifiers, changed)))));
        int day = state.humanPopulation().schedule(home.settlementId()).dayTicks();
        var schedule = new SettlementDailySchedule(day, List.of(new SettlementDailySchedule.Segment(0, 1, SettlementDailySchedule.Window.WORK),
                new SettlementDailySchedule.Segment(1, day, SettlementDailySchedule.Window.FREE)));
        samples.add(new Sample("local-schedule", state.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(
                state.humanPopulation().withSchedule(home.settlementId(), schedule)))));
        var station = state.inventory().containers().values().stream().filter(c -> c.ownerId().equals(home.settlementId())
                && c.productionStation().isPresent()).findFirst().orElseThrow();
        var conditions = ImmutableLookupMap.withEntry(state.structureConditions(), station.productionStation().orElseThrow().facilityId(), StructureCondition.DAMAGED);
        samples.add(new Sample("local-station-unavailable", state.withChanges(FrontierWorldStateUpdate.begin().structureConditions(conditions))));
        return List.copyOf(samples);
    }
    private static FrontierWorldState move(FrontierWorldState state, SubjectId home) {
        var id = state.humanPopulation().residentsIn(home).getFirst().id(); var old = state.actorLocations().get(id);
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(ImmutableLookupMap.withEntry(state.actorLocations(), id,
                old.withBody(old.body().offset(1, 0, 0)))));
    }
    private static FrontierWorldState removeOne(FrontierWorldState state, GoodsParticipant p) {
        var ledger = state.inventory().fungibleResources();
        for (var kind : state.bootstrap().ruleset().goodsTrade().policies().get(p.policy())) {
            for (var id : GoodsParticipantStorage.containers(state, p, kind.itemKind())) {
                var account = FungibleResourceCustodySupport.accountAtContainer(state, id).orElse(null);
                if (account == null || !account.claimQuantities().isEmpty()
                        || ledger.bindings().values().stream().anyMatch(b -> b.accountId().equals(account.id()))) continue;
                for (var entry : account.lotQuantities().entrySet()) {
                    if (!ledger.lots().get(entry.getKey()).economicOwnerId().equals(p.party().id())) continue;
                    return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory()
                            .withFungibleResources(ledger.destroy(account.id(), Map.of(entry.getKey(), 1), Map.of()))));
                }
            }
        }
        throw new IllegalStateException("checkpoint has no unbound unclaimed local stock for actual removal");
    }
    private static void concurrency(FrontierWorldState state, GoodsParticipant p, List<Sample> samples, List<String> checks) throws Exception {
        var projection = new ValuationExperimentInputs(); var base = projection.capture(state, p);
        var other = projection.capture(samples.stream().filter(s -> s.name().equals("local-stock")).findFirst().orElseThrow().state(), p);
        var executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2));
        var scheduler = Schedulers.fromExecutor(executor::execute); // wrapper never owns/shuts down host executor
        var count = new AtomicInteger(); var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        var closeStarted = new CountDownLatch(1); var closeRelease = new CountDownLatch(1);
        try (var reactor = new ReactorValuationProbe<Map<String, FixedScalar>>(1, 1, 8, scheduler)) {
            var first = reactor.request(base, 1, () -> {
                count.incrementAndGet(); started.countDown(); await(release); return compute(state, p);
            });
            check(started.await(30, TimeUnit.SECONDS), "computation did not start");
            var shared = reactor.request(base, 1, () -> { throw new AssertionError("duplicate computation"); });
            var cancelled = reactor.request(base, 1, () -> { throw new AssertionError("duplicate after cancellation"); });
            cancelled.cancel(true);
            var latest = reactor.request(base, 3, () -> { throw new AssertionError("unrelated witness restarted computation"); });
            release.countDown();
            check(first.get(30, TimeUnit.SECONDS).status() == ReactorValuationProbe.Status.STALE, "old witness published");
            check(shared.get(30, TimeUnit.SECONDS).status() == ReactorValuationProbe.Status.STALE, "old shared witness published");
            check(latest.get(30, TimeUnit.SECONDS).value().equals(compute(state, p)) && count.get() == 1, "singleflight wrong result");
            // No sleep or performance assertion: barrier on the same owned executor drains callbacks.
            executor.submit(() -> {}).get(30, TimeUnit.SECONDS);
            var branchState = samples.stream().filter(s -> s.name().equals("local-stock")).findFirst().orElseThrow().state();
            var branchStarted = new CountDownLatch(1); var branchRelease = new CountDownLatch(1);
            var branch = reactor.request(other, 4, () -> {
                branchStarted.countDown(); await(branchRelease); return compute(branchState, p);
            });
            check(branchStarted.await(30, TimeUnit.SECONDS), "branch did not start");
            check(reactor.request(base, 5, () -> compute(state, p)).join().status() == ReactorValuationProbe.Status.WAITING_CAPACITY,
                    "saturation became a domain result");
            branchRelease.countDown();
            check(branch.get(30, TimeUnit.SECONDS).status() == ReactorValuationProbe.Status.STALE,
                    "old branch published while its replacement waited for capacity");
            executor.submit(() -> {}).get(30, TimeUnit.SECONDS);
            check(reactor.request(base, 6, () -> compute(state, p)).get(30, TimeUnit.SECONDS).value().equals(compute(state, p)), "evicted base mixed");
            executor.submit(() -> {}).get(30, TimeUnit.SECONDS);
            checks.add("concurrent-sharing/cancel/saturation/stale/branch/eviction: " + reactor.statistics());
            var unfinished = reactor.request(other, 7, () -> {
                closeStarted.countDown(); await(closeRelease); return compute(branchState, p);
            });
            check(closeStarted.await(30, TimeUnit.SECONDS), "close scenario did not start");
            reactor.close();
            closeRelease.countDown();
            check(unfinished.get(30, TimeUnit.SECONDS).status() == ReactorValuationProbe.Status.STALE, "closed world published late result");
            check(reactor.request(base, 8, () -> compute(state, p)).join().status() == ReactorValuationProbe.Status.CLOSED, "closed world accepted query");
            check(!executor.isShutdown(), "adapter disposed host executor");
            checks.add("world-close-during-work/late-result/host-ownership: passed");
        } finally { release.countDown(); closeRelease.countDown(); scheduler.dispose(); executor.shutdownNow(); }
        try (var failure = new ReactorValuationProbe<Map<String, FixedScalar>>(1, 1, 1, null)) {
            try { failure.request(base, 1, () -> { throw new IllegalStateException("injected provider failure"); }).join(); throw new AssertionError("error became success"); }
            catch (CompletionException expected) { check(expected.getCause() instanceof IllegalStateException, "lost error"); }
            check(failure.request(base, 2, () -> compute(state, p)).join().value().equals(compute(state, p)), "error poisoned cache");
            checks.add("provider-error/explicit-retry: passed");
        }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("experiment barrier timeout"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
