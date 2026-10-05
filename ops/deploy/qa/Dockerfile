FROM node:22-bookworm AS node
FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends systemd systemd-sysv openjdk-21-jre-headless postgresql postgresql-client curl jq unzip zip ca-certificates && rm -rf /var/lib/apt/lists/* && useradd --create-home yanus
COPY --from=node /usr/local/bin/node /usr/local/bin/node
STOPSIGNAL SIGRTMIN+3
CMD ["/sbin/init"]
