package dev.patchrelay.screens;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenTreesTest {
    private static final String PREFIX = "{\"screenId\":\"checkout\",\"type\":\"column\",\"children\":[";
    @Test public void acceptsNativeWidgetContract() {
        ScreenTree tree = ScreenTrees.INSTANCE.parse(PREFIX + "{\"type\":\"text\",\"text\":\"Checkout\",\"heading\":true},{\"type\":\"button\",\"text\":\"Pay\",\"action\":\"pay\"}]}", "checkout");
        assertEquals(2, tree.getChildren().size());
        assertTrue(tree.getChildren().get(0) instanceof ScreenNode.Text);
        assertTrue(tree.getChildren().get(1) instanceof ScreenNode.Button);
    }
    @Test public void rejectsUnsupportedCapabilitiesAndMalformedTrees() {
        rejects(PREFIX + "{\"type\":\"webview\",\"text\":\"Run\"}]}");
        rejects(PREFIX + "{\"type\":\"button\",\"text\":\"Run\",\"action\":\"pay\",\"intent\":\"evil\"}]}");
        rejects(PREFIX + "{\"type\":\"text\",\"text\":\"x\",\"heading\":\"true\"}]}");
        rejects(PREFIX + "]}");
        rejects(PREFIX + "{\"type\":\"button\",\"text\":\"x\",\"action\":\"../../native\"}]}");
        rejects("{\"screenId\":\"other\",\"type\":\"column\",\"children\":[{\"type\":\"text\",\"text\":\"x\"}]}");
    }
    private static void rejects(String json) {
        try { ScreenTrees.INSTANCE.parse(json, "checkout"); fail("Accepted unsupported screen"); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
    }
}
