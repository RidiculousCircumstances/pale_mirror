package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableKeyedInputViewTest {
    @Test void independentSubjectsRetainExactInputsButChangesAndEvictionRecompute() {
        var view = new ImmutableKeyedInputView<String, Object>(2);
        var world = new Object(); var input = new Object(); var changed = new Object();
        var a = view.get(world, "a", List.of(input), Object::new);
        var b = view.get(world, "b", List.of(input), Object::new);
        assertSame(a, view.get(world, "a", List.of(input), Object::new));
        assertNotSame(a, view.get(world, "a", List.of(changed), Object::new));
        assertNotSame(a, view.get(world, "a", List.of(input), Object::new), "rollback rechecks the exact basis");
        view.get(world, "c", List.of(input), Object::new);
        assertNotSame(b, view.get(world, "b", List.of(input), Object::new), "bounded cache may evict, never fabricate");
        var fresh = view.get(new Object(), "b", List.of(input), Object::new);
        assertNotSame(b, fresh, "another generation cannot inherit evidence");
    }
}
