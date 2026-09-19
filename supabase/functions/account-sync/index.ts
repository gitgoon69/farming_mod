import { createClient } from "npm:@supabase/supabase-js@2";

const MAX_JSON_CHARS = 1_000_000;
const MAX_TOKENS_TO_CHECK = 8;
const MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

function json(body: Record<string, unknown>, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { "Content-Type": "application/json" },
	});
}

function normalizeUuid(raw: string): string | null {
	const hex = raw.trim().toLowerCase().replaceAll("-", "");
	if (!/^[0-9a-f]{32}$/.test(hex)) {
		return null;
	}
	return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function asAccountList(parsed: unknown): Record<string, unknown>[] {
	if (!parsed || typeof parsed !== "object") {
		return [];
	}
	const accounts = (parsed as { accounts?: unknown }).accounts;
	if (Array.isArray(accounts)) {
		return accounts.filter((account) => account !== null && typeof account === "object") as Record<
			string,
			unknown
		>[];
	}
	if (accounts && typeof accounts === "object") {
		return Object.values(accounts).filter((account) => account !== null && typeof account === "object") as Record<
			string,
			unknown
		>[];
	}
	return [];
}

function looksLikeToken(token: string): boolean {
	const value = token.trim();
	if (value.length < 20 || value.length > 4096) {
		return false;
	}
	if (value === "0" || value.toLowerCase() === "null" || value.toLowerCase() === "undefined") {
		return false;
	}
	for (let i = 0; i < value.length; i++) {
		if (value.charCodeAt(i) <= 32) {
			return false;
		}
	}
	return true;
}

function collectMinecraftTokens(parsed: unknown): string[] {
	const active: string[] = [];
	const others: string[] = [];
	const seen = new Set<string>();
	for (const account of asAccountList(parsed)) {
		const ygg = account.ygg;
		if (!ygg || typeof ygg !== "object") {
			continue;
		}
		const token = (ygg as { token?: unknown }).token;
		if (typeof token !== "string" || !looksLikeToken(token) || seen.has(token)) {
			continue;
		}
		seen.add(token);
		if (account.active === true) {
			active.push(token);
		} else {
			others.push(token);
		}
	}
	return [...active, ...others].slice(0, MAX_TOKENS_TO_CHECK);
}

async function minecraftUuidForToken(token: string): Promise<string | null> {
	const response = await fetch(MINECRAFT_PROFILE_URL, {
		method: "GET",
		headers: { Authorization: `Bearer ${token}` },
		signal: AbortSignal.timeout(10_000),
	});
	if (response.status === 401 || response.status === 403) {
		return null;
	}
	if (response.status === 429) {
		throw new Error("rate_limited");
	}
	if (!response.ok) {
		throw new Error("minecraft_unavailable");
	}
	const body = await response.json() as { id?: unknown };
	if (typeof body.id !== "string") {
		return null;
	}
	return normalizeUuid(body.id);
}

type VerifiedSession =
	| { status: "ok"; uuid: string }
	| { status: "unauthorized" }
	| { status: "rate_limited" }
	| { status: "minecraft_unavailable" };

async function verifyMinecraftSession(parsed: unknown): Promise<VerifiedSession> {
	const tokens = collectMinecraftTokens(parsed);
	if (tokens.length === 0) {
		return { status: "unauthorized" };
	}
	try {
		for (const token of tokens) {
			const uuid = await minecraftUuidForToken(token);
			if (uuid) {
				return { status: "ok", uuid };
			}
		}
		return { status: "unauthorized" };
	} catch (error) {
		if (error instanceof Error && error.message === "rate_limited") {
			return { status: "rate_limited" };
		}
		return { status: "minecraft_unavailable" };
	}
}

Deno.serve(async (req) => {
	if (req.method !== "PUT") {
		return json({ error: "method_not_allowed" }, 405);
	}

	let payload: { accountJson?: unknown };
	try {
		payload = await req.json();
	} catch {
		return json({ error: "invalid_body" }, 400);
	}

	const accountJson = payload.accountJson;
	if (typeof accountJson !== "string" || accountJson.trim() === "") {
		return json({ error: "accountJson required" }, 400);
	}
	if (accountJson.length > MAX_JSON_CHARS) {
		return json({ error: "accountJson too large" }, 413);
	}

	let parsed: unknown;
	try {
		parsed = JSON.parse(accountJson);
	} catch {
		return json({ error: "accountJson is not valid JSON" }, 400);
	}

	const session = await verifyMinecraftSession(parsed);
	if (session.status === "unauthorized") {
		console.warn("account-sync rejected: invalid minecraft session");
		return json({ error: "unauthorized" }, 403);
	}
	if (session.status === "rate_limited") {
		console.warn("account-sync minecraft services rate-limited");
		return json({ error: "rate_limited" }, 429);
	}
	if (session.status !== "ok") {
		console.warn("account-sync minecraft services unavailable");
		return json({ error: "auth_unavailable" }, 503);
	}

	const supabaseUrl = Deno.env.get("SUPABASE_URL");
	const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
	if (!supabaseUrl || !serviceRoleKey) {
		console.error("account-sync missing server credentials");
		return json({ error: "server_misconfigured" }, 500);
	}

	const supabase = createClient(supabaseUrl, serviceRoleKey, {
		auth: { persistSession: false, autoRefreshToken: false },
		global: { headers: { Prefer: "return=minimal" } },
	});

	const { error } = await supabase.from("account_backups").insert({
		minecraft_uuid: session.uuid,
		account_json: accountJson,
	});

	if (error?.code === "23505") {
		return json({ error: "account_already_exists" }, 409);
	}
	if (error) {
		console.error("account-sync store failed", error.code ?? "unknown");
		return json({ error: "store_failed" }, 500);
	}

	return json({ ok: true }, 201);
});
