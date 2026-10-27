import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import checkFile from "eslint-plugin-check-file";
import { namingRules } from "../eslint-naming-rules.mjs";

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
