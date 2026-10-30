export function isAllowedPublicInvoiceRequest(request: Request, configuredOrigin?: string): boolean {
  if (request.headers.get("sec-fetch-site") === "cross-site") return false;
  const suppliedOrigin = request.headers.get("origin");
  if (!suppliedOrigin) return false;
  try {
    const origin = new URL(suppliedOrigin);
    if (!["http:", "https:"].includes(origin.protocol) || suppliedOrigin !== origin.origin) return false;
    if (request.headers.get("host") !== origin.host) return false;
    if (configuredOrigin) {
      const configured = new URL(configuredOrigin);
      if (configured.username || configured.password || configured.pathname !== "/" || configured.search || configured.hash) return false;
      return origin.origin === configured.origin;
    }
    return ["localhost", "127.0.0.1", "[::1]"].includes(origin.hostname);
  } catch { return false; }
}
