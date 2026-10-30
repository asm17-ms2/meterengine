import assert from "node:assert/strict";
import test from "node:test";
import frontendConfig from "../eslint.config.mjs";
import { namingRules as demoNamingRules } from "../../demo/eslint-naming-rules.mjs";

test("demo keeps the same naming-convention rules as the frontend ESLint config", () => {
  const configured = frontendConfig.find(config => config.rules?.["@typescript-eslint/naming-convention"]);
  const [, ...frontendNamingRules] = configured.rules["@typescript-eslint/naming-convention"];
  assert.deepStrictEqual(demoNamingRules, frontendNamingRules);
});
