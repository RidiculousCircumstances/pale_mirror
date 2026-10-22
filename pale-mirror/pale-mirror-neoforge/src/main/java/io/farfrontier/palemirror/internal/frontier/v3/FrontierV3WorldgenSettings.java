package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRulesets;

/** Fresh-world composition input shared by bootstrap and the graybox content provider.
 * It never reads a running simulation. Recovered generation receipts retain its digest. */
final class FrontierV3WorldgenSettings {
    private FrontierV3WorldgenSettings() { }
    static FrontierRuleset ruleset() {
        String selected = System.getProperty("pale_mirror.frontier_v3.ruleset");
        return selected == null ? FrontierRulesets.production() : FrontierRulesets.installed(selected);
    }
}
