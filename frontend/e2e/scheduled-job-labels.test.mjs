import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const operations = readFileSync(new URL("../../backend/src/main/java/com/meterengine/dashboard/service/OperationsService.java", import.meta.url), "utf8");
const view = readFileSync(new URL("../src/components/workspace/WorkspaceView.tsx", import.meta.url), "utf8");

test("every scheduled job the operations API lists has a label on the operations screen", () => {
  const expectedJobs = operations.slice(operations.indexOf("from (values"), operations.indexOf("expected(job_name)"));
  const jobs = [...expectedJobs.matchAll(/'([a-z_]+)'/g)].map(match => match[1]);
  const labels = view.match(/const JOB_LABELS[^{]*\{([^}]*)\}/)[1];
  assert.ok(jobs.length > 0);
  for (const job of jobs) assert.match(labels, new RegExp(`\\b${job}:`), `${job} has no label in JOB_LABELS`);
});
