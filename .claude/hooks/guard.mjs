// PreToolUse hook: blocks the few things that must never happen, whoever asks.
//
// Blocks (exit code 2; the message goes back to Claude):
// - Write/Edit of a secrets file (.env, .env.local, ...). .env.example is allowed.
// - Write/Edit of frontend/src/api/schema.d.ts. It is generated: change docs/api/openapi.yaml, run `npm run gen:api`.
// - Write/Edit of a Flyway migration that already exists on main. Applied migrations are history: add a new one.
// - Any Write/Edit/Bash input that contains an Anthropic API key literal (sk-ant-...).
// - A git command with --no-verify. The git hooks in .githooks/ are the commit and push gates.
//
// Not covered: Bash commands that edit those files (sed, redirects). The git hooks catch secrets and keys at commit time.
// Any problem inside this hook lets the call through (exit 0): a broken guard must not freeze work.
// Tests: node --test .claude/hooks/
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const KEY = /sk-ant-[A-Za-z0-9_-]{16,}/;
// .env and .env.local etc., but not .env.example
const ENV_FILE = /(^|[\\/])\.env(\.(?!example$)[^\\/]+)?$/;
const GENERATED_SCHEMA = 'frontend/src/api/schema.d.ts';
const MIGRATION = /^backend\/src\/main\/resources\/db\/migration\/.+\.sql$/;
const NO_VERIFY = /\bgit\b[^;&|\n]*\s--no-verify\b/;

// Returns the reason a tool call must be blocked, or null. `isOnMain(relativePath)` is passed in so tests need no git.
export function check(payload, { root, isOnMain }) {
	const tool = payload.tool_name ?? '';
	const input = payload.tool_input ?? {};

	if (tool === 'Write' || tool === 'Edit' || tool === 'MultiEdit') {
		const filePath = String(input.file_path ?? '');
		const relative = path.relative(root, path.resolve(root, filePath)).split(path.sep).join('/');
		if (ENV_FILE.test(filePath)) {
			return `${filePath} is a secrets file. Put variable names in .env.example; the person running the app fills in .env.local.`;
		}
		if (relative === GENERATED_SCHEMA) {
			return `${GENERATED_SCHEMA} is generated. Change docs/api/openapi.yaml (own commit), then run \`npm run gen:api\` in frontend/.`;
		}
		if (MIGRATION.test(relative) && isOnMain(relative)) {
			return `${relative} is already on main and may have run on a database. Never edit it; add a new migration with the next version of the module's range (docs/conventions/01, section 6).`;
		}
		let text = [input.content, input.new_string].map((value) => String(value ?? '')).join(' ');
		for (const edit of input.edits ?? []) {
			text += ' ' + String(edit.new_string ?? '');
		}
		if (KEY.test(text)) {
			return 'the text contains an Anthropic API key (sk-ant-...). Never write keys into files.';
		}
	}

	if (tool === 'Bash') {
		const command = String(input.command ?? '');
		if (KEY.test(command)) {
			return 'the command contains an Anthropic API key (sk-ant-...). Keys never go into commands.';
		}
		// quoted text (a commit message that mentions the flag) is not a flag
		const unquoted = command.replace(/"[^"]*"|'[^']*'/g, '""');
		if (NO_VERIFY.test(unquoted)) {
			return '--no-verify skips the commit and push gates. Fix what the gate reports; if the gate itself is wrong, ask the user whether to change the rule.';
		}
	}
	return null;
}

function gitIsOnMain(root) {
	return (relative) => {
		try {
			execFileSync('git', ['cat-file', '-e', `main:${relative}`], { cwd: root, stdio: 'ignore' });
			return true;
		}
		catch {
			return false;
		}
	};
}

async function main() {
	let raw = '';
	for await (const chunk of process.stdin) {
		raw += chunk;
	}
	const payload = JSON.parse(raw);
	const root = process.env.CLAUDE_PROJECT_DIR || payload.cwd || process.cwd();
	const reason = check(payload, { root, isOnMain: gitIsOnMain(root) });
	if (reason) {
		process.stderr.write(`BLOCKED by .claude/hooks/guard.mjs: ${reason}\n`);
		process.exit(2);
	}
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch(() => process.exit(0));
}
