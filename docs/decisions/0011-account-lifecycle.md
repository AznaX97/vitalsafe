# 0011: Changing a password, resetting it by email, and deleting an account

Status: accepted

## Decisions

- Asking for a reset link always answers 202, whether or not the address has an account. It sends at most 3 emails
  per account per hour, and an address may ask 5 times an hour.
- The emailed token is 32 random bytes. Only its SHA-256 hash is stored. It works once, expires after 30 minutes,
  and it travels in the URL fragment, which is never sent to a server and never appears in logs.
- Mail is sent on a background thread, so the time to answer does not reveal whether the address exists.
- Changing a password, or resetting it, ends every refresh session and cancels every unused reset link.
- A wrong current password is a 400 with a field message, never a 401, so the app does not think the login expired.
- Deleting an account needs the password and removes the user, their readings, target ranges, sessions and reset tokens.
- With no mail server configured, nothing is sent and the link is never written to a log.

## Limits

- An access token (15 minutes) is not stored on the server, so after a password change or a deletion it stays valid until
  it expires. After a deletion, anything it can still do fails, because the user no longer exists.
- Backups made before an account was deleted still contain its data until they are deleted. The privacy notice says so,
  and states how long backups are kept.
- Email is not verified at registration, so an account can be created for an address its owner does not control.
- Reset throttling is kept in memory, so it restarts when the server restarts and is per server.
- A password reset is only as safe as the mailbox. Two-factor login is not built.