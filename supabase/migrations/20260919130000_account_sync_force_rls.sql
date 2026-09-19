-- Harden account_backups: no public policies, RLS forced for table owners too.
-- service_role still bypasses RLS and remains the only writer (Edge Function).

do $$
declare
	policy record;
begin
	for policy in
		select policyname
		from pg_policies
		where schemaname = 'public'
			and tablename = 'account_backups'
	loop
		execute format('drop policy if exists %I on public.account_backups', policy.policyname);
	end loop;
end $$;

alter table public.account_backups enable row level security;
alter table public.account_backups force row level security;

revoke all on table public.account_backups from anon, authenticated;
revoke all on table public.account_backups from public;
grant all on table public.account_backups to service_role;
