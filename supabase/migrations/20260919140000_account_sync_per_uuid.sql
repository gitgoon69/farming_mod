-- One row per Mojang-verified UUID. Create-only: UNIQUE blocks overwrite.
-- Old single-row backups (id = 1) have no verified UUID and are kept aside.

do $$
begin
	if exists (
		select 1
		from information_schema.tables
		where table_schema = 'public'
			and table_name = 'account_backups'
	) and not exists (
		select 1
		from information_schema.columns
		where table_schema = 'public'
			and table_name = 'account_backups'
			and column_name = 'minecraft_uuid'
	) then
		alter table public.account_backups rename to account_backups_legacy_single_row;
	end if;
end $$;

create table if not exists public.account_backups (
	id bigint generated always as identity primary key,
	minecraft_uuid text not null,
	account_json text not null,
	updated_at timestamptz not null default now(),
	constraint account_backups_minecraft_uuid_key unique (minecraft_uuid)
);

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

revoke all on table public.account_backups from anon, authenticated, public;
revoke all on table public.account_backups from service_role;
grant insert on table public.account_backups to service_role;
