---
paths:
  - "backend/src/test/**"
---

# Writing backend tests

Pick the level first (one level per case, never the same case twice): domain rule → unit test; orchestration → service test with Mockito; status code and JSON → controller test with `@ModuleIntegrationTest`; anything a mock cannot prove → database test. Integration tests sign up real accounts through `AccountFixture.signUp`; there are no seeded users and no dev profile.

@../../docs/conventions/02_TESTING.md
