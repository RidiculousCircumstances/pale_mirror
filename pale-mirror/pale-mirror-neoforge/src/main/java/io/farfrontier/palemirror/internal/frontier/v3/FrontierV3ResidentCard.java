package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.combat.*;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.internal.presentation.PlayerContextCard;
import java.util.ArrayList;
import java.util.Locale;

/** Compact immutable player view; no scheduling, repairs or world mutation. */
final class FrontierV3ResidentCard {
    private FrontierV3ResidentCard() { }
    static PlayerContextCard from(FrontierWorldState state, SubjectId actor, long tick,
                                 FrontierV3MinecraftGoalNavigation.Observation navigation) {
        var person = java.util.Objects.requireNonNull(state.humanPopulation().resident(actor), "inspected resident");
        var view = ResidentPresentation.from(state, actor);
        var lines = new ArrayList<String>();
        lines.add("Settlement: " + view.settlement());
        lines.add("Task: " + view.task());
        String activity = switch (navigation.status()) {
            case "WAITING" -> "Waiting for route";
            case "WAITING_FOR_CROSSING" -> "Waiting for passage";
            case "BLOCKED", "PATH_UNAVAILABLE", "KNOWN_NO_PATH", "PATH_STALLED", "TRAFFIC_BLOCKED" -> "Route blocked";
            case "IDLE" -> state.actorMovements().containsKey(actor) ? "Awaiting movement" : view.activity();
            default -> view.activity();
        };
        lines.add("Role: " + view.role() + " · " + activity);
        lines.add("Combat style: " + state.bootstrap().ruleset().melee().styles().require(person.combatStyle()).name());
        var combat=person.combatProfile();var attributes=combat.attributes();
        lines.add("Combat: Sword "+combat.skill(CombatSkillId.SWORD)
            +" · Evade "+combat.skill(CombatSkillId.EVASION)
            +" · Defence "+combat.skill(CombatSkillId.WEAPON_DEFENCE));
        lines.add("Str "+attributes.strength()+" · Agi "+attributes.agility()+" · End "+attributes.endurance()+" · Per "+attributes.perception()+" · Tough "+attributes.toughness());
        lines.add("Skills: Agr "+person.skill(ResidentSkill.AGRICULTURE)+" · Build "+person.skill(ResidentSkill.BUILDING)
            +" · Craft "+person.skill(ResidentSkill.CRAFTING)+" · Sec "+person.skill(ResidentSkill.SECURITY)+" · Med "+person.skill(ResidentSkill.MEDICINE)+" · Haul "+person.skill(ResidentSkill.LOGISTICS));
        lines.add("Abilities: Agr "+person.capability(HumanCapability.AGRICULTURE)+" · Ext "+person.capability(HumanCapability.EXTRACTION)
            +" · Ind "+person.capability(HumanCapability.INDUSTRY)+" · Eng "+person.capability(HumanCapability.ENGINEERING)
            +" · Log "+person.capability(HumanCapability.LOGISTICS)+" · Med "+person.capability(HumanCapability.MEDICINE)
            +" · Sec "+person.capability(HumanCapability.SECURITY)+" · Civic "+person.capability(HumanCapability.CIVIC));
        lines.add("Hunger ×" + rate(person.characteristics().effectiveMetabolismPermille(tick))
                + " · " + state.humanPopulation().nutrition(actor).status().name().toLowerCase(Locale.ROOT));
        var modifiers = person.characteristics().workModifiers().modifiers().values().stream()
                .sorted(java.util.Comparator.comparing(ResidentWorkModifiers.Modifier::sourceId)).toList();
        String details = modifiers.isEmpty() ? "none" : modifiers.stream()
                .map(m -> m.capability().name().substring(0, 3) + " ×" + rate(m.factorPermille()))
                .collect(java.util.stream.Collectors.joining(" · "));
        // Bounded optional detail; exact modifiers remain solely in the resident profile.
        lines.add("Work modifiers: " + (details.length() > 96 ? details.substring(0, 95) + "…" : details));
        return new PlayerContextCard(view.name(), lines, 0xF4B942);
    }
    private static String rate(int value) { return String.format(Locale.ROOT, "%.2f", value / 1000.0); }
}
