# Email

## Development: the fake mail server

    docker compose -f docker-compose.mail.yml up -d --wait

Every email lands at http://localhost:8025 and nothing leaves your PC. `.env` holds `SPRING_MAIL_HOST=localhost` and
`SPRING_MAIL_PORT=1025`, which `scripts\run-backend.ps1` passes to the backend. Stop it with
`docker compose -f docker-compose.mail.yml down`.

## Production

There is no mail server in the production stack. Without one, "forgot password" sends nothing, and the log says so
without printing the link. To send real email you need an SMTP provider, which needs an account with them. Put its
settings in `.env.prod` and in the backend's `environment:` in `docker-compose.prod.yml`:
`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, and
`SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`.
Also set `APP_MAIL_FROM` to an address on your own domain, because providers refuse unknown senders.
Then add the provider to the privacy notice, because it will see the email addresses of people who ask for a reset.