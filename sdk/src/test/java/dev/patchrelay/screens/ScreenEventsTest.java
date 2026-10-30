package dev.patchrelay.screens;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenEventsTest {
    @Test public void coalescesTypingWithoutCrossingClicks() {
        ScreenEvents queue = new ScreenEvents();
        queue.add(new ScreenEvent("email", "a"));
        queue.add(new ScreenEvent("email", "ab"));
        queue.add(new ScreenEvent("pay", null));
        queue.add(new ScreenEvent("email", ""));
        assertEquals("ab", queue.next().getValue());
        assertEquals("pay", queue.next().getAction());
        assertEquals("", queue.next().getValue());
        assertNull(queue.next());
    }
    @Test public void boundsPendingEvents() {
        ScreenEvents queue = new ScreenEvents();
        for (int i = 0; i < 64; i++) queue.add(new ScreenEvent("add", null));
        try { queue.add(new ScreenEvent("add", null)); fail("Unbounded queue"); }
        catch (IllegalArgumentException expected) {}
    }
}
