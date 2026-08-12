# Continuous integration

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs on every pull request, on pushes to
`main`, and on demand.

| Job | What it runs |
|---|---|
| `backend` | `./gradlew build` on Temurin 25 — compile with `-Werror`, the full test suite, ArchUnit rules |
| `frontend` | `npm ci`, then typecheck, lint, tests and the production build on Node 24.19.0 |
| `images` | Builds both container images, and throws them away |

Together these are `./scripts/test-all.sh` plus the image builds, run the same way locally and in CI
so a green pull request means the same thing in both places.

## What CI deliberately cannot do

**It deploys nothing and touches no cloud account.** There is no registry login, no OIDC role, no
`AWS_*` secret, and `permissions: contents: read` — the workflow could not push an image or create a
resource even if a step tried to. Merging a pull request therefore costs nothing and changes nothing
outside the repository.

Deployment, when it exists, will be a separate workflow that runs only on manual dispatch against a
protected environment. It will never be triggered by a merge.

## Supply chain

Every action is pinned to a commit SHA, with the version it corresponds to in a trailing comment:

```yaml
uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
```

A tag can be repointed at new code by whoever owns the action, and an action runs with access to the
workflow's context. A SHA cannot be repointed. Updating one is then a visible commit with a
reviewable diff, which is the same rule the Dockerfiles follow for base images.

| Action | Version |
|---|---|
| `actions/checkout` | v7.0.1 |
| `actions/setup-java` | v5.7.0 |
| `actions/setup-node` | v7.0.0 |
| `actions/upload-artifact` | v7.0.1 |
| `docker/setup-buildx-action` | v4.2.0 |
| `docker/build-push-action` | v7.3.0 |

## Known gaps, recorded rather than hidden

- **Browser-tagged tests do not run in CI.** `./gradlew browserTest` downloads Chromium and is
  excluded from `build`; CI runs `build`. The gap is deliberate and stated here so nobody reads a
  green run as covering the Playwright path.
- **The provider acceptance suite does not run in CI.** Against the fake it would add nothing that
  the normal suite does not already cover; against a real provider it would spend money on every
  pull request. It is run deliberately, by hand — see
  [provider-evaluation.md](provider-evaluation.md).
