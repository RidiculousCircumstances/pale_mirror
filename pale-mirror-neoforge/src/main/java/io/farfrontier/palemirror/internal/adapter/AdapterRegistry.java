package io.farfrontier.palemirror.internal.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.IntegrationAdapter;
import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.integration.crimson.CrimsonSandboxAdapter;
import io.farfrontier.palemirror.internal.integration.spore.SporeSandboxAdapter;
import io.farfrontier.palemirror.internal.integration.create.CreateLogisticsAdapter;
import io.farfrontier.palemirror.internal.integration.ftb.FtbQuestsPresentationAdapter;
import io.farfrontier.palemirror.internal.integration.millenaire.MillenaireIntegrationConfig;
import io.farfrontier.palemirror.internal.integration.millenaire.MillenaireSettlementAdapter;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class AdapterRegistry {
    private static final VanillaAnchorAdapter VANILLA_ANCHOR = new VanillaAnchorAdapter();
    private static final CrimsonSandboxAdapter CRIMSON = new CrimsonSandboxAdapter();
    private static final SporeSandboxAdapter SPORE = new SporeSandboxAdapter();
    private static final CreateLogisticsAdapter CREATE_LOGISTICS = new CreateLogisticsAdapter();
    private static final VanillaVillageSettlementAdapter VANILLA_VILLAGES = new VanillaVillageSettlementAdapter();
    private static final FtbQuestsPresentationAdapter FTB_QUESTS = new FtbQuestsPresentationAdapter();
    private static final MillenaireSettlementAdapter MILLENAIRE_SETTLEMENTS = new MillenaireSettlementAdapter();
    private static final List<SourceThreatAdapter> SOURCE_ADAPTERS = List.of(CRIMSON, SPORE);
    private static final List<LogisticsAdapter> LOGISTICS_ADAPTERS = List.of(CREATE_LOGISTICS);
    private static final List<SettlementAdapter> SETTLEMENT_ADAPTERS = List.of(VANILLA_VILLAGES, MILLENAIRE_SETTLEMENTS);
    private static final Map<String, IntegrationAdapter> ADAPTERS = Map.of(
            VANILLA_ANCHOR.id(), VANILLA_ANCHOR,
            CRIMSON.id(), CRIMSON,
            SPORE.id(), SPORE,
            CREATE_LOGISTICS.id(), CREATE_LOGISTICS,
            VANILLA_VILLAGES.id(), VANILLA_VILLAGES,
            MILLENAIRE_SETTLEMENTS.id(), MILLENAIRE_SETTLEMENTS,
            FTB_QUESTS.id(), FTB_QUESTS);

    private AdapterRegistry() { }
    public static List<IntegrationAdapter> all() { return ADAPTERS.values().stream().sorted(java.util.Comparator.comparing(IntegrationAdapter::id)).toList(); }
    public static VanillaAnchorAdapter vanillaAnchor() { return VANILLA_ANCHOR; }
    public static List<SourceThreatAdapter> sourceAdapters() { return SOURCE_ADAPTERS; }
    public static List<LogisticsAdapter> logisticsAdapters() { return LOGISTICS_ADAPTERS; }
    public static List<SettlementAdapter> settlementAdapters() { return SETTLEMENT_ADAPTERS; }
    public static boolean campaignEligible(io.farfrontier.palemirror.internal.world.SettlementObservationRecord record) {
        return !MILLENAIRE_SETTLEMENTS.owns(record) || MILLENAIRE_SETTLEMENTS.campaignEnabled();
    }
    public static void registerConfigs(net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                MillenaireIntegrationConfig.SPEC, "pale-mirror-millenaire.toml");
    }
    /** Optional generic player-facing action; no FTB type or progress crosses this boundary. */
    public static Optional<String> scenarioJournalCommand() { return FTB_QUESTS.journalOpenCommand(); }
    public static Optional<LogisticsRouteObservation> observeLogisticsRoute(ServerLevel level,
                                                                             LogisticsRouteContract contract) {
        return logisticsAdapters().stream().filter(adapter -> adapter.health().status() == AdapterHealth.Status.AVAILABLE)
                .findFirst().map(adapter -> adapter.observe(level, contract));
    }
    /** All returned candidates are bounded to already loaded chunks around the caller's focus. */
    public static List<SettlementObservation> observeSettlements(ServerLevel level, net.minecraft.core.BlockPos focus) {
        return settlementAdapters().stream().filter(adapter -> adapter.health().status() == AdapterHealth.Status.AVAILABLE)
                .flatMap(adapter -> adapter.observeNearby(level, focus).stream()).toList();
    }
    public static SourceThreatAdapter sourceAdapter(InfectionSourceId source) {
        return sourceAdapters().stream().filter(adapter -> adapter.source().equals(source)).findFirst()
                .orElseGet(() -> new UnavailableThreatActorAdapter(source));
    }
    public static Optional<SourceThreatAdapter> sourceForAlias(String alias) {
        return sourceAdapters().stream().filter(adapter -> adapter.commandAliases().contains(alias)).findFirst();
    }
    public static boolean supports(Set<Capability> capabilities) {
        Set<Capability> available = ADAPTERS.values().stream()
                .filter(adapter -> adapter.health().status() == io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE)
                .flatMap(adapter -> adapter.health().capabilities().stream())
                .collect(Collectors.toUnmodifiableSet());
        return available.containsAll(capabilities);
    }
    public static boolean supports(InfectionSourceId source, Set<Capability> capabilities) {
        Set<Capability> coreCapabilities = capabilities.stream()
                .filter(capability -> capability == Capability.PM_ANCHOR_MATERIALIZATION
                        || capability == Capability.PM_ANCHOR_OBSERVATION)
                .collect(Collectors.toUnmodifiableSet());
        Set<Capability> sourceCapabilities = capabilities.stream()
                .filter(capability -> !coreCapabilities.contains(capability))
                .collect(Collectors.toUnmodifiableSet());
        SourceThreatAdapter adapter = sourceAdapter(source);
        return supports(coreCapabilities)
                && (sourceCapabilities.isEmpty() || adapter.health().status() == AdapterHealth.Status.AVAILABLE
                && adapter.health().capabilities().containsAll(sourceCapabilities));
    }
    public static void onServerStarted(net.minecraft.server.MinecraftServer server) {
        sourceAdapters().forEach(adapter -> adapter.onServerStarted(server));
        FTB_QUESTS.onServerStarted(server);
    }
    public static void tickRuntime(net.minecraft.server.MinecraftServer server,
                                   io.farfrontier.palemirror.internal.world.PaleMirrorSavedData data) {
        sourceAdapters().forEach(adapter -> adapter.tickRuntime(server, data));
    }
    public static List<net.minecraft.server.packs.resources.PreparableReloadListener> reloadListeners() {
        return sourceAdapters().stream().flatMap(adapter -> adapter.reloadListeners().stream()).toList();
    }
    public static void registerBuiltInPacks(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        sourceAdapters().forEach(adapter -> adapter.registerBuiltInPacks(event));
    }
    public static boolean rejectsUnmanagedEntity(Entity entity) {
        return sourceAdapters().stream().anyMatch(adapter -> adapter.rejectsUnmanagedEntity(entity));
    }

    /** Unknown source identities must visibly degrade optional presentation rather than impersonating another source. */
    private record UnavailableThreatActorAdapter(InfectionSourceId source) implements SourceThreatAdapter {
        @Override public String id() { return "pale_mirror:unavailable_source_actor"; }
        @Override public AdapterHealth health() { return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                "No installed PM actor adapter for source " + source.value(), Set.of()); }
        @Override public ActorOperationResult ensureActor(ServerLevel level, TestMineRecord site, String jobId,
                                                           EncounterProfile.ActorSlot slot) {
            return ActorOperationResult.unavailable(health().detail());
        }
        @Override public ActorOperationResult removeActor(ServerLevel level, TestMineRecord site, String slotId) {
            return ActorOperationResult.unavailable(health().detail());
        }
        @Override public boolean matchesActor(Entity entity) { return false; }
        @Override public boolean matchesOwnedActor(Entity entity, TestMineRecord site, String slotId) { return false; }
    }
}
