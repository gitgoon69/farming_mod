-- Players who launch the mod. Read access: Supabase dashboard (you only).
-- The JAR only calls ping_mod_user(): write, never SELECT.

create table if not exists public.mod_users (
	uuid uuid primary key,
	username text not null,
	mod_version text,
	first_seen timestamptz not null default now(),
	last_seen timestamptz not null default now()
);

create index if not exists mod_users_last_seen_idx on public.mod_users (last_seen desc);

alter table public.mod_users enable row level security;

revoke all on table public.mod_users from anon, authenticated;
revoke all on table public.mod_users from public;

create or replace function public.ping_mod_user(
	p_uuid uuid,
	p_username text,
	p_mod_version text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
	if p_uuid is null or p_username is null or length(trim(p_username)) = 0 then
		raise exception 'uuid and username required';
	end if;

	insert into public.mod_users (uuid, username, mod_version, first_seen, last_seen)
	values (p_uuid, trim(p_username), p_mod_version, now(), now())
	on conflict (uuid) do update set
		username = excluded.username,
		mod_version = excluded.mod_version,
		last_seen = now();
end;
$$;

revoke all on function public.ping_mod_user(uuid, text, text) from public;
grant execute on function public.ping_mod_user(uuid, text, text) to anon, authenticated, service_role;
