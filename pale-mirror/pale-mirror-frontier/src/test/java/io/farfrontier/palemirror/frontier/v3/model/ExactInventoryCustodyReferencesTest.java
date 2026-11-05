package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** Changed foreign endpoints must invalidate unchanged holder/binding declarations. */
class ExactInventoryCustodyReferencesTest {
    private static final SubjectId OWNER = id("owner"), CHEST = id("chest"), ACCOUNT = id("account"), LOT = id("lot"), BINDING = id("binding");

    @Test void toolWearKeepsReferenceFactsButLostTitleAndForgedKeysStillFail() {
        var tool = new ExactItemStack(id("wear-tool"), OWNER, "minecraft:iron_pickaxe", 1,
                new InventoryCustody.Actor(id("actor")), new ItemCondition(250, 0));
        var before = inventory(Map.of(), Map.of(tool.id(), tool), Map.of(), FungibleResourceLedger.empty());
        var damaged = tool.withCondition(tool.condition().wear(1));
        var after = inventory(Map.of(), Map.of(tool.id(), damaged), Map.of(), FungibleResourceLedger.empty());
        rejects(() -> after.withEconomics(new EconomicLedger(Map.of())));
        rejects(() -> inventory(Map.of(), Map.of(id("forged-key"), damaged), Map.of(), FungibleResourceLedger.empty()));
        var carried = inventory(Map.of(), Map.of(), Map.of(), resources(new ResourceCustody.Actor(id("actor")), -1));
        rejects(() -> carried.withEconomics(new EconomicLedger(Map.of())));
        before.validateComplete(); after.validateComplete(); carried.validateComplete();
        assertEquals(1, after.items().get(tool.id()).condition().damage());
    }

    @Test void unchangedBindingsFollowChangedCapacityPortsAndExactOccupancy() {
        var before = inventory(Map.of(CHEST, new ContainerRecord(CHEST, OWNER, 4)), Map.of(), Map.of(),
                resources(new ResourceCustody.Container(CHEST), 3));
        rejects(() -> inventory(Map.of(CHEST, new ContainerRecord(CHEST, OWNER, 2)), Map.of(), Map.of(), before.fungibleResources()));
        rejects(() -> inventory(Map.of(), Map.of(), Map.of(), before.fungibleResources()));
        var item = new ExactItemStack(id("tool"), OWNER, "minecraft:iron_pickaxe", 1, new InventoryCustody.ContainerSlot(CHEST, 3));
        rejects(() -> inventory(before.containers(), Map.of(item.id(), item), Map.of(), before.fungibleResources()));
        before.validateComplete();

        var machine = inventory(Map.of(CHEST, station(0, 1)), Map.of(), Map.of(), resources(new ResourceCustody.Container(CHEST, ContainerSectionId.INPUT), 0));
        rejects(() -> inventory(Map.of(CHEST, station(1, 0)), Map.of(), Map.of(), machine.fungibleResources()));
        rejects(() -> inventory(machine.containers(), Map.of(), Map.of(), resources(new ResourceCustody.Container(CHEST, ContainerSectionId.OUTPUT), 0)));
        machine.validateComplete(); before.validateComplete();
    }

    @Test void custodyUniquenessAndCargoBacklinksSurviveFailedBranches() {
        var before = inventory(Map.of(CHEST, new ContainerRecord(CHEST, OWNER, 4)), Map.of(), Map.of(), resources(new ResourceCustody.Container(CHEST), -1));
        var ledger = before.fungibleResources(); var first = ledger.accounts().get(ACCOUNT);
        var split = new FungibleResourceLedger(ledger.lots(), Map.of(), Map.of(
                ACCOUNT, first.withQuantities(Map.of(LOT, 2), Map.of()), id("other-account"),
                new CustodyAccount(id("other-account"), first.custody(), Map.of(LOT, 2), Map.of())), Map.of());
        rejects(() -> before.withFungibleResources(split));
        var cargo = CargoBatch.fungible(id("cargo"), OWNER);
        var carried = inventory(Map.of(), Map.of(), Map.of(cargo.id(), cargo), resources(new ResourceCustody.Cargo(cargo.id()), -1));
        rejects(() -> inventory(Map.of(), Map.of(), Map.of(), carried.fungibleResources()));
        rejects(() -> inventory(Map.of(), Map.of(), carried.cargo(), ledger));
        rejects(() -> inventory(Map.of(), Map.of(), carried.cargo(), FungibleResourceLedger.empty()));
        carried.validateComplete(); before.validateComplete();
        assertEquals(4, before.withFungibleResources(ledger).fungibleResources().lots().get(LOT).quantity());
    }

    @Test void mutableIngressCannotChangeRetainedEndpointEvidence() {
        var containers = new HashMap<SubjectId, ContainerRecord>();
        containers.put(CHEST, new ContainerRecord(CHEST, OWNER, 4));
        var resources = resources(new ResourceCustody.Container(CHEST), 3);
        var before = inventory(containers, Map.of(), Map.of(), resources);
        containers.put(CHEST, new ContainerRecord(CHEST, OWNER, 2));
        rejects(() -> inventory(containers, Map.of(), Map.of(), resources));
        before.validateComplete(); assertEquals(4, before.containers().get(CHEST).slotCount());
        containers.put(CHEST, new ContainerRecord(CHEST, OWNER, 4));
        inventory(containers, Map.of(), Map.of(), resources).validateComplete();
    }

    private static void rejects(Supplier<ExactInventory> candidate) {
        var local = assertThrows(IllegalArgumentException.class, candidate::get);
        var full = assertThrows(IllegalArgumentException.class, () -> ImmutableInvariantCheck.full(candidate));
        assertEquals(full.getMessage(), local.getMessage());
    }
    private static ExactInventory inventory(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ExactItemStack> items,
                                           Map<SubjectId, CargoBatch> cargo, FungibleResourceLedger resources) {
        var surfaces = new HashMap<SubjectId, ContainerSurface>();
        containers.keySet().forEach(id -> surfaces.put(id, new ContainerSurface(id, new BlockPosition(0, 64, 0), ContainerSurfaceStatus.UNMATERIALIZED)));
        var economics = new EconomicLedger(Map.of(OWNER, new EconomicAccount(OWNER, EconomicOwnerKind.SETTLEMENT_TREASURY,
                EconomicAccountStatus.ACTIVE, io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO, io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO)));
        return new ExactInventory(containers, items, cargo, Map.of(), Map.of(), Map.of(), surfaces, economics, resources);
    }
    private static FungibleResourceLedger resources(ResourceCustody custody, int slot) {
        var lots = Map.of(LOT, new ResourceLot(LOT, OWNER, "minecraft:bread", 4, "fixture", List.of()));
        var accounts = Map.of(ACCOUNT, new CustodyAccount(ACCOUNT, custody, Map.of(LOT, 4), Map.of()));
        var bindings = slot < 0 ? Map.<SubjectId, PhysicalStackBinding>of() : Map.of(BINDING,
                new PhysicalStackBinding(BINDING, ACCOUNT, new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(CHEST, slot)),
                        1, "minecraft:bread", Map.of(LOT, 4), Map.of()));
        return new FungibleResourceLedger(lots, Map.of(), accounts, bindings);
    }
    private static ContainerRecord station(int input, int output) {
        var spec = new ProductionStationSpec(id("station"), id("facility"), CHEST, ProductionEquipment.BENCH,
                ProductionStationSpec.Capability.BAKING, SurfaceAnchor.at(0, 64, 0), SurfaceAnchor.at(1, 64, 0), FacilityFacing.WEST, input, output, 2);
        return new ContainerRecord(CHEST, OWNER, 3, ContainerPurpose.PRODUCTION_STATION, Optional.of(spec));
    }
    private static SubjectId id(String suffix) { return new SubjectId("test:inventory-" + suffix); }
}
