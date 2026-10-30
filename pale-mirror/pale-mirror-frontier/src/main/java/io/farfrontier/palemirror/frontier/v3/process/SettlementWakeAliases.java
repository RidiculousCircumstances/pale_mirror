package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Existing settlement wake addresses only. These aliases grant no storage or work authority. */
final class SettlementWakeAliases {
    private record Directory(Map<SubjectId, SubjectId> byHome, Set<SubjectId> containers) { }
    private static final ImmutableInputView<Directory> DIRECTORY = new ImmutableInputView<>();
    private static Directory directory(FrontierWorldState state) {
        return DIRECTORY.get(List.of(state.bootstrap()), () -> {
            var homes = new HashMap<SubjectId, SubjectId>();
            state.bootstrap().settlements().forEach(home -> homes.put(home.id(), FrontierWorldState.depotId(home.id())));
            return new Directory(Map.copyOf(homes), Set.copyOf(homes.values()));
        });
    }
    static Set<SubjectId> changed(FrontierWorldState before, FrontierWorldState after, SubjectId subject,
                                  ResidentProfile resident, boolean access, boolean stock, boolean custody) {
        var aliases = directory(after); var keys = new HashSet<SubjectId>();
        if (access) {
            addHome(aliases, subject, keys);
            if (resident != null) addHome(aliases, resident.settlementId(), keys);
            var previousJob = before.productionJobs().get(subject); var nextJob = after.productionJobs().get(subject);
            if (previousJob != null) addHome(aliases, previousJob.settlementId(), keys);
            if (nextJob != null) addHome(aliases, nextJob.settlementId(), keys);
            SubjectId site = null;
            if (after.resourceSites().sites().containsKey(subject)) site = subject;
            else site = after.resourceSites().workSiteId(subject).orElse(null);
            if (site != null) addHome(aliases, after.resourceSite(site).settlementId(), keys);
        }
        if (stock) {
            var candidates = new HashSet<SubjectId>();
            ImmutableMapChanges.forEach(before.inventory().fungibleResources().accounts(), after.inventory().fungibleResources().accounts(),
                    change -> { addStock(aliases, change.before(), candidates); addStock(aliases, change.after(), candidates); });
            for (var container : candidates) if (!FungibleResourceCustodySupport.accountAtContainer(before, container)
                    .equals(FungibleResourceCustodySupport.accountAtContainer(after, container))) keys.add(container);
        }
        if (custody) {
            var candidates = new HashSet<SubjectId>();
            ImmutableMapChanges.forEach(before.replicaCustody().replicas(), after.replicaCustody().replicas(),
                    change -> { if (aliases.containers().contains(change.key())) candidates.add(change.key()); });
            ImmutableMapChanges.forEach(before.replicaCustody().custodyByScope(), after.replicaCustody().custodyByScope(),
                    change -> { addLease(aliases, change.before(), candidates); addLease(aliases, change.after(), candidates); });
            for (var container : candidates) if (ReferenceContainerCustody.blocksCanonicalUse(before, container)
                    != ReferenceContainerCustody.blocksCanonicalUse(after, container)
                    || ReferenceContainerCustody.hasLiveCustody(before, container) != ReferenceContainerCustody.hasLiveCustody(after, container))
                keys.add(container);
        }
        return Set.copyOf(keys);
    }
    private static void addHome(Directory aliases, SubjectId home, Set<SubjectId> keys) {
        var key = aliases.byHome().get(home); if (key != null) keys.add(key);
    }
    private static void addStock(Directory aliases, CustodyAccount account, Set<SubjectId> keys) {
        if (account != null && account.custody() instanceof ResourceCustody.Container container
                && container.section().equals(ContainerSectionId.STOCK) && aliases.containers().contains(container.containerId()))
            keys.add(container.containerId());
    }
    private static void addLease(Directory aliases, PhysicalCustodyLease lease, Set<SubjectId> keys) {
        if (lease != null && aliases.containers().contains(lease.objectId())) keys.add(lease.objectId());
    }
    private SettlementWakeAliases() { }
}
