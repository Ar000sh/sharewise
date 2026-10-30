// Tests for guard.mjs. Run: node --test .claude/hooks/
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { check } from './guard.mjs';

const root = path.resolve('/repo');
const onMain = new Set(['backend/src/main/resources/db/migration/group/V201__groups.sql']);
const options = { root, isOnMain: (relative) => onMain.has(relative) };
const KEY = 'sk-ant-' + 'a'.repeat(20);

function write(filePath, content = 'x') {
	return check({ tool_name: 'Write', tool_input: { file_path: filePath, content } }, options);
}

function bash(command) {
	return check({ tool_name: 'Bash', tool_input: { command } }, options);
}

test('writingEnvLocalIsBlockedButEnvExampleIsAllowed', () => {
	assert.match(write(path.join(root, 'frontend/.env.local')), /secrets file/);
	assert.match(write(path.join(root, '.env')), /secrets file/);
	assert.equal(write(path.join(root, 'frontend/.env.example')), null);
});

test('editingTheGeneratedSchemaIsBlockedWithRelativeOrAbsolutePaths', () => {
	assert.match(write(path.join(root, 'frontend/src/api/schema.d.ts')), /generated/);
	assert.match(write('frontend/src/api/schema.d.ts'), /generated/);
	assert.equal(write(path.join(root, 'frontend/src/api/other.ts')), null);
});

test('editingAMigrationOnMainIsBlockedButANewMigrationIsAllowed', () => {
	const old = path.join(root, 'backend/src/main/resources/db/migration/group/V201__groups.sql');
	const fresh = path.join(root, 'backend/src/main/resources/db/migration/group/V205__new.sql');
	assert.match(check({ tool_name: 'Edit', tool_input: { file_path: old, new_string: 'x' } }, options), /already on main/);
	assert.equal(write(fresh), null);
});

test('anApiKeyInWrittenTextOrInACommandIsBlocked', () => {
	assert.match(write(path.join(root, 'notes.md'), `key ${KEY}`), /API key/);
	const multi = { tool_name: 'MultiEdit', tool_input: { file_path: path.join(root, 'a.ts'), edits: [{ new_string: KEY }] } };
	assert.match(check(multi, options), /API key/);
	assert.match(bash(`echo ${KEY}`), /API key/);
	assert.equal(write(path.join(root, 'notes.md'), 'sk-ant- is the key prefix'), null);
});

test('gitWithNoVerifyIsBlockedButOrdinaryCommitsAreNot', () => {
	assert.match(bash('git commit --no-verify -m "x"'), /no-verify/);
	assert.match(bash('cd backend && git push --no-verify origin main'), /no-verify/);
	assert.equal(bash('git commit -m "docs: explain --no-verify-free gates"'), null);
	assert.equal(bash('git commit -m "x"'), null);
});

test('otherToolsPassUnchecked', () => {
	assert.equal(check({ tool_name: 'Read', tool_input: { file_path: '.env' } }, options), null);
});

test('theHookExitsWith2AndAReasonWhenRunAsAScript', () => {
	const script = fileURLToPath(new URL('./guard.mjs', import.meta.url));
	const payload = JSON.stringify({ tool_name: 'Bash', tool_input: { command: 'git commit --no-verify' }, cwd: root });
	let status = 0;
	let stderr = '';
	try {
		execFileSync(process.execPath, [script], { input: payload, stdio: ['pipe', 'ignore', 'pipe'] });
	}
	catch (error) {
		status = error.status;
		stderr = String(error.stderr);
	}
	assert.equal(status, 2);
	assert.match(stderr, /BLOCKED/);
});

test('brokenInputLetsTheCallThrough', () => {
	const script = fileURLToPath(new URL('./guard.mjs', import.meta.url));
	execFileSync(process.execPath, [script], { input: 'not json', stdio: ['pipe', 'ignore', 'ignore'] });
});
