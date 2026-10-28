// 보고(report.json, report.md, state.json)가 맡은 항목을 빠짐없이 끝 상태로 남겼는지, 고친 커밋이 검사를 거쳐 push됐는지 확인한다.
// 사용: git fetch origin 뒤 clone 루트에서 node .stack456-team/tools/check-report.mjs <패키지>
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const pkg = process.argv[2];
if (!pkg) throw new Error("사용: node check-report.mjs <패키지>");
const REPORT_DIR = join(ROOT, ".s456-report", "stack456-team-report");
const TERMINAL = ["refuted", "already-fixed", "fixed", "needs-decision", "blocked"];
const STATUSES = [...TERMINAL, "open"];
const RUN_STATUSES = ["running", "paused-usage", "stopped", "done"];
const CHECK_KINDS = ["backend", "frontend", "frontend-unit", "screen", "demo", "deploy", "rds-auto-stop", "static", "forbidden-chars"];
const CHECK_RESULTS = ["pass", "fail", "not-run", "not-applicable", "flaky"];
const KINDS_BY_PATH = [
  [/^backend\//, ["backend"]],
  [/^frontend\//, ["frontend", "frontend-unit"]],
  [/^frontend\/src\/(app|components)\/|^frontend\/src\/.*\.css$|^frontend\/e2e\/.*\.real\.spec\.ts$/, ["screen"]],
  [/^demo\//, ["demo"]],
  [/^deploy\//, ["deploy"]],
  [/^measurements\/terraform\/modules\/rds-auto-stop\//, ["rds-auto-stop"]],
  [/^measurements\/(?!terraform\/modules\/rds-auto-stop\/)|^deploy\/grafana\/|^\.github\/|\.sh$/, ["static"]],
];
const PASSING_RESULTS = { screen: ["pass", "not-applicable"] };
const STACK = [
  ["feat/account-permissions", "72e8718"], ["feat/product-design", "0f1d1bf"], ["feat/webhook-jobs", "81651ed"],
  ["feat/billing-payments", "54bb1d5"], ["feat/pricing-aggregation", "c27b229"], ["feat/resource-archive", "8babc3d"],
  ["feat/request-audit", "bc7e538"], ["feat/customer-invoices", "2fe98d1"], ["feat/event-ingestion", "7b1d1e5"],
  ["feat/billing-queries", "17cc788"], ["feat/llm-service-demo", "2aa2be2"], ["feat/environment-isolation", "f014e03"],
  ["feat/auth-rate-limit", "dac784b"], ["feat/rds-auto-stop", "0fe7d2a"],
];
const BLOCKED_PREFIX = /^(not-owner|do-not-touch|needs-new-migration|check-unavailable|no-evidence|sync-conflict)\b/;
const EVIDENCE_FORMS = [
  /^\S+:\d+(?:-\d+)?(?:,\d+(?:-\d+)?)* @ [0-9a-f]{7,40}$/,
  /^.+: (통과|실패|pass|fail)(\s|$)/,
  /^커밋 [0-9a-f]{7,40}$/,
];
const errors = [];
const warnings = [];

function load(path) {
  if (!existsSync(path)) { errors.push(`파일이 없다: ${path}`); return null; }
  try { return JSON.parse(readFileSync(path, "utf8")); } catch (error) { errors.push(`JSON이 아니다: ${path}: ${error.message}`); return null; }
}
function need(object, keys, where) {
  for (const key of keys) if (!object || !(key in object)) errors.push(`${where}에 ${key}가 없다`);
}
function isAncestor(ancestor, descendant) {
  try {
    execFileSync("git", ["-C", ROOT, "merge-base", "--is-ancestor", ancestor, descendant], { stdio: "ignore" });
    return true;
  } catch {
    return false;
  }
}
function changedPaths(sha) {
  try {
    return execFileSync("git", ["-C", ROOT, "diff", "--name-only", `${sha}^1`, sha], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).split("\n").filter(Boolean);
  } catch {
    return null;
  }
}
function git(args) {
  try {
    return execFileSync("git", ["-C", ROOT, ...args], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim();
  } catch {
    return null;
  }
}
function fullSha(sha) {
  return git(["rev-parse", "--verify", "--quiet", `${sha}^{commit}`]);
}
function passes(check, kind) {
  return (PASSING_RESULTS[kind] ?? ["pass"]).includes(check.result);
}
function ownCommits(branch, tip, email, reported) {
  const index = STACK.findIndex(([name]) => name === branch);
  if (index < 0) return null;
  const lower = index === 0 ? "origin/main" : `origin/${STACK[index - 1][0]}`;
  const log = git(["log", "--no-merges", "--format=%H %ae", `${STACK[index][1]}..${tip}`, `^${lower}`]);
  if (log === null) return null;
  return log.split("\n").filter(Boolean).map(line => line.split(" ")).filter(([sha, author]) => author === email || reported.has(sha)).map(([sha]) => sha);
}
function requiredKinds(paths) {
  const kinds = new Set(["forbidden-chars"]);
  for (const path of paths) for (const [pattern, list] of KINDS_BY_PATH) if (pattern.test(path)) list.forEach(kind => kinds.add(kind));
  return kinds;
}

const rawFindings = load(join(ROOT, ".stack456-team", pkg, "findings.json"));
const findings = Array.isArray(rawFindings) ? rawFindings : rawFindings?.findings ?? rawFindings?.items ?? [];
const report = load(join(REPORT_DIR, "report.json"));
const state = load(join(REPORT_DIR, "state.json"));
const mdPath = join(REPORT_DIR, "report.md");
const firstLine = existsSync(mdPath) ? readFileSync(mdPath, "utf8").split("\n")[0] : null;
if (firstLine === null) errors.push(`파일이 없다: ${mdPath}`);

need(report, ["package", "owner", "status", "updated_at", "start_heads", "end_heads", "env", "items", "new_findings", "checks", "pushes", "usage_log", "local_db_recreate"], "report.json");
need(state, ["package", "status", "updated_at", "handover_sha", "stop_reason", "resume_hint", "current", "finished_ids", "open_ids", "since_report_push", "branches", "usage", "live_env"], "state.json");
if (report && !RUN_STATUSES.includes(report.status)) errors.push(`report.json status가 잘못됐다: ${report.status}`);
if (state && !RUN_STATUSES.includes(state.status)) errors.push(`state.json status가 잘못됐다: ${state.status}`);
if (report && state && report.status !== state.status) errors.push(`report.json(${report.status})과 state.json(${state.status})의 status가 다르다`);

const checks = report?.checks ?? [];
for (const check of checks) {
  if (!CHECK_KINDS.includes(check.kind)) errors.push(`checks ${check.branch} ${check.sha}: kind가 잘못됐다: ${check.kind}`);
  if (!CHECK_RESULTS.includes(check.result)) errors.push(`checks ${check.branch} ${check.sha}: result가 잘못됐다: ${check.result}`);
}
function hasPassingCheck(branch, kind, commit) {
  return checks.some(check => check.branch === branch && check.kind === kind && passes(check, kind) && isAncestor(commit, check.sha));
}

const assigned = [];
for (const item of findings) {
  assigned.push(item.id);
  for (const merged of item.merged_ids ?? []) assigned.push(merged);
}

const statusOf = new Map();
const entryOf = new Map();
const syncConflicts = [];
function checkEntry(entry, where) {
  const label = `${where} ${entry.id ?? "(id 없음)"}`;
  need(entry, ["id", "merged_ids", "layer", "branch", "status", "verdict", "reason", "evidence", "commits", "tests", "behavior_change"], label);
  if (!STATUSES.includes(entry.status)) errors.push(`${label}: status가 잘못됐다: ${entry.status}`);
  if (entry.status === "fixed") {
    if (!entry.commits?.length) errors.push(`${label}: fixed인데 commits가 비었다`);
    if (typeof entry.fails_without_fix !== "boolean") errors.push(`${label}: fixed인데 fails_without_fix가 없다`);
    if (entry.fails_without_fix === true && !(entry.evidence ?? []).some(line => /: (실패|fail)(\s|$)/.test(line))) errors.push(`${label}: fails_without_fix가 true인데 evidence에 고치기 전 실패 출력이 없다`);
    if (entry.fails_without_fix === false && !entry.tests?.length) errors.push(`${label}: fails_without_fix가 false인데 tests에 확인한 방법이 없다`);
    if (!entry.top_head) errors.push(`${label}: fixed인데 top_head가 없다`);
    for (const sha of entry.commits ?? []) {
      const paths = changedPaths(sha);
      if (!paths) { errors.push(`${label}: 커밋 ${sha}의 바뀐 파일을 읽지 못했다`); continue; }
      for (const kind of requiredKinds(paths)) if (!hasPassingCheck(entry.branch, kind, sha)) errors.push(`${label}: 커밋 ${sha}를 포함한 ${entry.branch}의 ${kind} 통과 기록이 checks에 없다`);
    }
  }
  if (["refuted", "already-fixed", "fixed"].includes(entry.status)) {
    for (const line of entry.evidence ?? []) if (!EVIDENCE_FORMS.some(form => form.test(line))) errors.push(`${label}: evidence 형식이 아니다: ${line}`);
  }
  if (entry.status === "needs-decision" && !entry.question) errors.push(`${label}: needs-decision인데 question이 없다`);
  if (entry.status === "blocked") {
    if (!entry.blocked_reason) errors.push(`${label}: blocked인데 blocked_reason이 없다`);
    else if (!BLOCKED_PREFIX.test(entry.blocked_reason)) errors.push(`${label}: blocked_reason이 정한 머리말로 시작하지 않는다: ${entry.blocked_reason}`);
    else if (entry.blocked_reason.startsWith("sync-conflict")) syncConflicts.push(entry);
  }
  if (entry.status === "open" && !entry.left) errors.push(`${label}: open인데 left가 없다`);
  if (["refuted", "already-fixed"].includes(entry.status) && !entry.evidence?.length) errors.push(`${label}: ${entry.status}인데 evidence가 비었다`);
  for (const sha of entry.commits ?? []) if (!isAncestor(sha, `origin/${entry.branch}`)) errors.push(`${label}: 커밋 ${sha}가 origin/${entry.branch}에 없다`);
}
for (const entry of report?.items ?? []) {
  checkEntry(entry, "items");
  for (const id of [entry.id, ...(entry.merged_ids ?? [])]) {
    if (statusOf.has(id)) errors.push(`${id}가 두 번 나온다`);
    statusOf.set(id, entry.status);
    entryOf.set(id, entry);
  }
}
for (const entry of report?.new_findings ?? []) checkEntry(entry, "new_findings");

for (const entry of syncConflicts) {
  const pr = /^sync-conflict #(\d+)/.exec(entry.blocked_reason)?.[1] ?? String(entry.layer);
  const dir = join(REPORT_DIR, "evidence", "sync-conflict", pr);
  const patches = existsSync(dir) ? readdirSync(dir).filter(name => name.endsWith(".patch")) : [];
  if (!patches.length) errors.push(`${entry.id}: sync-conflict인데 evidence/sync-conflict/${pr}/에 patch가 없다`);
}
if (report?.status === "done" && syncConflicts.length) errors.push(`status가 done인데 sync-conflict 항목이 ${syncConflicts.length}건 남았다`);
const unpushed = Object.entries(state?.branches ?? {}).filter(([, branch]) => branch.unpushed?.length);
if (report?.status === "done" && unpushed.length) errors.push(`status가 done인데 push하지 않은 커밋이 남은 브랜치가 있다: ${unpushed.map(([name]) => name).join(", ")}`);

const email = git(["config", "user.email"]);
if (!email) errors.push("git config user.email을 읽지 못했다");
const reported = new Set();
for (const entry of [...(report?.items ?? []), ...(report?.new_findings ?? [])]) {
  for (const sha of entry.commits ?? []) {
    const full = fullSha(sha);
    if (full) reported.add(full);
  }
}
const pushes = report?.pushes ?? [];
for (const push of pushes) {
  const label = `pushes ${push.branch ?? "(branch 없음)"} ${push.sha ?? "(sha 없음)"}`;
  need(push, ["branch", "sha", "at"], label);
  const pushed = push.sha ? fullSha(push.sha) : null;
  if (!pushed) { errors.push(`${label}: 커밋을 찾지 못했다`); continue; }
  if (!isAncestor(pushed, `origin/${push.branch}`)) errors.push(`${label}: origin/${push.branch}에 없다`);
  const own = ownCommits(push.branch, pushed, email, reported);
  if (own === null) { errors.push(`${label}: 이 세션 커밋을 읽지 못했다(스택 브랜치인지, git fetch origin을 했는지 본다)`); continue; }
  const kinds = new Set(["forbidden-chars"]);
  for (const sha of own) {
    const paths = changedPaths(sha);
    if (!paths) errors.push(`${label}: 커밋 ${sha.slice(0, 7)}의 바뀐 파일을 읽지 못했다`);
    else requiredKinds(paths).forEach(kind => kinds.add(kind));
  }
  for (const kind of kinds) {
    if (!checks.some(check => check.branch === push.branch && check.kind === kind && passes(check, kind) && fullSha(check.sha) === pushed)) errors.push(`${label}: push한 sha에서 ${kind} 통과 기록이 checks에 없다`);
  }
}
for (const [branch] of STACK) {
  if (!git(["rev-parse", "--verify", "--quiet", `origin/${branch}`])) continue;
  const own = ownCommits(branch, `origin/${branch}`, email, reported);
  if (own === null) { errors.push(`${branch}: 이 세션 커밋을 읽지 못했다`); continue; }
  for (const sha of own) {
    if (!reported.has(sha)) errors.push(`${branch}: 커밋 ${sha.slice(0, 7)}이 어느 항목의 commits에도 없다`);
    if (!pushes.some(push => push.branch === branch && push.sha && isAncestor(sha, push.sha))) errors.push(`${branch}: 커밋 ${sha.slice(0, 7)}을 올린 push가 pushes에 없다`);
  }
}

const assignedSet = new Set(assigned);
for (const id of statusOf.keys()) if (!assignedSet.has(id)) warnings.push(`${id}는 findings.json에 없는 id다. 새 지적이면 new_findings로 옮긴다`);
const open = assigned.filter(id => !TERMINAL.includes(statusOf.get(id)));
const newOpen = (report?.new_findings ?? []).filter(entry => !TERMINAL.includes(entry.status)).map(entry => entry.id);
const totalOpen = open.length + newOpen.length;
const counts = Object.fromEntries(TERMINAL.map(status => [status, assigned.filter(id => statusOf.get(id) === status).length]));
const fixedAbove = assigned.filter(id => statusOf.get(id) === "already-fixed" && String(entryOf.get(id)?.top_head ?? "").startsWith("fixed-above")).length;
const noEvidence = assigned.filter(id => statusOf.get(id) === "blocked" && String(entryOf.get(id)?.blocked_reason ?? "").startsWith("no-evidence")).length;
const done = assigned.length - open.length;

if (state) {
  const stateOpen = new Set(state.open_ids ?? []);
  const missing = open.filter(id => !stateOpen.has(id));
  const extra = [...stateOpen].filter(id => !open.includes(id));
  if (missing.length || extra.length) errors.push(`state.json open_ids가 report.json과 다르다(빠짐 ${missing.length}, 남음 ${extra.length})`);
}

if (firstLine !== null) {
  const prefix = totalOpen > 0 ? `미완료 ${totalOpen}건, ` : "";
  if (totalOpen > 0 && !firstLine.startsWith(prefix)) errors.push(`report.md 첫 줄이 "${prefix}"로 시작해야 한다: ${firstLine}`);
  if (totalOpen === 0 && firstLine.startsWith("미완료")) errors.push("모두 끝났는데 report.md 첫 줄이 미완료로 시작한다");
  const body = firstLine.slice(firstLine.startsWith(prefix) ? prefix.length : 0);
  if (!body.startsWith("결과:")) errors.push(`report.md 첫 줄의 미완료 다음이 "결과:"로 시작해야 한다: ${firstLine}`);
  for (const part of [`끝 상태 ${done}/${assigned.length}건`, `fixed-above ${fixedAbove}`, `no-evidence ${noEvidence}`]) {
    if (!firstLine.includes(part)) errors.push(`report.md 첫 줄에 "${part}"가 없다: ${firstLine}`);
  }
}
if (report?.status === "done" && totalOpen > 0) errors.push(`status가 done인데 끝 상태가 없는 id가 ${totalOpen}건이다`);

console.log(`끝 상태 ${done}/${assigned.length}건 (fixed ${counts.fixed}, refuted ${counts.refuted}, already-fixed ${counts["already-fixed"]}(그중 fixed-above ${fixedAbove}), needs-decision ${counts["needs-decision"]}, blocked ${counts.blocked}(그중 no-evidence ${noEvidence})), 미완료 ${totalOpen}건`);
if (open.length) console.log(`끝 상태가 없는 id: ${open.join(", ")}`);
if (newOpen.length) console.log(`끝 상태가 없는 새 지적: ${newOpen.join(", ")}`);
for (const warning of warnings) console.log(`주의: ${warning}`);
for (const error of errors) console.log(`오류: ${error}`);
process.exit(errors.length ? 1 : 0);
