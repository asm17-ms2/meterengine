import policy from "./environment-routes.json" with { type: "json" };

export function requiresWorkspaceEnvironment(path: string): boolean {
  const segments = path.split("?", 1)[0].split("/");
  const route = policy.routes.find(route => {
    const template = route.path.split("/");
    return template.length === segments.length && template.every((segment, index) => /^\{[^{}]+\}$/.test(segment) ? segments[index].length > 0 : segment === segments[index]);
  });
  return route?.environment_required ?? policy.default.environment_required;
}
