/* =============================================================================
 * FILENAME: wild_hats.h
 *
 * Ularn: Wild Edition feature "hats" (docs/WILD_EDITION_PLAN.md, 3.4).
 * Small helpers the upstream code calls at the shared insertion points.
 * Every one of them is a no-op (or returns the neutral value) when the
 * WILDF_HATS bit is off, so the calls need no guard of their own.
 * =============================================================================
 */
#ifndef ULARN_WILD_HATS_H
#define ULARN_WILD_HATS_H

/* Inventory slot of the hat on the player's head, or -1. */
int  wild_hats_worn_slot(void);

/* Propeller beanie: 1 (after printing a message) when the player flies over
 * the pit / trap door named by `what` instead of falling in. */
int  wild_hats_fly(const char *what);

/* Wizard hat: the extra spell point when regen() gives one back. */
void wild_hats_regen(void);

/* Tinfoil hat: clears any confusion (with a message). */
void wild_hats_unconfuse(void);

/* DND store: hats are hidden (out of stock) when the feature is off. */
int  wild_hats_hidden(int obj);

/* DND store: the price after the party hat's 10% discount. */
long wild_hats_price(long price);

#endif
