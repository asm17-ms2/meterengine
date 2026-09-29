import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import checkFile from "eslint-plugin-check-file";

const NEXT_RESERVED_EXPORTS =
  "^(metadata|viewport|config|dynamic|dynamicParams|revalidate|fetchCache|runtime|preferredRegion|maxDuration|generateMetadata|generateViewport|generateStaticParams)$";
const HTTP_HEADER_NAME = "^[A-Z][a-z]+(-[A-Z][a-z]+)*$";
const CONSECUTIVE_CAPITALS = "[A-Z]{2}";

const namingRules = [
  { selector: "default", format: ["camelCase"], custom: { regex: CONSECUTIVE_CAPITALS, match: false } },
  { selector: "variable", format: ["strictCamelCase", "StrictPascalCase", "UPPER_CASE"] },
  { selector: "function", format: ["camelCase", "PascalCase"], custom: { regex: CONSECUTIVE_CAPITALS, match: false } },
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

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  globalIgnores([".next/**", "out/**", "build/**", "next-env.d.ts"]),
  {
    files: ["src/**/*.{ts,tsx}"],
    plugins: { "check-file": checkFile },
    languageOptions: { parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname } },
    rules: {
      "@typescript-eslint/naming-convention": ["error", ...namingRules],
      "check-file/filename-naming-convention": [
        "error",
        { "src/components/**/*.tsx": "PASCAL_CASE", "src/!(components)/**/*.{ts,tsx}": "KEBAB_CASE" },
        { ignoreMiddleExtensions: true },
      ],
      "check-file/folder-naming-convention": [
        "error",
        { "src/app/**/": "NEXT_JS_APP_ROUTER_CASE", "src/!(app)/**/": "KEBAB_CASE" },
      ],
    },
  },
]);

export default eslintConfig;
