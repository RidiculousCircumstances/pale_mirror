package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.SubjectIdHolder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.*;

/** Settlement stock, company, employment and production event wire codecs. */
final class SettlementEconomyPayloadCodecs {
    private SettlementEconomyPayloadCodecs() { }

    static PayloadCodec companyRegistered() { return new CompanyRegisteredCodec(); }
    static PayloadCodec employmentOpened() { return new EmploymentContractOpenedCodec(); }
    static PayloadCodec employmentTerminated() { return new EmploymentContractTerminatedCodec(); }
    static PayloadCodec resourceDeposited() { return new ResourceDepositedCodec(); }
    static PayloadCodec productionStarted() { return new ProductionStartedCodec(); }
    static PayloadCodec productionCompleted() { return new ProductionCompletedCodec(); }
    static PayloadCodec productionBlocked() { return new ProductionBlockedCodec(); }

    private static final class CompanyRegisteredCodec implements PayloadCodec {
        @Override public String type() { return "frontier.company_registered"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            Company company = ((CompanyRegistered) payload).company(); writeSubject(output, company.id()); writeSubject(output, company.settlementId());
            writeSubject(output, company.founderId()); output.writeByte(company.purpose().wireTag()); output.writeByte(company.status().wireTag()); output.writeLong(company.registeredAtTick());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectId id = readSubject(input).value(); SubjectId settlement = readSubject(input).value(); SubjectId founder = readSubject(input).value();
            int purpose = input.readUnsignedByte(); int status = input.readUnsignedByte(); long registeredAt = input.readLong();
            if (purpose >= CompanyPurpose.values().length || status >= CompanyStatus.values().length) throw new IllegalArgumentException("invalid company registration payload");
            return new CompanyRegistered(new Company(id, settlement, founder, FrontierWireTags.require(CompanyPurpose.class, purpose), FrontierWireTags.require(CompanyStatus.class, status), registeredAt));
        }); }
    } private static final class EmploymentContractOpenedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.employment_contract_opened"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            EmploymentContract contract = ((EmploymentContractOpened) payload).contract(); writeSubject(output, contract.id()); writeSubject(output, contract.companyId());
            writeSubject(output, contract.residentId()); output.writeLong(contract.invoicePerCompletedJob().raw()); output.writeLong(contract.wagePerCompletedJob().raw());
            output.writeByte(contract.status().wireTag()); output.writeLong(contract.openedAtTick()); output.writeLong(contract.completedJobs()); output.writeLong(contract.totalWagesPaid().raw());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectId id = readSubject(input).value(); SubjectId company = readSubject(input).value(); SubjectId resident = readSubject(input).value();
            long invoice = input.readLong(); long wage = input.readLong(); int status = input.readUnsignedByte(); long openedAt = input.readLong();
            long completed = input.readLong(); long totalWages = input.readLong();
            if (status >= EmploymentContractStatus.values().length) throw new IllegalArgumentException("invalid employment contract status");
            return new EmploymentContractOpened(new EmploymentContract(id, company, resident, new FixedScalar(invoice), new FixedScalar(wage),
                    FrontierWireTags.require(EmploymentContractStatus.class, status), openedAt, completed, new FixedScalar(totalWages)));
        }); }
    } private static final class EmploymentContractTerminatedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.employment_contract_terminated"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            EmploymentContractTerminated terminated = (EmploymentContractTerminated) payload;
            writeSubject(output, terminated.contractId()); writeSubject(output, terminated.residentId()); output.writeByte(terminated.reason().wireTag());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectId contract = readSubject(input).value(); SubjectId resident = readSubject(input).value(); int reason = input.readUnsignedByte();
            if (reason >= EmploymentTerminationReason.values().length) throw new IllegalArgumentException("invalid employment termination reason");
            return new EmploymentContractTerminated(contract, resident, FrontierWireTags.require(EmploymentTerminationReason.class, reason));
        }); }
    } private static final class ResourceDepositedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.resource_deposited"; } @Override public byte[] encode(FrontierPayload payload) {
            ResourceDeposited deposited = (ResourceDeposited) payload;
            return encodeProduction(output -> {
                ExactItemStack item = deposited.item();
                if (!(item.custody() instanceof InventoryCustody.ContainerSlot slot)) {
                    throw new IllegalArgumentException("resource deposit must have container custody");
                }
                writeSubject(output, item.id()); writeSubject(output, item.economicOwnerId()); writeString(output, item.itemKind()); output.writeByte(item.count());
                writeSubject(output, slot.containerId()); output.writeByte(slot.slot());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                SubjectIdHolder item = readSubject(input); SubjectIdHolder owner = readSubject(input); String kind = readString(input); int count = input.readUnsignedByte();
                SubjectIdHolder container = readSubject(input); int slot = input.readUnsignedByte();
                return new ResourceDeposited(new ExactItemStack(item.value(), owner.value(), kind, count,
                        new InventoryCustody.ContainerSlot(container.value(), slot)));
            });
        }
    } private static final class ProductionStartedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_started"; } @Override public byte[] encode(FrontierPayload payload) {
            ProductionStarted started = (ProductionStarted) payload;
            return encodeProduction(output -> { writeJob(output, started.job()); writeSubject(output, started.inputItemId()); writeProductionInputHold(output, started.job().inputHold()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                ProductionJob job = readJob(input); SubjectIdHolder item = readSubject(input);
                job = job.withInputHold(readProductionInputHold(input, job.consumedItemId()));
                return new ProductionStarted(job, item.value());
            });
        }
    } private static final class ProductionCompletedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_completed"; } @Override public byte[] encode(FrontierPayload payload) {
            ProductionCompleted completed = (ProductionCompleted) payload;
            return encodeProduction(output -> {
                writeSubject(output, completed.jobId()); writeSubject(output, completed.output().id()); writeSubject(output, completed.output().economicOwnerId()); writeString(output, completed.output().itemKind());
                output.writeByte(completed.output().count());
                if (!(completed.output().custody() instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("production output must have container custody");
                writeSubject(output, slot.containerId()); output.writeByte(slot.slot());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                SubjectIdHolder job = readSubject(input); SubjectIdHolder output = readSubject(input); SubjectIdHolder owner = readSubject(input); String kind = readString(input); int count = input.readUnsignedByte();
                SubjectIdHolder container = readSubject(input); int slot = input.readUnsignedByte();
                return new ProductionCompleted(job.value(), new ExactItemStack(output.value(), owner.value(), kind, count, new InventoryCustody.ContainerSlot(container.value(), slot)));
            });
        }
    } private static final class ProductionBlockedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_blocked"; } @Override public byte[] encode(FrontierPayload payload) {
            ProductionBlocked blocked = (ProductionBlocked) payload;
            return encodeProduction(output -> { writeSubject(output, blocked.settlementId()); writeSubject(output, blocked.facilityId()); writeSubject(output, blocked.workId()); writeSubject(output, blocked.taskId());
                    output.writeByte(blocked.reason().wireTag()); writeDiagnosticTuple(output, blocked.diagnostic()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                SubjectIdHolder settlement = readSubject(input); SubjectIdHolder facility = readSubject(input); SubjectIdHolder work = readSubject(input); SubjectIdHolder task = readSubject(input);
                int ordinal = input.readUnsignedByte();
                if (ordinal >= ProductionBlockReason.values().length) throw new IllegalArgumentException("unknown production block reason");
                return new ProductionBlocked(settlement.value(), facility.value(), work.value(), task.value(), FrontierWireTags.require(ProductionBlockReason.class, ordinal), readDiagnosticTuple(input));
            });
        }
    }

    private static void writeJob(DataOutputStream output, ProductionJob job) throws IOException {
        writeSubject(output, job.id()); writeSubject(output, job.taskId()); writeSubject(output, job.settlementId()); writeSubject(output, job.facilityId()); writeSubject(output, job.workerId());
        writeSubject(output, job.consumedItemId()); writeSubject(output, job.outputItemId()); writeString(output, job.outputItemKind()); output.writeByte(job.outputCount());
        ProductionWorkProgressStateCodec.write(output, job.workProgress()); TraversalTopologyStateCodec.write(output, job.workTraversal()); output.writeShort(job.traversalCursor());
        BakeryWorkStateCodec.write(output, job.bakeryWork()); StationApproachStateCodec.write(output, job.spatial());
    }
    private static ProductionJob readJob(DataInputStream input) throws IOException {
        SubjectId id = readSubject(input).value(); SubjectId task = readSubject(input).value(); SubjectId settlement = readSubject(input).value(); SubjectId facility = readSubject(input).value();
        SubjectId worker = readSubject(input).value(); SubjectId consumed = readSubject(input).value(); SubjectId output = readSubject(input).value();
        String outputKind = readString(input); int outputCount = input.readUnsignedByte();
        ProductionWorkProgress progress = ProductionWorkProgressStateCodec.read(input); TraversalTopology traversal = TraversalTopologyStateCodec.read(input);
        int cursor = input.readUnsignedShort();
        return new ProductionJob(id, task, settlement, facility, worker, consumed, new ProductionInputHold.Materialized(consumed), output, outputKind, outputCount,
                progress, traversal, cursor, BakeryWorkStateCodec.read(input), StationApproachStateCodec.read(input));
    }
    private static void writeProductionInputHold(DataOutputStream output, ProductionInputHold hold) throws IOException {
        if (hold instanceof ProductionInputHold.Materialized) { output.writeByte(0); return; }
        if (hold instanceof ProductionInputHold.Cold cold) {
            ExactItemStack item = cold.item();
            output.writeByte(1); writeSubject(output, item.economicOwnerId()); writeString(output, item.itemKind()); output.writeByte(item.count());
            if (!(item.custody() instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("cold production input must retain its depot slot");
            writeSubject(output, slot.containerId()); output.writeByte(slot.slot());
        } else if (hold instanceof ProductionInputHold.FungibleCold cold) {
            output.writeByte(4); writeSubject(output, cold.accountId()); writeSubject(output, cold.claimId());
            writeProductionInputLots(output, cold.inputLots());
        } else if (hold instanceof ProductionInputHold.FungibleBound bound) {
            output.writeByte(5); writeSubject(output, bound.accountId()); writeSubject(output, bound.claimId()); output.writeLong(bound.authorityEpoch());
            writeProductionInputLots(output, bound.inputLots());
        } else throw new IllegalArgumentException("unknown production input hold");
    }
    private static ProductionInputHold readProductionInputHold(DataInputStream input, SubjectId itemId) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new ProductionInputHold.Materialized(itemId);
            case 1 -> new ProductionInputHold.Cold(new ExactItemStack(itemId, readSubject(input).value(), readString(input), input.readUnsignedByte(),
                    new InventoryCustody.ContainerSlot(readSubject(input).value(), input.readUnsignedByte())));
            case 2 -> new ProductionInputHold.FungibleCold(itemId, readSubject(input).value(), readSubject(input).value());
            case 3 -> new ProductionInputHold.FungibleBound(itemId, readSubject(input).value(), readSubject(input).value(), input.readLong());
            case 4 -> new ProductionInputHold.FungibleCold(itemId, readSubject(input).value(), readSubject(input).value(), readProductionInputLots(input));
            case 5 -> new ProductionInputHold.FungibleBound(itemId, readSubject(input).value(), readSubject(input).value(), input.readLong(), readProductionInputLots(input));
            default -> throw new IllegalArgumentException("unknown production input hold");
        };
    }
    private static void writeProductionInputLots(DataOutputStream output, Map<SubjectId, Integer> lots) throws IOException {
        output.writeByte(lots.size());
        for (var entry : lots.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeSubject(output, entry.getKey()); output.writeByte(entry.getValue());
        }
    }
    private static Map<SubjectId, Integer> readProductionInputLots(DataInputStream input) throws IOException {
        int count = input.readUnsignedByte();
        if (count < 1 || count > 64) throw new IllegalArgumentException("invalid production input lot count");
        Map<SubjectId, Integer> lots = new java.util.LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            if (lots.put(readSubject(input).value(), input.readUnsignedByte()) != null) {
                throw new IllegalArgumentException("duplicate production input lot");
            }
        }
        return lots;
    }
}
