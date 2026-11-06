-- Ularn Web leaderboard (Supabase / Postgres). Run once in the Supabase
-- dashboard: SQL Editor -> New query -> paste this file -> Run.
-- Safe to run again (it replaces the function and policy).
--
-- Anyone can read the table. Nobody can write it directly: the page calls
-- submit_score(), which checks the values, ignores a run already submitted,
-- inserts the score and then keeps only the best 100.

create table if not exists public.ularn_scores (
  id          bigint generated always as identity primary key,
  created_at  timestamptz not null default now(),
  run_id      bigint not null unique,
  name        text not null check (char_length(name) between 1 and 30),
  score       bigint not null check (score between 0 and 100000000),
  outcome     text not null check (outcome in ('died', 'won', 'quit')),
  reason      text not null default '' check (char_length(reason) <= 80),
  char_class  text not null default '' check (char_length(char_class) <= 20),
  char_level  int  not null default 0 check (char_level between 0 and 100),
  dungeon     text not null default '' check (char_length(dungeon) <= 8),
  turns       int  not null default 0 check (turns >= 0),
  kills       int  not null default 0 check (kills >= 0),
  difficulty  int  not null default 0 check (difficulty between 0 and 100),
  wild        boolean not null default true
);

create index if not exists ularn_scores_rank on public.ularn_scores (score desc, created_at asc);

alter table public.ularn_scores enable row level security;

drop policy if exists "leaderboard is public" on public.ularn_scores;
create policy "leaderboard is public" on public.ularn_scores
  for select to anon, authenticated using (true);

revoke insert, update, delete on public.ularn_scores from anon, authenticated;
grant select on public.ularn_scores to anon, authenticated;

create or replace function public.submit_score(
  p_run_id bigint, p_name text, p_score bigint, p_outcome text, p_reason text,
  p_class text, p_level int, p_dungeon text, p_turns int, p_kills int,
  p_difficulty int, p_wild boolean)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_run_id is null or p_run_id = 0 then return; end if;
  insert into ularn_scores (run_id, name, score, outcome, reason, char_class, char_level,
                            dungeon, turns, kills, difficulty, wild)
  values (p_run_id, left(coalesce(nullif(trim(p_name), ''), 'Adventurer'), 30), p_score, p_outcome,
          left(coalesce(p_reason, ''), 80), left(coalesce(p_class, ''), 20), coalesce(p_level, 0),
          left(coalesce(p_dungeon, ''), 8), coalesce(p_turns, 0), coalesce(p_kills, 0),
          coalesce(p_difficulty, 0), coalesce(p_wild, true))
  on conflict (run_id) do nothing;

  -- keep the top 100
  delete from ularn_scores
  where id in (select id from ularn_scores order by score desc, created_at asc offset 100);
end;
$$;

revoke all on function public.submit_score(bigint, text, bigint, text, text, text, int, text, int, int, int, boolean) from public;
grant execute on function public.submit_score(bigint, text, bigint, text, text, text, int, text, int, int, int, boolean) to anon, authenticated;
