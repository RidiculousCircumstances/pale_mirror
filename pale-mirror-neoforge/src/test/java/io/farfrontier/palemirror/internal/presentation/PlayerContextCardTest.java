package io.farfrontier.palemirror.internal.presentation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class PlayerContextCardTest {
    @Test
    void cardHasBoundedReplaceNotStackShape() {
        assertDoesNotThrow(() -> new PlayerContextCard("Northwatch", List.of("Stable", "Food reserve: 2 days"), 0x5EE27A));
        assertThrows(IllegalArgumentException.class, () -> new PlayerContextCard("Northwatch", List.of("a", "b", "c"), 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayerContextCard("North\nwatch", List.of(), 0));
    }
}
