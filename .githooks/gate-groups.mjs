// Which groups of checks a push needs, so the push gate does not run 11 minutes of backend tests for a frontend change.
// Used by gate.mjs. CI never uses it: the pipeline runs every group on every change, and it is what main requires.

const CONTRACT = 'docs/api/openapi.yaml';

// files: the paths a push changes, or null when they are unknown (then everything runs).
// Skipping is safe only for a group that cannot be affected, so the rules lean towards running:
//   frontend: only when the push touches frontend/ or the contract (the frontend types are generated from it)
//   backend:  unless every file is under frontend/ or is a document; anything else (the realm file, the compose file,
//             the hooks, the contract) can change what the backend tests do
//   repo:     always (contract lint and hook tests take about 30 seconds)
export function groupsToRun(files) {
	if (files === null) {
		return ['backend', 'frontend', 'repo'];
	}
	const groups = [];
	if (files.some((file) => !file.startsWith('frontend/') && !file.endsWith('.md'))) {
		groups.push('backend');
	}
	if (files.some((file) => file.startsWith('frontend/') || file === CONTRACT)) {
		groups.push('frontend');
	}
	groups.push('repo');
	return groups;
}
