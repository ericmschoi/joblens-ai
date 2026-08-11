# Version baseline

JobLens targets the newest production-ready stable versions that are *officially compatible with
each other*, not simply the highest version numbers. Long-running runtimes (Java, Node.js) use the
newest LTS line. Alpha, beta, RC, canary, snapshot and nightly builds are never used.

All versions are pinned: `backend/gradle/wrapper/gradle-wrapper.properties`, `backend/build.gradle.kts`,
`backend/gradle.lockfile`, `frontend/package.json` and `frontend/package-lock.json`. Floating tags
such as `latest` are not used anywhere.

Verified on 2026-08-10.

## Runtimes and build tools

| Component | Version | Verified against |
|---|---|---|
| Java | Eclipse Temurin 25.0.4+7 LTS | `api.adoptium.net/v3/assets/latest/25/hotspot` (`most_recent_lts: 25`) |
| Spring Boot | 4.1.0 | Maven Central `spring-boot-starter-parent` metadata; `start.spring.io/actuator/info` |
| Spring Framework | 7.0.8 (managed by the Spring Boot BOM) | Spring Boot 4.1.0 system requirements |
| Gradle | 9.7.0, Kotlin DSL | `services.gradle.org/versions/current` (`snapshot:false`, `activeRc:false`) |
| Node.js | 24.19.0 LTS (Krypton) | `nodejs.org/dist/index.json` |
| npm | 11.17.0 | Bundled with Node.js 24.19.0 |

Java 26 and Node.js 26 exist but are not LTS, so they are not used as project runtimes.

### Compatibility evidence

- **Gradle 9.7.0 with Java 25** — the Gradle compatibility matrix lists Java 25 support for both
  toolchains and running the Gradle daemon from Gradle 9.1.0 onwards.
- **Spring Boot 4.1.0 with Java 25 and Gradle 9.7.0** — the Spring Boot system requirements state
  Java 17 through 26, Gradle 8.14 or later and 9.x, and Spring Framework 7.0.8 or above.
- **npm** — npm 12.0.2 is published and declares support for Node.js `^24.15.0`, so it is compatible.
  The project stays on 11.17.0, the version that ships with Node.js 24 LTS, because the lockfile
  format is part of reproducibility and everyone building this repository should produce the same
  one. Revisit when Node.js 24 LTS bundles a newer npm.

## Frontend

| Component | Version |
|---|---|
| React / React DOM | 19.2.8 |
| TypeScript | 6.0.3 (see the deviation below) |
| Vite | 8.2.1 |
| `@vitejs/plugin-react` | 6.0.5 |
| Vitest | 4.1.10 |
| jsdom | 30.0.1 |
| Testing Library (react / dom / jest-dom / user-event) | 16.3.2 / 10.4.1 / 7.0.1 / 14.6.3 |
| axe-core | 4.13.0 |
| ESLint | 10.8.1 |
| typescript-eslint | 8.67.0 |
| `eslint-plugin-react-hooks` | 7.1.1 |

`jsdom@30.0.1` requires Node.js `^24.15.0`, which is one of the reasons the project requires
Node.js 24.19.0 rather than an older 24.x patch.

## Deviations from "newest stable"

### TypeScript is pinned to 6.0.3, not 7.0.2

TypeScript 7.0.2 is the newest stable release, but it cannot be used with type-aware linting yet.

- `typescript-eslint@8.67.0`, the newest stable release, declares the peer range
  `typescript >=4.8.4 <6.1.0`. Installing TypeScript 7 alongside it fails resolution outright.
- TypeScript 7.0 replaced the compiler with the native Go implementation and does not yet ship a
  stable programmatic API. That API is targeted for TypeScript 7.1, and typescript-eslint depends on
  it for type-aware rules.
- The available workarounds — suppressing peer resolution, or running TypeScript 7 for compilation
  alongside a TypeScript 6 alias purely for linting — mean two compilers disagreeing about types.
  That is not an acceptable foundation.

TypeScript 6.0.3 is the newest stable release that the whole toolchain officially supports. No other
component was downgraded.

**Upgrade trigger:** move to the TypeScript 7 line as soon as typescript-eslint publishes a stable
release whose peer range accepts it.

### `eslint-plugin-jsx-a11y` is not used

`eslint-plugin-jsx-a11y@6.10.2` declares support for ESLint 3 through 9 and does not accept
ESLint 10. Rather than hold ESLint back for a static approximation of accessibility, the project
asserts accessibility at runtime with axe-core in component tests, and checks colour contrast
directly against the design tokens in `frontend/src/styles/tokens.test.ts`. Reconsider the plugin
when it supports ESLint 10.

## Docker

Docker packaging is a later phase and no Dockerfile exists yet. The versions currently installed on
the development machine are Docker Desktop 4.11.0, Docker Engine client 20.10.17 and Docker Compose
v2.7.0, which are well behind current releases. Upgrade before starting the Docker phase.

When that phase starts: use the official Eclipse Temurin 25 image, pin base images by tag *and*
digest, separate build and runtime stages, run as a non-root user, keep no build tooling in the
runtime image, and keep the container's Java major version identical to the local build.
