package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3CrashBoundaryProbeTest {
    @Test
    void pilotCrashMixinResourceOwnsOnlyItsDedicatedMixinNamespace() throws IOException {
        try (InputStream stream = FrontierV3CrashBoundaryProbeTest.class.getClassLoader()
                .getResourceAsStream("pale_mirror.frontier_v3.pilot_crash.mixins.json")) {
            assertNotNull(stream, "pilot crash mixin resource");
            JsonObject resource = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("io.farfrontier.palemirror.internal.frontier.v3.mixin", resource.get("package").getAsString(),
                    "the mixin package must not own fixture, hook, or probe classes");
            List<String> mixins = new ArrayList<>();
            resource.getAsJsonArray("mixins").forEach(mixin -> mixins.add(mixin.getAsString()));
            assertEquals(List.of("FrontierV3DurableServerSaveMixin", "FrontierV3DurableCrashWindowMixin",
                    "FrontierV3HarvestCrashWindowMixin"), mixins);
        }
    }

    @Test
    void pilotCrashHooksRejectANonRuntimeBeforeDelegatingToTheProbe() {
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3PilotCrashHooks.afterVisibleCropEffectBeforeObservation(new Object(), null));
    }

    @Test
    void armMatchesOnlyItsExactPilotNonceOwnerRevisionAndPayload() {
        FrontierV3CrashBoundaryProbe probe = FrontierV3CrashBoundaryProbe.from(properties(7L, "site:crash-owner",
                FrontierV3CrashBoundaryProbe.HOT_CHECKPOINT_DURABLE_BEFORE_DRAIN_RELEASE,
                "frontier.resource_site_harvest_hot_traversal_advanced")::get, ignored -> { });
        assertTrue(probe.armed());
        assertTrue(probe.matches(transaction(7L, "site:crash-owner", "frontier.resource_site_harvest_hot_traversal_advanced")));
        assertFalse(probe.matches(transaction(8L, "site:crash-owner", "frontier.resource_site_harvest_hot_traversal_advanced")));
        assertFalse(probe.matches(transaction(7L, "site:other-owner", "frontier.resource_site_harvest_hot_traversal_advanced")));
        assertFalse(probe.matches(transaction(7L, "site:crash-owner", "frontier.other")));
    }

    @Test
    void observedRevisionArmPublishesTheExactTransactionItParksInside() {
        Map<String, String> values = new java.util.HashMap<>(properties(7L, "site:crash-owner",
                FrontierV3CrashBoundaryProbe.HOT_CHECKPOINT_DURABLE_BEFORE_DRAIN_RELEASE,
                "frontier.resource_site_harvest_hot_traversal_advanced"));
        values.put("pale_mirror.frontier_v3.pilot.crash.revision", "observed_at_boundary");
        FrontierV3CrashBoundaryProbe probe = FrontierV3CrashBoundaryProbe.from(values::get, ignored -> { });
        // The event is still fully namespaced by boundary/owner/payload.  The first match parks
        // the server thread, so no second transaction can slip through before the external PID
        // controller receives the actual revision from the marker.
        assertTrue(probe.matches(transaction(173L, "site:crash-owner", "frontier.resource_site_harvest_hot_traversal_advanced")));
        assertFalse(probe.matches(transaction(173L, "site:other-owner", "frontier.resource_site_harvest_hot_traversal_advanced")));
        assertFalse(probe.matches(transaction(173L, "site:crash-owner", "frontier.other")));
    }

    @Test
    void partialOrBroadPilotCrashConfigurationFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> FrontierV3CrashBoundaryProbe.from(Map.<String, String>of(
                "pale_mirror.frontier_v3.pilot.crash.boundary", FrontierV3CrashBoundaryProbe.HOT_CHECKPOINT_DURABLE_BEFORE_DRAIN_RELEASE)::get, ignored -> { }));
        assertFalse(FrontierV3CrashBoundaryProbe.from(ignored -> "", ignored -> { }).armed());
    }

    @Test
    void directSubjectWindowsMatchOnlyTheirOwnProductionPayload() {
        Map<String, String> expected = Map.of(
                FrontierV3CrashBoundaryProbe.TYPED_OBSERVATION_DURABLE_BEFORE_NEXT_PROCESS_CHECKPOINT, "frontier.resource_site_harvest_progressed",
                FrontierV3CrashBoundaryProbe.HOT_CHECKPOINT_DURABLE_BEFORE_DRAIN_RELEASE, "frontier.resource_site_harvest_hot_traversal_advanced");
        expected.forEach((boundary, payload) -> {
            FrontierV3CrashBoundaryProbe probe = FrontierV3CrashBoundaryProbe.from(properties(7L, "site:crash-owner", boundary, payload)::get, ignored -> { });
            assertTrue(probe.matches(transaction(7L, "site:crash-owner", payload)), boundary);
            assertFalse(probe.matches(transaction(7L, "site:crash-owner", "frontier.other")), boundary);
        });
    }

    @Test
    void physicalWindowCannotBeMistakenForAGenericWalAppend() {
        FrontierV3CrashBoundaryProbe probe = FrontierV3CrashBoundaryProbe.from(properties(7L, "site:crash-owner",
                FrontierV3CrashBoundaryProbe.PHYSICAL_EFFECT_VISIBLE_BEFORE_TYPED_OBSERVATION,
                "frontier.resource_site_harvest_progressed")::get, ignored -> { });
        assertFalse(probe.matches(transaction(7L, "site:crash-owner", "frontier.resource_site_harvest_progressed")));
    }

    @Test
    void armRejectsUnknownWindowOrMismatchedSemanticPayload() {
        Map<String, String> values = new java.util.HashMap<>(properties(7L, "site:crash-owner",
                FrontierV3CrashBoundaryProbe.HOT_CHECKPOINT_DURABLE_BEFORE_DRAIN_RELEASE,
                "frontier.resource_site_harvest_hot_traversal_advanced"));
        values.put("pale_mirror.frontier_v3.pilot.crash.boundary", "hot_checkpoint_durable_before_release");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3CrashBoundaryProbe.from(values::get, ignored -> { }));
        values.put("pale_mirror.frontier_v3.pilot.crash.boundary", FrontierV3CrashBoundaryProbe.HOT_CHECKPOINT_DURABLE_BEFORE_DRAIN_RELEASE);
        values.put("pale_mirror.frontier_v3.pilot.crash.payload", "frontier.other");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3CrashBoundaryProbe.from(values::get, ignored -> { }));
    }

    @Test
    void typedLeasePreparationMatchesTheArmedJobBeforeAnyPhysicalMaterialization() {
        String job = "job:site-harvest-crash";
        FrontierV3CrashBoundaryProbe probe = FrontierV3CrashBoundaryProbe.from(properties(7L, job,
                FrontierV3CrashBoundaryProbe.LEASE_RECORDED_BEFORE_PHYSICAL_MATERIALIZATION,
                "frontier.resource_site_harvest_scene_lease_prepared")::get, ignored -> { });

        assertTrue(probe.matches(prepare(7L, "settlement:crash", job, "lease:crash")),
                "the durable PREPARED lease is the exact pre-materialization boundary");
        assertFalse(probe.matches(handoff(7L, "settlement:crash", job, "lease:crash")),
                "a later ambient handoff must not stand in for the already durable lease record");
    }

    @Test
    void typedReleaseMatchesOnlyThePreviouslyPreparedArmedLease() {
        String job = "job:site-harvest-crash";
        FrontierV3CrashBoundaryProbe probe = FrontierV3CrashBoundaryProbe.from(properties(7L, job,
                FrontierV3CrashBoundaryProbe.RELEASE_DURABLE_BEFORE_COLD_RESUMPTION,
                "frontier.scene_lease_released_v2")::get, ignored -> { });

        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:crash")));
        assertTrue(probe.matches(release(7L, "settlement:crash", job, "lease:crash")));
    }

    @Test
    void typedHandoffMatchesTheArmedLeaseReleaseWithItsExactColdContinuation() {
        String job = "job:site-harvest-crash";
        FrontierV3CrashBoundaryProbe probe = releaseProbe(7L, job);

        assertFalse(probe.matches(handoff(6L, "settlement:crash", job, "lease:crash")));
        assertFalse(probe.matches(handoff(6L, "settlement:other", "job:site-harvest-other", "lease:other")),
                "unrelated handoff traffic must not erase the armed witness");
        assertTrue(probe.matches(release(7L, "settlement:crash", job, "lease:crash")));
    }

    @Test
    void handoffWitnessRejectsForeignIdentityAndRemainsAmbiguousAcrossMixedAdmissions() {
        String job = "job:site-harvest-crash";
        FrontierV3CrashBoundaryProbe probe = releaseProbe(7L, job);
        assertFalse(probe.matches(handoff(6L, "settlement:crash", "job:site-harvest-other", "lease:crash")));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:crash")), "foreign job cannot arm release");

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(handoff(6L, "settlement:crash", job, "lease:foreign")));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:crash")), "foreign lease cannot arm release");

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(withWorld(handoff(6L, "settlement:crash", job, "lease:crash"), "frontier:other")));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:crash")), "foreign world cannot arm release");

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(handoff(6L, "settlement:other", job, "lease:crash")));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:crash")), "foreign event owner cannot arm release");

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(transaction(6L, List.of(
                prepare(6L, "settlement:crash", job, "lease:one").events().getFirst(),
                handoff(6L, "settlement:crash", job, "lease:two").events().getFirst(),
                prepare(6L, "settlement:crash", job, "lease:three").events().getFirst()))));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:three")),
                "mixed in-transaction admissions remain sticky ambiguous");

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:one")));
        assertFalse(probe.matches(handoff(6L, "settlement:crash", job, "lease:two")));
        assertFalse(probe.matches(handoff(6L, "settlement:crash", job, "lease:three")));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:three")),
                "mixed callback admissions remain sticky ambiguous after later traffic");
    }

    @Test
    void handoffFirstMixedDuplicateAdmissionsRejectTheirExactRetainedLeaseRelease() {
        String job = "job:site-harvest-crash";
        FrontierV3CrashBoundaryProbe probe = releaseProbe(7L, job);
        assertFalse(probe.matches(transaction(6L, List.of(
                handoff(6L, "settlement:crash", job, "lease:crash").events().getFirst(),
                prepare(6L, "settlement:crash", job, "lease:crash").events().getFirst()))));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:crash")),
                "Handoff-first in-transaction duplicate evidence is ambiguous for the exact retained lease");

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(handoff(6L, "settlement:crash", job, "lease:crash")));
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:crash")));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:crash")),
                "Handoff-first cross-callback duplicate evidence is ambiguous for the exact retained lease");
    }

    @Test
    void releaseWitnessRejectsForeignLeaseJobContinuationAndMalformedCandidatesWithoutErasingOnOtherJobs() {
        String job = "job:site-harvest-crash";
        FrontierV3CrashBoundaryProbe probe = releaseProbe(7L, job);
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:crash")));
        assertFalse(probe.matches(release(7L, "settlement:other", "job:site-harvest-other", "lease:other")), "unrelated job must not erase the witness");
        assertTrue(probe.matches(release(7L, "settlement:crash", job, "lease:crash")));

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:crash")));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:foreign")), "foreign lease must fail");
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:crash")), "foreign candidate clears the obsolete witness");

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:crash")));
        assertTrue(probe.matches(release(7L, "settlement:crash", "job:site-harvest-other", "lease:crash")),
                "a no-work release carries no second schedule subject; the previously durable exact lease remains the owner witness");

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:crash")));
        TransactionRecord malformed = withEvents(release(7L, "settlement:crash", job, "lease:crash"), List.of(
                release(7L, "settlement:crash", job, "lease:crash").events().get(0),
                event(7L, 1, "settlement:crash", new TestPayload("frontier.unrelated"))));
        assertFalse(probe.matches(malformed), "extra facts cannot complete a release witness");
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:crash")), "malformed candidate clears the witness");

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:crash")));
        assertFalse(probe.matches(withUnexpectedSchedule(release(7L, "settlement:crash", job, "lease:crash"))),
                "a release that constructs any schedule effect must fail");
    }

    @Test
    void releaseWitnessRejectsMissingDuplicateWrongPayloadAndWrongRevisionThenAllowsLaterFixedRevisionLease() {
        String job = "job:site-harvest-crash";
        FrontierV3CrashBoundaryProbe probe = releaseProbe(7L, job);
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:crash")), "release without typed prepare witness must fail");
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:one")));
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:one")), "duplicate witness is ambiguous");
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:one")));

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:one")));
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:two")), "a second witness is ambiguous");
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:two")));

        probe = releaseProbe(8L, job);
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:one")));
        assertFalse(probe.matches(transaction(7L, "settlement:crash", "frontier.other")), "wrong payload must not match");
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:one")), "wrong revision clears that witness");
        assertFalse(probe.matches(prepare(7L, "settlement:crash", job, "lease:two")));
        assertTrue(probe.matches(release(8L, "settlement:crash", job, "lease:two")), "a later exact lease may satisfy a fixed revision arm");
    }

    @Test
    void repeatedPreparedWitnessesRemainAmbiguousWithinAndAcrossDurableCallbacks() {
        String job = "job:site-harvest-crash";
        FrontierV3CrashBoundaryProbe probe = releaseProbe(7L, job);
        assertFalse(probe.matches(transaction(6L, List.of(
                prepare(6L, "settlement:crash", job, "lease:one").events().getFirst(),
                prepare(6L, "settlement:crash", job, "lease:two").events().getFirst(),
                prepare(6L, "settlement:crash", job, "lease:three").events().getFirst()))));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:three")),
                "a third in-transaction prepare must not restore a usable witness");

        probe = releaseProbe(7L, job);
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:one")));
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:two")));
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:three")));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:three")),
                "a third callback prepare must not restore a usable witness");
    }

    @Test
    void ambiguousWitnessReleaseAllowsOnlyALaterLegitimateLeaseTurnover() {
        String job = "job:site-harvest-crash";
        FrontierV3CrashBoundaryProbe probe = releaseProbe(8L, job);
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:one")));
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:two")));
        assertFalse(probe.matches(prepare(6L, "settlement:crash", job, "lease:three")));
        assertFalse(probe.matches(release(7L, "settlement:crash", job, "lease:three")),
                "the ambiguous release must not satisfy a fixed-revision arm");
        assertFalse(probe.matches(prepare(7L, "settlement:crash", job, "lease:four")));
        assertTrue(probe.matches(release(8L, "settlement:crash", job, "lease:four")),
                "a later observed lease remains a legitimate turnover");
    }

    @Test
    void crashMixinPreflightRequiresTheExactSelectedAppliedMixin() {
        assertDoesNotThrow(() -> FrontierV3CrashBoundaryProbe.requireAppliedMixin(
                FrontierV3CrashBoundaryProbe.RELEASE_DURABLE_BEFORE_COLD_RESUMPTION,
                java.util.Set.of("io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3DurableCrashWindowMixin")));
        assertDoesNotThrow(() -> FrontierV3CrashBoundaryProbe.requireAppliedMixin(
                FrontierV3CrashBoundaryProbe.PHYSICAL_EFFECT_VISIBLE_BEFORE_TYPED_OBSERVATION,
                java.util.Set.of("io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3HarvestCrashWindowMixin")));
        assertThrows(IllegalStateException.class, () -> FrontierV3CrashBoundaryProbe.requireAppliedMixin(
                FrontierV3CrashBoundaryProbe.RELEASE_DURABLE_BEFORE_COLD_RESUMPTION, java.util.Set.of()));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3CrashBoundaryProbe.requireAppliedMixin("not_a_boundary", java.util.Set.of()));
    }

    @Test
    void configuredCrashMixinPreflightReadsTheExactAppliedMixinCacheKeyForEveryWindow() {
        Map<String, String> expected = Map.of(
                FrontierV3CrashBoundaryProbe.LEASE_RECORDED_BEFORE_PHYSICAL_MATERIALIZATION,
                "io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3DurableCrashWindowMixin",
                FrontierV3CrashBoundaryProbe.TYPED_OBSERVATION_DURABLE_BEFORE_NEXT_PROCESS_CHECKPOINT,
                "io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3DurableCrashWindowMixin",
                FrontierV3CrashBoundaryProbe.HOT_CHECKPOINT_DURABLE_BEFORE_DRAIN_RELEASE,
                "io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3DurableCrashWindowMixin",
                FrontierV3CrashBoundaryProbe.RELEASE_DURABLE_BEFORE_COLD_RESUMPTION,
                "io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3DurableCrashWindowMixin",
                FrontierV3CrashBoundaryProbe.PHYSICAL_EFFECT_VISIBLE_BEFORE_TYPED_OBSERVATION,
                "io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3HarvestCrashWindowMixin");
        expected.forEach((boundary, mixinClass) -> assertDoesNotThrow(() -> FrontierV3CrashBoundaryProbe
                .requireConfiguredMixinApplication(property(boundary), cacheKey -> {
                    assertEquals(mixinClass, cacheKey, boundary + " must use its successful-applied mixin cache key");
                    return Set.of(mixinClass);
                })));

        String release = FrontierV3CrashBoundaryProbe.RELEASE_DURABLE_BEFORE_COLD_RESUMPTION;
        assertThrows(IllegalStateException.class, () -> FrontierV3CrashBoundaryProbe
                .requireConfiguredMixinApplication(property(release), ignored -> Set.of()));
        assertThrows(IllegalStateException.class, () -> FrontierV3CrashBoundaryProbe
                .requireConfiguredMixinApplication(property(release), ignored -> Set.of(
                        "io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3HarvestCrashWindowMixin")));
        AtomicBoolean queried = new AtomicBoolean();
        assertDoesNotThrow(() -> FrontierV3CrashBoundaryProbe.requireConfiguredMixinApplication(ignored -> "", cacheKey -> {
            queried.set(true);
            return Set.of();
        }));
        assertFalse(queried.get(), "ordinary unarmed startup must not inspect a pilot mixin");
    }

    @Test
    void pilotCrashMixinsDeclareExactlyOneExpectedTargetEach() throws IOException {
        assertEquals(List.of("net.minecraft.server.MinecraftServer"),
                mixinTargets("io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3DurableServerSaveMixin"));
        assertEquals(List.of("io.farfrontier.palemirror.internal.frontier.v3.FrontierStoreTransactionCommitter"),
                mixinTargets("io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3DurableCrashWindowMixin"));
        assertEquals(List.of("io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ResourceSiteHarvestSceneExecutor"),
                mixinTargets("io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3HarvestCrashWindowMixin"));
    }

    private static Map<String, String> properties(long revision, String owner, String boundary, String payload) {
        return Map.of(
                "pale_mirror.frontier_v3.pilot.run_id", "00000000-0000-0000-0000-000000000007",
                "pale_mirror.frontier_v3.pilot.crash.boundary", boundary,
                "pale_mirror.frontier_v3.pilot.crash.owner", owner,
                "pale_mirror.frontier_v3.pilot.crash.revision", Long.toString(revision),
                "pale_mirror.frontier_v3.pilot.crash.payload", payload);
    }

    private static java.util.function.Function<String, String> property(String boundary) {
        return name -> "pale_mirror.frontier_v3.pilot.crash.boundary".equals(name) ? boundary : "";
    }

    private static List<String> mixinTargets(String mixinClass) throws IOException {
        String path = mixinClass.replace('.', '/') + ".class";
        try (InputStream stream = FrontierV3CrashBoundaryProbeTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return mixinTargets(stream.readAllBytes(), mixinClass);
        }
    }

    private static List<String> mixinTargets(byte[] classBytes, String mixinClass) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            assertEquals(0xCAFEBABE, input.readInt(), mixinClass + " class file magic");
            input.readUnsignedShort(); input.readUnsignedShort();
            Object[] constants = constantPool(input);
            skipFully(input, 6);
            skipFully(input, input.readUnsignedShort() * 2L);
            skipMembers(input); skipMembers(input);
            List<String> targets = new ArrayList<>();
            AtomicBoolean directClassTargets = new AtomicBoolean();
            int attributes = input.readUnsignedShort();
            for (int index = 0; index < attributes; index++) {
                String name = utf8(constants, input.readUnsignedShort());
                byte[] bytes = input.readNBytes(Math.toIntExact(Integer.toUnsignedLong(input.readInt())));
                if ("RuntimeInvisibleAnnotations".equals(name)) parseMixinAnnotation(bytes, constants, targets, directClassTargets);
            }
            assertFalse(directClassTargets.get(), mixinClass + " must retain exactly one string target");
            return List.copyOf(targets);
        }
    }

    private static Object[] constantPool(DataInputStream input) throws IOException {
        Object[] constants = new Object[input.readUnsignedShort()];
        for (int index = 1; index < constants.length; index++) {
            switch (input.readUnsignedByte()) {
                case 1 -> constants[index] = input.readUTF();
                case 3, 4 -> skipFully(input, 4);
                case 5, 6 -> { skipFully(input, 8); index++; }
                case 7, 8, 16, 19, 20 -> skipFully(input, 2);
                case 9, 10, 11, 12, 17, 18 -> skipFully(input, 4);
                case 15 -> skipFully(input, 3);
                default -> throw new IOException("unknown class-file constant tag");
            }
        }
        return constants;
    }

    private static void skipMembers(DataInputStream input) throws IOException {
        int members = input.readUnsignedShort();
        for (int index = 0; index < members; index++) {
            skipFully(input, 6);
            int attributes = input.readUnsignedShort();
            for (int attribute = 0; attribute < attributes; attribute++) {
                skipFully(input, 2);
                skipFully(input, Integer.toUnsignedLong(input.readInt()));
            }
        }
    }

    private static void parseMixinAnnotation(byte[] bytes, Object[] constants, List<String> targets,
                                             AtomicBoolean directClassTargets) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int annotations = input.readUnsignedShort();
            for (int index = 0; index < annotations; index++) {
                boolean mixin = "Lorg/spongepowered/asm/mixin/Mixin;".equals(utf8(constants, input.readUnsignedShort()));
                int elements = input.readUnsignedShort();
                for (int element = 0; element < elements; element++) {
                    String name = utf8(constants, input.readUnsignedShort());
                    int tag = input.readUnsignedByte();
                    if (mixin && "targets".equals(name) && tag == '[') {
                        int values = input.readUnsignedShort();
                        for (int value = 0; value < values; value++) {
                            if (input.readUnsignedByte() != 's') throw new IOException("mixin target is not a string");
                            targets.add(utf8(constants, input.readUnsignedShort()));
                        }
                    } else {
                        if (mixin && "value".equals(name)) directClassTargets.set(true);
                        skipElementValue(input, tag);
                    }
                }
            }
        }
    }

    private static void skipElementValue(DataInputStream input, int tag) throws IOException {
        switch (tag) {
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 's', 'c' -> skipFully(input, 2);
            case 'e' -> skipFully(input, 4);
            case '@' -> { skipFully(input, 2); for (int index = input.readUnsignedShort(); index > 0; index--) { skipFully(input, 2); skipElementValue(input, input.readUnsignedByte()); } }
            case '[' -> { for (int index = input.readUnsignedShort(); index > 0; index--) skipElementValue(input, input.readUnsignedByte()); }
            default -> throw new IOException("unknown annotation element tag");
        }
    }

    private static String utf8(Object[] constants, int index) throws IOException {
        if (index <= 0 || index >= constants.length || !(constants[index] instanceof String value)) throw new IOException("class-file UTF8 entry is missing");
        return value;
    }

    private static void skipFully(InputStream input, long count) throws IOException {
        while (count > 0) {
            long skipped = input.skip(count);
            if (skipped == 0) throw new IOException("truncated class file");
            count -= skipped;
        }
    }

    private static TransactionRecord transaction(long revision, String owner, String payload) {
        TransactionId id = new TransactionId("transaction:crash-probe-" + revision + '-' + owner.substring(owner.indexOf(':') + 1));
        FrontierEvent event = new FrontierEvent(1, new EventId("event:crash-probe-" + revision), id, new WorldId("frontier:crash-probe"),
                new Revision(revision), new SimInstant(revision), new SubjectId(owner), CauseChain.root(new CommandId("command:crash-probe")),
                new TestPayload(payload));
        return new TransactionRecord(id, new WorldId("frontier:crash-probe"), new Revision(revision), new SimInstant(revision), java.util.List.of(event));
    }

    private static TransactionRecord prepare(long revision, String settlement, String job, String lease) {
        SceneLease sceneLease = lease(revision, job, lease);
        return transaction(revision, List.of(event(revision, 0, settlement, new ResourceSiteHarvestSceneLeasePrepared(sceneLease))));
    }

    private static TransactionRecord handoff(long revision, String settlement, String job, String lease) {
        SceneLease sceneLease = lease(revision, job, lease);
        return transaction(revision, List.of(event(revision, 0, settlement,
                new ResourceSiteHarvestSceneLeaseHandoff(sceneLease, List.of(new SceneMemberPosition(worker(), body(), FixedScalar.whole(20)))))));
    }

    private static TransactionRecord release(long revision, String settlement, String job, String lease) {
        SceneLease sceneLease = lease(revision - 1L, job, lease);
        SceneLeaseReleased released = new SceneLeaseReleased(sceneLease.id(), List.of(new SceneMemberPosition(worker(), body(), FixedScalar.whole(20))));
        return transaction(revision, List.of(event(revision, 0, settlement, released)));
    }

    private static FrontierV3CrashBoundaryProbe releaseProbe(long revision, String job) {
        return FrontierV3CrashBoundaryProbe.from(properties(revision, job,
                FrontierV3CrashBoundaryProbe.RELEASE_DURABLE_BEFORE_COLD_RESUMPTION,
                "frontier.scene_lease_released_v2")::get, ignored -> { });
    }

    private static SceneLease lease(long revision, String job, String lease) {
        WorldId world = new WorldId("frontier:crash-probe");
        SubjectId worker = worker();
        return SceneLease.forCause(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId(lease), world,
                new ResourceSiteHarvestSceneCause(new SubjectId(job)), new BlockPosition(1, 64, 1), new SimInstant(revision), revision,
                SceneLeaseStatus.PREPARED, List.of(new SceneMember(worker, SceneLease.deterministicEntityId(world, worker))),
                Map.of(worker, body()), java.util.Set.of(), java.util.Optional.empty());
    }

    private static SubjectId worker() { return new SubjectId("resident:crash-worker"); }
    private static BodyPosition body() { return new BodyPosition(1, 65, 1); }

    private static TransactionRecord transaction(long revision, List<FrontierEvent> events) {
        TransactionId id = new TransactionId("transaction:crash-probe-typed-" + revision);
        return new TransactionRecord(id, new WorldId("frontier:crash-probe"), new Revision(revision), new SimInstant(revision), events);
    }

    private static TransactionRecord withEvents(TransactionRecord original, List<FrontierEvent> events) {
        return new TransactionRecord(original.id(), original.worldId(), original.revision(), original.instant(), events);
    }

    private static TransactionRecord withWorld(TransactionRecord original, String world) {
        return new TransactionRecord(original.id(), new WorldId(world), original.revision(), original.instant(), original.events());
    }

    private static TransactionRecord withUnexpectedSchedule(TransactionRecord original) {
        FrontierEvent release = original.events().getFirst();
        return withEvents(original, List.of(release, event(original.revision().value(), 1, "job:site-harvest-crash",
                new TestPayload("kernel.schedule_rescheduled"))));
    }

    private static FrontierEvent event(long revision, int ordinal, String owner, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        TransactionId id = new TransactionId("transaction:crash-probe-typed-" + revision);
        return new FrontierEvent(1, new EventId("event:crash-probe-typed-" + revision + '-' + ordinal), id, new WorldId("frontier:crash-probe"),
                new Revision(revision), new SimInstant(revision), new SubjectId(owner), CauseChain.root(new CommandId("command:crash-probe-typed")), payload);
    }

    private record TestPayload(String type) implements io.farfrontier.palemirror.frontier.v3.api.FrontierPayload { }
}
