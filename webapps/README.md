# KeepSpace web app

Static production pages for KeepSpace:

- `/` — product home page
- `/privacy/` — privacy policy for KeepSpace 1.1
- `/terms/` — terms of use for KeepSpace 1.1
- `/support/` — public support page
- `/healthz` — container health endpoint

Production URL: <https://keepspace.itston.com/>

The site intentionally uses no JavaScript, cookies, analytics, remote fonts, or third-party page resources.

## Run locally with Docker

```bash
docker build -t keepspace-web ./webapps
docker run --rm -p 8080:8080 keepspace-web
```

Open <http://localhost:8080>.

## Run locally with Docker Compose

```bash
docker compose -f webapps/docker-compose.yml up --build -d
docker compose -f webapps/docker-compose.yml ps
```

Stop the container with:

```bash
docker compose -f webapps/docker-compose.yml down
```

The default host port is `8080`. Override it when needed:

```bash
KEEP_SPACE_WEB_PORT=8081 docker compose -f webapps/docker-compose.yml up --build -d
```

## Run without Docker

Any static file server works. Directory-index support is required for `/privacy/` and `/terms/`.

```bash
python3 -m http.server 8080 --directory webapps
```

The source-of-truth legal text remains in `../legal/privacy-policy.md` and `../legal/terms-of-use.md`. Update the web pages whenever those files change.
