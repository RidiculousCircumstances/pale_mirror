package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.TacticalBehaviour;
import io.farfrontier.palemirror.frontier.v3.model.TacticalPlan;
import io.farfrontier.palemirror.frontier.v3.model.TacticalPlanPhase;
import io.farfrontier.palemirror.frontier.v3.model.TacticalPolicyDescriptor;
import io.farfrontier.palemirror.frontier.v3.model.TacticalPolicyRegistry;
import io.farfrontier.palemirror.frontier.v3.model.TacticalRole;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact durable tactical-plan envelope shared by snapshots and operation WAL payloads. */
final class TacticalPlanStateCodec {
    private TacticalPlanStateCodec() { }

    static void write(DataOutputStream output, TacticalPlan plan) throws IOException {
        subject(output, plan.id()); subject(output, plan.operationId()); subject(output, plan.authorityId());
        output.writeLong(plan.authorityEpoch()); output.writeLong(plan.planEpoch());
        string(output, TacticalPolicyRegistry.require(plan.policy()).id()); output.writeInt(plan.policy().version());
        output.writeByte(FrontierWireTags.tag(plan.phase()));
        output.writeByte(plan.objectiveIds().size()); for (SubjectId objective : plan.objectiveIds()) subject(output, objective);
        output.writeByte(plan.roles().size());
        for (Map.Entry<SubjectId, TacticalRole> entry : plan.roles().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            subject(output, entry.getKey()); output.writeByte(FrontierWireTags.tag(entry.getValue()));
        }
        output.writeByte(plan.permittedBehaviours().size());
        for (TacticalBehaviour behaviour : plan.permittedBehaviours()) output.writeByte(FrontierWireTags.tag(behaviour));
        position(output, plan.rendezvous()); position(output, plan.fallback()); position(output, plan.retreat());
    }

    static TacticalPlan read(DataInputStream input) throws IOException {
        SubjectId id = subject(input), operation = subject(input), authority = subject(input);
        long authorityEpoch = input.readLong(), planEpoch = input.readLong();
        TacticalPolicyDescriptor policy = new TacticalPolicyDescriptor(string(input), input.readInt()); TacticalPolicyRegistry.require(policy);
        TacticalPlanPhase phase = FrontierWireTags.require(TacticalPlanPhase.class, input.readUnsignedByte());
        List<SubjectId> objectives = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) objectives.add(subject(input));
        Map<SubjectId, TacticalRole> roles = new LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectId member = subject(input);
            if (roles.put(member, FrontierWireTags.require(TacticalRole.class, input.readUnsignedByte())) != null) {
                throw new IllegalArgumentException("duplicate tactical role member");
            }
        }
        List<TacticalBehaviour> behaviours = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            TacticalBehaviour behaviour = FrontierWireTags.require(TacticalBehaviour.class, input.readUnsignedByte());
            if (behaviours.contains(behaviour)) throw new IllegalArgumentException("duplicate tactical behaviour");
            behaviours.add(behaviour);
        }
        return new TacticalPlan(id, operation, authority, authorityEpoch, planEpoch, policy, phase, objectives, roles, behaviours,
                position(input), position(input), position(input));
    }

    private static void subject(DataOutputStream output, SubjectId id) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, id); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
    private static void string(DataOutputStream output, String value) throws IOException { FrontierWorldPayloadCodecs.writeString(output, value); }
    private static String string(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readString(input); }
    private static void position(DataOutputStream output, BlockPosition value) throws IOException { FrontierWorldPayloadCodecs.writePosition(output, value); }
    private static BlockPosition position(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readPosition(input); }
}
