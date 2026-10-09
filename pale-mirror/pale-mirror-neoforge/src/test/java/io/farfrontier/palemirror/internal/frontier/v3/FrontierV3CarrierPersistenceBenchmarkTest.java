package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in real-file A/B of identical production ledger mutations; not a server TPS benchmark. */
class FrontierV3CarrierPersistenceBenchmarkTest {
    private static final String PACKAGE = "io.farfrontier.palemirror.internal.frontier.v3.";
    private static final int WARMUP = 16, WRITES = 32, PAIRS = 5;
    @TempDir Path directory;

    @Test void measureIdenticalDurableActorChangesAgainstSelectedBaselineJar() throws Exception {
        String baselineJar = System.getenv("PM_CARRIER_BENCHMARK_BASELINE_JAR");
        assumeTrue(baselineJar != null, "explicit baseline required; never a normal test gate");
        assertTrue(Files.isRegularFile(Path.of(baselineJar)));
        SharedConstants.tryDetectVersion();
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:carrier-storage-benchmark"), 91L));
        var baseline = new ArrayList<Sample>(); var candidate = new ArrayList<Sample>();
        try (var loader = new URLClassLoader(new URL[]{Path.of(baselineJar).toUri().toURL()}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (name.startsWith(PACKAGE)) {
                        var found = findLoadedClass(name);
                        if (found == null) try { found = findClass(name); } catch (ClassNotFoundException absent) { /* shared host */ }
                        if (found != null) { if (resolve) resolveClass(found); return found; }
                    }
                    return super.loadClass(name, resolve);
                }
            }
        }) {
            for (int pair = 0; pair < PAIRS; pair++) {
                // Alternate order; each side gets the same roster, warmup and durable operations.
                if (pair % 2 == 0) {
                    baseline.add(sample(loader, state, "baseline-" + pair));
                    candidate.add(sample(getClass().getClassLoader(), state, "candidate-" + pair));
                } else {
                    candidate.add(sample(getClass().getClassLoader(), state, "candidate-" + pair));
                    baseline.add(sample(loader, state, "baseline-" + pair));
                }
            }
        }
        double baselineMedian = median(baseline), candidateMedian = median(candidate);
        String report = "{\"kind\":\"physical_carrier_persistence_ab\",\"roster\":" + state.actorLocations().size()
                + ",\"pairs\":" + PAIRS + ",\"warmupWrites\":" + WARMUP + ",\"measuredWritesPerSample\":" + WRITES
                + ",\"baseline\":" + samples(baseline) + ",\"candidate\":" + samples(candidate)
                + ",\"baselineMedianMillis\":" + baselineMedian + ",\"candidateMedianMillis\":" + candidateMedian
                + ",\"medianRatio\":" + baselineMedian / candidateMedian
                + ",\"scope\":\"same synchronous durable actor update; not total tick latency or async insertion\"}";
        System.out.println(report);
        String output = System.getenv("PM_CARRIER_BENCHMARK_REPORT");
        if (output != null) Files.writeString(Path.of(output), report + "\n");
    }

    private record Sample(double millis, long encodedBytes, double maxWriteMillis) { }
    private Sample sample(ClassLoader loader, FrontierWorldState state, String name) throws Exception {
        var ledgerClass = loader.loadClass(PACKAGE + "FrontierV3AmbientCarrierLedger");
        var identityClass = loader.loadClass(PACKAGE + "FrontierV3ActorFirstAdmission$Identity");
        var permitClass = loader.loadClass(PACKAGE + "FrontierV3ActorFirstAdmission");
        var declarationClass = loader.loadClass(PACKAGE + "FrontierV3ActorCarrierComposition$Declaration");
        var ownerClass = loader.loadClass(PACKAGE + "FrontierV3ActorCarrierComposition$Owner");
        var representationClass = loader.loadClass(PACKAGE + "FrontierV3ActorCarrierComposition$Representation");
        Object ledger = method(ledgerClass, "emptyForTest").invoke(null);
        var identity = identityClass.getDeclaredConstructor(SubjectId.class, ActorKind.class, UUID.class); identity.setAccessible(true);
        var never = method(permitClass, "neverCreated", identityClass);
        var permits = new ArrayList<>();
        for (var actor : new TreeMap<>(state.actorLocations()).entrySet()) {
            var uuid = SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor.getKey());
            permits.add(never.invoke(null, identity.newInstance(actor.getKey(), actor.getValue().kind(), uuid)));
        }
        assertEquals(true, method(ledgerClass, "registerFirstAdmissions", List.class).invoke(ledger, permits));
        var actor = new SubjectId("resident:1-1");
        var constructor = declarationClass.getDeclaredConstructor(SubjectId.class, ActorKind.class, ownerClass,
                UUID.class, representationClass, long.class, long.class); constructor.setAccessible(true);
        Object declaration = constructor.newInstance(actor, state.actorLocations().get(actor).kind(),
                enumValue(ownerClass, "ACTOR_BODY"), SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor),
                enumValue(representationClass, "LIVE_BODY"), 0L, 1L);
        var change = method(ledgerClass, "beginBodyResidence", declarationClass);
        var save = method(ledgerClass, "save", java.io.File.class, HolderLookup.Provider.class);
        Path folder = Files.createDirectory(directory.resolve(name)), file = folder.resolve("physical.dat");
        save.invoke(ledger, file.toFile(), null);
        for (int i = 0; i < WARMUP; i++) { change.invoke(ledger, declaration); save.invoke(ledger, file.toFile(), null); }
        boolean journal = Files.isDirectory(folder.resolve("physical.dat.journal"));
        long initialBytes = journal ? diskBytes(folder) : 0, encodedBytes = 0, elapsed = 0, maximum = 0;
        for (int i = 0; i < WRITES; i++) {
            long before = System.nanoTime(); change.invoke(ledger, declaration); save.invoke(ledger, file.toFile(), null);
            long duration = System.nanoTime() - before; elapsed += duration; maximum = Math.max(maximum, duration);
            if (!journal) encodedBytes += Files.size(file); // Old path really rewrites this complete compressed image.
        }
        if (journal) encodedBytes = diskBytes(folder) - initialBytes; // No checkpoint rollover in this bounded sample.
        assertEquals(true, method(ledgerClass, "currentBodyResidence", SubjectId.class, long.class).invoke(ledger, actor, (long) WARMUP + WRITES));
        return new Sample(elapsed / 1_000_000.0, encodedBytes, maximum / 1_000_000.0);
    }
    private static Method method(Class<?> type, String name, Class<?>... arguments) throws Exception {
        var result = type.getDeclaredMethod(name, arguments); result.setAccessible(true); return result;
    }
    @SuppressWarnings({"unchecked", "rawtypes"}) private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }
    private static long diskBytes(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            long total = 0; for (Path file : paths.filter(Files::isRegularFile).toList()) total += Files.size(file); return total;
        }
    }
    private static double median(List<Sample> values) { return values.stream().mapToDouble(Sample::millis).sorted().toArray()[PAIRS / 2]; }
    private static String samples(List<Sample> values) {
        return values.stream().map(value -> "{\"millis\":" + value.millis() + ",\"encodedBytes\":" + value.encodedBytes()
                + ",\"maxWriteMillis\":" + value.maxWriteMillis() + "}").collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
