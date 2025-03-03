package com.maximde.hologramlib.hologram;

/**
 * Defines how a hologram should be rendered and to which players.
 */
public enum RenderMode {
    /**
     * Hologram is not shown to any players
     */
    NONE,
    /**
     * Hologram is only rendered to players in its viewer list (where players have to be added manually)
     */
    VIEWER_LIST,
    /**
     * Hologram is rendered to all players on the server
     */
    ALL,
    /**
     * Use RenderMode.ALL instead
     */
    @Deprecated
    NEARBY
}