package dev.patchrelay;

/** Stable boundary shared by the installed app and separately compiled patches. */
public interface Patch {
    String invoke(String operation, String input) throws Exception;
}
