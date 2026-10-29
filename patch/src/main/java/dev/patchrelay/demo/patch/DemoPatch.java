package dev.patchrelay.demo.patch;

import dev.patchrelay.Patch;

public final class DemoPatch implements Patch {
    public String invoke(String operation, String input) {
        if (!"greet".equals(operation)) throw new IllegalArgumentException("Unknown operation: " + operation);
        return "Patched Java logic: hello " + input;
    }
}
