# syntax=docker/dockerfile:1.6
#
# Stackmania plugin-compat container.
#
# Goal: boot a Stackmania 1.1.2+ server with exactly one plugin installed,
# wait for "Done!", capture latest.log, then exit. Used by .github/workflows/
# plugin-compat.yml and by maintainers running locally.
#
# Image size is not a concern (CI ephemeral). We use jdk-jammy rather than
# alpine because Forge/Mohist's net.minecraftforge.bootstrap occasionally
# trips over musl-libc quirks, and apt-get gets us bash + yq + curl in one
# layer without --add edge.

FROM eclipse-temurin:17-jdk-jammy

# ---- OS deps --------------------------------------------------------------
# bash:        entrypoint + test scripts use bash-isms (set -o pipefail, traps)
# curl:        download Stackmania jar + plugin jars
# unzip, jq:   plugin manifest parsing in future extensions
# ca-certs:    HTTPS to GitHub releases
# python3-yaml: fallback parser if `yq` is unavailable (used by test_plugin.sh)
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
        bash curl unzip jq ca-certificates python3 python3-yaml \
 && rm -rf /var/lib/apt/lists/*

# Install yq (mikefarah/yq v4, Go binary, single static file).
ARG YQ_VERSION=v4.44.3
RUN curl -fsSL "https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/yq_linux_amd64" \
      -o /usr/local/bin/yq \
 && chmod +x /usr/local/bin/yq

# ---- Server layout --------------------------------------------------------
WORKDIR /server

# Download the pinned Stackmania server jar. The tag is reproducible —
# bump this ARG when validating against a newer release.
ARG STACKMANIA_TAG=stackmania-1.20.1-1.1.2
ARG STACKMANIA_JAR=stackmania-1.20.1-1.1.2-server.jar
ARG STACKMANIA_URL=https://github.com/Lorenz0FF/Stackmania/releases/download/${STACKMANIA_TAG}/${STACKMANIA_JAR}

RUN curl -fL "${STACKMANIA_URL}" -o /server/stackmania-server.jar \
 && test -s /server/stackmania-server.jar

# EULA + minimal server.properties + plugins directory
COPY eula.txt /server/eula.txt
COPY server.properties.template /server/server.properties
RUN mkdir -p /server/plugins /server/logs

# Scripts and plugin matrix
COPY entrypoint.sh    /server/entrypoint.sh
COPY test_plugin.sh   /server/test_plugin.sh
COPY plugins.yml      /server/plugins.yml
RUN chmod +x /server/entrypoint.sh /server/test_plugin.sh

# ---- Runtime --------------------------------------------------------------
# JVM kept small — we only test boot+enable(), not gameplay.
ENV JAVA_OPTS="-Xmx2G -Xms2G -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV STACKMANIA_JAR=/server/stackmania-server.jar
ENV BOOT_WAIT_SECONDS=60

# Default command boots an empty server. The plugin matrix workflow overrides
# this with `test_plugin.sh <plugin_id>`.
CMD ["/server/entrypoint.sh"]
