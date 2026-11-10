import { defineConfig } from "eslint/config";
import checkFile from "eslint-plugin-check-file";
import tseslint from "typescript-eslint";
import { namingRules } from "../eslint-naming-rules.mjs";

export default defineConfig([
  {
    files: ["src/**/*.ts"],
    extends: [tseslint.configs.recommended],
    plugins: { "check-file": checkFile },
    languageOptions: { parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname } },
    rules: {
      "@typescript-eslint/naming-convention": ["error", ...namingRules],
      "check-file/filename-naming-convention": ["error", { "src/**/*.ts": "KEBAB_CASE" }, { ignoreMiddleExtensions: true }],
      "check-file/folder-naming-convention": ["error", { "src/**/": "KEBAB_CASE" }],
    },
  },
]);
