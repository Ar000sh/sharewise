// Tests for the choice of check groups of the push gate: node --test .githooks/gate-groups.test.mjs
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { groupsToRun } from './gate-groups.mjs';

test('aPushWithoutKnownFilesRunsEveryGroup', () => {
	assert.deepEqual(groupsToRun(null), ['backend', 'frontend', 'repo']);
});

test('aFrontendOnlyPushSkipsTheBackendTests', () => {
	assert.deepEqual(groupsToRun(['frontend/src/app/page.tsx', 'frontend/package.json']), ['frontend', 'repo']);
});

test('aBackendOnlyPushSkipsTheFrontendChecks', () => {
	assert.deepEqual(groupsToRun(['backend/src/main/java/de/hsh/favs/sharewise/group/domain/Group.java']), ['backend', 'repo']);
});

test('aContractChangeRunsBackendAndFrontendBecauseTheTypesAreGeneratedFromIt', () => {
	assert.deepEqual(groupsToRun(['docs/api/openapi.yaml']), ['backend', 'frontend', 'repo']);
});

test('aChangeOutsideBothAppsRunsTheBackendToBeSafe', () => {
	// the Keycloak realm file, the compose file and the hooks are used by the backend tests or decide how they run
	assert.deepEqual(groupsToRun(['docker/keycloak/sharewise-realm.json']), ['backend', 'repo']);
	assert.deepEqual(groupsToRun(['.githooks/gate.mjs']), ['backend', 'repo']);
});

test('aMixedPushRunsEveryGroupItTouches', () => {
	assert.deepEqual(groupsToRun(['frontend/src/lib/money.ts', 'backend/pom.xml']), ['backend', 'frontend', 'repo']);
});

test('theRepoChecksAlwaysRunBecauseTheyAreCheap', () => {
	assert.ok(groupsToRun(['frontend/README.txt']).includes('repo'));
});

test('markdownFilesDoNotMakeTheBackendRun', () => {
	assert.deepEqual(groupsToRun(['frontend/src/a.ts', 'RUNNING.md']), ['frontend', 'repo']);
});
