/** Production: the SPA is served behind the same origin as the API (reverse proxy), so relative URLs are used. */
export const environment = {
  production: true,
  apiBaseUrl: '',
  wsBaseUrl: (typeof location !== 'undefined' ? (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host : ''),
};
