package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset;

/** Immutable identity validation; never serialize the whole ruleset per visited block. */
final class FrontierV3GenerationConfiguration {
    private static FrontierRuleset rules;
    private static String fingerprint;
    private FrontierV3GenerationConfiguration() { }
    static synchronized void require(FrontierBootstrap bootstrap, long seed, String configuration) {
        if (rules != bootstrap.ruleset()) {
            rules = bootstrap.ruleset(); fingerprint = rules.contentSha256();
        }
        if (seed != bootstrap.seed() || !fingerprint.equals(configuration))
            throw new IllegalStateException("generated baseline belongs to a different bootstrap configuration");
    }
}
