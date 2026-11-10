import "@testing-library/jest-dom/vitest";
import { configure } from "@testing-library/react";

// findBy* and waitFor give up after 1 s by default. On a busy machine (the push gate runs the frontend checks next to
// the backend tests, CI runners are small) a component that needs one render pass after a mocked fetch can take longer
// and a correct test fails. They retry and return as soon as the element is there, so a higher limit costs nothing when
// the tests are quick.
configure({ asyncUtilTimeout: 5000 });
