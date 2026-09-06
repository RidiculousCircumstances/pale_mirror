package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PilotExpectedCrashTest {
    private static final String CONTROL = "pale_mirror.frontier_v3.test_pilot.session_control_directory";
    private static final String MODE = "pale_mirror.frontier_v3.test_pilot.session_mode";
    private static final String LIFECYCLE = "pale_mirror.frontier_v3.test_pilot.lifecycle_control_directory";

    @Test
    void completeImmutableArmClaimsOneObservedLossWithoutNormalDisconnect(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.installProperties();
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            FrontierV3PilotSessionControl.bindActiveConnection(new Object());
            assertTrue(FrontierV3PilotSessionControl.expectedCrashSegment());
            assertTrue(FrontierV3PilotSessionControl.armExpectedLoss());
            assertFalse(FrontierV3PilotSessionControl.mayContinueScenarioActions());
            assertTrue(FrontierV3PilotSessionControl.claimExpectedLoss());
            assertFalse(FrontierV3PilotSessionControl.mayContinueScenarioActions());
            assertFalse(FrontierV3PilotSessionControl.claimExpectedLoss());
            assertTrue(FrontierV3PilotSessionControl.awaitingResume());
            assertTrue(Files.isRegularFile(fixture.lifecycle.resolve("signals/client_expected_loss-crash_before_0.json")));
            assertFalse(Files.exists(fixture.lifecycle.resolve("signals/client_normally_disconnected-crash_before_0.json")));
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void immutableArmPrefixStopsActionsAndExactlyMatchesItsOneAuthenticatedLoss(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.installProperties();
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            Object connection = new Object();
            FrontierV3PilotSessionControl.bindActiveConnection(connection);
            assertThrows(IllegalArgumentException.class, () -> FrontierV3PilotSessionControl.armExpectedLoss(-1));
            assertTrue(FrontierV3PilotSessionControl.armExpectedLoss(1));
            assertFalse(FrontierV3PilotSessionControl.mayContinueScenarioActions());
            JsonObject armed = read(fixture.lifecycle.resolve("signals/expected_loss_armed-crash_before_0.json"));
            assertEquals(1, armed.getAsJsonObject("detail").get("completedActionSteps").getAsInt());
            assertThrows(IllegalStateException.class, () -> FrontierV3PilotSessionControl.claimExpectedLoss(new Object()));
            assertTrue(FrontierV3PilotSessionControl.claimExpectedLoss(connection));
            JsonObject loss = read(fixture.lifecycle.resolve("signals/client_expected_loss-crash_before_0.json"));
            assertEquals(1, loss.getAsJsonObject("detail").get("completedActionSteps").getAsInt());
            assertFalse(FrontierV3PilotSessionControl.claimExpectedLoss(connection));
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void armAdmissionRefusesAbsentOrdinaryFinalRecoveredAndUnknownModes(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.installProperties();
            Files.delete(fixture.control.resolve("expected-crash").resolve(file(0)));
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            assertFalse(FrontierV3PilotSessionControl.armExpectedLoss());

            fixture.writeCrashSegment(0, 17, 3);
            JsonObject descriptor = fixture.descriptor(0);
            descriptor.addProperty("completion", "terminal"); descriptor.remove("expectedCrash");
            write(fixture.control.resolve("segments").resolve(file(0)), descriptor);
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            assertFalse(FrontierV3PilotSessionControl.expectedCrashSegment());
            assertFalse(FrontierV3PilotSessionControl.armExpectedLoss());

            fixture.writeCrashSegment(0, 17, 3);
            descriptor = fixture.descriptor(0); descriptor.addProperty("final", true);
            write(fixture.control.resolve("segments").resolve(file(0)), descriptor);
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            assertTrue(FrontierV3PilotSessionControl.expectedCrashSegment());
            assertFalse(FrontierV3PilotSessionControl.armExpectedLoss());

            fixture.writeRecoveredSegment(0);
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            assertFalse(FrontierV3PilotSessionControl.expectedCrashSegment());
            assertFalse(FrontierV3PilotSessionControl.armExpectedLoss());

            fixture.writeCrashSegment(0, 17, 3);
            descriptor = fixture.descriptor(0); descriptor.addProperty("completion", "unknown_completion");
            write(fixture.control.resolve("segments").resolve(file(0)), descriptor);
            FrontierV3PilotSessionControl.reset();
            assertThrows(IllegalArgumentException.class, () -> FrontierV3PilotSessionControl.onLogin(fixture.scenario));

            fixture.writeCrashSegment(0, 17, 3);
            descriptor = fixture.descriptor(0); descriptor.addProperty("schema", 2);
            write(fixture.control.resolve("segments").resolve(file(0)), descriptor);
            FrontierV3PilotSessionControl.reset();
            assertThrows(IllegalArgumentException.class, () -> FrontierV3PilotSessionControl.onLogin(fixture.scenario));
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void expectedLossPublicationFailureClaimsOnceAndStopsFurtherActions(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.installProperties();
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            Object connection = new Object();
            FrontierV3PilotSessionControl.bindActiveConnection(connection);
            assertTrue(FrontierV3PilotSessionControl.armExpectedLoss());
            Files.delete(fixture.lifecycle.resolve("signals/expected_loss_armed-crash_before_0.json"));
            Files.delete(fixture.lifecycle.resolve("signals"));
            assertThrows(java.io.IOException.class, () -> FrontierV3PilotSessionControl.claimExpectedLoss(connection));
            assertFalse(FrontierV3PilotSessionControl.claimExpectedLoss(connection));
            assertTrue(FrontierV3PilotSessionControl.expectedLossClaimed());
            assertTrue(FrontierV3PilotSessionControl.claimPersistentLifecycleFailure());
            assertFalse(FrontierV3PilotSessionControl.claimPersistentLifecycleFailure());
            assertTrue(FrontierV3PilotSessionControl.persistentLifecycleFailure());
            assertFalse(FrontierV3PilotSessionControl.mayContinueScenarioActions());
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void lifecycleFailureClassificationKeepsOrdinaryAndReconnectStatesDistinct(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addVerifiedRecovery(0);
            fixture.installProperties();
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            assertFalse(FrontierV3PilotSessionControl.unexpectedActiveLossRequiresFatal());
            Object connection = new Object();
            FrontierV3PilotSessionControl.bindActiveConnection(connection);
            assertTrue(FrontierV3PilotSessionControl.unexpectedActiveLossRequiresFatal());
            assertTrue(FrontierV3PilotSessionControl.armExpectedLoss());
            assertTrue(FrontierV3PilotSessionControl.claimExpectedLoss(connection));
            assertFalse(FrontierV3PilotSessionControl.unexpectedActiveLossRequiresFatal());
            assertTrue(FrontierV3PilotSessionControl.reconnectRequestFailureRequiresFatal());
            assertFalse(FrontierV3PilotSessionControl.reconnectFailureRequiresFatal());
            Files.writeString(fixture.control.resolve("resume/0001.token"), "foreign\n", StandardCharsets.UTF_8);
            assertThrows(IllegalArgumentException.class, () -> FrontierV3PilotSessionControl.requestReconnect("127.0.0.1"));
            assertTrue(FrontierV3PilotSessionControl.reconnectRequestFailureRequiresFatal());
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(connection));
            assertThrows(IllegalStateException.class, FrontierV3PilotSessionControl::prepareReconnectTransportDeparture);
            Files.writeString(fixture.control.resolve("resume/0001.token"), Files.readString(fixture.control.resolve("run-id")).trim() + ":1\n", StandardCharsets.UTF_8);
            FrontierV3PilotSessionControl.requestReconnect("127.0.0.1");
            assertTrue(FrontierV3PilotSessionControl.reconnectFailureRequiresFatal());
            assertTrue(FrontierV3PilotSessionControl.reconnectRequestFailureRequiresFatal());
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            FrontierV3PilotSessionControl.completeReconnect();
            assertFalse(FrontierV3PilotSessionControl.reconnectFailureRequiresFatal());
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void exactPredecessorDepartureIsNotAReplacementLoss(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addVerifiedRecovery(0);
            fixture.installProperties();
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            Object predecessor = new Object();
            FrontierV3PilotSessionControl.bindActiveConnection(predecessor);
            assertTrue(FrontierV3PilotSessionControl.armExpectedLoss());
            assertTrue(FrontierV3PilotSessionControl.claimExpectedLoss(predecessor));
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(predecessor));
            FrontierV3PilotSessionControl.requestReconnect("127.0.0.1");
            assertTrue(FrontierV3PilotSessionControl.reconnectInFlight());
            assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(predecessor));
            assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(predecessor));
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(new Object()));
            // ConnectScreen.startConnecting invokes Minecraft.disconnect after the crash has
            // already removed the LocalPlayer, so NeoForge delivers the predecessor cleanup
            // LoggingOut callback with no Connection.  That null has no authority before the
            // transport begins the already authenticated reconnect cleanup.
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
            FrontierV3PilotSessionControl.executeReconnectTransportDeparture(() -> {
                assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
                assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
            });
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
            assertFalse(Files.exists(fixture.lifecycle.resolve("signals/client_normally_disconnected-crash_before_0.json")));
            try (var signals = Files.list(fixture.lifecycle.resolve("signals"))) {
                assertEquals(2L, signals.count());
            }
            assertTrue(FrontierV3PilotSessionControl.reconnectFailureRequiresFatal());

            Object replacement = new Object();
            FrontierV3PilotSessionControl.bindActiveConnection(replacement);
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(predecessor));
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
            assertTrue(FrontierV3PilotSessionControl.reconnectFailureRequiresFatal());
            FrontierV3PilotSessionControl.completeReconnect();
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(predecessor));
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
            assertTrue(FrontierV3PilotSessionControl.unexpectedActiveLossRequiresFatal());
            FrontierV3PilotSessionControl.reset();
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(predecessor));
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void authenticatedGracefulHandoffAllowsOnlySynchronousNullCleanup(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addGracefulRecovery(0);
            fixture.installProperties();
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            Object predecessor = new Object();
            FrontierV3PilotSessionControl.bindActiveConnection(predecessor);
            FrontierV3PilotSessionControl.markAwaitingResume();
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));

            FrontierV3PilotSessionControl.requestReconnect("127.0.0.1");
            assertTrue(FrontierV3PilotSessionControl.reconnectInFlight());
            assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(predecessor));
            FrontierV3PilotSessionControl.executeReconnectTransportDeparture(() ->
                    assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null)));
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
            assertTrue(FrontierV3PilotSessionControl.reconnectFailureRequiresFatal());
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void gracefulCleanupAuthorizationExpiresAfterExceptionalTransportExit(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addGracefulRecovery(0);
            fixture.installProperties();
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            FrontierV3PilotSessionControl.bindActiveConnection(new Object());
            FrontierV3PilotSessionControl.markAwaitingResume();
            FrontierV3PilotSessionControl.requestReconnect("127.0.0.1");
            assertThrows(IllegalStateException.class, () -> FrontierV3PilotSessionControl.executeReconnectTransportDeparture(() -> {
                assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
                throw new IllegalStateException("connector failed");
            }));
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
            assertTrue(FrontierV3PilotSessionControl.reconnectFailureRequiresFatal());
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void repeatedCrossWorldGracefulHandoffsRemainBoundedToEachLivePredecessor(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addGracefulRecovery(0, "world_a", "world_b");
            fixture.addGracefulRecovery(1, "world_b", "world_c");
            fixture.installProperties();
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);

            Object first = new Object();
            completeGracefulHandoff(first, fixture.scenario);
            Object second = new Object();
            completeGracefulHandoff(second, fixture.scenario);

            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(first));
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
            assertFalse(FrontierV3PilotSessionControl.reconnectInFlight());
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void reconnectCleanupAuthorizationExpiresAfterExceptionalTransportExit(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addVerifiedRecovery(0);
            fixture.installProperties();
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            Object predecessor = new Object();
            FrontierV3PilotSessionControl.bindActiveConnection(predecessor);
            assertTrue(FrontierV3PilotSessionControl.armExpectedLoss());
            assertTrue(FrontierV3PilotSessionControl.claimExpectedLoss(predecessor));
            FrontierV3PilotSessionControl.requestReconnect("127.0.0.1");
            assertThrows(IllegalStateException.class, () -> FrontierV3PilotSessionControl.executeReconnectTransportDeparture(() -> {
                assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
                throw new IllegalStateException("connector failed");
            }));
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(new Object()));
            assertTrue(FrontierV3PilotSessionControl.reconnectFailureRequiresFatal());
            assertThrows(IllegalStateException.class,
                    () -> FrontierV3PilotSessionControl.executeReconnectTransportDeparture(() -> { }));
            FrontierV3PilotSessionControl.completeReconnect();
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
            FrontierV3PilotSessionControl.reset();
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void liveReconnectCleanupAuthorizationRetiresOnReplacementBind(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addVerifiedRecovery(0);
            fixture.installProperties();
            authorizePendingNullCleanup(fixture);
            FrontierV3PilotSessionControl.bindActiveConnection(new Object());
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void liveReconnectCleanupAuthorizationRetiresOnCompleteWithoutBind(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addVerifiedRecovery(0);
            fixture.installProperties();
            authorizePendingNullCleanup(fixture);
            FrontierV3PilotSessionControl.completeReconnect();
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void liveReconnectCleanupAuthorizationRetiresOnReset(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addVerifiedRecovery(0);
            fixture.installProperties();
            authorizePendingNullCleanup(fixture);
            FrontierV3PilotSessionControl.reset();
            assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void rehashedArmMutationsCannotAuthorizeExpectedLoss(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.installProperties();
            assertRehashedArmRejected(fixture, "identity");
            assertRehashedArmRejected(fixture, "clientPid");
            assertRehashedArmRejected(fixture, "revision");
            assertRehashedArmRejected(fixture, "authority");
            assertRehashedArmRejected(fixture, "boundary");
            assertRehashedArmRejected(fixture, "overflow");
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void admitsEveryControllerBoundaryAndRejectsAnUnknownBoundary(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.installProperties();
            for (String boundary : new String[] {
                    "lease_recorded_before_physical_materialization",
                    "physical_effect_visible_before_typed_observation",
                    "typed_observation_durable_before_next_process_checkpoint",
                    "hot_checkpoint_durable_before_drain_release",
                    "release_durable_before_cold_resumption" }) {
                fixture.writeBoundary(boundary);
                FrontierV3PilotExpectedCrash.validateArm(fixture.control, fixture.lifecycle, fixture.descriptor(0), 0);
            }
            fixture.writeBoundary("not_a_crash_boundary");
            assertThrows(IllegalArgumentException.class,
                    () -> FrontierV3PilotExpectedCrash.validateArm(fixture.control, fixture.lifecycle, fixture.descriptor(0), 0));
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void fixedRevisionAndRetainedAuthorityCannotBeCoupledAway(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.installProperties();
            fixture.writeFixedRevision(17);
            FrontierV3PilotExpectedCrash.validateArm(fixture.control, fixture.lifecycle, fixture.descriptor(0), 0);
            JsonObject revision = fixture.arm(0);
            revision.addProperty("resolvedRevision", 18);
            revision.getAsJsonObject("descriptor").addProperty("resolvedRevision", 18);
            fixture.rehash(revision);
            fixture.writeArm(0, revision);
            assertThrows(IllegalArgumentException.class,
                    () -> FrontierV3PilotExpectedCrash.validateArm(fixture.control, fixture.lifecycle, fixture.descriptor(0), 0));

            fixture.writeCrashSegment(0, 17, 3);
            fixture.writeArm(0, fixture.nodeEquivalentArm(0));
            JsonObject changedAuthority = fixture.arm(0);
            changedAuthority.addProperty("authorityEpoch", 4);
            changedAuthority.getAsJsonObject("descriptor").addProperty("expectedAuthorityEpoch", 4);
            fixture.rehash(changedAuthority);
            fixture.writeArm(0, changedAuthority);
            assertThrows(IllegalArgumentException.class,
                    () -> FrontierV3PilotExpectedCrash.validateArm(fixture.control, fixture.lifecycle, fixture.descriptor(0), 0));

            JsonObject missingAuthority = fixture.nodeEquivalentArm(0);
            missingAuthority.remove("authorityEpoch");
            missingAuthority.getAsJsonObject("descriptor").remove("expectedAuthorityEpoch");
            fixture.rehash(missingAuthority);
            fixture.writeArm(0, missingAuthority);
            assertThrows(IllegalArgumentException.class,
                    () -> FrontierV3PilotExpectedCrash.validateArm(fixture.control, fixture.lifecycle, fixture.descriptor(0), 0));

            fixture.removeRetainedAuthority();
            FrontierV3PilotExpectedCrash.validateArm(fixture.control, fixture.lifecycle, fixture.descriptor(0), 0);
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void twoExpectedCrashCyclesRecoverInOneSessionWithoutReset(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addTwoCrashCycles();
            fixture.installProperties();
            FrontierV3PilotSessionControl.reset();
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            recoverCrashCycle(fixture.scenario);
            assertFalse(FrontierV3PilotSessionControl.expectedCrashSegment());
            assertFalse(FrontierV3PilotSessionControl.armExpectedLoss());

            FrontierV3PilotSessionControl.markAwaitingResume();
            FrontierV3PilotSessionControl.requestReconnect("127.0.0.1");
            FrontierV3PilotSessionControl.onLogin(fixture.scenario);
            recoverCrashCycle(fixture.scenario);
            assertTrue(FrontierV3PilotSessionControl.resumed());
            assertFalse(FrontierV3PilotSessionControl.expectedCrashSegment());
            assertFalse(FrontierV3PilotSessionControl.armExpectedLoss());
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void rehashedIncompleteOrForeignReleaseCannotAuthorizeReconnect(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addVerifiedRecovery(0);
            fixture.installProperties();
            String[] fields = { "short", "arm", "fired", "exit", "loss", "closed", "successor", "ready" };
            for (String field : fields) assertRehashedReleaseRejected(fixture, field);
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    @Test
    void successorIdentityAndControlRecordSizeFailClosed(@TempDir Path root) throws Exception {
        Fixture fixture = Fixture.create(root);
        try {
            fixture.addVerifiedRecovery(0);
            fixture.installProperties();
            FrontierV3PilotExpectedCrash.validateRelease(fixture.control, fixture.lifecycle, fixture.descriptor(0), fixture.descriptor(1), 0);
            JsonObject successor = fixture.descriptor(1);
            successor.addProperty("workerId", "foreign_worker");
            write(fixture.control.resolve("segments").resolve(file(1)), successor);
            assertThrows(IllegalArgumentException.class,
                    () -> FrontierV3PilotExpectedCrash.validateRelease(fixture.control, fixture.lifecycle, fixture.descriptor(0), fixture.descriptor(1), 0));
            fixture.writeRecoveredSegment(1);
            Files.writeString(fixture.control.resolve("expected-crash").resolve(file(0)), "x".repeat(65_537), StandardCharsets.UTF_8);
            assertThrows(IllegalArgumentException.class,
                    () -> FrontierV3PilotExpectedCrash.validateArm(fixture.control, fixture.lifecycle, fixture.descriptor(0), 0));
        } finally {
            FrontierV3PilotSessionControl.reset();
            fixture.restoreProperties();
        }
    }

    private static void recoverCrashCycle(Path scenario) throws Exception {
        FrontierV3PilotSessionControl.bindActiveConnection(new Object());
        assertTrue(FrontierV3PilotSessionControl.expectedCrashSegment());
        assertTrue(FrontierV3PilotSessionControl.armExpectedLoss());
        assertTrue(FrontierV3PilotSessionControl.claimExpectedLoss());
        FrontierV3PilotSessionControl.requestReconnect("127.0.0.1");
        assertTrue(FrontierV3PilotSessionControl.reconnectInFlight());
        FrontierV3PilotSessionControl.onLogin(scenario);
        assertTrue(FrontierV3PilotSessionControl.reconnectInFlight());
        FrontierV3PilotSessionControl.completeReconnect();
        assertFalse(FrontierV3PilotSessionControl.reconnectInFlight());
    }

    private static void completeGracefulHandoff(Object predecessor, Path scenario) throws Exception {
        FrontierV3PilotSessionControl.bindActiveConnection(predecessor);
        FrontierV3PilotSessionControl.markAwaitingResume();
        FrontierV3PilotSessionControl.requestReconnect("127.0.0.1");
        assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(predecessor));
        assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
        FrontierV3PilotSessionControl.executeReconnectTransportDeparture(() ->
                assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null)));
        assertFalse(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
        FrontierV3PilotSessionControl.onLogin(scenario);
        FrontierV3PilotSessionControl.bindActiveConnection(new Object());
        FrontierV3PilotSessionControl.completeReconnect();
    }

    private static void authorizePendingNullCleanup(Fixture fixture) throws Exception {
        FrontierV3PilotSessionControl.reset();
        FrontierV3PilotSessionControl.onLogin(fixture.scenario);
        Object predecessor = new Object();
        FrontierV3PilotSessionControl.bindActiveConnection(predecessor);
        assertTrue(FrontierV3PilotSessionControl.armExpectedLoss());
        assertTrue(FrontierV3PilotSessionControl.claimExpectedLoss(predecessor));
        FrontierV3PilotSessionControl.requestReconnect("127.0.0.1");
        FrontierV3PilotSessionControl.prepareReconnectTransportDeparture();
        assertTrue(FrontierV3PilotSessionControl.expectedReconnectPredecessorDeparture(null));
    }

    private static void assertRehashedArmRejected(Fixture fixture, String mutation) throws Exception {
        JsonObject arm = fixture.arm(0);
        switch (mutation) {
            case "identity" -> arm.getAsJsonObject("identity").addProperty("nonce", UUID.randomUUID().toString());
            case "clientPid" -> arm.addProperty("clientPid", ProcessHandle.current().pid() + 1);
            case "revision" -> arm.addProperty("resolvedRevision", 18);
            case "authority" -> arm.addProperty("authorityEpoch", 4);
            case "boundary" -> arm.getAsJsonObject("descriptor").addProperty("boundary", "not_a_crash_boundary");
            case "overflow" -> arm.addProperty("clientPid", 9_007_199_254_740_992L);
            default -> throw new IllegalArgumentException(mutation);
        }
        fixture.rehash(arm);
        fixture.writeArm(0, arm);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3PilotExpectedCrash.validateArm(fixture.control, fixture.lifecycle, fixture.descriptor(0), 0));
        fixture.writeArm(0, fixture.nodeEquivalentArm(0));
    }

    private static void assertRehashedReleaseRejected(Fixture fixture, String mutation) throws Exception {
        JsonObject release = fixture.release(1);
        switch (mutation) {
            case "short" -> release.add("proof", new JsonObject());
            case "arm" -> release.addProperty("armSha256", "f".repeat(64));
            case "fired" -> release.getAsJsonObject("proof").getAsJsonObject("fired").addProperty("revision", 99);
            case "exit" -> release.getAsJsonObject("proof").getAsJsonObject("ownedExit").addProperty("exited", false);
            case "loss" -> release.getAsJsonObject("proof").getAsJsonObject("clientLoss").addProperty("workerId", "foreign");
            case "closed" -> release.getAsJsonObject("proof").getAsJsonObject("portClosed").addProperty("closed", false);
            case "successor" -> release.getAsJsonObject("successor").addProperty("serverRunId", "server_foreign");
            case "ready" -> release.getAsJsonObject("successor").addProperty("ready", false);
            default -> throw new IllegalArgumentException(mutation);
        }
        fixture.rehash(release);
        fixture.writeRelease(1, release);
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3PilotExpectedCrash.validateRelease(fixture.control, fixture.lifecycle, fixture.descriptor(0), fixture.descriptor(1), 0));
        fixture.writeRelease(1, fixture.nodeEquivalentRelease(0));
    }

    private static final class Fixture {
        private final Path control;
        private final Path lifecycle;
        private final Path scenario;
        private final JsonObject identity;
        private final String previousControl;
        private final String previousMode;
        private final String previousLifecycle;
        private final String previousScenario;

        private Fixture(Path control, Path lifecycle, Path scenario, JsonObject identity) {
            this.control = control;
            this.lifecycle = lifecycle;
            this.scenario = scenario;
            this.identity = identity;
            previousControl = System.getProperty(CONTROL);
            previousMode = System.getProperty(MODE);
            previousLifecycle = System.getProperty(LIFECYCLE);
            previousScenario = System.getProperty("pale_mirror.frontier_v3.test_pilot.scenario");
        }

        static Fixture create(Path root) throws Exception {
            Path control = Files.createDirectory(root.resolve("control"));
            Files.createDirectory(control.resolve("segments"));
            Files.createDirectory(control.resolve("expected-crash"));
            Files.createDirectory(control.resolve("server-ready"));
            Files.createDirectory(control.resolve("recovery"));
            Files.createDirectory(control.resolve("resume"));
            String runId = UUID.randomUUID().toString();
            Files.writeString(control.resolve("run-id"), runId + "\n", StandardCharsets.UTF_8);
            Path scenario = root.resolve("scenario.json");
            Files.writeString(scenario, "{\"id\":\"crash_scenario\"}\n", StandardCharsets.UTF_8);
            JsonObject identity = identity("a".repeat(64), runId, UUID.randomUUID().toString(), UUID.randomUUID().toString());
            Path lifecycle = Files.createDirectory(root.resolve("lifecycle"));
            Files.createDirectory(lifecycle.resolve("staging"));
            Files.createDirectory(lifecycle.resolve("signals"));
            Files.writeString(lifecycle.resolve("identity.json"), identity + "\n", StandardCharsets.UTF_8);
            Fixture fixture = new Fixture(control, lifecycle, scenario, identity);
            fixture.writeCrashSegment(0, 17, 3);
            fixture.writeArm(0, fixture.nodeEquivalentArm(0));
            return fixture;
        }

        void installProperties() {
            System.setProperty(CONTROL, control.toString());
            System.setProperty(MODE, "matrix");
            System.setProperty(LIFECYCLE, lifecycle.toString());
            System.setProperty("pale_mirror.frontier_v3.test_pilot.scenario", scenario.toString());
        }

        void restoreProperties() {
            restore(CONTROL, previousControl);
            restore(MODE, previousMode);
            restore(LIFECYCLE, previousLifecycle);
            restore("pale_mirror.frontier_v3.test_pilot.scenario", previousScenario);
        }

        void addTwoCrashCycles() throws Exception {
            addVerifiedRecovery(0);
            writeCrashSegment(2, 18, 4);
            writeArm(2, nodeEquivalentArm(2));
            writeResume(2);
            addVerifiedRecovery(2);
        }

        void addVerifiedRecovery(int predecessorEpoch) throws Exception {
            int successorEpoch = predecessorEpoch + 1;
            writeRecoveredSegment(successorEpoch);
            writeRelease(successorEpoch, nodeEquivalentRelease(predecessorEpoch));
            writeResume(successorEpoch);
        }

        void addGracefulRecovery(int predecessorEpoch) throws Exception {
            addGracefulRecovery(predecessorEpoch, "world_a", "world_a");
        }

        void addGracefulRecovery(int predecessorEpoch, String predecessorWorld, String successorWorld) throws Exception {
            int successorEpoch = predecessorEpoch + 1;
            writeGracefulSegment(predecessorEpoch, predecessorWorld);
            writeRecoveredSegment(successorEpoch, successorWorld);
            writeResume(successorEpoch);
        }

        JsonObject descriptor(int epoch) throws Exception { return read(control.resolve("segments").resolve(file(epoch))); }
        JsonObject arm(int epoch) throws Exception { return read(control.resolve("expected-crash").resolve(file(epoch))); }
        JsonObject release(int epoch) throws Exception { return read(control.resolve("recovery").resolve(file(epoch))); }
        void writeArm(int epoch, JsonObject value) throws Exception { write(control.resolve("expected-crash").resolve(file(epoch)), value); }
        void writeRelease(int epoch, JsonObject value) throws Exception { write(control.resolve("recovery").resolve(file(epoch)), value); }

        void writeBoundary(String boundary) throws Exception {
            JsonObject descriptor = descriptor(0);
            descriptor.getAsJsonObject("expectedCrash").addProperty("boundary", boundary);
            write(control.resolve("segments").resolve(file(0)), descriptor);
            writeArm(0, nodeEquivalentArm(0));
        }

        void writeFixedRevision(int revision) throws Exception {
            JsonObject descriptor = descriptor(0);
            descriptor.getAsJsonObject("expectedCrash").addProperty("expectedRevision", revision);
            descriptor.getAsJsonObject("expectedCrash").addProperty("resolvedRevision", revision);
            write(control.resolve("segments").resolve(file(0)), descriptor);
            writeArm(0, nodeEquivalentArm(0));
        }

        void removeRetainedAuthority() throws Exception {
            JsonObject descriptor = descriptor(0);
            descriptor.getAsJsonObject("expectedCrash").remove("expectedAuthorityEpoch");
            write(control.resolve("segments").resolve(file(0)), descriptor);
            writeArm(0, nodeEquivalentArm(0));
        }

        JsonObject nodeEquivalentArm(int epoch) throws Exception {
            JsonObject descriptor = descriptor(epoch);
            JsonObject arm = new JsonObject();
            put(arm, "schema", 1); put(arm, "kind", "frontier-v3-persistent-expected-crash-arm");
            arm.add("identity", compactIdentity()); put(arm, "epoch", epoch); arm.add("descriptor", descriptor.get("expectedCrash"));
            put(arm, "serverRunId", "server_" + epoch); put(arm, "serverPid", 2222 + epoch); put(arm, "clientPid", ProcessHandle.current().pid());
            put(arm, "port", 25575); put(arm, "resolvedRevision", descriptor.getAsJsonObject("expectedCrash").get("resolvedRevision").getAsInt());
            if (descriptor.getAsJsonObject("expectedCrash").has("expectedAuthorityEpoch")) {
                put(arm, "authorityEpoch", descriptor.getAsJsonObject("expectedCrash").get("expectedAuthorityEpoch").getAsInt());
            }
            rehash(arm);
            return arm;
        }

        JsonObject nodeEquivalentRelease(int predecessorEpoch) throws Exception {
            int successorEpoch = predecessorEpoch + 1;
            JsonObject arm = arm(predecessorEpoch);
            JsonObject descriptor = descriptor(predecessorEpoch);
            JsonObject release = new JsonObject();
            put(release, "schema", 1); put(release, "kind", "frontier-v3-persistent-expected-crash-release");
            put(release, "armSha256", arm.get("contentSha256").getAsString()); release.add("identity", compactIdentity()); put(release, "predecessorEpoch", predecessorEpoch);
            JsonObject successor = new JsonObject();
            put(successor, "epoch", successorEpoch); put(successor, "segment", segment(successorEpoch)); put(successor, "scenarioSha256", scenarioHash());
            put(successor, "worldKey", "world_a"); put(successor, "serverRunId", "server_" + successorEpoch); put(successor, "serverPid", 2222 + successorEpoch); put(successor, "ready", true);
            release.add("successor", successor);
            JsonObject proof = new JsonObject();
            JsonObject fired = new JsonObject();
            put(fired, "runId", "server_" + predecessorEpoch); put(fired, "boundary", descriptor.getAsJsonObject("expectedCrash").get("boundary").getAsString());
            put(fired, "owner", descriptor.getAsJsonObject("expectedCrash").get("owner").getAsString()); put(fired, "payloadType", descriptor.getAsJsonObject("expectedCrash").get("payloadType").getAsString());
            put(fired, "serverPid", 2222 + predecessorEpoch); put(fired, "revision", 17 + predecessorEpoch / 2); put(fired, "authorityEpoch", 3 + predecessorEpoch / 2);
            proof.add("fired", fired);
            JsonObject exit = new JsonObject(); put(exit, "serverPid", 2222 + predecessorEpoch); put(exit, "serverRunId", "server_" + predecessorEpoch); put(exit, "exited", true); proof.add("ownedExit", exit);
            JsonObject loss = new JsonObject();
            put(loss, "clientPid", ProcessHandle.current().pid()); put(loss, "epoch", predecessorEpoch); put(loss, "segment", segment(predecessorEpoch));
            put(loss, "runId", identity.get("runId").getAsString()); put(loss, "nonce", identity.get("nonce").getAsString()); put(loss, "sessionId", identity.get("sessionId").getAsString());
            put(loss, "workerId", identity.get("workerId").getAsString()); put(loss, "buildIdentitySha256", identity.get("buildIdentitySha256").getAsString()); proof.add("clientLoss", loss);
            JsonObject closed = new JsonObject(); put(closed, "serverPid", 2222 + predecessorEpoch); put(closed, "serverRunId", "server_" + predecessorEpoch); put(closed, "port", 25575); put(closed, "closed", true); proof.add("portClosed", closed);
            release.add("proof", proof); rehash(release);
            return release;
        }

        private void writeCrashSegment(int epoch, int revision, int authorityEpoch) throws Exception {
            JsonObject declaration = declaration(epoch, revision, authorityEpoch);
            writeSegment(epoch, "expected_crash", declaration);
            writeReady(epoch);
        }

        private void writeGracefulSegment(int epoch, String worldKey) throws Exception { writeSegment(epoch, "graceful_handoff", null, worldKey); writeReady(epoch, worldKey); }
        private void writeRecoveredSegment(int epoch) throws Exception { writeSegment(epoch, "recovered_terminal", null); writeReady(epoch); }
        private void writeRecoveredSegment(int epoch, String worldKey) throws Exception { writeSegment(epoch, "recovered_terminal", null, worldKey); writeReady(epoch, worldKey); }
        private void writeSegment(int epoch, String completion, JsonObject declaration) throws Exception {
            writeSegment(epoch, completion, declaration, "world_a");
        }
        private void writeSegment(int epoch, String completion, JsonObject declaration, String worldKey) throws Exception {
            JsonObject value = new JsonObject();
            put(value, "schema", 1); put(value, "kind", "frontier-v3-pilot-matrix-segment"); put(value, "runId", identity.get("runId").getAsString()); put(value, "epoch", epoch);
            put(value, "segment", segment(epoch)); put(value, "scenarioId", "crash_scenario"); put(value, "final", false); put(value, "completion", completion);
            put(value, "scenarioSha256", scenarioHash()); put(value, "worldKey", worldKey); put(value, "workerId", identity.get("workerId").getAsString()); put(value, "buildIdentitySha256", identity.get("buildIdentitySha256").getAsString());
            put(value, "nonce", identity.get("nonce").getAsString()); put(value, "sessionId", identity.get("sessionId").getAsString());
            if (declaration != null) value.add("expectedCrash", declaration);
            write(control.resolve("segments").resolve(file(epoch)), value);
        }
        private void writeReady(int epoch) throws Exception {
            writeReady(epoch, "world_a");
        }
        private void writeReady(int epoch, String worldKey) throws Exception {
            JsonObject ready = new JsonObject();
            put(ready, "schema", 1); put(ready, "kind", "frontier-v3-persistent-server-ready"); put(ready, "runId", identity.get("runId").getAsString()); put(ready, "epoch", epoch);
            put(ready, "segment", segment(epoch)); put(ready, "scenarioSha256", scenarioHash()); put(ready, "worldKey", worldKey); put(ready, "serverRunId", "server_" + epoch);
            put(ready, "serverPid", 2222 + epoch); put(ready, "port", 25575); put(ready, "ready", true);
            write(control.resolve("server-ready").resolve(file(epoch)), ready);
        }
        private void writeResume(int epoch) throws Exception { Files.writeString(control.resolve("resume").resolve(file(epoch).replace(".json", ".token")), identity.get("runId").getAsString() + ":" + epoch + "\n"); }
        private JsonObject declaration(int epoch, int revision, int authorityEpoch) {
            JsonObject value = new JsonObject();
            put(value, "completion", "expected_crash"); put(value, "segment", segment(epoch)); put(value, "scenarioId", "crash_scenario"); put(value, "scenarioSha256", scenarioHash());
            put(value, "worldKey", "world_a"); put(value, "laneId", "lane_a"); put(value, "boundary", "hot_checkpoint_durable_before_drain_release"); put(value, "owner", "job:harvest-1");
            put(value, "payloadType", "frontier.resource_site_harvest_hot_traversal_advanced"); put(value, "expectedRevision", "observed_at_boundary"); put(value, "resolvedRevision", revision); put(value, "expectedAuthorityEpoch", authorityEpoch);
            return value;
        }
        private JsonObject compactIdentity() {
            JsonObject compact = new JsonObject();
            for (String key : new String[]{ "buildIdentitySha256", "workerId", "runId", "nonce", "sessionId" }) compact.add(key, identity.get(key));
            return compact;
        }
        private String scenarioHash() {
            try { return sha(Files.readAllBytes(scenario)); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }
        void rehash(JsonObject value) { value.remove("contentSha256"); put(value, "contentSha256", sha(value.toString().getBytes(StandardCharsets.UTF_8))); }
    }

    private static JsonObject identity(String build, String runId, String nonce, String session) {
        JsonObject value = new JsonObject();
        put(value, "schema", 1); put(value, "buildIdentitySha256", build); put(value, "workerId", "worker_0"); put(value, "runId", runId);
        put(value, "scenarioId", "crash_scenario"); put(value, "segmentId", "crash_before_0"); put(value, "nonce", nonce); put(value, "sessionId", session);
        return value;
    }
    private static String segment(int epoch) { return epoch % 2 == 0 ? "crash_before_" + epoch / 2 : "crash_after_" + (epoch - 1) / 2; }
    private static String file(int epoch) { return String.format("%04d.json", epoch); }
    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static void write(Path path, JsonObject value) throws Exception { Files.writeString(path, value + "\n", StandardCharsets.UTF_8); }
    private static void restore(String key, String value) { if (value == null) System.clearProperty(key); else System.setProperty(key, value); }
    private static void put(JsonObject object, String key, String value) { object.addProperty(key, value); }
    private static void put(JsonObject object, String key, int value) { object.addProperty(key, value); }
    private static void put(JsonObject object, String key, long value) { object.addProperty(key, value); }
    private static void put(JsonObject object, String key, boolean value) { object.addProperty(key, value); }
    private static String sha(byte[] bytes) {
        try {
            StringBuilder out = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format("%02x", value));
            return out.toString();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
