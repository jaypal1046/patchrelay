package dev.patchrelay.screens;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenTreesTest {
    private static String tree(String children) {
        return "{\"screenId\":\"checkout\",\"type\":\"column\",\"children\":[" + children + "]}";
    }
    private static String text(String id) { return "{\"id\":\"" + id + "\",\"type\":\"text\",\"text\":\"Hello\"}"; }
    @Test public void acceptsNestedNativeWidgets() {
        String card = "{\"id\":\"card\",\"type\":\"card\",\"children\":[" + text("title") + ",{\"id\":\"email\",\"type\":\"input\",\"text\":\"Email\",\"value\":\"\",\"action\":\"email\"}]}";
        ScreenTree parsed = ScreenTrees.INSTANCE.parse(tree(card), "checkout");
        assertEquals("input", parsed.getChildren().get(0).getChildren().get(1).getType());
    }
    @Test public void rejectsUnsupportedAndMalformedWidgets() {
        rejects(tree("{\"id\":\"x\",\"type\":\"webview\",\"text\":\"Run\"}"));
        rejects(tree(text("x") + "," + text("x")));
        rejects(tree(text("x").replace("\"Hello\"", "42")));
        rejects(tree(text("x").replace("\"Hello\"", "\"Hello\",\"intent\":\"evil\"")));
        rejects(tree("{\"id\":\"x\",\"type\":\"button\",\"text\":\"Pay\",\"action\":\"pay\",\"enabled\":\"true\"}"));
        rejects(tree("{\"id\":\"x\",\"type\":\"button\",\"text\":\"Pay\",\"action\":\"../native\"}"));
        rejects(tree(""));
        rejects(tree(text("x")).replace("\"checkout\"", "\"other\""));
    }
    @Test public void boundsDepthCountAndPayload() {
        String nested = text("leaf");
        for (int i = 0; i < 8; i++) nested = "{\"id\":\"n" + i + "\",\"type\":\"column\",\"children\":[" + nested + "]}";
        rejects(tree(nested));
        String[] many = new String[33];
        for (int i = 0; i < many.length; i++) many[i] = text("n" + i);
        rejects(tree(String.join(",", many)));
        String[] groups = new String[5];
        for (int group = 0; group < groups.length; group++) {
            String[] leaves = new String[26];
            for (int i = 0; i < leaves.length; i++) leaves[i] = text("g" + group + "n" + i);
            groups[group] = "{\"id\":\"g" + group + "\",\"type\":\"column\",\"children\":[" + String.join(",", leaves) + "]}";
        }
        rejects(tree(String.join(",", groups)));
        rejects(tree(text("x").replace("Hello", String.join("", java.util.Collections.nCopies(1025, "a")))));
    }
    private static void rejects(String json) {
        try { ScreenTrees.INSTANCE.parse(json, "checkout"); fail("Accepted unsupported screen"); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
    }
}
