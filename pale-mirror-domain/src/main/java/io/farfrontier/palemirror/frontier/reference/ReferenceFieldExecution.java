package io.farfrontier.palemirror.frontier.reference;

import static io.farfrontier.palemirror.frontier.reference.ReferenceOperationRules.rolesFor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Daily execution extracted from Python {@code FieldWarfare}; field warfare retains every record. */
final class ReferenceFieldExecution {
    private ReferenceFieldExecution() { }

    static boolean resolveOperation(ReferenceFieldWarfare field, ReferenceWorld world, ReferenceOperation operation) {
        return ReferenceFieldOperationResolution.resolve(field, world, operation);
    }

    static boolean startNestRaid(ReferenceFieldWarfare field, ReferenceWorld world, ReferenceOperation operation) {
        if (operation.target().id() == null || !world.infection().organs().containsKey(operation.target().id())) return false;
        for (ReferenceFieldEngagement engagement : field.engagements().values()) {
            if (engagement.operationId() != null && engagement.operationId() == operation.id()
                    && engagement.status() == ReferenceEngagementStatus.ACTIVE) return true;
        }
        Integer campaignId = detailInteger(operation, "campaign_id");
        ReferenceFieldEngagement engagement = new ReferenceFieldEngagement(field.nextEngagementIdAndIncrement(),
                ReferenceEngagementKind.NEST_RAID, operation.x(), operation.y(), world.day(), campaignId, null,
                operation.id(), operation.target().id(), null);
        field.mutableEngagements().put(engagement.id(), engagement);
        if (campaignId != null && field.mutableCampaigns().containsKey(campaignId)) {
            ReferenceFieldCampaign campaign = field.mutableCampaigns().get(campaignId);
            campaign.mutableEngagementIds().add(engagement.id());
            campaign.phase(ReferenceCampaignPhase.ENGAGE);
        }
        return true;
    }

    static void step(ReferenceFieldWarfare field, ReferenceWorld world) {
        advancePosts(field, world);
        resolveEngagements(field, world);
    }

    static void detectSwarms(ReferenceFieldWarfare field, ReferenceWorld world) {
        for (ReferenceSwarm swarm : world.infection().swarms()) {
            if (field.engagements().values().stream().anyMatch(item -> item.status() == ReferenceEngagementStatus.ACTIVE
                    && item.swarmId() != null && item.swarmId() == swarm.id())) continue;
            ArrayList<ReferenceFieldPost> candidates = new ArrayList<>();
            for (ReferenceFieldPost post : field.activePosts()) {
                if (post.status() == ReferenceFieldPostStatus.ACTIVE && post.kind() != ReferenceFieldPostKind.OBSERVATION
                        && Math.hypot(post.x() - swarm.x(), post.y() - swarm.y()) <= ReferenceFieldRules.POST_ENGAGEMENT_RADIUS) candidates.add(post);
            }
            for (ReferenceFieldLink link : field.links().values()) {
                if (link.kind() != ReferenceFieldLinkKind.FORTIFIED_LINE || !link.status().equals("active")) continue;
                ReferenceFieldPost a = field.mutablePosts().get(link.aPostId());
                ReferenceFieldPost b = field.mutablePosts().get(link.bPostId());
                if (a != null && b != null && segmentDistance(swarm.x(), swarm.y(), a.x(), a.y(), b.x(), b.y())
                        <= ReferenceFieldRules.FORTIFIED_LINE_INTERCEPTION_RADIUS) {
                    if (a.status() == ReferenceFieldPostStatus.ACTIVE) candidates.add(a);
                    if (b.status() == ReferenceFieldPostStatus.ACTIVE) candidates.add(b);
                }
            }
            ReferenceFieldPost nearest = candidates.stream().min(java.util.Comparator.comparingDouble(
                    post -> Math.hypot(post.x() - swarm.x(), post.y() - swarm.y()))).orElse(null);
            if (nearest == null) continue;
            ReferenceFieldEngagement engagement = new ReferenceFieldEngagement(field.nextEngagementIdAndIncrement(),
                    ReferenceEngagementKind.POST_DEFENCE, nearest.x(), nearest.y(), world.day(), nearest.campaignId(), nearest.id(),
                    null, null, swarm.id());
            field.mutableEngagements().put(engagement.id(), engagement);
            ReferenceFieldCampaign campaign = field.mutableCampaigns().get(nearest.campaignId());
            if (campaign != null) campaign.mutableEngagementIds().add(engagement.id());
            world.marketWorld().event("D" + world.day() + ": swarm " + swarm.id() + " engaged field post " + nearest.id());
        }
    }

    private static void advancePosts(ReferenceFieldWarfare field, ReferenceWorld world) {
        for (ReferenceFieldPost post : field.activePosts()) {
            if (post.status() == ReferenceFieldPostStatus.BUILDING) {
                post.buildDaysRemaining(post.buildDaysRemaining() - 1);
                if (post.buildDaysRemaining() <= 0) {
                    post.status(ReferenceFieldPostStatus.ACTIVE);
                    world.marketWorld().event("D" + world.day() + ": field post " + post.id() + " is operational");
                }
                continue;
            }
            consumePostSupplies(world, post);
            for (ReferenceFieldModuleKind module : List.copyOf(post.moduleProjects().keySet())) {
                int days = post.moduleProjects().get(module) - 1;
                post.mutableModuleProjects().put(module, days);
                if (days <= 0) {
                    post.mutableModuleProjects().remove(module);
                    post.mutableModules().add(module);
                    world.marketWorld().event("D" + world.day() + ": field post " + post.id() + " completed " + module.id());
                }
            }
            if (post.isolationDays() >= ReferenceFieldRules.ABANDON_AFTER_ISOLATION_DAYS) {
                post.status(ReferenceFieldPostStatus.ABANDONED);
                evacuatePostGarrison(world, post);
                world.marketWorld().event("D" + world.day() + ": field post " + post.id() + " was abandoned after supply failure");
            }
        }
        for (ReferenceFieldLink link : field.mutableLinks().values()) {
            if (link.status().equals("building")) {
                link.buildDaysRemaining(link.buildDaysRemaining() - 1);
                if (link.buildDaysRemaining() <= 0) link.status("active");
            }
            ReferenceFieldPost a = field.mutablePosts().get(link.aPostId());
            ReferenceFieldPost b = field.mutablePosts().get(link.bPostId());
            if (a == null || b == null || !active(a) || !active(b)) link.status("inactive");
        }
    }

    private static boolean consumePostSupplies(ReferenceWorld world, ReferenceFieldPost post) {
        double food = post.garrison() * ReferenceFieldRules.POST_FOOD_PER_PERSON_DAY;
        double medicine = (post.garrison() + post.wounded()) * ReferenceFieldRules.POST_MEDICINE_PER_PERSON_DAY;
        boolean sufficient = post.stock(ReferenceResource.FOOD) >= food && post.stock(ReferenceResource.MEDICINE) >= medicine;
        post.stock(ReferenceResource.FOOD, Math.max(0.0d, post.stock(ReferenceResource.FOOD) - food));
        post.stock(ReferenceResource.MEDICINE, Math.max(0.0d, post.stock(ReferenceResource.MEDICINE) - medicine));
        post.isolationDays(sufficient ? 0 : post.isolationDays() + 1);
        post.status(sufficient ? ReferenceFieldPostStatus.ACTIVE : ReferenceFieldPostStatus.ISOLATED);
        if (post.hasModule(ReferenceFieldModuleKind.FIELD_HOSPITAL) && post.wounded() > 0.0d) {
            double treated = Math.min(post.wounded(), ReferenceFieldRules.FIELD_HOSPITAL_TREATMENT_PER_DAY);
            for (Integer settlementId : List.copyOf(post.woundedBySettlement().keySet())) {
                double wounded = post.woundedBySettlement().getOrDefault(settlementId, 0.0d);
                double recovered = Math.min(wounded, treated * wounded / Math.max(1.0e-9d, post.wounded()));
                post.mutableWoundedBySettlement().put(settlementId, wounded - recovered);
                post.mutableGarrisonBySettlement().merge(settlementId, recovered, Double::sum);
                ReferenceSettlement settlement = world.settlements().get(settlementId);
                if (settlement != null && settlement.discretePeople()) {
                    List<String> patients = post.woundedResidentIdsBySettlement().getOrDefault(settlementId, List.of());
                    List<String> recoveredIds = settlement.recoverExposedWoundedPeople(patients, recovered, "field_hospital");
                    ArrayList<String> remaining = new ArrayList<>(patients);
                    remaining.removeAll(recoveredIds);
                    post.mutableWoundedResidentIdsBySettlement().put(settlementId, remaining);
                    post.mutableResidentIdsBySettlement().put(settlementId, merge(post.mutableResidentIdsBySettlement().get(settlementId), recoveredIds));
                    post.mutableWoundedBySettlement().put(settlementId, (double) remaining.size());
                    post.mutableGarrisonBySettlement().put(settlementId,
                            (double) post.mutableResidentIdsBySettlement().get(settlementId).size());
                }
            }
        }
        return sufficient;
    }

    private static void resolveEngagements(ReferenceFieldWarfare field, ReferenceWorld world) {
        for (ReferenceFieldEngagement engagement : List.copyOf(field.engagements().values())) {
            if (engagement.status() == ReferenceEngagementStatus.ACTIVE) {
                if (engagement.kind() == ReferenceEngagementKind.POST_DEFENCE) resolvePostEngagement(field, world, engagement);
                else resolveNestRaid(field, world, engagement);
            }
            if (engagement.status() != ReferenceEngagementStatus.ACTIVE) {
                field.mutableCompletedEngagements().add(engagement);
                field.mutableEngagements().remove(engagement.id());
            }
        }
    }

    private static void resolvePostEngagement(ReferenceFieldWarfare field, ReferenceWorld world, ReferenceFieldEngagement engagement) {
        ReferenceFieldPost post = engagement.postId() == null ? null : field.mutablePosts().get(engagement.postId());
        ReferenceSwarm swarm = swarmById(world, engagement.swarmId());
        if (post == null || swarm == null || !active(post)) { engagement.status(ReferenceEngagementStatus.COMPLETE); return; }
        double attack = Math.max(0.0d, swarm.power());
        double defence = field.postPower(post);
        double supportAmmo = ReferenceFieldRules.FIRE_SUPPORT_AMMO / post.cargoScale();
        if (post.hasModule(ReferenceFieldModuleKind.FIRE_SUPPORT) && post.stock(ReferenceResource.AMMO) >= supportAmmo) {
            post.stock(ReferenceResource.AMMO, post.stock(ReferenceResource.AMMO) - supportAmmo);
            defence += ReferenceFieldRules.FIRE_SUPPORT_POWER / post.cargoScale();
        }
        post.stock(ReferenceResource.AMMO, Math.max(0.0d, post.stock(ReferenceResource.AMMO)
                - Math.min(post.stock(ReferenceResource.AMMO), post.garrison() * ReferenceFieldRules.AMMO_PER_DEFENDER)));
        double swarmDamage = defence * ReferenceFieldRules.POST_DAMAGE_PER_POWER;
        double defenderDamage = attack * ReferenceFieldRules.SWARM_DAMAGE_PER_POWER;
        swarm.power(Math.max(0.0d, swarm.power() - swarmDamage));
        post.integrity(Math.max(0.0d, post.integrity() - defenderDamage * ReferenceFieldRules.POST_INTEGRITY_DAMAGE));
        double casualties = Math.min(post.garrison(), defenderDamage * ReferenceFieldRules.PERSONNEL_DAMAGE);
        double killed = casualties * ReferenceFieldRules.KILLED_FRACTION;
        double wounded = casualties - killed;
        applyPostLosses(world, post, killed, wounded);
        engagement.days(engagement.days() + 1);
        engagement.attackerPower(swarm.power());
        engagement.defenderPower(field.postPower(post));
        engagement.killed(engagement.killed() + killed);
        engagement.wounded(engagement.wounded() + wounded);
        if (swarm.power() <= ReferenceFieldRules.MINIMUM_ENGAGEMENT_POWER) {
            world.infection().removeSwarm(swarm);
            engagement.status(ReferenceEngagementStatus.ATTACKERS_DESTROYED);
        } else if (post.integrity() <= 0.0d || post.garrison() <= ReferenceFieldRules.MINIMUM_GARRISON) {
            double remaining = post.garrison();
            applyPostLosses(world, post, remaining * .28d, remaining * .34d);
            post.status(ReferenceFieldPostStatus.OVERRUN);
            post.destroyedDay(world.day());
            world.infection().ecosystem().addDetritus(post.x(), post.y(), post.stock(ReferenceResource.FOOD) + post.stock(ReferenceResource.TIMBER));
            evacuatePostGarrison(world, post);
            engagement.status(ReferenceEngagementStatus.DEFENDERS_BROKEN);
            world.marketWorld().event("D" + world.day() + ": field post " + post.id() + " was overrun");
        }
    }

    private static void resolveNestRaid(ReferenceFieldWarfare field, ReferenceWorld world, ReferenceFieldEngagement engagement) {
        ReferenceOperation operation = world.operations().active().stream()
                .filter(item -> item.id() == (engagement.operationId() == null ? -1 : engagement.operationId())).findFirst().orElse(null);
        ReferenceHiveOrgan nest = engagement.nestId() == null ? null : world.infection().organs().get(engagement.nestId());
        if (operation == null || nest == null) { engagement.status(ReferenceEngagementStatus.COMPLETE); return; }
        double readiness = world.infection().organReadiness(nest);
        operation.mutableDetails().putIfAbsent("target_vitality_before", nest.vitality());
        double enemyPower = (28.0d + world.infection().pressureAt(nest.x(), nest.y()) * 110.0d) / world.profile().personScale()
                * organDefence(nest.kind()) * (.35d + readiness * .65d);
        double support = 0.0d;
        for (ReferenceFieldPost post : field.activePosts()) {
            if (post.hasModule(ReferenceFieldModuleKind.FIRE_SUPPORT)
                    && Math.hypot(post.x() - nest.x(), post.y() - nest.y()) <= ReferenceFieldRules.FIRE_SUPPORT_RANGE
                    && post.stock(ReferenceResource.AMMO) >= ReferenceFieldRules.FIRE_SUPPORT_AMMO / post.cargoScale()) {
                support += ReferenceFieldRules.FIRE_SUPPORT_POWER / post.cargoScale();
            }
        }
        for (ReferenceFieldPost post : field.activePosts()) {
            if (post.hasModule(ReferenceFieldModuleKind.FIRE_SUPPORT)
                    && Math.hypot(post.x() - nest.x(), post.y() - nest.y()) <= ReferenceFieldRules.FIRE_SUPPORT_RANGE) {
                post.stock(ReferenceResource.AMMO, Math.max(0.0d, post.stock(ReferenceResource.AMMO)
                        - ReferenceFieldRules.FIRE_SUPPORT_AMMO / post.cargoScale()));
            }
        }
        Map<ReferenceHumanUnitKind, Double> roles = rolesFor(operation);
        double total = Math.max(1.0d, roles.values().stream().mapToDouble(Double::doubleValue).sum());
        double assaultRatio = roles.getOrDefault(ReferenceHumanUnitKind.ASSAULT, 0.0d) / total;
        double engineerRatio = roles.getOrDefault(ReferenceHumanUnitKind.ENGINEER, 0.0d) / total;
        double medicRatio = roles.getOrDefault(ReferenceHumanUnitKind.MEDIC, 0.0d) / total;
        double attack = (operation.power() + support) * (1.0d + assaultRatio * .40d);
        double ratio = attack / Math.max(1.0d, enemyPower);
        double tissueDamage = attack * ReferenceFieldRules.RAID_TISSUE_DAMAGE;
        double counterDamage = enemyPower * ReferenceFieldRules.RAID_COUNTER_DAMAGE * (1.0d - engineerRatio * .28d);
        double removed = world.infection().suppressArea(nest.x(), nest.y(), 2.0d, Math.min(.35d, tissueDamage * .003d));
        nest.biomass(Math.max(0.0d, nest.biomass() - tissueDamage));
        nest.vitality(Math.max(0.0d, nest.vitality() - tissueDamage * ReferenceFieldRules.RAID_VITALITY_FRACTION));
        operation.mutableDetails().put("target_vitality_after", nest.vitality());
        double casualties = Math.min(operation.personnel(), counterDamage * ReferenceFieldRules.PERSONNEL_DAMAGE);
        double killed = casualties * ReferenceFieldRules.KILLED_FRACTION * Math.max(.35d, 1.0d - medicRatio * .55d);
        double wounded = casualties - killed;
        if (world.profile().discretePeople()) {
            ReferenceCasualtyResult result = world.operations().applyDiscreteCasualties(world, operation, killed, wounded, "nest_raid_combat");
            killed = result.killedIds().size();
            wounded = result.woundedIds().size();
            casualties = killed + wounded;
        } else {
            applyOperationLosses(world, operation, killed, wounded);
            applyRoleLosses(operation, casualties, false);
            operation.personnel(Math.max(0.0d, operation.personnel() - casualties));
        }
        operation.power(Math.max(0.0d, operation.power() - counterDamage));
        engagement.days(engagement.days() + 1);
        engagement.attackerPower(operation.power());
        engagement.defenderPower(enemyPower);
        engagement.killed(engagement.killed() + killed);
        engagement.wounded(engagement.wounded() + wounded);
        operation.mutableDetails().merge("infection_removed", removed, (oldValue, value) -> (double) oldValue + (double) value);
        operation.mutableDetails().merge("casualties", casualties, (oldValue, value) -> (double) oldValue + (double) value);
        world.infection().removeDestroyedOrgans();
        if (!world.infection().organs().containsKey(nest.id())) {
            operation.outcome("raid_success");
            operation.status(ReferenceOperationStatus.RETURNING);
            engagement.status(ReferenceEngagementStatus.DEFENDERS_BROKEN);
            world.marketWorld().event("D" + world.day() + ": operation " + operation.id() + " destroyed " + nest.kind().name().toLowerCase() + " " + nest.id());
        } else if (operation.personnel() <= Math.max(2.0d, ReferenceFieldRules.MINIMUM_RAIDERS / world.profile().personScale())
                || ratio < ReferenceFieldRules.RAID_WITHDRAW_RATIO || engagement.days() >= ReferenceFieldRules.MAXIMUM_RAID_DAYS) {
            operation.outcome("raid_withdrawn");
            operation.status(ReferenceOperationStatus.RETURNING);
            engagement.status(ReferenceEngagementStatus.ATTACKERS_WITHDREW);
        }
    }

    private static void applyPostLosses(ReferenceWorld world, ReferenceFieldPost post, double killed, double wounded) {
        double total = Math.max(1.0e-9d, post.garrison());
        for (Integer settlementId : List.copyOf(post.garrisonBySettlement().keySet())) {
            double committed = post.garrisonBySettlement().getOrDefault(settlementId, 0.0d);
            ReferenceSettlement settlement = world.settlements().get(settlementId);
            if (settlement == null) continue;
            if (settlement.discretePeople()) {
                List<String> ids = post.residentIdsBySettlement().getOrDefault(settlementId, List.of());
                ReferenceCasualtyResult result = settlement.applyExposedCasualties(ids, killed * committed / total,
                        wounded * committed / total, "field_post_combat");
                ArrayList<String> survivors = new ArrayList<>();
                for (String id : ids) {
                    ReferenceResident resident = settlement.residents().resident(id);
                    if (resident != null && resident.condition() == ReferenceResidentCondition.ACTIVE) survivors.add(id);
                }
                post.mutableResidentIdsBySettlement().put(settlementId, survivors);
                post.mutableWoundedResidentIdsBySettlement().put(settlementId,
                        merge(post.mutableWoundedResidentIdsBySettlement().get(settlementId), result.woundedIds()));
                post.mutableGarrisonBySettlement().put(settlementId, (double) survivors.size());
                post.mutableWoundedBySettlement().put(settlementId,
                        (double) post.mutableWoundedResidentIdsBySettlement().get(settlementId).size());
            } else {
                double share = committed / total;
                double dead = killed * share;
                double hurt = wounded * share;
                settlement.population(settlement.population() - dead);
                settlement.mobilizedPersonnel(settlement.mobilizedPersonnel() - dead);
                post.mutableGarrisonBySettlement().put(settlementId, Math.max(0.0d, committed - dead - hurt));
                post.mutableWoundedBySettlement().merge(settlementId, hurt, Double::sum);
            }
        }
    }

    /** Apply one validated materialized resident event to its exact field-post custody. */
    static boolean applyExactResidentObservation(
            ReferenceFieldPost post,
            ReferenceSettlement settlement,
            String residentId,
            ReferenceGrayboxResidentObservation.Kind kind
    ) {
        List<String> active = new ArrayList<>(post.mutableResidentIdsBySettlement().getOrDefault(settlement.id(), List.of()));
        List<String> wounded = new ArrayList<>(post.mutableWoundedResidentIdsBySettlement().getOrDefault(settlement.id(), List.of()));
        boolean isActive = active.contains(residentId);
        boolean isWounded = wounded.contains(residentId);
        if (!isActive && !isWounded) return false;
        boolean changed = kind == ReferenceGrayboxResidentObservation.Kind.KILLED
                ? ReferenceResidentObservationMutation.apply(settlement, residentId, kind)
                : isActive && ReferenceResidentObservationMutation.apply(settlement, residentId, kind);
        if (!changed) return false;
        active.remove(residentId);
        wounded.remove(residentId);
        if (kind == ReferenceGrayboxResidentObservation.Kind.WOUNDED) {
            wounded.add(residentId);
            wounded.sort(String::compareTo);
        }
        post.mutableResidentIdsBySettlement().put(settlement.id(), active);
        post.mutableWoundedResidentIdsBySettlement().put(settlement.id(), merge(List.of(), wounded));
        post.mutableGarrisonBySettlement().put(settlement.id(), (double) active.size());
        post.mutableWoundedBySettlement().put(settlement.id(), (double) wounded.size());
        return true;
    }

    private static void applyOperationLosses(ReferenceWorld world, ReferenceOperation operation, double killed, double wounded) {
        double total = Math.max(1.0e-9d, operation.personnelBySettlement().values().stream().mapToDouble(Double::doubleValue).sum());
        for (Map.Entry<Integer, Double> entry : List.copyOf(operation.personnelBySettlement().entrySet())) {
            ReferenceSettlement settlement = world.settlements().get(entry.getKey());
            if (settlement == null) continue;
            double share = entry.getValue() / total;
            double dead = killed * share;
            double hurt = wounded * share;
            settlement.population(settlement.population() - dead);
            settlement.mobilizedPersonnel(settlement.mobilizedPersonnel() - dead);
            operation.mutablePersonnelBySettlement().put(entry.getKey(), Math.max(0.0d, entry.getValue() - dead - hurt));
            operation.mutableEvacuatedWoundedBySettlement().merge(entry.getKey(), hurt, Double::sum);
        }
    }

    private static void applyRoleLosses(ReferenceOperation operation, double casualties, boolean updatePersonnel) {
        double bounded = Math.min(Math.max(0.0d, casualties), operation.personnel());
        double total = Math.max(1.0e-9d, operation.personnel());
        for (Map.Entry<Integer, EnumMap<ReferenceHumanUnitKind, Double>> entry : operation.mutableUnitCompositionBySettlement().entrySet()) {
            double localTotal = Math.max(1.0e-9d, entry.getValue().values().stream().mapToDouble(Double::doubleValue).sum());
            double local = bounded * localTotal / total;
            for (ReferenceHumanUnitKind role : List.copyOf(entry.getValue().keySet())) {
                double amount = entry.getValue().get(role);
                double lost = Math.min(amount, local * amount / localTotal);
                entry.getValue().put(role, Math.max(0.0d, amount - lost));
                operation.mutableUnitLosses().merge(role, lost, Double::sum);
            }
            if (updatePersonnel) operation.mutablePersonnelBySettlement().put(entry.getKey(),
                    entry.getValue().values().stream().mapToDouble(Double::doubleValue).sum());
        }
        if (updatePersonnel) operation.personnel(Math.max(0.0d, operation.personnel() - bounded));
    }

    private static void evacuatePostGarrison(ReferenceWorld world, ReferenceFieldPost post) {
        for (Map.Entry<Integer, Double> entry : List.copyOf(post.garrisonBySettlement().entrySet())) {
            ReferenceSettlement settlement = world.settlements().get(entry.getKey());
            if (settlement == null) continue;
            if (settlement.discretePeople()) settlement.returnPeopleHome(post.residentIdsBySettlement().getOrDefault(entry.getKey(), List.of()));
            else settlement.mobilizedPersonnel(settlement.mobilizedPersonnel() - entry.getValue());
        }
        for (Map.Entry<Integer, Double> entry : List.copyOf(post.woundedBySettlement().entrySet())) {
            ReferenceSettlement settlement = world.settlements().get(entry.getKey());
            if (settlement == null) continue;
            if (settlement.discretePeople()) settlement.returnPeopleHome(post.woundedResidentIdsBySettlement().getOrDefault(entry.getKey(), List.of()));
            else {
                settlement.mobilizedPersonnel(settlement.mobilizedPersonnel() - entry.getValue());
                settlement.woundedPersonnel(settlement.woundedPersonnel() + entry.getValue());
            }
        }
        post.mutableGarrisonBySettlement().clear();
        post.mutableWoundedBySettlement().clear();
        post.mutableResidentIdsBySettlement().clear();
        post.mutableWoundedResidentIdsBySettlement().clear();
    }

    private static ReferenceSwarm swarmById(ReferenceWorld world, Integer id) {
        if (id == null) return null;
        return world.infection().swarms().stream().filter(item -> item.id() == id).findFirst().orElse(null);
    }

    private static double segmentDistance(double x, double y, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq <= 1.0e-9d) return Math.hypot(x - ax, y - ay);
        double ratio = Math.max(0.0d, Math.min(1.0d, ((x - ax) * dx + (y - ay) * dy) / lengthSq));
        return Math.hypot(x - (ax + dx * ratio), y - (ay + dy * ratio));
    }

    private static boolean active(ReferenceFieldPost post) {
        return post.status() == ReferenceFieldPostStatus.ACTIVE || post.status() == ReferenceFieldPostStatus.ISOLATED;
    }
    static Integer detailInteger(ReferenceOperation operation, String key) {
        Object value = operation.details().get(key);
        return value instanceof Integer integer ? integer : null;
    }
    private static double organDefence(ReferenceOrganKind kind) {
        return switch (kind) { case CORE -> .95d; case SYNAPSE -> .72d; case DIGESTIVE_POOL -> .93d; case BROOD_SAC -> .98d; case SPORULATOR -> .76d; };
    }
    static List<String> merge(List<String> first, List<String> second) {
        ArrayList<String> result = new ArrayList<>();
        if (first != null) result.addAll(first);
        result.addAll(second);
        result.sort(String::compareTo);
        ArrayList<String> unique = new ArrayList<>();
        String previous = null;
        for (String item : result) if (!item.equals(previous)) { unique.add(item); previous = item; }
        return unique;
    }
}
