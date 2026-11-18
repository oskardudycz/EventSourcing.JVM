import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { after, describe, it } from 'node:test';
import { createRepository, removeRepositories, runHook } from './fake-repository.ts';
import { recordEdit } from './record-edit.ts';

const hookScript = join(import.meta.dirname, 'record-edit.ts');

const marker = (root: string) =>
  join(root, '.agents/.cache/agent-check/session-1');

const recordedPackages = async (root: string) => {
  try {
    return (await readFile(marker(root), 'utf8')).split('\n').filter(Boolean);
  } catch {
    return undefined;
  }
};

const edit = (root: string, file: string, cwd = root) => ({
  cwd,
  session_id: 'session-1',
  tool_input: { file_path: join(root, file) },
});

describe('record-edit hook', { concurrency: true }, () => {
  after(removeRepositories);

  it('records the package of the edited file from the hook script input', async () => {
    const root = await createRepository({ 'src/jvm': 0 });

    const run = await runHook(hookScript, edit(root, 'src/jvm/src/App.java'));

    assert.deepEqual(run, { exitCode: 0, stdout: '', stderr: '' });
    assert.deepEqual(await recordedPackages(root), ['src/jvm']);
  });

  it('records each edited package once', async () => {
    const root = await createRepository({ 'src/jvm': 0, 'src/dotnet': 0 });

    recordEdit(edit(root, 'src/jvm/src/App.java'));
    recordEdit(edit(root, 'src/dotnet/src/App.java'));
    recordEdit(edit(root, 'src/jvm/src/Other.java'));

    assert.deepEqual(await recordedPackages(root), ['src/jvm', 'src/dotnet']);
  });

  it('records every package a patch changes, relative to the working directory', async () => {
    const root = await createRepository({ 'src/jvm': 0, 'src/node': 0 });

    recordEdit({
      cwd: join(root, 'src/jvm'),
      session_id: 'session-1',
      tool_input: {
        command: [
          '*** Begin Patch',
          '*** Update File: src/App.java',
          '*** Add File: ../node/src/Added.java',
          '*** End Patch',
        ].join('\n'),
      },
    });

    assert.deepEqual(await recordedPackages(root), [
      'src/jvm',
      'src/node',
    ]);
  });

  it('records the edit in the repository root when the session works in a subdirectory', async () => {
    const root = await createRepository({ 'src/jvm': 0 });

    recordEdit(edit(root, 'src/jvm/src/App.java', join(root, 'src/jvm')));

    assert.deepEqual(await recordedPackages(root), ['src/jvm']);
  });

  it('does not record an edit outside of any package', async () => {
    const root = await createRepository({ 'src/jvm': 0 });

    recordEdit(edit(root, 'README.md'));

    assert.equal(await recordedPackages(root), undefined);
  });

  it('does not record the edit when the session id is not valid', async () => {
    const root = await createRepository({ 'src/jvm': 0 });

    recordEdit({ ...edit(root, 'src/jvm/src/App.java'), session_id: '../escape' });

    assert.equal(await recordedPackages(root), undefined);
  });

  it('does not fail when the edit cannot be recorded', async () => {
    const root = await createRepository({ 'src/jvm': 0 });
    await writeFile(join(root, '.agents'), '');

    assert.doesNotThrow(() => recordEdit(edit(root, 'src/jvm/src/App.java')));
  });
});
