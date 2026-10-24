package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.io.*;
import java.math.BigInteger;
import java.util.*;

/** Persists accepted advice, never the solver's mutable search graph. */
final class SupplyPlanCodec {
    static void write(DataOutputStream out, SupplyPlanner.Plan plan) throws IOException {
        out.writeInt(plan.demands().size());
        for (var entry : new TreeMap<>(plan.demands()).entrySet()) {
            out.writeUTF(entry.getKey()); var value = entry.getValue();
            out.writeLong(value.purchase()); out.writeLong(value.production()); out.writeLong(value.recoveryProduction());
            out.writeLong(value.protectedInput()); out.writeLong(value.deadlineTicks());
            EconomicPlanCodec.strings(out, value.reasons().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
            out.writeBoolean(value.process().isPresent()); if (value.process().isPresent()) out.writeUTF(value.process().orElseThrow());
            out.writeLong(value.urgentPurchase());
        }
        out.writeInt(plan.reserves().size());
        for (var entry : new TreeMap<>(plan.reserves()).entrySet()) {
            out.writeUTF(entry.getKey()); var value = entry.getValue();
            out.writeLong(value.quantity()); out.writeLong(value.protectedQuantity());
            out.writeLong(value.reorderPoint()); out.writeLong(value.horizonTicks());
            out.writeUTF(value.consumption().numerator().toString()); out.writeUTF(value.consumption().denominator().toString());
            out.writeLong(value.timing().leadTicks()); out.writeUTF(value.timing().basis().name());
        }
        EconomicPlanCodec.quantities(out, plan.preferredPurchaseSpending());
        EconomicPlanCodec.quantities(out, plan.preferredPurchaseQuantities());
        EconomicPlanCodec.quantities(out, plan.inputRequirements());
        out.writeBoolean(plan.economy().isPresent()); if (plan.economy().isPresent()) EconomicPlanCodec.write(out, plan.economy().orElseThrow());
        EconomicPlanCodec.quantities(out, plan.backedRecipes());
    }
    static SupplyPlanner.Plan read(DataInputStream in) throws IOException {
        var demands = new HashMap<String, SupplyPlanner.Demand>();
        for (int n = EconomicPlanCodec.count(in, 256); n > 0; n--) {
            String item = in.readUTF(); long purchase = in.readLong(), production = in.readLong(), recovery = in.readLong();
            long protection = in.readLong(), deadline = in.readLong();
            var reasons = EconomicPlanCodec.strings(in).stream().map(SupplyPlanner.Reason::valueOf).collect(java.util.stream.Collectors.toSet());
            var process = in.readBoolean() ? Optional.of(in.readUTF()) : Optional.<String>empty();
            if (demands.put(item, new SupplyPlanner.Demand(purchase, production, recovery, protection, deadline, reasons, process, in.readLong())) != null)
                throw new IllegalArgumentException("duplicate accepted demand");
        }
        var reserves = new HashMap<String, SupplyReservePlanner.Target>();
        for (int n = EconomicPlanCodec.count(in, 256); n > 0; n--) {
            String item = in.readUTF(); long quantity = in.readLong(), protection = in.readLong(), reorder = in.readLong(), horizon = in.readLong();
            var rate = new SupplyReservePlanner.Rate(new BigInteger(in.readUTF()), new BigInteger(in.readUTF()));
            var timing = new SupplyReservePlanner.Timing(in.readLong(), SupplyReservePlanner.TimingBasis.valueOf(in.readUTF()));
            if (reserves.put(item, new SupplyReservePlanner.Target(quantity, protection, reorder, horizon, rate, timing)) != null)
                throw new IllegalArgumentException("duplicate accepted reserve");
        }
        var spending = EconomicPlanCodec.quantities(in); var quantities = EconomicPlanCodec.quantities(in); var inputs = EconomicPlanCodec.quantities(in);
        var economy = in.readBoolean() ? Optional.of(EconomicPlanCodec.read(in)) : Optional.<io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlan>empty();
        return new SupplyPlanner.Plan(demands, reserves, spending, quantities, inputs, economy, EconomicPlanCodec.quantities(in));
    }
    private SupplyPlanCodec() { }
}
