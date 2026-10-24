package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlan;
import java.io.*;
import java.util.*;

/** Explicit bounded decision grammar shared by snapshots and publication events. */
final class EconomicPlanCodec {
    static void write(DataOutputStream out, EconomicPlan plan) throws IOException {
        out.writeLong(plan.horizonTicks()); quantities(out, plan.domesticTargets());
        quantities(out, plan.additionalProduction()); quantities(out, plan.purchaseBudgets());
        out.writeLong(plan.retainedLiquidity()); out.writeLong(plan.unfundedRequirements()); out.writeLong(plan.contractReceivables());
        out.writeInt(plan.checkpoints().size());
        for (var point : plan.checkpoints()) {
            out.writeLong(point.afterTicks()); quantities(out, point.netRequirements());
            out.writeLong(point.importCost()); out.writeLong(point.cashWithoutNewSales());
        }
        out.writeInt(plan.exports().size());
        for (var export : plan.exports()) {
            out.writeUTF(export.item()); out.writeLong(export.existingStock());
            out.writeLong(export.additionalProduction()); out.writeLong(export.conditionalRevenue());
        }
        strings(out, plan.constraints().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        quantities(out, plan.purchasePrices()); strings(out, plan.fundedRestarts()); quantities(out, plan.saleableStock());
    }
    static EconomicPlan read(DataInputStream in) throws IOException {
        long horizon = in.readLong(); var targets = quantities(in); var production = quantities(in); var budgets = quantities(in);
        long retained = in.readLong(), unfunded = in.readLong(), receivables = in.readLong();
        var points = new ArrayList<EconomicPlan.Checkpoint>();
        for (int n = count(in, 4096); n > 0; n--)
            points.add(new EconomicPlan.Checkpoint(in.readLong(), quantities(in), in.readLong(), in.readLong()));
        var exports = new ArrayList<EconomicPlan.Export>();
        for (int n = count(in, 256); n > 0; n--)
            exports.add(new EconomicPlan.Export(in.readUTF(), in.readLong(), in.readLong(), in.readLong()));
        var constraints = strings(in).stream().map(EconomicPlan.Constraint::valueOf).collect(java.util.stream.Collectors.toSet());
        return new EconomicPlan(horizon, targets, production, budgets, retained, unfunded, receivables,
                points, exports, constraints, quantities(in), strings(in), quantities(in));
    }
    static void quantities(DataOutputStream out, Map<String, Long> values) throws IOException {
        out.writeInt(values.size());
        for (var entry : new TreeMap<>(values).entrySet()) { out.writeUTF(entry.getKey()); out.writeLong(entry.getValue()); }
    }
    static Map<String, Long> quantities(DataInputStream in) throws IOException {
        var result = new HashMap<String, Long>();
        for (int n = count(in, 4096); n > 0; n--) {
            if (result.put(in.readUTF(), in.readLong()) != null) throw new IllegalArgumentException("duplicate economic quantity");
        }
        return Map.copyOf(result);
    }
    static void strings(DataOutputStream out, Set<String> values) throws IOException {
        out.writeInt(values.size()); for (var value : new TreeSet<>(values)) out.writeUTF(value);
    }
    static Set<String> strings(DataInputStream in) throws IOException {
        var result = new HashSet<String>();
        for (int n = count(in, 256); n > 0; n--)
            if (!result.add(in.readUTF())) throw new IllegalArgumentException("duplicate economic tag");
        return Set.copyOf(result);
    }
    static int count(DataInputStream in, int maximum) throws IOException {
        int n = in.readInt(); if (n < 0 || n > maximum) throw new IllegalArgumentException("invalid economic decision size"); return n;
    }
    private EconomicPlanCodec() { }
}
