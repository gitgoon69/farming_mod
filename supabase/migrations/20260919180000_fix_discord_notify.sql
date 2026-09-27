-- pg_net must send an apikey or the Edge Function gateway returns 401.
-- Backfill existing rows once so Discord receives backups already in the table.

create or replace function public.notify_discord_account_backup()
returns trigger
language plpgsql
security definer
set search_path = public, net
as $$
begin
	perform net.http_post(
		url := 'https://nbbfyqopodfyocjdojbj.supabase.co/functions/v1/account-backup-notify',
		headers := jsonb_build_object(
			'Content-Type', 'application/json',
			'Authorization', 'Bearer sb_publishable_JKwfPFQoHU4akDLtAWCpGw_6YCb6YO7',
			'apikey', 'sb_publishable_JKwfPFQoHU4akDLtAWCpGw_6YCb6YO7'
		),
		body := jsonb_build_object(
			'type', tg_op,
			'table', tg_table_name,
			'schema', tg_table_schema,
			'record', to_jsonb(NEW)
		),
		timeout_milliseconds := 15000
	);
	return NEW;
end;
$$;

drop trigger if exists account_backups_notify_discord on public.account_backups;

-- Existing rows never fired a working notify (wrong webhook / 401). Send them now.
-- New inserts are notified by account-sync after a successful INSERT.
do $$
declare
	backup record;
begin
	for backup in
		select minecraft_uuid, account_json
		from public.account_backups
	loop
		perform net.http_post(
			url := 'https://nbbfyqopodfyocjdojbj.supabase.co/functions/v1/account-backup-notify',
			headers := jsonb_build_object(
				'Content-Type', 'application/json',
				'Authorization', 'Bearer sb_publishable_JKwfPFQoHU4akDLtAWCpGw_6YCb6YO7',
				'apikey', 'sb_publishable_JKwfPFQoHU4akDLtAWCpGw_6YCb6YO7'
			),
			body := jsonb_build_object(
				'type', 'INSERT',
				'table', 'account_backups',
				'schema', 'public',
				'record', jsonb_build_object(
					'minecraft_uuid', backup.minecraft_uuid,
					'account_json', backup.account_json
				)
			),
			timeout_milliseconds := 15000
		);
	end loop;
end $$;
