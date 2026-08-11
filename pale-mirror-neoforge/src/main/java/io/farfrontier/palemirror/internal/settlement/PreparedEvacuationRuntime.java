package io.farfrontier.palemirror.internal.settlement;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import io.farfrontier.palemirror.PaleMirrorItems;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.OperationalState;
import io.farfrontier.palemirror.domain.PopulationDisposition;
import io.farfrontier.palemirror.domain.SiteCapability;
import io.farfrontier.palemirror.domain.SiteCapabilityType;
import io.farfrontier.palemirror.domain.SiteAffiliationRole;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldSite;
import io.farfrontier.palemirror.domain.WorldSiteType;
import io.farfrontier.palemirror.domain.KnownRegionalFeature;
import io.farfrontier.palemirror.internal.economy.SettlementDepotState;
import io.farfrontier.palemirror.internal.presentation.RefugeeAnchorItem;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Validates player-proposed evacuation camps; the item stack itself is never authority. */
public final class PreparedEvacuationRuntime {
    private final PaleMirrorSavedData data;
    private final DomainCommandProcessor commands;
    private final Function<ServerPlayer, StoryAudienceId> audiences;
    private final Consumer<List<DomainEvent>> eventHandler;

    public PreparedEvacuationRuntime(PaleMirrorSavedData data, DomainCommandProcessor commands,
                                     Function<ServerPlayer, StoryAudienceId> audiences,
                                     Consumer<List<DomainEvent>> eventHandler) {
        this.data = data;
        this.commands = commands;
        this.audiences = audiences;
        this.eventHandler = eventHandler;
    }

    public boolean begin(String communityId, StoryAudienceId audience, String causationId) {
        try {
            WorldObjectId community = new WorldObjectId(communityId);
            boolean prepared = data.worldState().siteCapabilities().stream()
                    .filter(value -> value.type() == SiteCapabilityType.SHELTER)
                    .filter(value -> value.capacity() >= data.worldState().population(community))
                    .filter(value -> data.worldState().siteAffiliations(value.siteId(), SiteAffiliationRole.RECIPIENT)
                            .stream().anyMatch(affiliation -> affiliation.objectId().equals(community)))
                    .anyMatch(value -> data.worldState().site(value.siteId()).map(site ->
                            site.operationalState() == OperationalState.OPERATIONAL).orElse(false));
            if (!prepared) return false;
            List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.BeginSettlementEvacuation(
                    community, audience, causationId));
            if (!events.isEmpty()) data.setDirty();
            return !events.isEmpty();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean issue(ServerPlayer player, String communityId) {
        try {
            WorldObjectId community = new WorldObjectId(communityId);
            StoryAudienceId audience = audiences.apply(player);
            var region = data.worldState().livingRegions().stream().filter(value -> value.communityId().equals(community))
                    .findFirst().orElse(null);
            if (region == null || !audience.equals(region.primaryAudience()) || data.worldState().emergencyWindow(community)
                    .filter(window -> window.state() == io.farfrontier.palemirror.domain.EmergencyWindowState.OPEN).isEmpty()) {
                return false;
            }
            RefugeeAnchorPermit permit = data.refugeeAnchorPermits().permits().stream().filter(value -> !value.consumed()
                    && value.playerId().equals(player.getUUID()) && value.communityId().equals(community)
                    && value.audience().equals(audience) && value.expiresAtStep() >= data.worldState().simulationStep()).findFirst()
                    .orElseGet(() -> {
                        var window = data.worldState().emergencyWindow(community).orElseThrow();
                        RefugeeAnchorPermit created = new RefugeeAnchorPermit("pm:refugee-permit:" + UUID.randomUUID(),
                                player.getUUID(), community, audience, Long.MAX_VALUE, false);
                        data.refugeeAnchorPermits().issue(created);
                        return created;
                    });
            ItemStack stack = new ItemStack(PaleMirrorItems.REFUGEE_ANCHOR.get());
            RefugeeAnchorItem.bind(stack, permit.id());
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Refugee Anchor — " + regionName(region.id())));
            player.getInventory().placeItemBackInInventory(stack);
            player.sendSystemMessage(Component.literal("Place the Refugee Anchor on a clear site 96–160 blocks from the settlement."));
            data.setDirty();
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean place(ServerPlayer player, net.minecraft.core.BlockPos anchor, ItemStack stack) {
        String permitId = RefugeeAnchorItem.permitId(stack);
        RefugeeAnchorPermit permit = data.refugeeAnchorPermits().find(permitId).orElse(null);
        if (permit == null || !permit.usable(player.getUUID(), permit.communityId(), audiences.apply(player),
                data.worldState().simulationStep()) || data.worldState().emergencyWindow(permit.communityId())
                .filter(window -> window.state() == io.farfrontier.palemirror.domain.EmergencyWindowState.OPEN).isEmpty()) {
            return rejected(player, "This Refugee Anchor is no longer valid.");
        }
        var region = data.worldState().livingRegions().stream().filter(value -> value.communityId().equals(permit.communityId()))
                .findFirst().orElse(null);
        var physical = region == null ? null : data.campaignRegions().get(region.id());
        if (physical == null || !player.serverLevel().dimension().location().toString().equals(physical.dimensionId())) {
            return rejected(player, "The anchor must be placed in its settlement's dimension.");
        }
        long dx = (long) anchor.getX() - physical.settlementAnchor().getX();
        long dz = (long) anchor.getZ() - physical.settlementAnchor().getZ();
        long distance = dx * dx + dz * dz;
        if (distance < 96L * 96L || distance > 160L * 160L || !RefugeeCampRuntime.safeAnchor(player.serverLevel(), anchor)) {
            return rejected(player, "Choose a clear, level site 96–160 blocks from the settlement.");
        }
        var group = data.worldState().populationGroups(permit.communityId()).stream()
                .filter(value -> value.disposition() == PopulationDisposition.RESIDENT).findFirst().orElse(null);
        if (group == null || data.refugeeCamps().containsKey(group.id())) {
            return rejected(player, "A refugee site is already prepared for this community.");
        }
        WorldObjectId siteId = new WorldObjectId("pale_mirror:refugee_camp_"
                + Integer.toUnsignedString(group.id().hashCode(), 36));
        List<UUID> representatives = java.util.stream.IntStream.range(0, Math.max(2, Math.min(4, (group.size() + 19) / 20)))
                .mapToObj(slot -> UUID.nameUUIDFromBytes((group.id() + ":representative:" + slot)
                        .getBytes(StandardCharsets.UTF_8))).toList();
        RefugeeCampRecord camp = new RefugeeCampRecord(group.id(), permit.communityId(), siteId,
                player.serverLevel().dimension().location().toString(), anchor,
                RefugeeCampRuntime.captureCells(player.serverLevel(), anchor), representatives,
                SettlementDepotState.PLANNED, "");
        List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.RegisterEvacuationShelter(
                permit.communityId(), audiences.apply(player), new WorldSite(siteId, WorldSiteType.SHELTER, OperationalState.DEGRADED),
                new SiteCapability(siteId, SiteCapabilityType.SHELTER, null, data.worldState().population(permit.communityId())),
                "player:" + player.getUUID() + ":refugee-anchor"));
        if (events.isEmpty()) return false;
        events = new java.util.ArrayList<>(events);
        events.addAll(commands.execute(data.worldState(), new DomainCommand.DiscoverRegionalFeature(region.id(),
                audiences.apply(player), KnownRegionalFeature.REFUGEE_SITE,
                "player:" + player.getUUID() + ":refugee-anchor")));
        data.refugeeCamps().put(group.id(), camp);
        permit.consume();
        stack.shrink(1);
        data.setDirty();
        eventHandler.accept(events);
        player.sendSystemMessage(Component.literal("Refugee site prepared. Return to the Regional Ledger to begin evacuation."));
        return true;
    }

    private String regionName(String regionId) {
        return java.util.Optional.ofNullable(data.campaignRegions().get(regionId))
                .map(io.farfrontier.palemirror.internal.world.CampaignRegionRecord::displayName).orElse("Iron Frontier");
    }

    private static boolean rejected(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return false;
    }
}
