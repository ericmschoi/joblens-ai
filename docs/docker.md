# Running JobLens in containers

Two images, both pinned by tag **and** digest, both running as a non-root user, and neither carrying
a secret.

```bash
docker compose up --build
open http://localhost:8081
```

That is the deployed shape: the browser talks only to the frontend, which serves the SPA and
forwards `/api` to the backend. The backend port is not published to the host.

## The images

| | Backend | Frontend |
|---|---|---|
| Build stage | `eclipse-temurin:25.0.3_9-jdk-noble` | `node:24.19.0-trixie-slim` |
| Runtime stage | `eclipse-temurin:25.0.3_9-jre-noble` | `nginx:1.29.8-alpine3.23` |
| Runs as | `joblens`, uid 10001 | `nginx`, uid 101 |
| Port | 8080 | 8081 |
| Size | ~570 MB | ~64 MB |

Every `FROM` carries `@sha256:…` next to the tag. A tag can be moved by whoever publishes it; a
digest cannot. Pinning both means "the image we tested" and "the image that ships" are the same
bytes, and a base-image update is a visible commit rather than something that happens to a build.

### Why Java 25.0.3+9 in the container and 25.0.4+7 locally

Both are Java 25 LTS. `25.0.3_9` is the newest Temurin 25 published to Docker Hub; `25.0.4+7` is
the newest Temurin 25 tarball, which is what `docs/versions.md` pins for local development. The
container tracks the newest available *image*, and moves when a `25.0.4` image is published.

### Why the frontend listens on 8081

A process that is not root cannot bind a privileged port. Dropping that capability is worth more
than the conventional port number, so nginx runs unprivileged on 8081 and whatever sits in front —
compose, an ALB, a local browser — maps it.

### Why there is no HEALTHCHECK instruction

The runtime image has no HTTP client, and adding curl to get one would widen the image for something
the orchestrator does better. ECS and the load balancer probe `GET /actuator/health` over HTTP,
which is a real check of the running application rather than of the container process.

## What is not in the images

- **No secrets.** The default analysis provider is the in-process fake, so the backend runs with no
  API key and makes no outbound AI call. A real provider's credentials would arrive from the
  environment at run time and never from a layer.
- **No source, no build tools, no package manager cache.** Both runtimes are separate stages that
  copy only the built artefact.
- **No personal documents.** `.dockerignore` in each directory keeps `*.pdf`, logs and local build
  output out of the build context entirely, so a stray file cannot reach a layer even by accident.

## Hardening applied by compose

```yaml
read_only: true       # the backend writes nothing but /tmp, because it persists nothing
tmpfs: [/tmp]
cap_drop: [ALL]
security_opt: [no-new-privileges:true]
```

Verified rather than assumed: `touch /app/x` inside the running backend answers
`Read-only file system`.

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `JOBLENS_ANALYSIS_PROVIDER` | `fake` | Which provider serves an analysis. |
| `JOBLENS_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Browser origins allowed to call the API. Empty in a same-origin deployment. |
| `JOBLENS_BACKEND` (frontend) | `backend:8080` | Where nginx forwards `/api`. Substituted into the config at startup. |

Spring's relaxed binding maps each of these onto the corresponding `joblens.*` property, so any
value in `application.yml` can be overridden the same way.

## Architectures

Built and verified on `linux/arm64` (Apple silicon). For an `amd64` deployment target, build for
both:

```bash
docker buildx build --platform linux/amd64,linux/arm64 -t <registry>/joblens-backend:<tag> backend
```

Both base images publish multi-arch manifests, and the digests above are manifest-list digests, so
the same pin works for either architecture.

## Verified locally

- `docker build` succeeds for both images
- `docker compose up` brings both up; the SPA is served at <http://localhost:8081>
- A resume upload, both confirmations and a full analysis complete through the containers, with the
  headline score recomputing exactly from the six categories
- The backend runs as uid 10001 on a read-only root filesystem; the frontend as uid 101
- API responses through the proxy keep their `no-store` and content-security headers, unduplicated
