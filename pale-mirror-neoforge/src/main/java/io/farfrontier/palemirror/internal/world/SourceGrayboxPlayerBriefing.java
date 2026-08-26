package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.Locale;
import java.util.Optional;

/**
 * Read-only player language for the source graybox.
 *
 * <p>This is deliberately a projection, not a second narrator or policy
 * engine. Every sentence is derived from one immutable source snapshot and
 * every offered physical action is one of that snapshot's declared
 * interactions. Technical {@link SourceGrayboxInspector} output remains the
 * separate exact QA view.</p>
 */
final class SourceGrayboxPlayerBriefing {
    private SourceGrayboxPlayerBriefing() { }

    static String settlementLabel(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Settlement settlement) {
        return "[SETTLEMENT] " + settlement.name() + " — " + title(settlement.civicState())
                + "\n" + settlementCause(snapshot, settlement)
                + "\nRight-click: risk and response.";
    }

    static String facilityLabel(ReferenceGrayboxSnapshot.Facility facility) {
        return "[BUILDING] " + words(facility.kind()) + "\nCondition: " + percent(facility.level())
                + "\nRight-click: role and consequence.";
    }

    static String resourceSiteLabel(ReferenceGrayboxSnapshot.ResourceSite site) {
        return "[SITE] " + words(site.kind()) + " #" + site.id() + "\nWorking condition: " + percent(site.condition())
                + "\nRight-click: output and risk.";
    }

    static String routeLabel(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Route route) {
        return "[ROUTE] " + settlementName(snapshot, route.settlementA()) + " → " + settlementName(snapshot, route.settlementB())
                + "\n" + routeStatus(route)
                + "\nRight-click: cause and effect.";
    }

    static String organLabel(ReferenceGrayboxSnapshot.HiveOrgan organ) {
        return "[HIVE] " + words(organ.kind()) + " #" + organ.id() + (organ.feral() ? " — FERAL" : "")
                + "\nVitality: " + number(organ.vitality()) + " | biomass: " + number(organ.biomass())
                + "\nRight-click: threat and response.";
    }

    static String fieldPostLabel(ReferenceGrayboxSnapshot.FieldPost post) {
        return "[FIELD POST] " + words(post.kind()) + " — " + title(post.status())
                + "\nIntegrity: " + number(post.integrity()) + " | garrison: " + post.garrison()
                + "\nRight-click: role and risk.";
    }

    static String fieldLinkLabel(ReferenceGrayboxSnapshot.FieldLink link) {
        return "[FIELD LINE] " + words(link.kind()) + " — " + title(link.status())
                + "\nIntegrity: " + number(link.integrity())
                + "\nRight-click: role and effect.";
    }

    static String activityLabel(ReferenceGrayboxSnapshot.Activity activity) {
        return "[OPERATION] " + words(activity.kind()) + " — " + title(activity.phase())
                + "\nPeople committed: " + number(activity.personnel())
                + "\nRight-click: purpose and current risk.";
    }

    static String cargoLabel(ReferenceGrayboxSnapshot.Cargo cargo) {
        return "[SUPPLIES] " + words(cargo.resource()) + "\nAmount: " + number(cargo.quantity())
                + "\nRight-click: owner and consequence.";
    }

    static String chrysalisLabel(ReferenceGrayboxSnapshot.Chrysalis chrysalis) {
        return "[HIVE CHRYSALIS] " + title(chrysalis.status())
                + "\nMatures in " + chrysalis.daysRemaining() + " day(s)"
                + "\nRight-click: threat and response.";
    }

    static String interactionLabel(ReferenceGrayboxSnapshot snapshot, SourceGrayboxPresentationLedger ledger,
                                   ReferenceGrayboxSnapshot.Interaction interaction) {
        int slots = availableSlots(ledger, interaction);
        return "[ACTION] " + actionTitle(interaction.kind())
                + "\n" + actionEffect(snapshot, interaction, interaction.totalWeight() / Math.max(1, slots))
                + "\nBreak marked block: " + slots + " point(s) remain.";
    }

    static String frontierReportLabel(ReferenceGrayboxSnapshot snapshot) {
        return "[FRONTIER REPORT] Day " + snapshot.day()
                + "\n" + forecast(snapshot)
                + "\nRight-click: recent changes.";
    }

    static String timelineLabel(ReferenceGrayboxSnapshot snapshot) {
        return "[TIMELINE] Day " + snapshot.day()
                + "\n" + Math.min(3, snapshot.events().size()) + " recent change(s) retained"
                + "\nRight-click: what changed and what is at risk.";
    }

    static Optional<String> at(ReferenceGrayboxSnapshot snapshot, int x, int z) {
        for (ReferenceGrayboxSnapshot.Settlement settlement : snapshot.settlements()) {
            if (contains(settlement.rectangle(), x, z)) return Optional.of(settlementBrief(snapshot, settlement));
        }
        for (ReferenceGrayboxSnapshot.Facility facility : snapshot.facilities()) {
            if (contains(facility.rectangle(), x, z)) return Optional.of(facilityBrief(snapshot, facility));
        }
        for (ReferenceGrayboxSnapshot.ResourceSite site : snapshot.resourceSites()) {
            if (contains(site.rectangle(), x, z)) return Optional.of(siteBrief(snapshot, site));
        }
        for (ReferenceGrayboxSnapshot.HiveOrgan organ : snapshot.hiveOrgans()) {
            if (contains(organ.rectangle(), x, z)) return Optional.of(organBrief(organ));
        }
        for (ReferenceGrayboxSnapshot.Route route : snapshot.routes()) {
            if (ReferenceGrayboxLayout.routeLine(route.start(), route.end()).stream().anyMatch(point -> at(point, x, z))) {
                return Optional.of(routeBrief(snapshot, route));
            }
        }
        for (ReferenceGrayboxSnapshot.FieldPost post : snapshot.fieldPosts()) {
            if (contains(post.rectangle(), x, z)) return Optional.of(fieldPostBrief(post));
        }
        for (ReferenceGrayboxSnapshot.FieldLink link : snapshot.fieldLinks()) {
            if (link.slots().stream().anyMatch(point -> at(point, x, z))) return Optional.of(fieldLinkBrief(link));
        }
        for (ReferenceGrayboxSnapshot.Activity activity : snapshot.activities()) {
            if (at(activity.position(), x, z)) return Optional.of(activityBrief(activity));
        }
        for (ReferenceGrayboxSnapshot.Cargo cargo : snapshot.cargoes()) {
            if (contains(cargo.rectangle(), x, z)) return Optional.of(cargoBrief(cargo));
        }
        for (ReferenceGrayboxSnapshot.Chrysalis chrysalis : snapshot.chrysalises()) {
            if (contains(chrysalis.rectangle(), x, z)) return Optional.of(chrysalisBrief(chrysalis));
        }
        return Optional.empty();
    }

    static Optional<String> forIdentity(ReferenceGrayboxSnapshot snapshot, String id, String kind) {
        if (id.equals("dashboard:summary")) return Optional.of(frontierReport(snapshot));
        if (id.equals("events:summary")) return Optional.of(timeline(snapshot));
        if (kind.equals("RESIDENT")) return snapshot.residents().stream().filter(value -> value.id().equals(id))
                .findFirst().map(value -> residentBrief(snapshot, value));
        if (kind.equals("BIOFORM")) return snapshot.bioforms().stream().filter(value -> value.id().equals(id))
                .findFirst().map(SourceGrayboxPlayerBriefing::bioformBrief);
        String subject = id.startsWith("interaction:") ? id.substring("interaction:".length()) : id;
        Optional<ReferenceGrayboxSnapshot.Interaction> interaction = snapshot.interactions().stream().filter(value -> value.id().equals(subject)).findFirst();
        if (interaction.isPresent()) return Optional.of(interactionBrief(snapshot, interaction.get()));
        Optional<ReferenceGrayboxSnapshot.Settlement> settlement = match(snapshot.settlements(), "settlement:", id,
                value -> Integer.toString(value.id()));
        if (settlement.isPresent()) return Optional.of(settlementBrief(snapshot, settlement.get()));
        Optional<ReferenceGrayboxSnapshot.Facility> facility = match(snapshot.facilities(), "facility:", id, ReferenceGrayboxSnapshot.Facility::id);
        if (facility.isPresent()) return Optional.of(facilityBrief(snapshot, facility.get()));
        Optional<ReferenceGrayboxSnapshot.ResourceSite> site = match(snapshot.resourceSites(), "site:", id, value -> Integer.toString(value.id()));
        if (site.isPresent()) return Optional.of(siteBrief(snapshot, site.get()));
        Optional<ReferenceGrayboxSnapshot.Route> route = match(snapshot.routes(), "route:", id, ReferenceGrayboxSnapshot.Route::id);
        if (route.isPresent()) return Optional.of(routeBrief(snapshot, route.get()));
        Optional<ReferenceGrayboxSnapshot.HiveOrgan> organ = match(snapshot.hiveOrgans(), "organ:", id, value -> Integer.toString(value.id()));
        if (organ.isPresent()) return Optional.of(organBrief(organ.get()));
        Optional<ReferenceGrayboxSnapshot.FieldPost> post = match(snapshot.fieldPosts(), "field-post:", id, value -> Integer.toString(value.id()));
        if (post.isPresent()) return Optional.of(fieldPostBrief(post.get()));
        Optional<ReferenceGrayboxSnapshot.FieldLink> link = match(snapshot.fieldLinks(), "field-link:", id, value -> Integer.toString(value.id()));
        if (link.isPresent()) return Optional.of(fieldLinkBrief(link.get()));
        Optional<ReferenceGrayboxSnapshot.Activity> activity = match(snapshot.activities(), "activity:", id, ReferenceGrayboxSnapshot.Activity::id);
        if (activity.isPresent()) return Optional.of(activityBrief(activity.get()));
        Optional<ReferenceGrayboxSnapshot.Cargo> cargo = match(snapshot.cargoes(), "cargo:", id, ReferenceGrayboxSnapshot.Cargo::id);
        if (cargo.isPresent()) return Optional.of(cargoBrief(cargo.get()));
        Optional<ReferenceGrayboxSnapshot.Chrysalis> chrysalis = match(snapshot.chrysalises(), "chrysalis:", id,
                value -> Integer.toString(value.organId()));
        return chrysalis.map(SourceGrayboxPlayerBriefing::chrysalisBrief);
    }

    static String acceptedReceipt(ReferenceGrayboxSnapshot before, ReferenceGrayboxSnapshot after,
                                  ReferenceGrayboxSnapshot.Interaction interaction) {
        return switch (interaction.kind()) {
            case "route_damaged" -> routeReceipt(before, after, interaction.subjectId());
            case "organ_damaged" -> organReceipt(before, after, interaction.subjectId());
            case "facility_damaged" -> "World changed: " + words(interaction.subjectId()) + " was damaged. Its settlement has less capacity."
                    + " Right-click the settlement to see the new risk.";
            case "site_damaged" -> "World changed: " + words(interaction.subjectId()) + " was damaged. Production will fall until the world repairs or replaces it.";
            case "operation_cargo_lost", "field_post_cargo_lost" -> "World changed: " + words(interaction.subjectId())
                    + " lost supplies. The operation may now run short.";
            case "field_post_damaged", "field_link_damaged" -> "World changed: " + words(interaction.subjectId())
                    + " was damaged. Front-line protection is weaker.";
            default -> "World changed: the marked " + words(interaction.subjectId()) + " accepted the physical damage.";
        };
    }

    static String acceptedEntityReceipt(ReferenceGrayboxSnapshot before, ReferenceGrayboxSnapshot after,
                                        SourceGrayboxMaterializer.ManagedEntity entity) {
        if (entity.kind().equals("RESIDENT")) {
            ReferenceGrayboxSnapshot.Resident resident = before.residents().stream().filter(value -> value.id().equals(entity.id())).findFirst().orElse(null);
            if (resident == null) return "World changed: a resident died and the settlement population was updated.";
            String name = settlementName(before, resident.homeSettlementId());
            double population = after.settlements().stream().filter(value -> value.id() == resident.homeSettlementId())
                    .mapToDouble(ReferenceGrayboxSnapshot.Settlement::population).findFirst().orElse(0.0d);
            return "World changed: a " + words(resident.occupation()) + " from " + name + " died. Population is now " + number(population)
                    + ". Right-click " + name + " to see the consequence.";
        }
        ReferenceGrayboxSnapshot.Bioform bioform = before.bioforms().stream().filter(value -> value.id().equals(entity.id())).findFirst().orElse(null);
        return bioform == null ? "World changed: a hive bioform was destroyed."
                : "World changed: " + words(bioform.kind()) + " was destroyed. The hive has one less active bioform in this area.";
    }

    private static String settlementBrief(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Settlement settlement) {
        return settlement.name() + " — " + title(settlement.civicState())
                + "\nState: " + number(settlement.population()) + " people; food for " + number(settlement.foodReserveDays()) + " day(s); threat " + percent(settlement.threat()) + "."
                + "\nCause: " + settlementCause(snapshot, settlement)
                + "\nRisk: " + settlementRisk(settlement)
                + "\nNext: inspect its routes, food site and nearby hive; marked blocks show exact physical interventions.";
    }

    private static String facilityBrief(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Facility facility) {
        return words(facility.kind()) + " for " + settlementName(snapshot, facility.settlementId())
                + "\nState: " + percent(facility.level()) + " operational."
                + "\nRole: " + facilityRole(facility.kind())
                + "\nRisk: destroying marked structure blocks removes this capacity from the source world."
                + "\nNext: right-click its settlement to see why this capacity matters now.";
    }

    private static String siteBrief(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.ResourceSite site) {
        return words(site.kind()) + " site #" + site.id() + " — owned by " + settlementName(snapshot, site.ownerSettlementId())
                + "\nState: " + percent(site.condition()) + " condition; output capacity " + number(site.capacity()) + "."
                + "\nRisk: contamination " + percent(site.contamination()) + " can cut useful output."
                + "\nNext: follow its route to see who depends on it.";
    }

    private static String routeBrief(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Route route) {
        return settlementName(snapshot, route.settlementA()) + " → " + settlementName(snapshot, route.settlementB())
                + "\nState: " + routeStatus(route) + "; capacity " + number(route.capacity()) + "."
                + "\nCause: " + routeCause(route)
                + "\nRisk: a blocked route can starve or isolate its endpoints."
                + "\nNext: follow the blue corridor; marked elevated blocks are its exact damage points.";
    }

    private static String organBrief(ReferenceGrayboxSnapshot.HiveOrgan organ) {
        return words(organ.kind()) + " hive organ #" + organ.id() + (organ.feral() ? " — FERAL" : "")
                + "\nState: vitality " + number(organ.vitality()) + "; biomass " + number(organ.biomass()) + "."
                + "\nRisk: living hive tissue supports infection, growth and hostile bioforms."
                + "\nNext: marked blocks are exact damage points; destroying one immediately reduces this organ's vitality.";
    }

    private static String fieldPostBrief(ReferenceGrayboxSnapshot.FieldPost post) {
        return words(post.kind()) + " field post #" + post.id() + " — " + title(post.status())
                + "\nState: integrity " + number(post.integrity()) + "; garrison " + post.garrison() + "; wounded " + post.wounded() + "."
                + "\nRole: " + fieldPostRole(post.kind())
                + "\nRisk: loss weakens the connected campaign."
                + "\nNext: inspect nearby field lines and supply pallets.";
    }

    private static String fieldLinkBrief(ReferenceGrayboxSnapshot.FieldLink link) {
        return words(link.kind()) + " field line #" + link.id() + " — " + title(link.status())
                + "\nState: integrity " + number(link.integrity()) + "."
                + "\nRole: connects field posts " + link.postA() + " and " + link.postB() + "."
                + "\nRisk: breaks can isolate a frontline position."
                + "\nNext: marked blocks are exact damage points.";
    }

    private static String activityBrief(ReferenceGrayboxSnapshot.Activity activity) {
        return words(activity.kind()) + " operation — " + title(activity.phase())
                + "\nState: " + number(activity.personnel()) + " people committed; indicator " + number(activity.indicator()) + "."
                + "\nPurpose: " + activityPurpose(activity.kind())
                + "\nRisk: the operation continues according to the source world, including supply and enemy pressure."
                + "\nNext: inspect its posts, links and cargo.";
    }

    private static String cargoBrief(ReferenceGrayboxSnapshot.Cargo cargo) {
        return words(cargo.resource()) + " supplies for " + words(cargo.ownerKind()) + " #" + cargo.ownerId()
                + "\nState: " + number(cargo.quantity()) + " units ready."
                + "\nRisk: loss makes the owner less able to continue."
                + "\nNext: marked blocks are the exact physical cargo-loss points.";
    }

    private static String chrysalisBrief(ReferenceGrayboxSnapshot.Chrysalis chrysalis) {
        return "Hive chrysalis at organ #" + chrysalis.organId() + " — " + title(chrysalis.status())
                + "\nState: " + chrysalis.daysRemaining() + " day(s) to resolution; biomass committed " + number(chrysalis.biomassCommitted()) + "."
                + "\nRisk: a mature chrysalis strengthens the hive."
                + "\nNext: destroy the linked organ's marked tissue to change its future.";
    }

    private static String interactionBrief(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Interaction interaction) {
        return actionTitle(interaction.kind())
                + "\nEffect: " + actionEffect(snapshot, interaction, interaction.totalWeight() / Math.max(1, interaction.slots().size()))
                + "\nAction: break one marked block to apply this exact effect once."
                + "\nResult: you receive an immediate receipt; the world then continues from its new canonical state.";
    }

    private static String residentBrief(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Resident resident) {
        return "Resident of " + settlementName(snapshot, resident.homeSettlementId())
                + "\nRole: " + words(resident.occupation()) + "; condition " + words(resident.condition()) + "."
                + "\nLocation: " + words(resident.location()) + "."
                + "\nRisk: death is immediate, permanent canonical population loss.";
    }

    private static String frontierReport(ReferenceGrayboxSnapshot snapshot) {
        return "Frontier report — day " + snapshot.day()
                + "\nForecast: " + forecast(snapshot)
                + "\nPriority: " + priority(snapshot)
                + "\nNext: use the nearby TIMELINE board, then follow the named route or hive landmark.";
    }

    private static String timeline(ReferenceGrayboxSnapshot snapshot) {
        StringBuilder result = new StringBuilder("Frontier timeline — day ").append(snapshot.day())
                .append("\nRecent canonical changes:");
        int start = Math.max(0, snapshot.events().size() - 3);
        if (start == snapshot.events().size()) result.append("\n• No recent source event is retained.");
        for (int index = start; index < snapshot.events().size(); index++) result.append("\n• ").append(playerEvent(snapshot.events().get(index)));
        return result.append("\nForecast: ").append(forecast(snapshot)).toString();
    }

    private static String bioformBrief(ReferenceGrayboxSnapshot.Bioform bioform) {
        return "Hive " + words(bioform.kind()) + (bioform.feral() ? " — FERAL" : "")
                + "\nState: " + title(bioform.phase()) + "; swarm " + bioform.swarmId() + "."
                + "\nRisk: this is one real source bioform, not a visual crowd."
                + "\nAction: killing it immediately removes it from the canonical hive.";
    }

    private static String forecast(ReferenceGrayboxSnapshot snapshot) {
        Optional<ReferenceGrayboxSnapshot.Settlement> siege = snapshot.settlements().stream()
                .filter(value -> value.alive() && value.civicState().equals("siege")).findFirst();
        if (siege.isPresent()) return siege.get().name() + " remains under siege; continued pressure can kill residents.";
        Optional<ReferenceGrayboxSnapshot.Settlement> emergency = snapshot.settlements().stream()
                .filter(value -> value.alive() && value.civicState().equals("emergency")).findFirst();
        if (emergency.isPresent()) return emergency.get().name() + " remains in emergency; food, illness or threat can escalate.";
        Optional<ReferenceGrayboxSnapshot.Route> closed = snapshot.routes().stream()
                .filter(value -> value.quarantined() || value.disrupted() || value.capacity() <= 0.0d).findFirst();
        if (closed.isPresent()) return "the route " + settlementName(snapshot, closed.get().settlementA()) + " → "
                + settlementName(snapshot, closed.get().settlementB()) + " is impaired; supplies may fail.";
        Optional<ReferenceGrayboxSnapshot.Chrysalis> chrysalis = snapshot.chrysalises().stream().findFirst();
        if (chrysalis.isPresent()) return "a hive chrysalis may mature in " + chrysalis.get().daysRemaining() + " day(s).";
        return "no immediate settlement crisis is projected; routes and hive tissue can still change the region.";
    }

    private static String priority(ReferenceGrayboxSnapshot snapshot) {
        Optional<ReferenceGrayboxSnapshot.Settlement> lowFood = snapshot.settlements().stream().filter(ReferenceGrayboxSnapshot.Settlement::alive)
                .min(java.util.Comparator.comparingDouble(ReferenceGrayboxSnapshot.Settlement::foodReserveDays));
        if (lowFood.isPresent() && lowFood.get().foodReserveDays() < 3.0d) {
            return lowFood.get().name() + " has food for only " + number(lowFood.get().foodReserveDays()) + " day(s).";
        }
        Optional<ReferenceGrayboxSnapshot.HiveOrgan> organ = snapshot.hiveOrgans().stream().filter(value -> value.vitality() > 0.0d).findFirst();
        return organ.map(value -> "hive organ #" + value.id() + " remains active nearby.")
                .orElse("watch the routes and settlements for the next source change.");
    }

    private static String playerEvent(String source) {
        String event = source.replaceFirst("^D[0-9]+: ", "").replace('_', ' ');
        if (event.startsWith("physical resident killed ")) return "a resident was killed in the physical world; population was updated.";
        if (event.startsWith("physical bioform killed ")) return "a hive bioform was killed in the physical world.";
        if (event.startsWith("physical route damaged ")) return "a physical route-damage action was accepted by the source world.";
        if (event.startsWith("physical organ damaged ")) return "a physical hive-organ damage action was accepted by the source world.";
        return event;
    }

    private static String routeReceipt(ReferenceGrayboxSnapshot before, ReferenceGrayboxSnapshot after, String routeId) {
        ReferenceGrayboxSnapshot.Route oldRoute = before.routes().stream().filter(value -> value.id().equals(routeId)).findFirst().orElse(null);
        ReferenceGrayboxSnapshot.Route newRoute = after.routes().stream().filter(value -> value.id().equals(routeId)).findFirst().orElse(null);
        if (oldRoute == null || newRoute == null) return "World changed: route damage was accepted by the simulation.";
        return "World changed: route " + settlementName(before, oldRoute.settlementA()) + " → " + settlementName(before, oldRoute.settlementB())
                + " lost " + number(oldRoute.capacity() - newRoute.capacity()) + " capacity (now " + number(newRoute.capacity()) + ")."
                + " Its endpoints may now lose supplies.";
    }

    private static String organReceipt(ReferenceGrayboxSnapshot before, ReferenceGrayboxSnapshot after, String organId) {
        int id = integerSuffix(organId);
        ReferenceGrayboxSnapshot.HiveOrgan oldOrgan = before.hiveOrgans().stream().filter(value -> value.id() == id).findFirst().orElse(null);
        ReferenceGrayboxSnapshot.HiveOrgan newOrgan = after.hiveOrgans().stream().filter(value -> value.id() == id).findFirst().orElse(null);
        if (oldOrgan == null) return "World changed: hive-organ damage was accepted by the simulation.";
        if (newOrgan == null) return "World changed: hive organ #" + id + " was destroyed. Its local hive support is gone.";
        return "World changed: hive organ #" + id + " lost " + number(oldOrgan.vitality() - newOrgan.vitality())
                + " vitality (now " + number(newOrgan.vitality()) + ").";
    }

    private static String settlementCause(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Settlement settlement) {
        if (settlement.civicState().equals("siege")) return "direct hive pressure has forced a siege.";
        if (settlement.civicState().equals("emergency")) return settlement.illnessBurden() > 0.0d
                ? "illness or infection has triggered an emergency." : "threat has triggered an emergency.";
        if (settlement.civicState().equals("watch")) return "frontier threat is being watched.";
        if (settlement.civicState().equals("recovery")) return "recent pressure is receding, but recovery is not complete.";
        Optional<ReferenceGrayboxSnapshot.Route> disrupted = snapshot.routes().stream()
                .filter(route -> route.settlementA() == settlement.id() || route.settlementB() == settlement.id())
                .filter(route -> route.disrupted() || route.quarantined() || route.capacity() <= 0.0d).findFirst();
        return disrupted.isPresent() ? "a connected route is impaired." : "supplies and civic order are currently stable.";
    }

    private static String settlementRisk(ReferenceGrayboxSnapshot.Settlement settlement) {
        if (!settlement.alive()) return "the settlement has collapsed; its former infrastructure remains as evidence.";
        if (settlement.civicState().equals("siege")) return "continued pressure can kill residents and destroy the settlement.";
        if (settlement.civicState().equals("emergency")) return "food, illness or threat can escalate into siege.";
        if (settlement.foodReserveDays() < 3.0d) return "food reserve is short; any route loss matters immediately.";
        if (settlement.civicState().equals("recovery")) return "recovery can fail if threat or supply loss returns.";
        return "new threat, disease or route damage can still change this situation.";
    }

    private static String routeStatus(ReferenceGrayboxSnapshot.Route route) {
        if (route.quarantined()) return "QUARANTINED — infection has closed traffic";
        if (route.disrupted()) return "DISRUPTED — damage has cut traffic";
        if (route.capacity() <= 0.0d) return "BLOCKED — no current capacity";
        return "OPEN — capacity " + number(route.capacity());
    }

    private static String routeCause(ReferenceGrayboxSnapshot.Route route) {
        if (route.quarantined()) return "infection on the corridor required quarantine.";
        if (route.disrupted()) return "physical damage has disrupted this route.";
        if (route.capacity() <= 0.0d) return "no usable capacity is currently available.";
        if (route.risk() > 0.35d || route.infection() > 0.10d) return "the route remains open, but infection or threat makes it unsafe.";
        return "the corridor is physically and economically usable today.";
    }

    private static String actionTitle(String kind) {
        return switch (kind) {
            case "route_damaged" -> "DAMAGE ROUTE";
            case "organ_damaged" -> "DAMAGE HIVE ORGAN";
            case "facility_damaged" -> "DAMAGE BUILDING";
            case "site_damaged" -> "DAMAGE RESOURCE SITE";
            case "operation_cargo_lost", "field_post_cargo_lost" -> "DESTROY SUPPLIES";
            case "field_post_damaged" -> "DAMAGE FIELD POST";
            case "field_link_damaged" -> "DAMAGE FIELD LINE";
            default -> "CHANGE SOURCE OBJECT";
        };
    }

    private static String actionEffect(ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxSnapshot.Interaction interaction, double weight) {
        return switch (interaction.kind()) {
            case "route_damaged" -> "removes " + number(weight) + " route capacity and can cut supply between its settlements.";
            case "organ_damaged" -> "removes " + number(weight) + " hive vitality and weakens this organ.";
            case "facility_damaged" -> "removes " + number(weight) + " building capacity from its settlement.";
            case "site_damaged" -> "removes " + number(weight) + " of site condition and future output.";
            case "operation_cargo_lost", "field_post_cargo_lost" -> "removes " + number(weight) + " supplies from its owner.";
            case "field_post_damaged" -> "removes " + number(weight) + " field-post integrity.";
            case "field_link_damaged" -> "removes " + number(weight) + " field-line integrity.";
            default -> "applies its declared source effect.";
        };
    }

    private static String facilityRole(String kind) {
        return switch (kind) {
            case "clinic" -> "treats illness and supports survival.";
            case "armory", "fortification" -> "supports settlement defence.";
            case "workshop" -> "supports production and repair.";
            case "civic_hall" -> "supports civic coordination.";
            default -> "contributes a settlement capability.";
        };
    }

    private static String fieldPostRole(String kind) {
        return switch (kind) {
            case "observation_post" -> "observes the frontier.";
            case "checkpoint" -> "controls a dangerous passage.";
            case "strongpoint" -> "holds a defensive position.";
            case "forward_base" -> "supports field operations.";
            default -> "supports the connected campaign.";
        };
    }

    private static String activityPurpose(String kind) {
        return switch (kind) {
            case "recon", "scout_spores" -> "gather information before a larger decision.";
            case "patrol", "defend", "escort" -> "protect people, routes or a settlement.";
            case "cleanse", "cleanse_perimeter", "reclaim", "raid_nest" -> "reduce hive control and reclaim ground.";
            case "evacuate", "evacuate_wounded" -> "move people away from immediate danger.";
            case "resupply", "reinforce" -> "keep a field position active.";
            default -> "changes the frontier according to its current phase.";
        };
    }

    private static int availableSlots(SourceGrayboxPresentationLedger ledger, ReferenceGrayboxSnapshot.Interaction interaction) {
        int result = 0;
        for (int index = 0; index < interaction.slots().size(); index++) {
            SourceGrayboxPresentationLedger.Claim claim = ledger.claim("interaction:" + interaction.id() + ":" + index);
            if (claim == null || (!claim.consumed() && !claim.conflicted())) result++;
        }
        return result;
    }

    private static String settlementName(ReferenceGrayboxSnapshot snapshot, int id) {
        return snapshot.settlements().stream().filter(value -> value.id() == id).map(ReferenceGrayboxSnapshot.Settlement::name)
                .findFirst().orElse("settlement #" + id);
    }

    private static <T> Optional<T> match(java.util.List<T> values, String prefix, String fullId, java.util.function.Function<T, String> id) {
        if (!fullId.startsWith(prefix)) return Optional.empty();
        String expected = fullId.substring(prefix.length());
        return values.stream().filter(value -> id.apply(value).equals(expected)).findFirst();
    }

    private static boolean contains(ReferenceGrayboxLayout.Rectangle rectangle, int x, int z) {
        return x >= rectangle.x() && x < rectangle.x() + rectangle.width() && z >= rectangle.z() && z < rectangle.z() + rectangle.depth();
    }

    private static boolean at(ReferenceGrayboxLayout.Point point, int x, int z) { return point.x() == x && point.z() == z; }

    private static int integerSuffix(String value) {
        int separator = value.lastIndexOf(':');
        if (separator < 0) return -1;
        try { return Integer.parseInt(value.substring(separator + 1)); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static String words(String value) { return value.replace('_', ' '); }
    private static String title(String value) { return words(value).toUpperCase(Locale.ROOT); }
    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static String percent(double value) { return String.format(Locale.ROOT, "%.0f%%", Math.max(0.0d, Math.min(1.0d, value)) * 100.0d); }
}
