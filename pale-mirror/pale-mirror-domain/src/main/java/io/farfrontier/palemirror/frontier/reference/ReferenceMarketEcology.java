package io.farfrontier.palemirror.frontier.reference;

import java.util.Locale;
import java.util.Objects;

/**
 * The market's narrow, source-ordered view of physical ecology.
 *
 * <p>The market never owns ecological state.  Its world root installs this
 * authority so a site can read its current yield immediately before work and
 * commit that site's withdrawal before the next site or investment decision.
 * This is deliberately a callback rather than a copied market-side ecology
 * ledger: Python's {@code MarketEconomy._produce} performs the same direct
 * read and write against {@code InfectionModel.ecosystem}.</p>
 */
interface ReferenceMarketEcology {
    double humanOutputFactor(ReferenceResourceSite site);

    double haulInfection(ReferenceResourceSite site, ReferenceSettlement owner);

    void humanExtract(ReferenceResourceSite site, double amount);

    static ReferenceMarketEcology infection(ReferenceInfectionModel infection) {
        ReferenceInfectionModel required = Objects.requireNonNull(infection, "infection");
        return new ReferenceMarketEcology() {
            @Override
            public double humanOutputFactor(ReferenceResourceSite site) {
                return required.ecosystem().humanOutputFactor(kind(site), site.x(), site.y());
            }

            @Override
            public double haulInfection(ReferenceResourceSite site, ReferenceSettlement owner) {
                return required.routeInfection(owner.x(), owner.y(), site.x(), site.y());
            }

            @Override
            public void humanExtract(ReferenceResourceSite site, double amount) {
                required.ecosystem().humanExtract(kind(site), site.x(), site.y(), amount);
            }
        };
    }

    private static String kind(ReferenceResourceSite site) {
        return site.kind().name().toLowerCase(Locale.ROOT);
    }
}
