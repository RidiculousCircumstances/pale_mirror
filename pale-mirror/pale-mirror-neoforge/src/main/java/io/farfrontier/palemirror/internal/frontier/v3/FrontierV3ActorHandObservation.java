package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestCargo;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only, naturally loaded evidence of the exact harvest worker's reserved offhand. */
final class FrontierV3ActorHandObservation {
    private FrontierV3ActorHandObservation() { }

    enum Disposition { UNAVAILABLE, FOREIGN_OWNER, EMPTY, FOREIGN_ITEM, WHEAT }

    static final class Review {
        private final Disposition disposition;
        private final Optional<FungiblePhysicalObservation.Stack> stack;
        private final PhysicalStackAddress.ActorHand address;
        private final SubjectId siteId;
        private final SubjectId jobId;
        private final long authorityEpoch;
        private final boolean worldRead;

        private Review(Disposition disposition, Optional<FungiblePhysicalObservation.Stack> stack,
                       PhysicalStackAddress.ActorHand address, SubjectId siteId, SubjectId jobId,
                       long authorityEpoch, boolean worldRead) {
            this.disposition = Objects.requireNonNull(disposition, "actor-hand review disposition");
            this.stack = Objects.requireNonNull(stack, "actor-hand review stack");
            this.address = address;
            this.siteId = siteId;
            this.jobId = jobId;
            this.authorityEpoch = authorityEpoch;
            this.worldRead = worldRead;
            if ((disposition == Disposition.WHEAT) != stack.isPresent())
                throw new IllegalArgumentException("actor-hand review must retain precisely its observed wheat");
        }
        static Review of(Disposition disposition) {
            return new Review(disposition, Optional.empty(), null, null, null, 0, false);
        }
        Disposition disposition() { return disposition; }
        Optional<FungiblePhysicalObservation.Stack> stack() { return stack; }
        private Review fromWorld(SubjectId currentSite, SubjectId currentJob, long currentEpoch) {
            return new Review(disposition, stack, address, currentSite, currentJob, currentEpoch, true);
        }
        boolean matches(FrontierV3ResourceFieldWitness.HandEffect effect) {
            return worldRead && address != null && address.actorId().equals(effect.actorId())
                    && address.entityId().equals(effect.entityId()) && siteId.equals(effect.siteId())
                    && jobId.equals(effect.jobId())
                    && authorityEpoch == effect.authorityEpoch();
        }
        boolean matchesBefore(FrontierV3ResourceFieldWitness.HandEffect effect) {
            if (!matches(effect)) return false;
            return matchesQuantity(effect.beforeCount());
        }
        boolean matchesQuantity(int count) {
            return worldRead && (count == 0 ? disposition == Disposition.EMPTY
                    : disposition == Disposition.WHEAT && stack.orElseThrow().quantity() == count);
        }
    }

    static Review observe(ServerLevel level, FrontierWorldState state, SceneLease lease, ResourceSiteHarvestJob job) {
        Objects.requireNonNull(level, "actor-hand server level");
        Objects.requireNonNull(state, "actor-hand canonical state");
        Objects.requireNonNull(lease, "actor-hand scene lease");
        Objects.requireNonNull(job, "actor-hand harvest job");
        if (!ownsCurrentHarvest(state, lease, job)) {
            return Review.of(Disposition.FOREIGN_OWNER);
        }
        var member = lease.members().getFirst();
        Entity entity = level.getEntity(member.entityId());
        if (entity == null) return Review.of(Disposition.UNAVAILABLE);
        if (!(entity instanceof Mob worker) || !worker.isAlive()
                || !FrontierV3SceneExecutor.owned(entity, state, lease, member)) {
            return Review.of(Disposition.FOREIGN_OWNER);
        }
        return read(state, lease, job, worker);
    }

    /** Read-only retained custody at the pre-loot boundary. Native health is
     * already zero there; neither health nor HOT actuation permission decides
     * whether the exact body still contains this owner's resources. */
    static Review observeRetained(ServerLevel level, FrontierWorldState state, SceneLease lease, ResourceSiteHarvestJob job,
            Mob body, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId identity) {
        if (identity.execution().activityKind() != io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST
                || !identity.execution().activityOwnerId().equals(job.id()) || !identity.body().actorId().equals(job.workerId())
                || !identity.body().equals(io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, job.workerId()))
                || body.level() != level || body.isRemoved() || level.getEntity(body.getUUID()) != body
                || lease.status() == SceneLeaseStatus.CLOSED || !ownsRetainedHarvest(state, lease, job)
                || !FrontierV3SceneExecutor.ownsDeclaration(body, state, lease, lease.members().getFirst()))
            return Review.of(Disposition.FOREIGN_OWNER);
        state.actorExecutions().requireRetained(identity.execution());
        return read(state, lease, job, body);
    }

    private static Review read(FrontierWorldState state, SceneLease lease, ResourceSiteHarvestJob job, Mob worker) {
        // MAINHAND is already used by exact tools, weapons and service materials. Farmer
        // cargo owns OFFHAND exclusively; reading the main hand would mistake that exact
        // equipment for a conflicting fungible stack or overwrite it on a later effect.
        ItemStack held = worker.getOffhandItem();
        return classify(job.workerId(), worker.getUUID(), held.isEmpty() ? "" : Objects.toString(BuiltInRegistries.ITEM.getKey(held.getItem()), ""),
                held.isEmpty() ? 0 : held.getCount(), held.isEmpty() || ItemStack.isSameItemSameComponents(held,
                        new ItemStack(Items.WHEAT, held.getCount()))).fromWorld(job.siteId(), job.id(),
                        ResourceSiteHarvestCargo.handEpoch(state, job, lease));
    }

    static Review classify(SubjectId actorId, UUID entityId, String itemKind, int count) {
        return classify(actorId, entityId, itemKind, count, true);
    }

    static Review classify(SubjectId actorId, UUID entityId, String itemKind, int count, boolean plainWheat) {
        Objects.requireNonNull(actorId, "actor-hand owner");
        Objects.requireNonNull(entityId, "actor-hand body");
        Objects.requireNonNull(itemKind, "actor-hand item kind");
        var address = new PhysicalStackAddress.ActorHand(actorId, entityId);
        if (count == 0 && itemKind.isEmpty())
            return new Review(Disposition.EMPTY, Optional.empty(), address, null, null, 0, false);
        if (!plainWheat || !"minecraft:wheat".equals(itemKind) || count < 1 || count > 64) {
            return new Review(Disposition.FOREIGN_ITEM, Optional.empty(), address, null, null, 0, false);
        }
        return new Review(Disposition.WHEAT, Optional.of(new FungiblePhysicalObservation.Stack(
                address, "minecraft:wheat", count)), address, null, null, 0, false);
    }

    /** A caller's stale lease/job pair cannot borrow the hand of a current physical body. */
    static boolean ownsCurrentHarvest(FrontierWorldState state, SceneLease lease, ResourceSiteHarvestJob job) {
        return lease.status() == SceneLeaseStatus.HOT && ownsRetainedHarvest(state, lease, job);
    }
    private static boolean ownsRetainedHarvest(FrontierWorldState state, SceneLease lease, ResourceSiteHarvestJob job) {
        ResourceSiteLifecycle site = state.resourceSites().sites().get(job.siteId());
        return lease.worldId().equals(state.bootstrap().worldId())
                && lease.equals(state.sceneLeases().get(lease.id()))
                && FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                && FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId())
                && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id())
                && site != null && site.phase() == ResourceSitePhase.HARVESTING
                && site.harvestJob(job.id()).filter(job::equals).isPresent()
                && lease.members().size() == 1
                && lease.members().getFirst().actorId().equals(job.workerId());
    }
}
