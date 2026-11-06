/* =============================================================================
 * FILENAME: wild_fishing.h
 *
 * Ularn: Wild Edition feature "fishing" (docs/WILD_EDITION_PLAN.md, 3.7).
 * Small helpers the upstream code calls at the shared insertion points.
 * Every one of them is a no-op (or returns the neutral value) when the
 * WILDF_FISHING bit is off, so the calls need no guard of their own.
 * =============================================================================
 */
#ifndef ULARN_WILD_FISHING_H
#define ULARN_WILD_FISHING_H

/* Fountain prompt: 1 when the (f)ish answer should be offered (feature on
 * and a fishing rod in the pack). */
int  wild_fishing_can_fish(void);

/* Fountain prompt answered (f): fish at the fountain under the player. */
void wild_fishing_fish(void);

/* DND store: the rod is hidden (out of stock) when the feature is off. */
int  wild_fishing_hidden(int obj);

/* Trading post: what the item is worth, or -1 when fishing sets no price
 * for it (the old boot sells for 1 gp). */
int  wild_fishing_value(int obj);

#endif
