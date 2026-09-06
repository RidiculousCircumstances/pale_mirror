package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReferenceResourceSiteTest {
    @Test
    void siteKeepsExactResourceOwnershipAndBoundedLocalStock() {
        ReferenceResourceSite site = new ReferenceResourceSite(
                7, ReferenceSiteKind.MINE, 12, -4, 0.75d, 2.5d, null);

        assertEquals(ReferenceResource.ORE, site.resource());
        assertFalse(site.operational());
        site.ownerId(3);
        assertTrue(site.operational());
        site.condition(0.02d);
        assertFalse(site.operational());
        site.condition(0.021d);
        assertTrue(site.operational());

        site.add(ReferenceResource.ORE, 8.0d);
        site.add(ReferenceResource.ORE, -3.0d);
        assertEquals(8.0d, site.amount(ReferenceResource.ORE));
        assertEquals(3.0d, site.remove(ReferenceResource.ORE, 3.0d));
        assertEquals(5.0d, site.remove(ReferenceResource.ORE, 20.0d));
        assertEquals(0.0d, site.remove(ReferenceResource.ORE, -1.0d));
        assertEquals(5.0d, site.distanceTo(15, 0));
    }
}
