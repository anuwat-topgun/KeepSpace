FROM nginxinc/nginx-unprivileged:alpine

COPY --chown=nginx:nginx nginx.conf /etc/nginx/conf.d/default.conf
COPY --chown=nginx:nginx index.html 404.html robots.txt /usr/share/nginx/html/
COPY --chown=nginx:nginx sitemap.xml /usr/share/nginx/html/
COPY --chown=nginx:nginx assets/ /usr/share/nginx/html/assets/
COPY --chown=nginx:nginx privacy/ /usr/share/nginx/html/privacy/
COPY --chown=nginx:nginx terms/ /usr/share/nginx/html/terms/
COPY --chown=nginx:nginx support/ /usr/share/nginx/html/support/

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
