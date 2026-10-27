import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import jsonata from 'jsonata';

const template = JSON.parse(await readFile(new URL('../workflow.asl.json', import.meta.url)));
const account = '928618307647';
const region = 'ap-northeast-2';
const db = 'meterengine-test-slo-db';
const arn = `arn:aws:rds:${region}:${account}:db:${db}`;
const now = '2026-10-26T12:00:00.000Z';
const config = {
  account_id: account, region, db_identifier: db, db_arn: arn,
  claim_table: 'meterengine-test-slo-rds-auto-stop-claims',
  legacy_schedule_name: 'meterengine-test-slo-stop-rds', legacy_schedule_group: 'default',
  legacy_scheduler_role_arn: `arn:aws:iam::${account}:role/meterengine-test-slo-scheduler-role`,
};
const event = {
  version: '0', id: '11111111-2222-3333-4444-555555555555',
  source: 'aws.rds', 'detail-type': 'RDS DB Instance Event', account, region,
  time: now, resources: [arn],
  detail: { EventID: 'RDS-EVENT-0154', SourceArn: arn, SourceIdentifier: db,
    SourceType: 'DB_INSTANCE', Date: now },
};
const nativeEvent = {
  SourceArn: arn, SourceIdentifier: db, SourceType: 'db-instance',
  EventCategories: ['notification'], Date: now,
  Message: 'DB instance is being started due to it exceeding the maximum allowed time being stopped.',
};
const schedule = {
  Arn: `arn:aws:scheduler:${region}:${account}:schedule/default/${config.legacy_schedule_name}`,
  Name: config.legacy_schedule_name, GroupName: 'default', State: 'ENABLED',
  ScheduleExpression: 'cron(0 4 * * ? *)', ScheduleExpressionTimezone: 'Asia/Seoul',
  FlexibleTimeWindow: { Mode: 'OFF' }, ActionAfterCompletion: 'NONE',
  Target: {
    Arn: 'arn:aws:scheduler:::aws-sdk:rds:stopDBInstance',
    RoleArn: config.legacy_scheduler_role_arn,
    Input: JSON.stringify({ DbInstanceIdentifier: db }),
    RetryPolicy: { MaximumEventAgeInSeconds: 86400, MaximumRetryAttempts: 0 },
  },
  CreationDate: '2026-10-01T00:00:00Z', LastModificationDate: '2026-10-02T00:00:00Z',
};

async function evaluate(value, bindings) {
  if (typeof value === 'string' && value.startsWith('{%') && value.endsWith('%}')) {
    const expression = jsonata(value.slice(2, -2));
    expression.registerFunction('parse', JSON.parse, '<s:o>');
    return expression.evaluate({}, bindings);
  }
  if (Array.isArray(value)) return Promise.all(value.map(v => evaluate(v, bindings)));
  if (value && typeof value === 'object') {
    return Object.fromEntries(await Promise.all(Object.entries(value).map(async ([k, v]) => [k, await evaluate(v, bindings)])));
  }
  return value;
}

function failure(name) {
  return Object.assign(new Error(name), { name });
}

// Execute the checked-in ASL expressions and transitions with SDK responses supplied by each scenario.
async function run(options = {}) {
  const definition = structuredClone(template);
  definition.States.Configure.Assign = { event: '{% $states.input %}', config };
  const input = options.event ?? structuredClone(event);
  const vars = {};
  const claims = options.claims ?? new Set();
  const trace = [];
  const nativeEvents = options.nativeEvents ?? [structuredClone(nativeEvent)];
  const statusPlan = options.statuses ?? ['starting', 'available', 'stopping', 'stopped'];
  let legacy = structuredClone(options.schedule ?? schedule);
  let statusIndex = 0;
  let elapsed = 0;
  let stateName = definition.StartAt;
  let stateInput = input;
  let outcome;
  const execution = { Id: `arn:aws:states:${region}:${account}:execution:test:${crypto.randomUUID()}`, StartTime: options.startTime ?? now };
  const bindings = (result, error) => ({ ...vars, states: { input: stateInput, result, errorOutput: error, context: { Execution: execution } } });
  async function sdk(resource, args) {
    trace.push({ resource, args: structuredClone(args) });
    if (options.sdkFailure && resource.endsWith(options.sdkFailure)) throw failure('Service.UnavailableException');
    if (resource.endsWith('dynamodb:putItem')) {
      assert.equal(args.ConditionExpression, 'attribute_not_exists(event_key)');
      const key = args.Item.event_key.S;
      if (claims.has(key)) throw failure('DynamoDb.ConditionalCheckFailedException');
      claims.add(key);
      assert.equal(Number(args.Item.expires_at.N), Date.parse(execution.StartTime) / 1000 + 604800);
      return {};
    }
    if (resource.endsWith('rds:describeDBInstances')) {
      assert.deepEqual(args, { DbInstanceIdentifier: db });
      return { DbInstances: [{ DbInstanceStatus: statusPlan[Math.min(statusIndex++, statusPlan.length - 1)] }] };
    }
    if (resource.endsWith('rds:describeEvents')) {
      assert.equal(args.SourceIdentifier, db);
      assert.equal(args.SourceType, 'db-instance');
      return { Events: nativeEvents, ...(options.eventMarker ? { Marker: options.eventMarker } : {}) };
    }
    if (resource.endsWith('rds:stopDBInstance')) {
      if (options.stopFailure) throw failure('Rds.InvalidDBInstanceStateException');
      return { DbInstance: { DbInstanceStatus: 'stopping' } };
    }
    if (resource.endsWith('scheduler:getSchedule')) return legacy;
    if (resource.endsWith('scheduler:updateSchedule')) {
      legacy = { ...legacy, ...args, State: options.keepEnabled ? 'ENABLED' : args.State };
      return { ScheduleArn: legacy.Arn };
    }
    throw new Error(`Unexpected resource ${resource}`);
  }
  try {
    for (let transitions = 0; transitions < 2000; transitions++) {
      if (elapsed >= definition.TimeoutSeconds) throw failure('States.Timeout');
      const state = definition.States[stateName];
      if (!state) throw new Error(`Missing state ${stateName}`);
      if (state.Type === 'Succeed') { outcome = stateName; break; }
      if (state.Type === 'Fail') throw failure(state.Error);
      if (state.Type === 'Choice') {
        let next = state.Default;
        for (const choice of state.Choices) {
          if (await evaluate(choice.Condition, bindings())) { next = choice.Next; break; }
        }
        stateName = next;
        continue;
      }
      if (state.Type === 'Wait') elapsed += state.Seconds;
      let result;
      if (state.Type === 'Task') {
        const args = await evaluate(state.Arguments, bindings());
        let retries = 0;
        while (true) {
          try { result = await sdk(state.Resource, args); break; }
          catch (error) {
            const caught = state.Catch?.find(c => c.ErrorEquals.includes(error.name) || c.ErrorEquals.includes('States.ALL'));
            if (caught) { stateName = caught.Next; result = undefined; break; }
            const retry = state.Retry?.find(r => r.ErrorEquals.includes(error.name) || r.ErrorEquals.includes('States.TaskFailed'));
            if (!retry || retries >= retry.MaxAttempts) throw error;
            elapsed += retry.IntervalSeconds * retry.BackoffRate ** retries++;
          }
        }
        if (result === undefined) continue;
      }
      const nextVars = await evaluate(state.Assign ?? {}, bindings(result));
      const output = await evaluate(state.Output ?? stateInput, bindings(result));
      Object.assign(vars, nextVars);
      stateInput = output;
      stateName = state.Next;
    }
  } catch (error) { outcome = error.name; }
  assert.ok(outcome, 'workflow must finish within the transition bound');
  return {
    outcome, trace, claims, legacy, elapsed,
    stops: trace.filter(c => c.resource.endsWith('rds:stopDBInstance')),
    updates: trace.filter(c => c.resource.endsWith('scheduler:updateSchedule')),
  };
}

function withoutSideEffects(result) {
  assert.equal(result.stops.length, 0);
  assert.equal(result.updates.length, 0);
}

test('automatic restart waits for available and confirms stopped before retiring only RDS protection', async () => {
  const result = await run();
  assert.equal(result.outcome, 'AutomaticStopConfirmed');
  assert.equal(result.stops.length, 1);
  assert.equal(result.updates.length, 1);
  assert.deepEqual(result.updates[0].args, {
    Name: schedule.Name, GroupName: 'default', State: 'DISABLED',
    ScheduleExpression: schedule.ScheduleExpression, ScheduleExpressionTimezone: schedule.ScheduleExpressionTimezone,
    FlexibleTimeWindow: schedule.FlexibleTimeWindow, Target: schedule.Target,
    ActionAfterCompletion: 'NONE',
  });
  assert.equal(result.trace.findIndex(c => c.resource.endsWith('scheduler:getSchedule')) > result.trace.findIndex(c => c.resource.endsWith('rds:stopDBInstance')), true);
  assert.equal(result.elapsed, 90);
});

for (const [name, mutate] of [
  ['manual-start event', e => { e.detail.EventID = 'RDS-EVENT-0088'; }],
  ['foreign account', e => { e.account = '111111111111'; }],
  ['foreign region', e => { e.region = 'us-east-1'; }],
  ['custom source', e => { e.source = 'custom.rds'; }],
  ['foreign database ARN', e => { e.detail.SourceArn += '-other'; }],
  ['foreign database identifier', e => { e.detail.SourceIdentifier += '-other'; }],
  ['foreign envelope resource', e => { e.resources = [arn + '-other']; }],
  ['multiple resources', e => { e.resources.push(arn + '-other'); }],
  ['wrong source type', e => { e.detail.SourceType = 'DB_CLUSTER'; }],
  ['invalid event id', e => { e.id = 'synthetic'; }],
  ['mismatched occurrence time', e => { e.detail.Date = '2026-10-26T11:57:00Z'; }],
  ['event older than 15 minutes', e => { e.time = e.detail.Date = '2026-10-26T11:44:59Z'; }],
  ['event more than one minute in future', e => { e.time = e.detail.Date = '2026-10-26T12:01:01Z'; }],
]) {
  test(`${name} cannot stop or change a schedule`, async () => {
    const input = structuredClone(event);
    mutate(input);
    const result = await run({ event: input });
    assert.equal(result.outcome, 'IgnoredEvent');
    assert.equal(result.trace.length, 0);
    withoutSideEffects(result);
  });
}

test('same event cannot stop a database manually started after the first execution', async () => {
  const first = await run();
  const duplicate = await run({ claims: first.claims, statuses: ['available'] });
  assert.equal(duplicate.outcome, 'DuplicateEvent');
  assert.equal(duplicate.trace.length, 1);
  withoutSideEffects(duplicate);
});

test('concurrent duplicate executions claim atomically', async () => {
  const claims = new Set();
  const results = await Promise.all([run({ claims }), run({ claims })]);
  assert.deepEqual(results.map(r => r.outcome).sort(), ['AutomaticStopConfirmed', 'DuplicateEvent']);
  assert.equal(results.flatMap(r => r.stops).length, 1);
});

test('already stopped does not falsely prove the new flow or disable legacy protection', async () => {
  const result = await run({ statuses: ['stopped'] });
  assert.equal(result.outcome, 'AlreadyStopped');
  withoutSideEffects(result);
});

test('missing native automatic restart record blocks synthetic input', async () => {
  const result = await run({ nativeEvents: [] });
  assert.equal(result.outcome, 'NativeEventNotConfirmed');
  withoutSideEffects(result);
});

test('a newer native stop makes the old event obsolete even with a different envelope id', async () => {
  const stopped = { ...nativeEvent, Date: '2026-10-26T12:02:00Z', Message: 'DB instance stopped' };
  const result = await run({ nativeEvents: [nativeEvent, stopped] });
  assert.equal(result.outcome, 'SupersededEvent');
  withoutSideEffects(result);
});

test('native event schema needs no EventID and accepts numeric AWS timestamps', async () => {
  assert.equal(Object.hasOwn(nativeEvent, 'EventID'), false);
  const result = await run({ nativeEvents: [{ ...nativeEvent, Date: Date.parse(now) / 1000 }] });
  assert.equal(result.outcome, 'AutomaticStopConfirmed');
});

test('never available times out with no stop and keeps protection', async () => {
  const result = await run({ statuses: ['starting'] });
  assert.equal(result.outcome, 'States.Timeout');
  withoutSideEffects(result);
});

test('stop rejection is propagated without retrying or disabling protection', async () => {
  const result = await run({ stopFailure: true });
  assert.equal(result.outcome, 'Rds.InvalidDBInstanceStateException');
  assert.equal(result.stops.length, 1);
  assert.equal(result.updates.length, 0);
});

test('accepted stop without stopped confirmation keeps protection until timeout', async () => {
  const result = await run({ statuses: ['available', 'stopping'] });
  assert.equal(result.outcome, 'States.Timeout');
  assert.equal(result.stops.length, 1);
  assert.equal(result.updates.length, 0);
});

test('a new start during stopped polling is not stopped again', async () => {
  const result = await run({ statuses: ['available', 'starting'] });
  assert.equal(result.outcome, 'UnexpectedDatabaseStatus');
  assert.equal(result.stops.length, 1);
  assert.equal(result.updates.length, 0);
});

test('database API errors are bounded and visible without schedule cutover', async () => {
  const result = await run({ sdkFailure: 'rds:describeDBInstances' });
  assert.equal(result.outcome, 'Service.UnavailableException');
  assert.equal(result.trace.length, 4);
  withoutSideEffects(result);
});

test('changed legacy target is retained', async () => {
  const changed = structuredClone(schedule);
  changed.Target.Input = JSON.stringify({ DbInstanceIdentifier: 'other-database' });
  const result = await run({ schedule: changed });
  assert.equal(result.outcome, 'LegacyScheduleChanged');
  assert.equal(result.updates.length, 0);
});

test('all writable optional schedule fields and exact target are preserved', async () => {
  const original = {
    ...schedule, Description: 'Keep this description',
    StartDate: '2026-10-01T00:00:00Z', EndDate: '2027-10-01T00:00:00Z',
    KmsKeyArn: `arn:aws:kms:${region}:${account}:key/test`,
  };
  const result = await run({ schedule: original });
  const expected = { ...original, State: 'DISABLED' };
  for (const key of ['Arn', 'CreationDate', 'LastModificationDate']) delete expected[key];
  assert.deepEqual(result.updates[0].args, expected);
});

test('schedule disable needs readback confirmation', async () => {
  const result = await run({ keepEnabled: true });
  assert.equal(result.outcome, 'LegacyScheduleNotDisabled');
});

test('failed claimed execution cannot be replayed against a later manual start', async () => {
  const first = await run({ stopFailure: true });
  const replay = await run({ claims: first.claims, statuses: ['available'] });
  assert.equal(replay.outcome, 'DuplicateEvent');
  withoutSideEffects(replay);
});


test('a paginated native event result cannot hide a later stop and trigger a new stop', async () => {
  const result = await run({ eventMarker: 'next-page-with-newer-stop' });
  assert.equal(result.outcome, 'NativeEventQueryIncomplete');
  withoutSideEffects(result);
});
