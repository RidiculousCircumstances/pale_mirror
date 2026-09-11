package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLease;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PhysicalExecutorsTest {
    @Test
    void recoveredFungibleDepartureIsObservedBeforeTheGenericContainerAudit() {
        var diagnostics = FrontierV3PhysicalExecutors.registry().diagnostics();
        List<String> ids = diagnostics.stream()
                .map(FrontierV3PhysicalExecutorRegistry.Diagnostic::id).toList();
        int fungible = ids.indexOf("fungible-resource-observation");
        int container = ids.indexOf("container-surfaces");

        assertTrue(fungible >= 0 && container >= 0 && fungible < container,
                "the actual physical composition must recover an ordinary saved HOT split before stale canonical audit can fence it");
        assertTrue(diagnostics.stream().filter(value -> value.id().equals("container-surfaces")).findFirst().orElseThrow()
                        .dependencies().containsAll(List.of("production-transformation", "fungible-resource-observation")),
                "the generic audit must declare both durable recovery predecessors rather than inherit declaration order");
    }

    @Test
    void unloadedRestartRetainsOnlyItsCurrentHotFungibleSourceForPhysicalRecovery() {
        SubjectId container = new SubjectId("container:depot");
        SubjectId account = new SubjectId("custody:container-depot");
        SubjectId lot = new SubjectId("lot:wheat");
        PhysicalCustodyLease lease = new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(container), container,
                ReferenceContainerCustody.PROVIDER_ID, 7L, 10L, 3L, PhysicalCustodyLeaseStatus.ACQUIRED, null);
        FungibleResourceLedger current = ledger(container, account, lot, 7L);
        FungibleResourceLedger stale = ledger(container, account, lot, 6L);

        Map<SubjectId, Long> noCurrentProcessObservation = Map.of();
        assertTrue(FrontierV3ReferenceContainerCustodyExecutor.retainsUnobservedRestartFungibleHot(noCurrentProcessObservation, current, lease),
                "a restart must preserve the exact saved HOT source until its naturally loaded player/container evidence is observed");
        assertFalse(FrontierV3ReferenceContainerCustodyExecutor.retainsUnobservedRestartFungibleHot(noCurrentProcessObservation, stale, lease),
                "an old physical epoch must not retain authority or bypass ordinary custody fencing");
        assertFalse(FrontierV3ReferenceContainerCustodyExecutor.retainsUnobservedRestartFungibleHot(Map.of(lease.scopeId(), lease.authorityEpoch()), current, lease),
                "ordinary observer loss after a current-process physical observation must checkpoint and release HOT custody");
    }

    private static FungibleResourceLedger ledger(SubjectId container, SubjectId account, SubjectId lot, long epoch) {
        ResourceLot resourceLot = new ResourceLot(lot, new SubjectId("settlement:one"), "minecraft:wheat", 64, "test", List.of());
        CustodyAccount custody = new CustodyAccount(account, new ResourceCustody.Container(container), Map.of(lot, 64), Map.of());
        PhysicalStackBinding binding = new PhysicalStackBinding(new SubjectId("binding:container-depot"), account,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 0)), epoch, "minecraft:wheat", Map.of(lot, 64), Map.of());
        return new FungibleResourceLedger(Map.of(lot, resourceLot), Map.of(), Map.of(account, custody), Map.of(binding.id(), binding));
    }
}
