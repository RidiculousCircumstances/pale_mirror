package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;

/** The economic owner persists its accepted plans separately from money and stock. */
final class SettlementEconomicPlanCodec {
    static void write(DataOutputStream out, SettlementEconomicPlan plan) throws IOException {
        out.writeLong(plan.revision()); out.writeLong(plan.inputRevision()); out.writeLong(plan.atTick()); out.writeLong(plan.reviewAtTick());
        SupplyPlanCodec.write(out, plan.domestic()); SupplyPlanCodec.write(out, plan.complete());
        EconomicPlanCodec.quantities(out, plan.productionCeilings()); out.writeInt(plan.buyerVersions().size());
        for (var entry : new TreeMap<>(plan.buyerVersions()).entrySet()) { out.writeUTF(entry.getKey().value()); out.writeLong(entry.getValue()); }
        out.writeInt(plan.affectedProducts().size());
        for (var entry : new TreeMap<>(plan.affectedProducts()).entrySet()) { out.writeUTF(entry.getKey()); EconomicPlanCodec.strings(out, entry.getValue()); }
        out.writeInt(plan.needs().size());
        for (var entry : new TreeMap<>(plan.needs()).entrySet()) {
            out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue().consumption().numerator().toString());
            out.writeUTF(entry.getValue().consumption().denominator().toString()); out.writeLong(entry.getValue().immediate());
        }
        out.writeBoolean(plan.cultivation().isPresent());
        if (plan.cultivation().isPresent()) {
            var value = plan.cultivation().orElseThrow(); out.writeLong(value.desiredOutput()); out.writeLong(value.safetyOutput());
            out.writeLong(value.horizonTicks()); out.writeBoolean(value.safe());
        }
    }
    static SettlementEconomicPlan read(DataInputStream in) throws IOException {
        long revision = in.readLong(), input = in.readLong(), at = in.readLong(), review = in.readLong();
        var domestic = SupplyPlanCodec.read(in); var complete = SupplyPlanCodec.read(in); var ceilings = EconomicPlanCodec.quantities(in);
        var buyers = new HashMap<SubjectId, Long>();
        for (int n = EconomicPlanCodec.count(in, GoodsParticipantState.MAX_PARTICIPANTS); n > 0; n--)
            if (buyers.put(new SubjectId(in.readUTF()), in.readLong()) != null) throw new IllegalArgumentException("duplicate economic buyer version");
        var affected = new HashMap<String, Set<String>>();
        for (int n = EconomicPlanCodec.count(in, 256); n > 0; n--)
            if (affected.put(in.readUTF(), EconomicPlanCodec.strings(in)) != null) throw new IllegalArgumentException("duplicate economic dependency");
        var needs = new HashMap<String, io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.Need>();
        for (int n = EconomicPlanCodec.count(in, 256); n > 0; n--) {
            var item = in.readUTF();
            var rate = new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.Rate(
                    new java.math.BigInteger(in.readUTF()), new java.math.BigInteger(in.readUTF()));
            if (needs.put(item, new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.Need(rate, in.readLong())) != null)
                throw new IllegalArgumentException("duplicate accepted need");
        }
        var cultivation = in.readBoolean() ? Optional.of(new CultivationEconomicIntent(in.readLong(), in.readLong(), in.readLong(), in.readBoolean()))
                : Optional.<CultivationEconomicIntent>empty();
        return new SettlementEconomicPlan(revision, input, at, review, domestic, complete, ceilings, buyers, affected, cultivation, needs);
    }
    static void writeStates(DataOutputStream out, Map<SubjectId, SettlementEconomicPlanningState> states) throws IOException {
        out.writeInt(states.size());
        for (var entry : new TreeMap<>(states).entrySet()) {
            out.writeUTF(entry.getKey().value()); var state = entry.getValue(); out.writeLong(state.inputRevision());
            out.writeBoolean(state.accepted().isPresent()); if (state.accepted().isPresent()) write(out, state.accepted().orElseThrow());
            out.writeInt(state.affected().size());
            for (var reason : new TreeMap<>(state.affected()).entrySet()) {
                out.writeUTF(reason.getKey().wireTag()); EconomicPlanCodec.strings(out, reason.getValue());
            }
        }
    }
    static Map<SubjectId, SettlementEconomicPlanningState> readStates(DataInputStream in) throws IOException {
        var result = new HashMap<SubjectId, SettlementEconomicPlanningState>();
        for (int n = EconomicPlanCodec.count(in, GoodsParticipantState.MAX_PARTICIPANTS); n > 0; n--) {
            var id = new SubjectId(in.readUTF()); long revision = in.readLong();
            var accepted = in.readBoolean() ? Optional.of(read(in)) : Optional.<SettlementEconomicPlan>empty();
            var reasons = new EnumMap<SettlementEconomicPlanningState.Change, Set<String>>(SettlementEconomicPlanningState.Change.class);
            for (int k = EconomicPlanCodec.count(in, SettlementEconomicPlanningState.Change.values().length); k > 0; k--)
                if (reasons.put(SettlementEconomicPlanningState.Change.fromWireTag(in.readUTF()), EconomicPlanCodec.strings(in)) != null)
                    throw new IllegalArgumentException("duplicate economic invalidation cause");
            if (result.put(id, new SettlementEconomicPlanningState(revision, accepted, reasons)) != null)
                throw new IllegalArgumentException("duplicate participant economic state");
        }
        return Map.copyOf(result);
    }
    private SettlementEconomicPlanCodec() { }
}
