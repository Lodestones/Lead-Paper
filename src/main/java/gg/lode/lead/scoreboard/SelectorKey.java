package gg.lode.lead.scoreboard;

/**
 * Which Lead value backs the vanilla scoreboard team name, and therefore what
 * target selectors match on.
 */
public enum SelectorKey {

    /** {@code @a[team=1]} — the team's id. Stable across renames. */
    ID,

    /** {@code @a[team="Hello World"]} — the team's display name, falling back to the id. */
    NAME
}
