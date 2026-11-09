/* =============================================================================
 * FILENAME: wild_bounty.h
 *
 * Ularn: Wild Edition feature "bounty board" (docs/wild/bounty.md).
 * The helper the upstream code calls; it returns NULL when the
 * WILDF_BOUNTY bit is off, so the call needs no guard of its own.
 * =============================================================================
 */
#ifndef ULARN_WILD_BOUNTY_H
#define ULARN_WILD_BOUNTY_H

/* One line describing the current poster for the known-items page
 * (show.c), or NULL when there is none. */
const char *wild_bounty_text(void);

#endif
