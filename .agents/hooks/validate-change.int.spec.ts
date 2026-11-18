import assert from 'node:assert/strict';
import { join } from 'node:path';
import { after, describe, it } from 'node:test';
import { checkRuns, createRepository, removeRepositories, runHook, writeGradleWrapper } from './fake-repository.ts';
import { recordEdit } from './record-edit.ts';
import { validateChangedSession } from './validate-change.ts';

const stopScript = join(import.meta.dirname, 'validate-change.ts');
const recordEditScript = join(import.meta.dirname, 'record-edit.ts');

const stop = (root: string) => ({ cwd: root, session_id: 'session-1' });

const edit = (root: string, file: string) => ({
  ...stop(root),
  tool_input: { file_path: join(root, file) },
});

describe('validate-change hook', { concurrency: true }, () => {
  after(removeRepositories);

  it('passes without running the check when the session did not edit files', async () => {
    const root = await createRepository({ 'src/jvm': 1 });

    const result = await validateChangedSession(stop(root));

    assert.deepEqual(result, { ok: true, output: '' });
    assert.deepEqual(await checkRuns(root), []);
  });

  it('formats and compiles only the edited packages, once, when they pass', async () => {
    const root = await createRepository({
      'src/jvm': 0,
      'src/dotnet': 0,
      'src/node': 1,
    });

    recordEdit(edit(root, 'src/jvm/src/App.java'));
    recordEdit(edit(root, 'src/dotnet/src/App.java'));
    const firstResult = await validateChangedSession(stop(root));
    const secondResult = await validateChangedSession(stop(root));

    assert.equal(firstResult.ok, true);
    assert.deepEqual(secondResult, { ok: true, output: '' });
    assert.deepEqual(await checkRuns(root), [
      'src/jvm spotlessApply',
      'src/jvm testClasses',
      'src/dotnet spotlessApply',
      'src/dotnet testClasses',
    ]);
  });

  it('checks again on the next stop only the packages that failed', async () => {
    const root = await createRepository({ 'src/jvm': 0, 'src/dotnet': 1 });

    recordEdit(edit(root, 'src/jvm/src/App.java'));
    recordEdit(edit(root, 'src/dotnet/src/App.java'));
    const firstResult = await validateChangedSession(stop(root));
    const secondResult = await validateChangedSession(stop(root));

    assert.equal(firstResult.ok, false);
    assert.match(firstResult.output, /Check failed in src\/dotnet\./);
    assert.match(firstResult.output, /testClasses output in .*src\/dotnet/);
    assert.doesNotMatch(firstResult.output, /src\/jvm/);
    assert.equal(secondResult.ok, false);
    assert.deepEqual(await checkRuns(root), [
      'src/jvm spotlessApply',
      'src/jvm testClasses',
      'src/dotnet spotlessApply',
      'src/dotnet testClasses',
      'src/dotnet spotlessApply',
      'src/dotnet testClasses',
    ]);
  });

  it('does not compile when formatting fails', async () => {
    const root = await createRepository({ 'src/jvm': 0 });
    await writeGradleWrapper(join(root, 'src/jvm'), 'node check.cjs "$@"; exit 1');

    recordEdit(edit(root, 'src/jvm/src/App.java'));
    const result = await validateChangedSession(stop(root));

    assert.equal(result.ok, false);
    assert.deepEqual(await checkRuns(root), ['src/jvm spotlessApply']);
  });
});

describe('validate-change hook script', { concurrency: true }, () => {
  after(removeRepositories);

  it('passes without running the check when the session did not edit files', async () => {
    const root = await createRepository({ 'src/jvm': 1 });

    const run = await runHook(stopScript, stop(root));

    assert.deepEqual(run, { exitCode: 0, stdout: '', stderr: '' });
    assert.deepEqual(await checkRuns(root), []);
  });

  it('passes after an edit when the check passes', async () => {
    const root = await createRepository({ 'src/jvm': 0 });

    const mark = await runHook(recordEditScript, edit(root, 'src/jvm/src/App.java'));
    const run = await runHook(stopScript, stop(root));

    assert.deepEqual(mark, { exitCode: 0, stdout: '', stderr: '' });
    assert.deepEqual(run, { exitCode: 0, stdout: '', stderr: '' });
    assert.deepEqual(await checkRuns(root), ['src/jvm spotlessApply', 'src/jvm testClasses']);
  });

  it('blocks with the check output after an edit when the check fails', async () => {
    const root = await createRepository({ 'src/jvm': 1 });

    await runHook(recordEditScript, edit(root, 'src/jvm/src/App.java'));
    const run = await runHook(stopScript, stop(root));

    assert.equal(run.exitCode, 2);
    assert.match(run.stderr, /`\.\/gradlew spotlessApply` and `\.\/gradlew testClasses` failed\./);
    assert.match(run.stderr, /Check failed in src\/jvm\./);
    assert.match(run.stderr, /testClasses output/);
  });

  it('skips the check when the stop hook is already active', async () => {
    const root = await createRepository({ 'src/jvm': 1 });

    await runHook(recordEditScript, edit(root, 'src/jvm/src/App.java'));
    const run = await runHook(stopScript, { ...stop(root), stop_hook_active: true });

    assert.deepEqual(run, { exitCode: 0, stdout: '', stderr: '' });
    assert.deepEqual(await checkRuns(root), []);
  });
});
