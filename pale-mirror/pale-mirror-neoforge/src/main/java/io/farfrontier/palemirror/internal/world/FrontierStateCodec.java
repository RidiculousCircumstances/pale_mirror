package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.FrontierFacility;
import io.farfrontier.palemirror.frontier.FrontierOperation;
import io.farfrontier.palemirror.frontier.FrontierHive;
import io.farfrontier.palemirror.frontier.FrontierHiveOrgan;
import io.farfrontier.palemirror.frontier.FrontierHiveOrganKind;
import io.farfrontier.palemirror.frontier.FrontierMorphogenesisProject;
import io.farfrontier.palemirror.frontier.FrontierBioformKind;
import io.farfrontier.palemirror.frontier.FrontierProfile;
import io.farfrontier.palemirror.frontier.FrontierResource;
import io.farfrontier.palemirror.frontier.FrontierStateHydration;
import io.farfrontier.palemirror.frontier.FrontierWorldState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** NBT adapter for Frontier state. Canonical identity is regenerated and checked by domain hydration. */
public final class FrontierStateCodec {
    /** Format 15 adopts the reference campaign terminal spelling COMPLETE. */
    private static final int FORMAT = 15;
    private FrontierStateCodec() { }
    public static CompoundTag write(FrontierWorldState state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", FORMAT);
        tag.putString("profile", state.profile().id());
        tag.putLong("seed", state.seed());
        tag.putLong("day", state.day());
        ListTag settlements = new ListTag();
        state.settlements().forEach(value -> {
            CompoundTag settlement = new CompoundTag();
            settlement.putString("id", value.id());
            FrontierCivicNbt.write(settlement, value);
            CompoundTag stocks = new CompoundTag();
            value.stocks().forEach((resource, amount) -> stocks.putLong(resource.name(), amount));
            settlement.put("stocks", stocks);
            settlements.add(settlement);
        });
        tag.put("settlements", settlements);
        ListTag residents = new ListTag();
        state.residents().forEach(value -> {
            CompoundTag resident = new CompoundTag();
            resident.putString("id", value.id());
            resident.putBoolean("alive", value.alive());
            resident.putLong("revision", value.revision());
            residents.add(resident);
        });
        tag.put("residents", residents);
        ListTag facilities = new ListTag();
        state.facilities().forEach(value -> {
            CompoundTag facility = new CompoundTag();
            facility.putString("id", value.id());
            facility.putString("state", value.state().name());
            facility.putLong("revision", value.revision());
            facilities.add(facility);
        });
        tag.put("facilities", facilities);
        ListTag operations = new ListTag();
        state.operations().forEach(value -> {
            CompoundTag operation = new CompoundTag();
            operation.putString("id", value.id());
            operation.putString("state", value.state().name());
            operation.putLong("revision", value.revision());
            operations.add(operation);
        });
        tag.put("operations", operations);
        ListTag cargo = new ListTag();
        state.cargo().forEach(value -> {
            CompoundTag shipment = new CompoundTag();
            shipment.putString("id", value.id());
            shipment.putString("route", value.routeId());
            shipment.putString("source", value.sourceSettlementId());
            shipment.putString("destination", value.destinationSettlementId());
            shipment.putString("resource", value.resource().name());
            shipment.putLong("amount", value.amount());
            shipment.putLong("creditValue", value.creditValue());
            shipment.putLong("dispatchedDay", value.dispatchedDay());
            shipment.putLong("revision", value.revision());
            cargo.add(shipment);
        });
        tag.put("cargo", cargo);
        ListTag hives = new ListTag();
        state.hives().forEach(value -> {
            CompoundTag hive = new CompoundTag();
            hive.putString("id", value.id());
            hive.putLong("biomass", value.biomass());
            hive.putLong("geneticMaterial", value.geneticMaterial());
            hive.putString("state", value.state().name());
            hive.putLong("revision", value.revision());
            hives.add(hive);
        });
        tag.put("hives", hives);
        ListTag hiveOrgans = new ListTag();
        state.hiveOrgans().forEach(value -> {
            CompoundTag organ = new CompoundTag();
            organ.putString("id", value.id());
            organ.putString("hive", value.hiveId());
            organ.putString("kind", value.kind().name());
            organ.putInt("x", value.position().x());
            organ.putInt("z", value.position().z());
            organ.putLong("bornDay", value.bornDay());
            organ.putString("state", value.state().name());
            organ.putLong("revision", value.revision());
            hiveOrgans.add(organ);
        });
        tag.put("hiveOrgans", hiveOrgans);
        tag.putString("hiveOrganDigest", hiveOrganDigest(state.hiveOrgans()));
        ListTag ecology = new ListTag();
        state.ecology().cells().forEach(value -> {
            CompoundTag cell = new CompoundTag();
            cell.putInt("x", value.x());
            cell.putInt("z", value.z());
            cell.putLong("flora", value.flora());
            cell.putLong("fauna", value.fauna());
            cell.putLong("detritus", value.detritus());
            cell.putLong("nutrients", value.nutrients());
            cell.putLong("moisture", value.moisture());
            cell.putLong("scar", value.scar());
            ecology.add(cell);
        });
        tag.put("ecology", ecology);
        ListTag hiveTissue = new ListTag();
        state.hiveTissue().forEach(value -> {
            CompoundTag cell = new CompoundTag();
            cell.putString("hive", value.hiveId());
            cell.putInt("x", value.position().x());
            cell.putInt("z", value.position().z());
            cell.putInt("strength", value.strength());
            hiveTissue.add(cell);
        });
        tag.put("hiveTissue", hiveTissue);
        // A dynamic topology has no fixed genesis count.  Bind the complete
        // saved content so a truncated list cannot be mistaken for legitimate
        // tissue decay during a restart.
        tag.putString("hiveTissueDigest", tissueDigest(state.hiveTissue()));
        ListTag morphogenesisProjects = new ListTag();
        state.morphogenesisProjects().forEach(value -> {
            CompoundTag project = new CompoundTag();
            project.putString("id", value.id());
            project.putString("hive", value.hiveId());
            project.putString("source", value.sourceOrganId());
            project.putString("kind", value.kind().name());
            project.putInt("x", value.position().x());
            project.putInt("z", value.position().z());
            project.putLong("startedDay", value.startedDay());
            project.putInt("remainingDays", value.remainingDays());
            project.putLong("revision", value.revision());
            morphogenesisProjects.add(project);
        });
        tag.put("morphogenesisProjects", morphogenesisProjects);
        tag.putString("morphogenesisDigest", morphogenesisDigest(state.morphogenesisProjects()));
        ListTag bioforms = new ListTag();
        state.bioforms().forEach(value -> {
            CompoundTag bioform = new CompoundTag();
            bioform.putString("id", value.id());
            bioform.putString("hive", value.hiveId());
            bioform.putString("kind", value.kind().name());
            bioform.putLong("bornDay", value.bornDay());
            bioform.putInt("birthOrdinal", value.birthOrdinal());
            bioform.putBoolean("alive", value.alive());
            bioform.putLong("deathDay", value.deathDay());
            bioform.putLong("revision", value.revision());
            bioforms.add(bioform);
        });
        tag.put("bioforms", bioforms); FrontierHarvesterRunNbt.write(tag, state.harvesterRuns()); FrontierPropagationNbt.write(tag, state);
        ListTag assaults = new ListTag();
        state.assaults().forEach(value -> {
            CompoundTag assault = new CompoundTag();
            assault.putString("id", value.id());
            assault.putString("hive", value.hiveId());
            assault.putString("target", value.targetSettlementId());
            assault.putString("kind", value.kind().name());
            assault.putString("state", value.state().name());
            assault.putInt("x", value.position().x());
            assault.putInt("z", value.position().z());
            ListTag participants = new ListTag();
            value.participantIds().forEach(id -> participants.add(StringTag.valueOf(id)));
            assault.put("participants", participants);
            assault.putLong("startedDay", value.startedDay());
            assault.putInt("transitDays", value.transitDays());
            assault.putInt("transitProgress", value.transitProgress());
            assault.putInt("engagementDays", value.engagementDays());
            assault.putLong("finishedDay", value.finishedDay());
            assault.putLong("revision", value.revision());
            assaults.add(assault);
        });
        tag.put("assaults", assaults);
        FrontierFieldOperationNbt.write(tag, state.fieldOperations());
        FrontierCampaignNbt.write(tag, state.campaigns());
        ListTag observations = new ListTag();
        state.processedObservationIds().forEach(value -> observations.add(StringTag.valueOf(value)));
        tag.put("processedObservations", observations);
        return tag;
    }
    public static FrontierWorldState read(CompoundTag tag) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("unsupported Frontier state format");
        List<FrontierStateHydration.ResidentState> residents = new ArrayList<>();
        List<FrontierStateHydration.SettlementState> settlements = new ArrayList<>();
        for (Tag value : tag.getList("settlements", Tag.TAG_COMPOUND)) {
            CompoundTag settlement = (CompoundTag) value;
            java.util.Map<FrontierResource, Long> stocks = new java.util.EnumMap<>(FrontierResource.class);
            for (FrontierResource resource : FrontierResource.values()) stocks.put(resource, settlement.getCompound("stocks").getLong(resource.name()));
            settlements.add(FrontierCivicNbt.read(settlement, stocks));
        }
        for (Tag value : tag.getList("residents", Tag.TAG_COMPOUND)) {
            CompoundTag resident = (CompoundTag) value;
            residents.add(new FrontierStateHydration.ResidentState(resident.getString("id"), resident.getBoolean("alive"),
                    resident.getLong("revision")));
        }
        List<FrontierStateHydration.FacilityState> facilities = new ArrayList<>();
        for (Tag value : tag.getList("facilities", Tag.TAG_COMPOUND)) {
            CompoundTag facility = (CompoundTag) value;
            try {
                facilities.add(new FrontierStateHydration.FacilityState(facility.getString("id"),
                        FrontierFacility.State.valueOf(facility.getString("state")), facility.getLong("revision")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("unknown Frontier facility state", invalid);
            }
        }
        List<FrontierStateHydration.OperationState> operations = new ArrayList<>();
        for (Tag value : tag.getList("operations", Tag.TAG_COMPOUND)) {
            CompoundTag operation = (CompoundTag) value;
            try {
                operations.add(new FrontierStateHydration.OperationState(operation.getString("id"),
                        FrontierOperation.State.valueOf(operation.getString("state")), operation.getLong("revision")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("unknown Frontier operation state", invalid);
            }
        }
        List<FrontierStateHydration.CargoState> cargo = new ArrayList<>();
        for (Tag value : tag.getList("cargo", Tag.TAG_COMPOUND)) {
            CompoundTag shipment = (CompoundTag) value;
            try {
                cargo.add(new FrontierStateHydration.CargoState(shipment.getString("id"), shipment.getString("route"),
                        shipment.getString("source"), shipment.getString("destination"),
                        FrontierResource.valueOf(shipment.getString("resource")), shipment.getLong("amount"), shipment.getLong("creditValue"),
                        shipment.getLong("dispatchedDay"), shipment.getLong("revision")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid Frontier cargo", invalid);
            }
        }
        List<FrontierStateHydration.HiveState> hives = new ArrayList<>();
        for (Tag value : tag.getList("hives", Tag.TAG_COMPOUND)) {
            CompoundTag hive = (CompoundTag) value;
            try {
                hives.add(new FrontierStateHydration.HiveState(hive.getString("id"), hive.getLong("biomass"), hive.getLong("geneticMaterial"),
                        FrontierHive.State.valueOf(hive.getString("state")), hive.getLong("revision")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid Frontier hive", invalid);
            }
        }
        List<FrontierStateHydration.HiveOrganState> hiveOrgans = new ArrayList<>();
        for (Tag value : tag.getList("hiveOrgans", Tag.TAG_COMPOUND)) {
            CompoundTag organ = (CompoundTag) value;
            try {
                hiveOrgans.add(new FrontierStateHydration.HiveOrganState(organ.getString("id"), organ.getString("hive"),
                        FrontierHiveOrganKind.valueOf(organ.getString("kind")),
                        new io.farfrontier.palemirror.frontier.FrontierPoint(organ.getInt("x"), organ.getInt("z")),
                        organ.getLong("bornDay"), FrontierHiveOrgan.State.valueOf(organ.getString("state")),
                        organ.getLong("revision")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid Frontier hive organ", invalid);
            }
        }
        if (!tag.getString("hiveOrganDigest").equals(hiveOrganStateDigest(hiveOrgans))) {
            throw new IllegalStateException("incomplete or corrupt Frontier hive organs");
        }
        List<io.farfrontier.palemirror.frontier.FrontierEcology.Cell> ecology = new ArrayList<>();
        for (Tag value : tag.getList("ecology", Tag.TAG_COMPOUND)) {
            CompoundTag cell = (CompoundTag) value;
            ecology.add(new io.farfrontier.palemirror.frontier.FrontierEcology.Cell(cell.getInt("x"), cell.getInt("z"),
                    cell.getLong("flora"), cell.getLong("fauna"), cell.getLong("detritus"), cell.getLong("nutrients"),
                    cell.getLong("moisture"), cell.getLong("scar")));
        }
        List<io.farfrontier.palemirror.frontier.FrontierHiveTissueCell> hiveTissue = new ArrayList<>();
        for (Tag value : tag.getList("hiveTissue", Tag.TAG_COMPOUND)) {
            CompoundTag cell = (CompoundTag) value;
            hiveTissue.add(new io.farfrontier.palemirror.frontier.FrontierHiveTissueCell(cell.getString("hive"),
                    new io.farfrontier.palemirror.frontier.FrontierPoint(cell.getInt("x"), cell.getInt("z")),
                    cell.getInt("strength")));
        }
        if (!tag.getString("hiveTissueDigest").equals(tissueDigest(hiveTissue))) {
            throw new IllegalStateException("incomplete or corrupt Frontier hive tissue");
        }
        List<FrontierStateHydration.MorphogenesisProjectState> morphogenesisProjects = new ArrayList<>();
        for (Tag value : tag.getList("morphogenesisProjects", Tag.TAG_COMPOUND)) {
            CompoundTag project = (CompoundTag) value;
            try {
                morphogenesisProjects.add(new FrontierStateHydration.MorphogenesisProjectState(project.getString("id"),
                        project.getString("hive"), project.getString("source"), FrontierHiveOrganKind.valueOf(project.getString("kind")),
                        new io.farfrontier.palemirror.frontier.FrontierPoint(project.getInt("x"), project.getInt("z")),
                        project.getLong("startedDay"), project.getInt("remainingDays"), project.getLong("revision")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid Frontier morphogenesis project", invalid);
            }
        }
        if (!tag.getString("morphogenesisDigest").equals(morphogenesisStateDigest(morphogenesisProjects))) {
            throw new IllegalStateException("incomplete or corrupt Frontier morphogenesis projects");
        }
        List<FrontierStateHydration.BioformState> bioforms = new ArrayList<>();
        for (Tag value : tag.getList("bioforms", Tag.TAG_COMPOUND)) {
            CompoundTag bioform = (CompoundTag) value;
            try {
                bioforms.add(new FrontierStateHydration.BioformState(bioform.getString("id"), bioform.getString("hive"),
                        FrontierBioformKind.valueOf(bioform.getString("kind")), bioform.getLong("bornDay"),
                        bioform.getInt("birthOrdinal"), bioform.getBoolean("alive"), bioform.getLong("deathDay"),
                        bioform.getLong("revision")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid Frontier bioform", invalid);
            }
        }
        List<FrontierStateHydration.HarvesterRunState> harvesterRuns = FrontierHarvesterRunNbt.read(tag);
        FrontierPropagationNbt.Read propagations = FrontierPropagationNbt.read(tag);
        List<FrontierStateHydration.AssaultState> assaults = new ArrayList<>();
        for (Tag value : tag.getList("assaults", Tag.TAG_COMPOUND)) {
            CompoundTag assault = (CompoundTag) value;
            try {
                assaults.add(new FrontierStateHydration.AssaultState(assault.getString("id"), assault.getString("hive"),
                        assault.getString("target"), io.farfrontier.palemirror.frontier.FrontierAssault.Kind.valueOf(assault.getString("kind")),
                        io.farfrontier.palemirror.frontier.FrontierAssault.State.valueOf(assault.getString("state")),
                        new io.farfrontier.palemirror.frontier.FrontierPoint(assault.getInt("x"), assault.getInt("z")),
                        assault.getList("participants", Tag.TAG_STRING).stream().map(Tag::getAsString).toList(),
                        assault.getLong("startedDay"), assault.getInt("transitDays"), assault.getInt("transitProgress"),
                        assault.getInt("engagementDays"), assault.getLong("finishedDay"), assault.getLong("revision")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid Frontier assault", invalid);
            }
        }
        List<FrontierStateHydration.FieldOperationState> fieldOperations = FrontierFieldOperationNbt.read(tag);
        List<FrontierStateHydration.CampaignState> campaigns = FrontierCampaignNbt.read(tag);
        List<String> observations = tag.getList("processedObservations", Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
        try {
            return FrontierStateHydration.restore(FrontierProfile.require(tag.getString("profile")), tag.getLong("seed"),
                    tag.getLong("day"), residents, facilities, settlements, operations, cargo, hives, hiveOrgans, bioforms,
                    ecology, hiveTissue, morphogenesisProjects, harvesterRuns, propagations.runs(), propagations.colonies(), propagations.adaptations(),
                    assaults, fieldOperations, campaigns, observations);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("incompatible Frontier state", invalid);
        }
    }
    private static String tissueDigest(Iterable<io.farfrontier.palemirror.frontier.FrontierHiveTissueCell> cells) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            java.util.ArrayList<io.farfrontier.palemirror.frontier.FrontierHiveTissueCell> ordered = new java.util.ArrayList<>();
            cells.forEach(ordered::add);
            ordered.stream().sorted(java.util.Comparator.comparing(io.farfrontier.palemirror.frontier.FrontierHiveTissueCell::hiveId)
                    .thenComparing(value -> value.position().z()).thenComparing(value -> value.position().x()))
                    .forEach(value -> digest.update((value.hiveId().length() + ":" + value.hiveId() + ":"
                            + value.position().x() + ":" + value.position().z() + ":" + value.strength() + "\\n")
                            .getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable for Frontier persistence", unavailable);
        }
    }
    private static String hiveOrganDigest(Iterable<FrontierHiveOrgan> organs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            java.util.ArrayList<FrontierHiveOrgan> ordered = new java.util.ArrayList<>();
            organs.forEach(ordered::add);
            ordered.stream().sorted(java.util.Comparator.comparing(FrontierHiveOrgan::id)).forEach(value -> digest.update(
                    (value.id().length() + ":" + value.id() + ":" + value.hiveId().length() + ":" + value.hiveId() + ":"
                            + value.kind() + ":" + value.position().x() + ":" + value.position().z() + ":" + value.bornDay()
                            + ":" + value.state() + ":" + value.revision() + "\\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable for Frontier persistence", unavailable);
        }
    }
    private static String hiveOrganStateDigest(Iterable<FrontierStateHydration.HiveOrganState> organs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            java.util.ArrayList<FrontierStateHydration.HiveOrganState> ordered = new java.util.ArrayList<>();
            organs.forEach(ordered::add);
            ordered.stream().sorted(java.util.Comparator.comparing(FrontierStateHydration.HiveOrganState::id)).forEach(value -> digest.update(
                    (value.id().length() + ":" + value.id() + ":" + value.hiveId().length() + ":" + value.hiveId() + ":"
                            + value.kind() + ":" + value.position().x() + ":" + value.position().z() + ":" + value.bornDay()
                            + ":" + value.state() + ":" + value.revision() + "\\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable for Frontier persistence", unavailable);
        }
    }
    private static String morphogenesisDigest(Iterable<FrontierMorphogenesisProject> projects) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            java.util.ArrayList<FrontierMorphogenesisProject> ordered = new java.util.ArrayList<>();
            projects.forEach(ordered::add);
            ordered.stream().sorted(java.util.Comparator.comparing(FrontierMorphogenesisProject::id)).forEach(value -> digest.update(
                    (value.id().length() + ":" + value.id() + ":" + value.hiveId().length() + ":" + value.hiveId() + ":"
                            + value.sourceOrganId().length() + ":" + value.sourceOrganId() + ":" + value.kind() + ":"
                            + value.position().x() + ":" + value.position().z() + ":" + value.startedDay() + ":"
                            + value.remainingDays() + ":" + value.revision() + "\\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable for Frontier persistence", unavailable);
        }
    }
    private static String morphogenesisStateDigest(Iterable<FrontierStateHydration.MorphogenesisProjectState> projects) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            java.util.ArrayList<FrontierStateHydration.MorphogenesisProjectState> ordered = new java.util.ArrayList<>();
            projects.forEach(ordered::add);
            ordered.stream().sorted(java.util.Comparator.comparing(FrontierStateHydration.MorphogenesisProjectState::id))
                    .forEach(value -> digest.update((value.id().length() + ":" + value.id() + ":" + value.hiveId().length()
                            + ":" + value.hiveId() + ":" + value.sourceOrganId().length() + ":" + value.sourceOrganId()
                            + ":" + value.kind() + ":" + value.position().x() + ":" + value.position().z() + ":"
                            + value.startedDay() + ":" + value.remainingDays() + ":" + value.revision() + "\\n")
                            .getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable for Frontier persistence", unavailable);
        }
    }

    static String harvesterRunDigest(Iterable<io.farfrontier.palemirror.frontier.FrontierHarvesterRun> runs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            java.util.ArrayList<io.farfrontier.palemirror.frontier.FrontierHarvesterRun> ordered = new java.util.ArrayList<>();
            runs.forEach(ordered::add);
            ordered.stream().sorted(java.util.Comparator.comparing(io.farfrontier.palemirror.frontier.FrontierHarvesterRun::id))
                    .forEach(value -> digest.update((value.id().length() + ":" + value.id() + ":" + value.hiveId().length() + ":"
                            + value.hiveId() + ":" + value.sourceOrganId().length() + ":" + value.sourceOrganId() + ":"
                            + value.bioformId().length() + ":" + value.bioformId() + ":" + value.origin().x() + ":"
                            + value.origin().z() + ":" + value.foragePosition().x() + ":" + value.foragePosition().z()
                            + ":" + value.position().x() + ":" + value.position().z() + ":"
                            + (value.receiverOrganId() == null ? -1 : value.receiverOrganId().length()) + ":"
                            + (value.receiverOrganId() == null ? "" : value.receiverOrganId()) + ":" + value.state() + ":"
                            + value.startedDay() + ":" + value.outboundDays() + ":" + value.transitProgress() + ":"
                            + value.returnDays() + ":" + value.cargo() + ":" + value.geneticCargo() + ":"
                            + value.finishedDay() + ":" + value.revision() + "\\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable for Frontier persistence", unavailable);
        }
    }

    static String harvesterRunStateDigest(Iterable<FrontierStateHydration.HarvesterRunState> runs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            java.util.ArrayList<FrontierStateHydration.HarvesterRunState> ordered = new java.util.ArrayList<>();
            runs.forEach(ordered::add);
            ordered.stream().sorted(java.util.Comparator.comparing(FrontierStateHydration.HarvesterRunState::id))
                    .forEach(value -> digest.update((value.id().length() + ":" + value.id() + ":" + value.hiveId().length() + ":"
                            + value.hiveId() + ":" + value.sourceOrganId().length() + ":" + value.sourceOrganId() + ":"
                            + value.bioformId().length() + ":" + value.bioformId() + ":" + value.origin().x() + ":"
                            + value.origin().z() + ":" + value.foragePosition().x() + ":" + value.foragePosition().z()
                            + ":" + value.position().x() + ":" + value.position().z() + ":"
                            + (value.receiverOrganId() == null ? -1 : value.receiverOrganId().length()) + ":"
                            + (value.receiverOrganId() == null ? "" : value.receiverOrganId()) + ":" + value.state() + ":"
                            + value.startedDay() + ":" + value.outboundDays() + ":" + value.transitProgress() + ":"
                            + value.returnDays() + ":" + value.cargo() + ":" + value.geneticCargo() + ":"
                            + value.finishedDay() + ":" + value.revision() + "\\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable for Frontier persistence", unavailable);
        }
    }
}
