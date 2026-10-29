const NEXT_RESERVED_EXPORTS =
  "^(metadata|viewport|config|dynamic|dynamicParams|revalidate|fetchCache|runtime|preferredRegion|maxDuration|generateMetadata|generateViewport|generateStaticParams)$";
const ROUTE_HANDLER_METHODS = "^(GET|HEAD|POST|PUT|DELETE|PATCH|OPTIONS)$";
const HTTP_HEADER_NAME = "^[A-Z][a-z]+(-[A-Z][a-z]+)*$";
const CONSECUTIVE_CAPITALS = "[A-Z]{2}";

export const namingRules = [
  { selector: "default", format: ["camelCase"], custom: { regex: CONSECUTIVE_CAPITALS, match: false } },
  { selector: "variable", format: ["strictCamelCase", "StrictPascalCase", "UPPER_CASE"] },
  { selector: "function", format: ["camelCase", "PascalCase"], custom: { regex: CONSECUTIVE_CAPITALS, match: false } },
  { selector: "function", modifiers: ["exported"], filter: { regex: ROUTE_HANDLER_METHODS, match: true }, format: null },
  { selector: "parameter", format: ["strictCamelCase"], leadingUnderscore: "allow" },
  { selector: "typeLike", format: ["PascalCase"], custom: { regex: CONSECUTIVE_CAPITALS, match: false } },
  { selector: "interface", format: ["StrictPascalCase"], custom: { regex: "^I[A-Z]", match: false } },
  {
    selector: ["typeProperty", "objectLiteralProperty"],
    format: ["camelCase", "snake_case"],
    custom: { regex: CONSECUTIVE_CAPITALS, match: false },
  },
  { selector: ["typeProperty", "objectLiteralProperty"], modifiers: ["requiresQuotes"], format: null },
  { selector: "objectLiteralProperty", filter: { regex: HTTP_HEADER_NAME, match: true }, format: null },
  { selector: "enumMember", format: ["UPPER_CASE"] },
  { selector: "import", format: null },
  {
    selector: "variable",
    modifiers: ["const", "global"],
    types: ["string", "number"],
    format: ["UPPER_CASE"],
    filter: { regex: NEXT_RESERVED_EXPORTS, match: false },
  },
  {
    selector: "variable",
    modifiers: ["const", "global"],
    types: ["boolean"],
    format: ["StrictPascalCase"],
    prefix: ["is", "has", "can"],
  },
  { selector: "variable", modifiers: ["destructured"], types: ["boolean"], format: ["strictCamelCase"] },
  { selector: "variable", types: ["boolean"], format: ["StrictPascalCase"], prefix: ["is", "has", "can"] },
];
