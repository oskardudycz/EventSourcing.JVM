import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { afterEach, describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import {
  checkRuns,
  createRepository,
  removeRepositories,
  writeGradleWrapper,
} from '../.agents/hooks/fake-repository.ts';

const execFileAsync = promisify(execFile);

const hookScript = resolve(dirname(fileURLToPath(import.meta.url)), 'pre-commit');

const git = (root: string, ...args: string[]) =>
  execFileAsync('git', args, { cwd: root });

const stage = async (root: string, path: string, content = 'class App {}\n') => {
  await mkdir(dirname(join(root, path)), { recursive: true });
  await writeFile(join(root, path), content);
  await git(root, 'add', path);
};

const stagedContent = async (root: string, path: string) =>
  (await git(root, 'show', `:${path}`)).stdout;

const runPreCommit = (root: string) =>
  new Promise<{ exitCode: number; stdout: string; stderr: string }>((done) =>
    execFile('sh', [hookScript], { cwd: root }, (error, stdout, stderr) =>
      done({
        exitCode: typeof error?.code === 'number' ? error.code : 0,
        stdout,
        stderr,
      }),
    ),
  );

describe('pre-commit hook', () => {
  afterEach(removeRepositories);

  it('formats each Gradle build that has staged Java files', async () => {
    const root = await createRepository({
      'samples/talk': 0,
      'workshops/intro/solved': 0,
    });
    await stage(root, 'samples/talk/src/App.java');
    await stage(root, 'workshops/intro/solved/src/main/java/App.java');

    const run = await runPreCommit(root);

    assert.equal(run.exitCode, 0);
    assert.deepEqual((await checkRuns(root)).sort(), [
      'samples/talk spotlessApply',
      'workshops/intro/solved spotlessApply',
    ]);
  });

  it('does not format builds without staged Java files', async () => {
    const root = await createRepository({ 'samples/talk': 0, 'samples/uniqueness': 0 });
    await stage(root, 'samples/talk/src/App.java');
    await stage(root, 'samples/uniqueness/README.md', '# Uniqueness\n');

    await runPreCommit(root);

    assert.deepEqual(await checkRuns(root), ['samples/talk spotlessApply']);
  });

  it('does nothing when no Java file is staged', async () => {
    const root = await createRepository({ 'samples/talk': 0 });
    await stage(root, 'README.md', '# Samples\n');

    const run = await runPreCommit(root);

    assert.equal(run.exitCode, 0);
    assert.deepEqual(await checkRuns(root), []);
  });

  it('does not format the build of a deleted file', async () => {
    const root = await createRepository({ 'samples/talk': 0 });
    await stage(root, 'samples/talk/src/App.java');
    await git(root, '-c', 'user.name=Test', '-c', 'user.email=test@example.com', 'commit', '--quiet', '-m', 'Added app');
    await git(root, 'rm', '--quiet', 'samples/talk/src/App.java');

    await runPreCommit(root);

    assert.deepEqual(await checkRuns(root), []);
  });

  it('stages the formatted content of the staged files', async () => {
    const root = await createRepository();
    const build = join(root, 'samples/talk');
    await mkdir(build, { recursive: true });
    await writeGradleWrapper(build, `printf 'class App {}\\n' > src/App.java`);
    await stage(root, 'samples/talk/src/App.java', 'class   App{}\n');

    const run = await runPreCommit(root);

    assert.equal(run.exitCode, 0);
    assert.equal(await stagedContent(root, 'samples/talk/src/App.java'), 'class App {}\n');
  });

  it('stops the commit when formatting fails', async () => {
    const root = await createRepository();
    const build = join(root, 'samples/talk');
    await mkdir(build, { recursive: true });
    await writeGradleWrapper(build, `echo 'spotlessApply failed' >&2; exit 1`);
    await stage(root, 'samples/talk/src/App.java');

    const run = await runPreCommit(root);

    assert.notEqual(run.exitCode, 0);
    assert.match(run.stderr, /spotlessApply failed/);
  });
});
