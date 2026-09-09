package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Test/moddev-only fixture selector. This class is deliberately excluded from the packaged mod. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
public final class FrontierV3PilotFixtureBootstrap {
    static final String PROFILE_PROPERTY = "pale_mirror.frontier_v3.pilot.profile";

    private FrontierV3PilotFixtureBootstrap() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarted(ServerStartedEvent event) {
        if (!FrontierV3ServerLifecycle.v3LaunchOwnsPhysicalWorld()) return;
        String profileId = System.getProperty(PROFILE_PROPERTY, FrontierV3FixtureCatalog.DEFAULT_PROFILE);
        FrontierV3FixtureCatalog.Profile profile = FrontierV3FixtureCatalog.profile(profileId);
        if (!"disposable_lite".equals(profile.allowedRunner())) {
            throw new IllegalStateException("Frontier v3 pilot profile is not allowed for the disposable runner: " + profileId);
        }
        FrontierV3ServerLifecycle.startModDevFixture(event.getServer(),
                FrontierV3FixtureCatalog.configuration(profileId, new WorldId("frontier:graybox"), event.getServer().overworld().getSeed()));
        FrontierV3CrashBoundaryProbe.requireConfiguredMixinApplication();
        // This is the first pilot-owned read after recovered state has been
        // installed and before any disposable client may continue it.  The
        // signal remains evidence only; it cannot schedule, load, or retain a
        // physical surface.
        FrontierV3PilotLifecycleSignal.serverRunReady(event.getServer());
        PaleMirrorMod.LOGGER.info("PMV3_PILOT_FIXTURE profile={} source={} assertion={}", profile.id(), profile.sourceProfile(), profile.requiredAssertion());
    }

}
