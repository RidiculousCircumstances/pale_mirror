package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.Locale;

/**
 * Read-only, position-bound text view for the deliberately dense source graybox.
 *
 * <p>The map uses colour and bounded metric towers for an overview. This
 * inspector exposes the exact immutable source values at the object under the
 * operator, rather than putting an unbounded paragraph above every sector.
 * It performs no observation, write, or simulation decision.</p>
 */
final class SourceGrayboxInspector {
    private SourceGrayboxInspector() { }

    static String at(ReferenceGrayboxSnapshot snapshot, int x, int z) {
        StringBuilder result = new StringBuilder("[SOURCE day=").append(snapshot.day()).append("] x=").append(x).append(" z=").append(z);
        boolean found = false;
        for (ReferenceGrayboxSnapshot.Sector sector : snapshot.sectors()) if (contains(sector.rectangle(), x, z)) {
            append(result, "[V2] sector=" + sector.key() + " control=" + sector.control() + " infection=" + number(sector.infection())
                    + " spores=" + number(sector.sporeLoad()) + " humanAccess=" + number(sector.humanAccess())
                    + " hiveInfluence=" + number(sector.hiveInfluence()) + " supplied=" + sector.supplied());
            found = true;
        }
        for (ReferenceGrayboxSnapshot.Cell cell : snapshot.cells()) if (contains(cell.rectangle(), x, z)) {
            append(result, "[E] cell=" + cell.x() + "," + cell.y() + " infection=" + number(cell.infection()) + " organic="
                    + number(cell.organicMass()) + " moisture=" + number(cell.moisture()) + " signal=" + number(cell.signal()));
            found = true;
        }
        for (ReferenceGrayboxSnapshot.Settlement settlement : snapshot.settlements()) if (contains(settlement.rectangle(), x, z)) {
            append(result, "[S] #" + settlement.id() + " " + settlement.name() + " population=" + number(settlement.population())
                    + " integrity=" + number(settlement.integrity()) + " threat=" + number(settlement.threat()) + " illness="
                    + number(settlement.illnessBurden()) + " civic=" + settlement.civicState() + " foodDays="
                    + number(settlement.foodReserveDays()) + " ration=" + number(settlement.rationFraction()));
            snapshot.readouts().stream().filter(readout -> contains(settlement.rectangle(), readout.position().x(), readout.position().z()))
                    .forEach(readout -> append(result, "[" + readout.category() + "] " + readout.text()));
            found = true;
        }
        for (ReferenceGrayboxSnapshot.Facility facility : snapshot.facilities()) if (contains(facility.rectangle(), x, z)) {
            append(result, "[F] " + facility.id() + " kind=" + facility.kind() + " level=" + number(facility.level()));
            found = true;
        }
        for (ReferenceGrayboxSnapshot.ResourceSite site : snapshot.resourceSites()) if (contains(site.rectangle(), x, z)) {
            append(result, "[R] #" + site.id() + " kind=" + site.kind() + " owner=" + site.ownerSettlementId() + " capacity="
                    + number(site.capacity()) + " condition=" + number(site.condition()) + " contamination=" + number(site.contamination()));
            found = true;
        }
        for (ReferenceGrayboxSnapshot.HiveOrgan organ : snapshot.hiveOrgans()) if (contains(organ.rectangle(), x, z)) {
            append(result, "[H] #" + organ.id() + " kind=" + organ.kind() + " biomass=" + number(organ.biomass()) + " vitality="
                    + number(organ.vitality()) + " feral=" + organ.feral());
            found = true;
        }
        for (ReferenceGrayboxSnapshot.Route route : snapshot.routes()) if (ReferenceGrayboxLayout.routeSlots(route.start(), route.end()).stream()
                .anyMatch(point -> at(point, x, z))) {
            append(result, "[T] " + route.id() + " settlements=" + route.settlementA() + "→" + route.settlementB() + " capacity="
                    + number(route.capacity()) + " risk=" + number(route.risk()) + " infection=" + number(route.infection())
                    + " quarantined=" + route.quarantined() + " disrupted=" + route.disrupted());
            found = true;
        }
        for (ReferenceGrayboxSnapshot.FieldLink link : snapshot.fieldLinks()) if (link.slots().stream().anyMatch(point -> at(point, x, z))) {
            append(result, "[L] #" + link.id() + " campaign=" + link.campaignId() + " kind=" + link.kind() + " posts=" + link.postA()
                    + "→" + link.postB() + " status=" + link.status() + " integrity=" + number(link.integrity()));
            found = true;
        }
        for (ReferenceGrayboxSnapshot.Activity activity : snapshot.activities()) if (at(activity.position(), x, z)) {
            append(result, "[A] " + activity.id() + " family=" + activity.family() + " kind=" + activity.kind() + " phase="
                    + activity.phase() + " personnel=" + number(activity.personnel()) + " indicator=" + number(activity.indicator())
                    + " terminal=" + activity.terminal());
            found = true;
        }
        for (ReferenceGrayboxSnapshot.FieldPost post : snapshot.fieldPosts()) if (contains(post.rectangle(), x, z)) {
            append(result, "[P] #" + post.id() + " campaign=" + post.campaignId() + " kind=" + post.kind() + " status=" + post.status()
                    + " integrity=" + number(post.integrity()) + " garrison=" + post.garrison() + " wounded=" + post.wounded()
                    + " modules=" + String.join(",", post.modules()));
            found = true;
        }
        for (ReferenceGrayboxSnapshot.Cargo cargo : snapshot.cargoes()) if (contains(cargo.rectangle(), x, z)) {
            append(result, "[C] " + cargo.id() + " owner=" + cargo.ownerKind() + "#" + cargo.ownerId() + " resource=" + cargo.resource()
                    + " quantity=" + number(cargo.quantity()));
            found = true;
        }
        for (ReferenceGrayboxSnapshot.Resident resident : snapshot.residents()) if (at(resident.position(), x, z)) {
            append(result, "[PERSON] " + resident.id() + " home=" + resident.homeSettlementId() + " occupation=" + resident.occupation()
                    + " class=" + resident.economicClass() + " location=" + resident.location() + " condition=" + resident.condition()
                    + " role=" + (resident.deploymentRole() == null ? "—" : resident.deploymentRole()));
            found = true;
        }
        for (ReferenceGrayboxSnapshot.Bioform bioform : snapshot.bioforms()) if (at(bioform.position(), x, z)) {
            append(result, "[BIOFORM] " + bioform.id() + " swarm=" + bioform.swarmId() + " kind=" + bioform.kind() + " phase="
                    + bioform.phase() + " feral=" + bioform.feral());
            found = true;
        }
        for (ReferenceGrayboxSnapshot.Chrysalis chrysalis : snapshot.chrysalises()) if (contains(chrysalis.rectangle(), x, z)) {
            append(result, "[C] organ=" + chrysalis.organId() + " sector=" + chrysalis.sectorKey() + " status=" + chrysalis.status()
                    + " days=" + chrysalis.daysRemaining() + " biomass=" + number(chrysalis.biomassCommitted()));
            found = true;
        }
        for (ReferenceGrayboxSnapshot.Interaction interaction : snapshot.interactions()) if (interaction.slots().stream().anyMatch(point -> at(point, x, z))) {
            append(result, "[X] " + interaction.id() + " target=" + interaction.subjectId() + " kind=" + interaction.kind() + " total="
                    + number(interaction.totalWeight()) + " slots=" + interaction.slots().size());
            found = true;
        }
        if (!found) result.append("; no source object at this position. Use the V2 towers: red infection, purple spores, cyan human access, lime hive influence.");
        return result.toString();
    }

    private static boolean contains(ReferenceGrayboxLayout.Rectangle rectangle, int x, int z) {
        return x >= rectangle.x() && x < rectangle.x() + rectangle.width() && z >= rectangle.z() && z < rectangle.z() + rectangle.depth();
    }

    private static boolean at(ReferenceGrayboxLayout.Point point, int x, int z) { return point.x() == x && point.z() == z; }

    private static void append(StringBuilder result, String text) { result.append('\n').append(text); }

    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}
