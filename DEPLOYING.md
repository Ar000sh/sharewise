# ShareWise lab deployment

For the complete team walkthrough, theory, responsibilities and commands for
each machine, see [the team deployment guide](docs/TEAM_DEPLOYMENT_GUIDE.md).

This configuration packages the current branch, not the lecturer's favsdemo.
It runs Next.js, Spring Boot (Java 21), PostgreSQL and Keycloak. It is a private
lab setup reached through SSH, not a public production HTTPS deployment.
Keycloak uses `start-dev` and a persistent H2 volume for this lab.

## Build and run locally

From the repository root, copy `deploy.env.example` to `.env.deploy.local`.
Set different private values for `DB_PASSWORD` and `KEYCLOAK_ADMIN_PASSWORD`.
Keep this file out of Git. Compose rejects empty passwords.

```sh
docker compose --env-file .env.deploy.local -f compose.deploy.yml config --quiet
docker compose --env-file .env.deploy.local -f compose.deploy.yml build
docker compose --env-file .env.deploy.local -f compose.deploy.yml up -d
docker compose --env-file .env.deploy.local -f compose.deploy.yml ps
docker compose --env-file .env.deploy.local -f compose.deploy.yml logs --tail=50
```

Open **http://localhost:3100** after the services finish starting. Use `localhost`
consistently (not `127.0.0.1` in the browser): the OIDC redirects and origin checks
use this exact address. Keycloak is at http://localhost:8180. Both ports must be
free; do not stop other projects to free them without checking first.

The frontend is built with `npm ci` and `next build`, then runs as the non-root
`node` user with production dependencies. The backend's existing Dockerfile
builds its JAR. Image builds are not a replacement for running the test suite.
The Next.js build downloads its configured Google fonts and needs network access.
Services have memory limits (frontend 512 MB, backend/Keycloak 768 MB each,
PostgreSQL 256 MB). Leave additional RAM for builds and the Docker host.

To run frontend checks in Linux, including the container address regression test:

```sh
docker build --target checks -t sharewise-frontend:checks frontend
docker run --rm sharewise-frontend:checks sh -c 'npm run lint && npm run typecheck && npm test -- --maxWorkers=1'
```

## Why login has two addresses

The browser uses `http://localhost:8180/realms/sharewise` as the public issuer.
The frontend container uses `KEYCLOAK_INTERNAL_ISSUER` at
`http://keycloak:8080/realms/sharewise` for token exchange, refresh and availability
checks. Redirects continue using the public address. Spring validates the public
issuer but fetches signing keys using the internal Docker address. No private
Docker hostname is sent to the browser. Host development still works with the
internal variable unset.

The existing realm permits frontend redirects on port 3100. Registration, login,
an authenticated `/api/me` call, refresh and logout should be checked before
claiming the complete application is deployed.

## Run on docker-01

Copy this configuration, the `docker/keycloak` realm directory and the private
environment file to the VM. Supply images by building from this repository there,
loading exported images, or pulling from a confirmed team-owned registry. Merely
copying Compose does not upload the source or local images.

For registry deployment, set `FRONTEND_IMAGE` and `BACKEND_IMAGE` in the private
environment file to the team's image paths with commit-specific tags, then:

```sh
docker compose --env-file .env.deploy.local -f compose.deploy.yml pull
docker compose --env-file .env.deploy.local -f compose.deploy.yml up -d --no-build
```

The GitHub workflow below publishes ShareWise images. The lecturer's demo images
are not ShareWise images.

## GitHub CI and container registry

The separate `CI` workflow in `ci.yml` publishes its E2E-tested images under
`ghcr.io/<repository-owner>/sharewise-backend` and `sharewise-frontend`.
Its namespace and image source labels follow the repository running the workflow;
a fork must not attempt publication into the upstream owner's namespace.
The VM deployment workflow selects the images published by `containers.yml`
described below, which use a different repository-prefixed naming convention.

`.github/workflows/containers.yml` runs on pushes and pull requests, and supports
manual runs. It runs frontend lint, type checking, unit tests and contract lint,
plus backend Maven `verify` with real PostgreSQL/Keycloak Testcontainers. Only
after both check jobs pass does it build the two production images. Browser E2E
tests are still a separate release check.

Only the repository's default branch publishes images. Feature branches and pull
requests build without publishing. A manual run on a feature branch also does
not publish. The workflow must be committed and pushed before GitHub can run it;
its presence locally does not create packages or deploy anything.

GitHub Actions authenticates to `ghcr.io` using its automatic `GITHUB_TOKEN` with
`packages: write`; no personal upload token is needed in repository secrets.
Repository/organization policy must permit Actions and package publishing.
Image names derive from the repository running the workflow, lowercased:

```text
ghcr.io/<owner>/<repository>/frontend:sha-<full-commit-sha>
ghcr.io/<owner>/<repository>/backend:sha-<full-commit-sha>
```

For the current origin, the prefix would be `ghcr.io/joiningwithmattheus/sharewise`.
Running the workflow in the team's upstream repository uses that repository's
owner instead. This prevents publishing a fork's images into someone else's
namespace. Commit tags identify the source version; both images build before
either is pushed. Use a fully successful workflow run, since a network failure
could still interrupt publication between the two pushes. For strict immutable
references, deployments can use registry image digests instead of tags.

After the first successful default-branch run:

1. Open the run in GitHub **Actions**. Its summary supplies the exact
   `FRONTEND_IMAGE` and `BACKEND_IMAGE` lines for the VM environment file.
2. Check the packages' access settings. For private packages, the VM's GitHub
   account needs read access and a personal access token (classic) with
   `read:packages`. On the VM run `docker login ghcr.io -u YOUR_GITHUB_USERNAME`
   and enter the token at the password prompt. Public images can be pulled
   anonymously. Never put the token in Git, Compose or chat.
3. In a separate `~/sharewise` directory on docker-01, place `compose.deploy.yml`,
   `docker/keycloak/sharewise-realm.json` (preserving its directories), and your
   private `.env.deploy.local` with both passwords and the two published image
   references. Keep the existing `~/favsdemo` deployment separate.
4. Run the Compose `pull` and `up -d --no-build` commands above, then `ps` and
   `logs --tail=50`. Confirm login and an authenticated app operation through the
   SSH forwards below. Do not treat a green image publication job as deployment.

This implements CI and image delivery; it does not remotely execute commands on
the university VM. On 20 November 2026 a runner service was installed on docker-01
for the separate private `JoiningwithMattheus/sharewise-deploy` repository. Its
deployment workflow now polls successful main publications every five minutes
and supports manual runs. A forced deployment of the verified version passed
with a database backup and service health checks. See sections
11 and 12 of [the team guide](docs/TEAM_DEPLOYMENT_GUIDE.md) for setup, service logs
and the remaining steps.

Reference: https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images

From Windows, forward both the application and identity-provider ports:

```sh
ssh -J edudscloud_lab -o ExitOnForwardFailure=yes -L 3100:127.0.0.1:3100 -L 8180:127.0.0.1:8180 student@192.168.181.2
```

Keep the terminal open and browse http://localhost:3100. Exclude `localhost` and
`127.0.0.1` from Firefox's SOCKS proxy. OpenVPN is not required for this SSH route.
The database and backend have no host ports; only Next.js accesses the backend.
The demo's 3000/8080 ports remain separate.

## Data and lifecycle

The `sharewise-lab` project owns separate database and identity volumes; existing
development volumes are not reused. `down` stops the stack and preserves data:

```sh
docker compose --env-file .env.deploy.local -f compose.deploy.yml down
```

Do not use `down -v` on retained data. Database migrations run on backend startup;
this branch includes historical destructive migrations, so do not point this new
deployment at an existing database without reviewing them and taking backups.
Realm imports and bootstrap passwords apply on initial setup, not as an update
mechanism. Changing an env password does not rotate existing database credentials.
Back up both identity and application data before upgrades.
