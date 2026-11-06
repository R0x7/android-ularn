/* Supabase project for the shared leaderboard (see web/README.md).
 * Both values are public by design: the anon key only allows what
 * web/leaderboard.sql permits (read the table, call submit_score).
 * Leave them empty to hide the leaderboard. */
window.ULARN_LEADERBOARD = {
  url: '',   // e.g. 'https://abcdefghijkl.supabase.co'
  key: '',   // the project's anon / publishable key
};
