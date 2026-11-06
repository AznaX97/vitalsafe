# Monitoring (local)

1. Start the backend: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-backend.ps1`
2. Start the stack: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\start-monitoring.ps1`
3. Prometheus is at http://localhost:9090 (Status, Targets: `vitalsafe-api` should be UP).
   Grafana is at http://localhost:3000 (user `admin`, password `GRAFANA_PASSWORD` from `.env`). The
   dashboard "VitalSafe overview" is in the VitalSafe folder.
4. Stop it with `docker compose -f docker-compose.monitoring.yml down` (add `-v` to delete the stored history).

If the target is DOWN, allow Java through the Windows firewall for private networks, because the containers reach
the backend through `host.docker.internal`.

## Pinning the image versions

The compose file uses `latest` so it works on first run. Once it does, find what you got and pin it:

    docker image ls --format "{{.Repository}}:{{.Tag}} {{.ID}}" | findstr /i "prom/prometheus grafana/grafana"
    docker run --rm prom/prometheus --version
    docker run --rm --entrypoint grafana grafana/grafana --version

Then replace `latest` with those version numbers in `docker-compose.monitoring.yml`.

## Rules for metrics

- Labels never carry a user id, an email, a value or a note.
- `/actuator/prometheus` needs `METRICS_TOKEN`. A user's login token does not open it. With no token set it
  stays closed.
- In production, do not proxy `/actuator` through the public reverse proxy. Prometheus should reach the backend
  over the internal Docker network.