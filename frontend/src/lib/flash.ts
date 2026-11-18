// A message to show once on the next page, for example on the login page after the session ended.
// It lives in a variable instead of the router, because ending the session makes a protected page redirect by itself,
// and that redirect would drop anything passed through the router.
let message: string | null = null;

export function setFlash(text: string): void {
  message = text;
}

export function peekFlash(): string | null {
  return message;
}

export function clearFlash(): void {
  message = null;
}