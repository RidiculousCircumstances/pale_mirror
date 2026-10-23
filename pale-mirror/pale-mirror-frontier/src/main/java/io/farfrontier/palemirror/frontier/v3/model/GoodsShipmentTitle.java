package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Build a title receipt from an actual receiving custody; commercial validation remains authoritative. */
public final class GoodsShipmentTitle {
    private GoodsShipmentTitle() { }
    public static GoodsTitleTransfer received(FrontierWorldState state, Shipment shipment, GoodsTradeContract contract,
            SubjectId receipt, SubjectId claim, Map<SubjectId, Integer> portions, boolean collection) {
        if (shipment.cargo() instanceof ShipmentCargo.Individual cargo) {
            var item = state.inventory().items().get(cargo.itemId());
            if (!cargo.matches(item) || !portions.equals(Map.of(cargo.itemId(), 1)))
                throw new IllegalArgumentException("individual title receipt lost its promised object");
            return new IndividualTitleTransfer(claim, item, contract.buyer().id());
        }
        var ledger = state.inventory().fungibleResources();
        var account = ledger.accounts().get(collection ? shipment.carriedAccountId() : shipment.receivingAccountId());
        if (account == null || account.claimQuantities().getOrDefault(claim, 0) != portions.values().stream().mapToInt(Integer::intValue).sum())
            throw new IllegalArgumentException("transport reception lost its received allocation");
        var children = new LinkedHashMap<SubjectId, SubjectId>();
        for (var portion : portions.entrySet()) if (ledger.lots().get(portion.getKey()).quantity() > portion.getValue())
            children.put(portion.getKey(), new SubjectId("lot:goods-" + UUID.nameUUIDFromBytes((receipt.value() + "\n" + contract.revision()
                    + "\n" + portion.getKey().value()).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        return new ResourceTitleTransfer(account.id(), claim, contract.seller().id(), contract.buyer().id(), portions, children);
    }
}
