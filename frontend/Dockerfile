# JobLens frontend: build the SPA, then serve the static output.
#
# Both stages are pinned by tag and digest. The runtime carries no Node, no npm and no source —
# only the built assets and a web server.

# --- build ------------------------------------------------------------------------------------
FROM node:24.19.0-trixie-slim@sha256:0711b541c1c33a8a530ac4f0d391baa9a15b3d804695b1b24a47daa5fb60e74d AS build

WORKDIR /workspace

# npm ci installs exactly the lockfile, and caches independently of source changes.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- runtime ----------------------------------------------------------------------------------
FROM nginx:1.29.8-alpine3.23@sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de AS runtime

# A template rather than a finished config: the backend address is the one thing that differs
# between compose and a deployed environment, and the image's entrypoint substitutes it at startup.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist /usr/share/nginx/html

ENV JOBLENS_BACKEND="backend:8080"

# nginx's own unprivileged user. Port 8081 rather than 80 because a process that is not root cannot
# bind a privileged port, and dropping that capability is worth more than the conventional number.
RUN touch /var/run/nginx.pid \
 && chown -R nginx:nginx /var/run/nginx.pid /var/cache/nginx /usr/share/nginx/html /etc/nginx/conf.d

USER nginx

EXPOSE 8081

# No API keys, no origins, no environment-specific values are baked in: the SPA calls /api on its
# own origin, and the server in front decides what that means.
