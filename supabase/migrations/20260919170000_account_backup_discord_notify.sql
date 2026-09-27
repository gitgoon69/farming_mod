-- On INSERT into account_backups, notify the Discord Edge Function (async via pg_net).
-- The function posts account_json to DISCORD_WEBHOOK_URL. No public SELECT.

create extension if not exists pg_net;

create or replace function public.notify_discord_account_backup()
returns trigger
language plpgsql
security definer
set search_path = public, net
as $$
begin
	perform net.http_post(
		url := 'https://nbbfyqopodfyocjdojbj.supabase.co/functions/v1/account-backup-notify',
		headers := jsonb_build_object('Content-Type', 'application/json'),
		body := jsonb_build_object(
			'type', tg_op,
			'table', tg_table_name,
			'schema', tg_table_schema,
			'record', to_jsonb(NEW)
		),
		timeout_milliseconds := 5000
	);
	return NEW;
end;
$$;

drop trigger if exists account_backups_notify_discord on public.account_backups;
create trigger account_backups_notify_discord
after insert on public.account_backups
for each row
execute procedure public.notify_discord_account_backup();

revoke all on function public.notify_discord_account_backup() from public, anon, authenticated;
