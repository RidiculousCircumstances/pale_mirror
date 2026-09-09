package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3PilotNaturalDemandStopAdmissionTest {
    private static final String RUN_ID = "00000000-0000-0000-0000-000000000081";
    private static final String NONCE = "00000000-0000-0000-0000-000000000082";
    private static final FrontierV3PilotNaturalDemandEpisode.ChunkKey CENTER = key(12, -7);
    private static final FrontierV3PilotNaturalDemandEpisode.ChunkKey DEPENDENCY = key(13, -7);
    private final Object server = new Object();
    private final Map<Integer, Object> holders = new HashMap<>();

    @Test
    void actualAdmissionPathFreshlyReadsEligibleStateAndHaltsExactlyOnce() {
        FrontierV3PilotNaturalDemandEpisode episode = eligible(41);
        FrontierV3PilotNaturalDemandStopAdmission admission = admission();
        AtomicInteger halts = new AtomicInteger();

        admission.admit(server, RUN_ID, 12345L, NONCE,
                () -> observe(episode, Set.of(), Map.of(CENTER, holder(41, 0, true)), true), halts::incrementAndGet);

        assertEquals(1, halts.get());
        assertEquals(true, admission.consumed());
        assertThrows(IllegalStateException.class, () -> admission.admit(server, RUN_ID, 12345L, NONCE,
                () -> observe(episode, Set.of(), Map.of(CENTER, holder(41, 0, true)), true), halts::incrementAndGet));
        assertEquals(1, halts.get(), "a replay cannot admit a second halt");
    }

    @Test
    void lateDependencyReadinessAtTheFormerCheckUseSeamAdmitsNoHalt() {
        FrontierV3PilotNaturalDemandEpisode episode = eligible(42);
        AtomicInteger halts = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> admission().admit(server, RUN_ID, 12345L, NONCE,
                () -> observe(episode, Set.of(), Map.of(CENTER, holder(42, 0, true), DEPENDENCY, holder(43, 1, false)), true),
                halts::incrementAndGet));

        assertEquals(0, halts.get());
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.INVALID, episode.status());
    }

    @Test
    void latePlayerDemandAtTheFormerCheckUseSeamAdmitsNoHalt() {
        FrontierV3PilotNaturalDemandEpisode episode = eligible(44);
        AtomicInteger halts = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> admission().admit(server, RUN_ID, 12345L, NONCE,
                () -> observe(episode, Set.of(CENTER), Map.of(CENTER, holder(44, 0, true)), true), halts::incrementAndGet));

        assertEquals(0, halts.get());
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.INVALID, episode.status());
    }

    @Test
    void staleOrForeignIdentityAdmitsNoHaltBeforeFreshRead() {
        FrontierV3PilotNaturalDemandEpisode episode = eligible(45);
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger halts = new AtomicInteger();
        FrontierV3PilotNaturalDemandStopAdmission admission = admission();

        assertThrows(IllegalStateException.class, () -> admission.admit(server, RUN_ID, 12345L,
                "00000000-0000-0000-0000-000000000083", () -> {
                    reads.incrementAndGet();
                    return observe(episode, Set.of(), Map.of(CENTER, holder(45, 0, true)), true);
                }, halts::incrementAndGet));
        assertThrows(IllegalStateException.class, () -> admission.admit(new Object(), RUN_ID, 12345L, NONCE, () -> {
            reads.incrementAndGet();
            return observe(episode, Set.of(), Map.of(CENTER, holder(45, 0, true)), true);
        }, halts::incrementAndGet));

        assertEquals(0, reads.get());
        assertEquals(0, halts.get());
    }

    private FrontierV3PilotNaturalDemandStopAdmission admission() {
        return new FrontierV3PilotNaturalDemandStopAdmission(server, RUN_ID, 12345L, NONCE);
    }

    private FrontierV3PilotNaturalDemandEpisode eligible(int diagnostic) {
        FrontierV3PilotNaturalDemandEpisode episode = new FrontierV3PilotNaturalDemandEpisode();
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.ARMED,
                observe(episode, Set.of(CENTER), Map.of(CENTER, holder(diagnostic, 0, true)), false));
        assertEquals(FrontierV3PilotNaturalDemandEpisode.Status.ELIGIBLE,
                observe(episode, Set.of(), Map.of(CENTER, holder(diagnostic, 0, true)), true));
        return episode;
    }

    private FrontierV3PilotNaturalDemandEpisode.Holder holder(int diagnostic, int references, boolean ready) {
        return new FrontierV3PilotNaturalDemandEpisode.Holder(holders.computeIfAbsent(diagnostic, ignored -> new Object()), diagnostic, references, ready);
    }

    private static FrontierV3PilotNaturalDemandEpisode.Status observe(FrontierV3PilotNaturalDemandEpisode episode,
            Set<FrontierV3PilotNaturalDemandEpisode.ChunkKey> tickets,
            Map<FrontierV3PilotNaturalDemandEpisode.ChunkKey, FrontierV3PilotNaturalDemandEpisode.Holder> values, boolean released) {
        return FrontierV3PilotNaturalDemandObserver.observeForTest(episode, tickets, values, released);
    }

    private static FrontierV3PilotNaturalDemandEpisode.ChunkKey key(int x, int z) {
        return new FrontierV3PilotNaturalDemandEpisode.ChunkKey("pale_mirror:frontier_graybox",
                (long) x & 0xFFFF_FFFFL | ((long) z & 0xFFFF_FFFFL) << 32);
    }
}
