/* =============================================================================
 * FILENAME: wild_coin.h
 *
 * Ularn: Wild Edition feature "coin" (docs/WILD_EDITION_PLAN.md, 3.5).
 * Small helpers the upstream code calls at the shared insertion points.
 * Every one of them is a no-op (or returns the neutral value) when the
 * WILDF_COIN bit is off, so the calls need no guard of their own.
 * =============================================================================
 */
#ifndef ULARN_WILD_COIN_H
#define ULARN_WILD_COIN_H

/* Bank menu (store.c: obanksub()): the keys the menu accepts, i.e. `keys`
 * plus LarnCoin's (b)uy / (c)ash out when the feature is on. */
char *wild_coin_bank_keys(char *keys);

/* Bank menu: prints the LarnCoin line (price, holdings, keys) at the cursor. */
void wild_coin_bank_line(void);

/* Bank menu: 'b' buys, 'c' cashes out into the account. 1 = key handled. */
int  wild_coin_bank_key(int key);

/* scores.c: calc_score(): what the coins are worth in gold now. */
long wild_coin_value(void);

/* monster.c: spattack() case 17 (tax auditor). Takes 10% of the carried gold
 * towards the taxes owed; returns the message (a format with %s for the
 * monster's name), or 0 when the feature is off. */
char *wild_coin_garnish(void);

#endif
