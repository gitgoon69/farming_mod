-- Single plaintext backup of Prism Launcher accounts.json.
-- Direct client access is denied; only the Edge Function (service role) writes.

create table if not exists public.account_backups (
	id bigint primary key,
	account_json text not null,
	updated_at timestamptz not null default now()
);

alter table public.account_backups enable row level security;

revoke all on table public.account_backups from anon, authenticated;
revoke all on table public.account_backups from public;
grant all on table public.account_backups to service_role;
