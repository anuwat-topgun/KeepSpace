# KeepSpace web app

Static production pages for KeepSpace:

- `/` — product home page
- `/privacy/` — privacy policy for KeepSpace 1.1
- `/terms/` — terms of use for KeepSpace 1.1
- `/healthz` — container health endpoint

The site intentionally uses no JavaScript, cookies, analytics, remote fonts, or third-party page resources.

## Run locally with Docker

```bash
docker build -t keepspace-web ./webapps
docker run --rm -p 8080:8080 keepspace-web
```

Open <http://localhost:8080>.

## Run without Docker

Any static file server works. Directory-index support is required for `/privacy/` and `/terms/`.

```bash
python3 -m http.server 8080 --directory webapps
```

The source-of-truth legal text remains in `../legal/privacy-policy.md` and `../legal/terms-of-use.md`. Update the web pages whenever those files change.
