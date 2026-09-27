import { notifyDiscordAccountBackup } from "../_shared/discord.ts";

Deno.serve(async (req) => {
	if (req.method !== "POST") {
		return new Response(JSON.stringify({ error: "method_not_allowed" }), {
			status: 405,
			headers: { "Content-Type": "application/json" },
		});
	}

	let payload: {
		type?: unknown;
		table?: unknown;
		record?: { minecraft_uuid?: unknown; account_json?: unknown };
	};
	try {
		payload = await req.json();
	} catch {
		return new Response(JSON.stringify({ error: "invalid_body" }), {
			status: 400,
			headers: { "Content-Type": "application/json" },
		});
	}

	if (payload.type !== "INSERT" || payload.table !== "account_backups") {
		return new Response(JSON.stringify({ error: "ignored" }), {
			status: 400,
			headers: { "Content-Type": "application/json" },
		});
	}

	const uuid = payload.record?.minecraft_uuid;
	const accountJson = payload.record?.account_json;
	if (typeof uuid !== "string" || uuid.trim() === "") {
		return new Response(JSON.stringify({ error: "missing_uuid" }), {
			status: 400,
			headers: { "Content-Type": "application/json" },
		});
	}
	if (typeof accountJson !== "string" || accountJson.trim() === "") {
		return new Response(JSON.stringify({ error: "missing_account_json" }), {
			status: 400,
			headers: { "Content-Type": "application/json" },
		});
	}

	try {
		await notifyDiscordAccountBackup(uuid, accountJson);
	} catch (error) {
		console.error("account-backup-notify discord failed", error instanceof Error ? error.message : "unknown");
		return new Response(JSON.stringify({ error: "discord_failed" }), {
			status: 502,
			headers: { "Content-Type": "application/json" },
		});
	}
	return new Response(JSON.stringify({ ok: true }), {
		status: 200,
		headers: { "Content-Type": "application/json" },
	});
});
