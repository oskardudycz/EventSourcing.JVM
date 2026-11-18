import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { afterEach, describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import {
  checkRuns,
  createRepository,
  removeRepositories,
  writeGradleWrapper,
} from '../.agents/hooks/fake-repository.ts';

const script = resolve(dirname(fileURLToPath(import.meta.url)), 'gradlew-of-file.sh');

const runTask = (root: string, file: string, ...tasks: string[]) =>
  new Promise<{ exitCode: number; stderr: string }>((done) =>
    execFile('sh', [script, join(root, file), ...tasks], { cwd: root }, (error, _stdout, stderr) =>
      done({ exitCode: typeof error?.code === 'number' ? error.code : 0, stderr }),
    ),
  );

const createFile = async (root: string, path: string) => {
  await mkdir(dirname(join(root, path)), { recursive: true });
  await writeFile(join(root, path), 'class App {}\n');
};

describe('gradlew-of-file', () => {
  afterEach(removeRepositories);

  it('runs the task in the Gradle build that holds the file', async () => {
    const root = await createRepository({ 'samples/talk': 0, 'workshops/intro/solved': 0 });
    await createFile(root, 'workshops/intro/solved/src/main/java/App.java');

    const run = await runTask(root, 'workshops/intro/solved/src/main/java/App.java', 'testClasses');

    assert.equal(run.exitCode, 0);
    assert.deepEqual(await checkRuns(root), ['workshops/intro/solved testClasses']);
  });

  it('passes all arguments to Gradle', async () => {
    const root = await createRepository();
    const build = join(root, 'samples/talk');
    await mkdir(build, { recursive: true });
    await writeGradleWrapper(build, `printf '%s\\n' "$*" > ${JSON.stringify(join(root, 'arguments'))}`);
    await createFile(root, 'samples/talk/src/App.java');

    await runTask(root, 'samples/talk/src/App.java', 'test', '--debug-jvm');

    assert.equal(await readFile(join(root, 'arguments'), 'utf8'), 'test --debug-jvm\n');
  });

  it('fails when no Gradle build holds the file', async () => {
    const root = await createRepository({ 'samples/talk': 0 });
    await createFile(root, 'README.md');

    const run = await runTask(root, 'README.md', 'testClasses');

    assert.notEqual(run.exitCode, 0);
    assert.match(run.stderr, /No Gradle build holds .*README\.md/);
    assert.deepEqual(await checkRuns(root), []);
  });
});
