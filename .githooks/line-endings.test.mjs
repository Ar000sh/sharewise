// Tests for the line-ending check of the commit gate: node --test .githooks/line-endings.test.mjs
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { flipsLineEndings, lineEndingStyle } from './line-endings.mjs';

const lf = (lines) => lines.join('\n') + '\n';
const crlf = (lines) => lines.join('\r\n') + '\r\n';
const lines = ['one', 'two', 'three', 'four'];

test('lineEndingStyleSaysWhichEndingMostLinesHave', () => {
	assert.equal(lineEndingStyle(lf(lines)), 'lf');
	assert.equal(lineEndingStyle(crlf(lines)), 'crlf');
	assert.equal(lineEndingStyle(''), 'none');
	assert.equal(lineEndingStyle('no newline at all'), 'none');
});

test('aFileThatChangesFromCrlfToLfIsAFlip', () => {
	assert.equal(flipsLineEndings(crlf(lines), lf(lines)), true);
});

test('aFileThatChangesFromLfToCrlfIsAFlip', () => {
	assert.equal(flipsLineEndings(lf(lines), crlf(lines)), true);
});

test('aNormalEditThatKeepsTheEndingsIsNoFlip', () => {
	assert.equal(flipsLineEndings(crlf(lines), crlf([...lines, 'five'])), false);
	assert.equal(flipsLineEndings(lf(lines), lf(['uno', ...lines])), false);
});

test('oneNewLfLineInACrlfFileIsNoFlipBecauseMostLinesKeepTheirEnding', () => {
	assert.equal(flipsLineEndings(crlf(lines), crlf(lines) + 'added\n'), false);
});

test('aFileWithoutAnyLineEndingOnOneSideIsNoFlip', () => {
	assert.equal(flipsLineEndings('', lf(lines)), false);
	assert.equal(flipsLineEndings(crlf(lines), ''), false);
});
