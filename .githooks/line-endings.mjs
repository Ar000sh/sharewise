// Line endings of a text, for the commit gate (gate.mjs): a commit that turns a file from CRLF into LF, or the other way,
// shows every line of the file as changed and hides the real change from a reviewer. The repo mixes both endings (files
// keep the one they were created with), so the gate only refuses a *flip* of an existing file.

/** 'crlf' or 'lf', whichever ending most lines of the text have; 'none' when the text has no line ending. */
export function lineEndingStyle(text) {
	const crlf = (text.match(/\r\n/g) ?? []).length;
	const lf = (text.match(/\n/g) ?? []).length - crlf;
	if (crlf === 0 && lf === 0) {
		return 'none';
	}
	return crlf > lf ? 'crlf' : 'lf';
}

/** True when the file had one ending before and has the other one now. */
export function flipsLineEndings(before, after) {
	const was = lineEndingStyle(before);
	const is = lineEndingStyle(after);
	return was !== 'none' && is !== 'none' && was !== is;
}
