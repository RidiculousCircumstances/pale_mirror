package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;
import java.io.*;
import java.util.*;

final class ForestryPayloadCodecs {
    private ForestryPayloadCodecs() { }
    static PayloadCodecs create() { return new PayloadCodecs(List.of(codec(ForestObservation.Generated.TYPE), codec(ForestObservation.Withdrawn.TYPE),
            codec(ForestrySourceBoundary.TYPE), codec(ForestryWorkStarted.TYPE), codec(ForestryWorkAdvanced.TYPE), codec(ForestryPhysicalEvent.Prepared.TYPE),
            codec(ForestryPhysicalEvent.Cancelled.TYPE), codec(ForestryPhysicalEvent.Observed.TYPE), codec(ForestryPhysicalEvent.HandBoundary.TYPE),
            codec(ForestryPhysicalEvent.Retired.TYPE), codec(ForestryPhysicalEvent.DeathSettled.TYPE))); }
    private static PayloadCodec codec(String type) {
        return new PayloadCodec() {
            @Override public String type() { return type; }
            @Override public byte[] encode(FrontierPayload payload) {
                if (!payload.type().equals(type)) throw new IllegalArgumentException("foreign forest payload codec");
                try { var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) {
                    switch (payload) {
                        case ForestObservation.Generated generated -> {
                            out.writeUTF(generated.configuration()); out.writeInt(generated.trees().size());
                            for (var tree : generated.trees()) ForestStateCodec.writeTree(out, tree);
                        }
                        case ForestObservation.Withdrawn withdrawn -> {
                            out.writeInt(withdrawn.withdrawals().size());
                            for (var loss : withdrawn.withdrawals()) {
                                ForestStateCodec.writeTree(out, loss.declaration()); out.writeLong(loss.expectedRevision()); out.writeLong(loss.sourceEpoch()); out.writeLong(loss.replicaRevision());
                                out.writeInt(loss.logs().size()); for (int log : loss.logs()) out.writeInt(log);
                            }
                        }
                        case ForestrySourceBoundary boundary -> {
                            ForestryWorkCodec.reference(out, boundary.tree()); out.writeByte(boundary.operation().wireTag());
                            out.writeLong(boundary.expectedEpoch()); out.writeLong(boundary.expectedReplicaRevision()); out.writeUTF(boundary.fingerprint());
                        }
                        case ForestryWorkStarted start -> {
                            ForestryWorkCodec.write(out, start.work()); ForestStateCodec.writeTree(out, start.declaration());
                            out.writeLong(start.expectedOrdinal()); route(out, start.toolRoute()); route(out, start.sourceRoute()); route(out, start.storageRoute());
                        }
                        case ForestryWorkAdvanced step -> {
                            out.writeUTF(step.job().value()); out.writeLong(step.expectedRevision()); out.writeByte(step.operation().wireTag());
                            if (step.selection().isPresent()) switch (step.selection().orElseThrow()) {
                                case ForestryWorkAdvanced.TreeSelection tree -> {
                                    out.writeByte(1); ForestStateCodec.writeTree(out, tree.declaration()); ForestryWorkCodec.writeTarget(out, tree.target());
                                    route(out, tree.approach()); ShipmentStateCodec.endpoint(out, tree.storage()); route(out, tree.delivery());
                                }
                                case ForestryWorkAdvanced.StorageSelection storage -> {
                                    out.writeByte(2); ShipmentStateCodec.endpoint(out, storage.storage()); route(out, storage.approach());
                                }
                            } else out.writeByte(0);
                        }
                        case ForestryPhysicalEvent.Prepared prepared -> { out.writeUTF(prepared.job().value()); ForestryWorkCodec.writeStep(out, prepared.step()); }
                        case ForestryPhysicalEvent.Cancelled cancelled -> { out.writeUTF(cancelled.job().value()); ForestryWorkCodec.writeStep(out, cancelled.step()); }
                        case ForestryPhysicalEvent.Observed observed -> outcome(out, observed);
                        case ForestryPhysicalEvent.Retired retired -> { outcome(out, retired.effect()); out.writeBoolean(retired.applied()); }
                        case ForestryPhysicalEvent.DeathSettled settled -> {
                            out.writeUTF(settled.identity().body().actorId().value()); out.writeLong(settled.identity().body().physicalEpoch());
                            ActorExecutionStateCodec.writeId(out, settled.identity().execution()); out.writeLong(settled.expectedRevision());
                        }
                        case ForestryPhysicalEvent.HandBoundary hand -> {
                            out.writeUTF(hand.job().value()); out.writeUTF(hand.identity().body().actorId().value()); out.writeLong(hand.identity().body().physicalEpoch());
                            ActorExecutionStateCodec.writeId(out, hand.identity().execution()); out.writeByte(hand.boundary().wireTag());
                            PhysicalObservationStackCodec.writeStacks(out, List.of(hand.stack()));
                        }
                        default -> throw new IllegalArgumentException("forest codec rejects undeclared payload");

                    }
                } return bytes.toByteArray(); }
                catch (IOException error) { throw new IllegalStateException("in-memory forest encoding failed", error); }
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    FrontierPayload payload;
                    if (type.equals(ForestObservation.Generated.TYPE)) {
                        String configuration = in.readUTF(); var trees = new ArrayList<TreeDescriptor>();
                        for (int i = 0, n = count(in, ForestObservation.MAX_TREES); i < n; i++) trees.add(ForestStateCodec.readTree(in));
                        payload = new ForestObservation.Generated(configuration, trees);
                    } else if (type.equals(ForestObservation.Withdrawn.TYPE)) {
                        var losses = new ArrayList<ForestObservation.Withdrawal>();
                        for (int i = 0, n = count(in, ForestObservation.MAX_TREES); i < n; i++) {
                            var tree = ForestStateCodec.readTree(in); long revision = in.readLong(), epoch = in.readLong(), replica = in.readLong(); var logs = new ArrayList<Integer>();
                            for (int j = 0, m = count(in, TreeBlocks.MAX_LOGS); j < m; j++) logs.add(in.readInt());
                            losses.add(new ForestObservation.Withdrawal(tree, revision, epoch, replica, logs));
                        }
                        payload = new ForestObservation.Withdrawn(losses);
                    } else if (type.equals(ForestrySourceBoundary.TYPE)) {
                        payload = new ForestrySourceBoundary(ForestryWorkCodec.reference(in), ForestrySourceBoundary.Operation.decode(in.readUnsignedByte()),
                                in.readLong(), in.readLong(), in.readUTF());
                    } else if (type.equals(ForestryWorkStarted.TYPE)) {
                        payload = new ForestryWorkStarted(ForestryWorkCodec.read(in), ForestStateCodec.readTree(in), in.readLong(), route(in), route(in), route(in));
                    } else if (type.equals(ForestryWorkAdvanced.TYPE)) {
                        var job = new SubjectId(in.readUTF()); long revision = in.readLong(); var operation = ForestryWorkAdvanced.Operation.decode(in.readUnsignedByte());
                        Optional<ForestryWorkAdvanced.Selection> selection = switch (in.readUnsignedByte()) {
                            case 0 -> Optional.empty();
                            case 1 -> Optional.of(new ForestryWorkAdvanced.TreeSelection(ForestStateCodec.readTree(in), ForestryWorkCodec.readTarget(in),
                                    route(in), ShipmentStateCodec.endpoint(in), route(in)));
                            case 2 -> Optional.of(new ForestryWorkAdvanced.StorageSelection(ShipmentStateCodec.endpoint(in), route(in)));
                            default -> throw new IllegalArgumentException("unknown forest selection tag");
                        };
                        payload = new ForestryWorkAdvanced(job, revision, operation, selection);
                    } else if (type.equals(ForestryPhysicalEvent.Prepared.TYPE)) {
                        payload = new ForestryPhysicalEvent.Prepared(new SubjectId(in.readUTF()), ForestryWorkCodec.readStep(in));
                    } else if (type.equals(ForestryPhysicalEvent.Cancelled.TYPE)) {
                        payload = new ForestryPhysicalEvent.Cancelled(new SubjectId(in.readUTF()), ForestryWorkCodec.readStep(in));
                    } else if (type.equals(ForestryPhysicalEvent.Observed.TYPE)) {
                        payload = outcome(in);
                    } else if (type.equals(ForestryPhysicalEvent.Retired.TYPE)) {
                        payload = new ForestryPhysicalEvent.Retired(outcome(in), in.readBoolean());
                    } else if (type.equals(ForestryPhysicalEvent.DeathSettled.TYPE)) {
                        var body = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(new SubjectId(in.readUTF()), in.readLong());
                        payload = new ForestryPhysicalEvent.DeathSettled(new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(
                                body, ActorExecutionStateCodec.readId(in)), in.readLong());
                    } else if (type.equals(ForestryPhysicalEvent.HandBoundary.TYPE)) {
                        var job = new SubjectId(in.readUTF()); var body = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(new SubjectId(in.readUTF()), in.readLong());
                        var identity = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(body, ActorExecutionStateCodec.readId(in));
                        var boundary = ForestryPhysicalEvent.HandBoundary.Boundary.decode(in.readUnsignedByte()); var stacks = PhysicalObservationStackCodec.readStacks(in);
                        if (stacks.size() != 1) throw new IllegalArgumentException("forest hand boundary requires its single observed stack");
                        payload = new ForestryPhysicalEvent.HandBoundary(job, identity, boundary, stacks.getFirst());
                    } else throw new IllegalArgumentException("unregistered forest codec");
                    if (in.available() != 0) throw new IllegalArgumentException("trailing forest payload bytes"); return payload;
                } catch (IOException error) { throw new IllegalArgumentException("truncated forest payload", error); }
            }
        };
    }
    private static void outcome(DataOutputStream out, ForestryPhysicalEvent.Observed observed) throws IOException {
        out.writeUTF(observed.job().value()); ForestryWorkCodec.writeStep(out, observed.step());
        PhysicalObservationStackCodec.writeStacks(out, observed.remainingSource()); PhysicalObservationStackCodec.writeStacks(out, observed.destination());
        out.writeBoolean(observed.externalSuccessor().isPresent());
        if (observed.externalSuccessor().isPresent()) ExtractionSiteStateCodec.writeBlock(out, observed.externalSuccessor().orElseThrow());
    }
    private static ForestryPhysicalEvent.Observed outcome(DataInputStream in) throws IOException {
        return new ForestryPhysicalEvent.Observed(new SubjectId(in.readUTF()), ForestryWorkCodec.readStep(in),
                PhysicalObservationStackCodec.readStacks(in), PhysicalObservationStackCodec.readStacks(in),
                in.readBoolean() ? Optional.of(ExtractionSiteStateCodec.readBlock(in)) : Optional.empty());
    }
    private static int count(DataInputStream in, int maximum) throws IOException {
        int n = in.readInt(); if (n < 1 || n > maximum) throw new IllegalArgumentException("unbounded forest observation"); return n;
    }
    private static void route(DataOutputStream out, Optional<PedestrianRouteReceipt> receipt) throws IOException {
        out.writeBoolean(receipt.isPresent()); if (receipt.isEmpty()) return;
        var route = receipt.orElseThrow().route(); out.writeInt(route.size());
        for (var point : route) FrontierWorldStateCodec.writePosition(out, point.support());
    }
    private static Optional<PedestrianRouteReceipt> route(DataInputStream in) throws IOException {
        if (!in.readBoolean()) return Optional.empty();
        int n = count(in, 65536); var points = new ArrayList<SurfaceAnchor>(n);
        for (int i = 0; i < n; i++) points.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
        return Optional.of(new PedestrianRouteReceipt(points));
    }
}
