const LOCAL_HOSTNAMES = new Set(["127.0.0.1", "localhost", "::1"]);

export function isLocalHost(host: string | null): boolean {
  if (host === null) {
    return false;
  }
  const value = host.trim().toLowerCase();
  const hostname = value.startsWith("[") ? value.slice(1, value.indexOf("]")) : value.replace(/:\d*$/, "");
  return LOCAL_HOSTNAMES.has(hostname);
}
