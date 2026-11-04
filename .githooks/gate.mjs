// Commit and push gates, run by .githooks/pre-commit and .githooks/pre-push for everyone (people and Claude).
// Enable once per clone: git config core.hooksPath .githooks
//
// pre-commit (fast, only for what is staged):
//   1. no secrets file staged, no Anthropic API key in the added lines
//   2. docs/api/openapi.yaml is committed on its own (contract first, docs/conventions/03 section 6)
//   3. package-lock.json did not lose other platforms' entries (npm on Windows drops them, docs/conventions/04 section 7)
//   4. no existing file flips its line endings (CRLF <-> LF): the whole file would show as changed and hide the real
//      change. An intended normalisation is committed alone with SHAREWISE_ALLOW_EOL_FLIP=1 (see line-endings.mjs)
//   5. checks for the staged parts: backend format, frontend lint + typecheck, contract lint, guard hook tests
//   Docs-only commits (.md) skip step 5.
// pre-push (complete, what `main` must always be): backend verify (needs Docker), frontend lint, typecheck, tests,
//   build, contract lint. Skipped when every pushed file is .md.
//
// If a gate is wrong or too slow, change this file in its own commit. Never bypass it with --no-verify.
import { execFileSync, spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { flipsLineEndings } from './line-endings.mjs';

const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
const ENV_FILE = /(^|\/)\.env(\.(?!example$)[^/]+)?$/;
const KEY_IN_ADDED_LINES = /^\+(?!\+\+).*sk-ant-[A-Za-z0-9_-]{16,}/m;
const CONTRACT = 'docs/api/openapi.yaml';
const LOCK_FILE = 'frontend/package-lock.json';
const PLATFORM_ENTRY = /"node_modules\/[^"]*(darwin|linux|win32|freebsd|android|openbsd|sunos|aix)[^"]*"/g;
const isWindows = process.platform === 'win32';
// explicit path: cmd.exe does not always search the current folder (NoDefaultCurrentDirectoryInExePath)
const mvnw = isWindows ? '.\\mvnw.cmd' : './mvnw';

function git(...args) {
	return execFileSync('git', args, { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
}

function gitBuffer(...args) {
	return execFileSync('git', args, { cwd: root, maxBuffer: 64 * 1024 * 1024 });
}

function fail(message) {
	process.stderr.write(`\n${process.argv[2]} BLOCKED: ${message}\n`);
	process.exit(1);
}

// Runs one check and stops the gate when it fails. shell: true so mvnw.cmd and npm resolve on Windows.
function run(label, command, cwd = '.') {
	const started = Date.now();
	process.stdout.write(`gate: ${label} ... `);
	const result = spawnSync(command, { cwd: path.join(root, cwd), shell: true, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
	const seconds = ((Date.now() - started) / 1000).toFixed(0);
	if (result.status !== 0) {
		process.stdout.write(`FAILED (${seconds}s)\n`);
		const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim().split('\n').slice(-40).join('\n');
		process.stderr.write(`${output}\n`);
		fail(`${label} failed. Fix it and commit again (command: ${command}, in ${cwd}).`);
	}
	process.stdout.write(`ok (${seconds}s)\n`);
}

function countPlatformEntries(text) {
	return (text.match(PLATFORM_ENTRY) ?? []).length;
}

function lockFileLostPlatforms() {
	let before;
	try {
		before = git('show', `HEAD:${LOCK_FILE}`);
	}
	catch {
		return false;
	}
	return countPlatformEntries(git('show', `:${LOCK_FILE}`)) < countPlatformEntries(before);
}

// Staged text files that exist at HEAD and now have the other line ending.
function filesWithFlippedLineEndings() {
	const modified = git('diff', '--cached', '--name-only', '--diff-filter=M').split('\n').filter(Boolean);
	return modified.filter((file) => {
		const before = gitBuffer('show', `HEAD:${file}`);
		const after = gitBuffer('show', `:${file}`);
		if (before.includes(0) || after.includes(0)) {
			return false; // binary
		}
		return flipsLineEndings(before.toString('utf8'), after.toString('utf8'));
	});
}

function preCommit() {
	const staged = git('diff', '--cached', '--name-only').split('\n').filter(Boolean);
	if (staged.length === 0) {
		return;
	}
	const secrets = staged.filter((file) => ENV_FILE.test(file));
	if (secrets.length > 0) {
		fail(`secrets file staged: ${secrets.join(', ')}. Run: git restore --staged <file>`);
	}
	if (KEY_IN_ADDED_LINES.test(git('diff', '--cached', '-U0'))) {
		fail('an Anthropic API key (sk-ant-...) is in the staged changes. Remove it; rotate the key if it was ever pushed.');
	}
	if (staged.includes(CONTRACT) && staged.length > 1) {
		fail(`${CONTRACT} is staged together with other files. The contract changes in its own commit ("contract(<scope>): ..."), the code follows.`);
	}
	if (staged.includes(LOCK_FILE) && lockFileLostPlatforms()) {
		fail(`${LOCK_FILE} lost entries for other platforms (npm on Windows drops them). Restore it and reinstall: git checkout HEAD -- ${LOCK_FILE} && npm install`);
	}
	if (process.env.SHAREWISE_ALLOW_EOL_FLIP !== '1') {
		const flipped = filesWithFlippedLineEndings();
		if (flipped.length > 0) {
			fail(`the line endings (CRLF <-> LF) of existing files changed, so every line shows as changed: ${flipped.join(', ')}. `
				+ 'Restore them (git checkout HEAD -- <file>) and make the edit with a tool that keeps them (sed -i and some editors strip them). '
				+ 'If the change is intended, commit it alone with SHAREWISE_ALLOW_EOL_FLIP=1.');
		}
	}
	if (staged.every((file) => file.endsWith('.md'))) {
		console.log('gate: docs-only commit, checks skipped');
		return;
	}
	if (staged.some((file) => file.startsWith('backend/'))) {
		run('backend format (spring-javaformat; fix: ./mvnw spring-javaformat:apply)', `${mvnw} -q validate`, 'backend');
	}
	if (staged.some((file) => file.startsWith('frontend/'))) {
		run('frontend lint', 'npm run lint --silent', 'frontend');
		run('frontend typecheck', 'npm run typecheck --silent', 'frontend');
	}
	if (staged.includes(CONTRACT)) {
		run('contract lint', `npx --yes @redocly/cli lint ${CONTRACT}`);
	}
	if (staged.some((file) => file.startsWith('.claude/hooks/') || file.startsWith('.githooks/'))) {
		run('guard hook tests', 'node --test .claude/hooks/guard.test.mjs .githooks/line-endings.test.mjs');
	}
}

// The files a push sends, from the "<local ref> <local sha> <remote ref> <remote sha>" lines git passes on stdin.
// Returns null (check everything) when there are no such lines, e.g. when /review-branch runs the gate by hand.
function pushedFiles() {
	const zero = /^0+$/;
	const files = new Set();
	const lines = readFileSync(0, 'utf8').split('\n').filter(Boolean);
	if (lines.length === 0) {
		return null;
	}
	for (const line of lines) {
		const [, localSha, , remoteSha] = line.split(' ');
		if (zero.test(localSha)) {
			continue; // deleting a remote branch
		}
		let base = remoteSha;
		if (zero.test(remoteSha)) {
			try {
				base = git('merge-base', localSha, 'origin/main').trim();
			}
			catch {
				return null; // no common base: check everything
			}
		}
		git('diff', '--name-only', base, localSha).split('\n').filter(Boolean).forEach((file) => files.add(file));
	}
	return [...files];
}

function prePush() {
	const files = pushedFiles();
	if (files && files.every((file) => file.endsWith('.md'))) {
		console.log('gate: docs-only push, checks skipped');
		return;
	}
	if (spawnSync('docker info', { shell: true, stdio: 'ignore' }).status !== 0) {
		fail('Docker is not running. The backend tests need it: start Docker Desktop and push again.');
	}
	run('backend verify (all backend tests, Docker)', `${mvnw} -q verify`, 'backend');
	run('frontend lint', 'npm run lint --silent', 'frontend');
	run('frontend typecheck', 'npm run typecheck --silent', 'frontend');
	run('frontend tests', 'npm test --silent', 'frontend');
	run('frontend build', 'npm run build --silent', 'frontend');
	run('contract lint', `npx --yes @redocly/cli lint ${CONTRACT}`);
	run('guard hook tests', 'node --test .claude/hooks/guard.test.mjs .githooks/line-endings.test.mjs');
}

if (process.argv[2] === 'pre-commit') {
	preCommit();
}
else if (process.argv[2] === 'pre-push') {
	prePush();
}
else {
	fail('usage: node .githooks/gate.mjs pre-commit|pre-push');
}
