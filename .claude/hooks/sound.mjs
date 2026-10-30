// Stop/Notification hook: plays a random .wav from .claude/sounds/<kind>/ ("done" or "attention").
// Opt-in per person: plays only when SHAREWISE_SOUNDS is "1", set in the environment or under "env" in the
// uncommitted .claude/settings.local.json. Windows only (play-sound.ps1); elsewhere it does nothing.
// Never fails the hook: every error is swallowed and the script exits 0.
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

function soundsEnabled(root) {
	if (process.env.SHAREWISE_SOUNDS === '1') {
		return true;
	}
	try {
		const local = JSON.parse(readFileSync(path.join(root, '.claude', 'settings.local.json'), 'utf8'));
		return local.env?.SHAREWISE_SOUNDS === '1';
	}
	catch {
		return false;
	}
}

try {
	const root = process.env.CLAUDE_PROJECT_DIR || process.cwd();
	const kind = process.argv[2] === 'attention' ? 'attention' : 'done';
	if (process.platform === 'win32' && soundsEnabled(root)) {
		const script = path.join(root, '.claude', 'hooks', 'play-sound.ps1');
		const folder = path.join(root, '.claude', 'sounds', kind);
		spawnSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', script, folder], { stdio: 'ignore' });
	}
}
catch {
	// a missing or broken sound must never block Claude
}
process.exit(0);
