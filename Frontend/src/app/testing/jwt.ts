/** Builds an unsigned JWT-shaped token with the given expiry (seconds from now) for tests. */
export function fakeJwt(expInSeconds: number, extra: Record<string, unknown> = {}): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64({ alg: 'HS256' })}.${b64({ sub: 'u1', exp: Math.floor(Date.now() / 1000) + expInSeconds, ...extra })}.sig`;
}
