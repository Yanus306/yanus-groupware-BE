FROM postgres:16.15
RUN apt-get update && apt-get install -y --no-install-recommends age jq curl && rm -rf /var/lib/apt/lists/*
COPY ops/backup /usr/local/libexec/yanus-backup
RUN chmod 0755 /usr/local/libexec/yanus-backup/*.sh
