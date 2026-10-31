import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const routes = readFileSync(new URL("../../backend/src/main/resources/db/migration/V3__create_guards_and_routes.sql", import.meta.url), "utf8");
const view = readFileSync(new URL("../src/components/workspace/WorkspaceView.tsx", import.meta.url), "utf8");

test("every scheduled job the operations API lists has a label on the operations screen", () => {
  const sqlView = routes.slice(routes.indexOf("CREATE VIEW shared.operations_job_status"));
  const expectedJobs = sqlView.match(/CROSS JOIN \(VALUES ([\s\S]*?)\) jobs\(job_name\)/)?.[1] ?? "";
  const jobs = [...expectedJobs.matchAll(/'([a-z_]+)'::varchar/g)].map(match => match[1]);
  const labels = view.match(/const JOB_LABELS[^{]*\{([^}]*)\}/)[1];
  assert.ok(jobs.length > 0);
  for (const job of jobs) assert.match(labels, new RegExp(`\\b${job}:`), `${job} has no label in JOB_LABELS`);
});
