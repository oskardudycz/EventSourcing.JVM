import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, before, describe, it } from 'node:test';
import { editedFiles, packageOf } from './edited-packages.ts';

describe('edited files', () => {
  it('reads the file path of a Claude Code edit', () => {
    assert.deepEqual(
      editedFiles({ file_path: '/repository/src/jvm/src/App.java' }, '/repository'),
      ['/repository/src/jvm/src/App.java'],
    );
  });

  it('reads the notebook path of a Claude Code notebook edit', () => {
    assert.deepEqual(
      editedFiles({ notebook_path: '/repository/src/jvm/notes.ipynb' }, '/repository'),
      ['/repository/src/jvm/notes.ipynb'],
    );
  });

  it('reads the file path of an OpenCode edit relative to the working directory', () => {
    assert.deepEqual(
      editedFiles({ filePath: 'src/jvm/src/App.java' }, '/repository'),
      ['/repository/src/jvm/src/App.java'],
    );
  });

  it('reads every file a patch adds, updates, moves or deletes', () => {
    const patch = [
      '*** Begin Patch',
      '*** Add File: src/jvm/src/Added.java',
      '+export const added = 1;',
      '*** Update File: src/dotnet/src/Updated.java',
      '*** Move to: src/dotnet/src/Moved.java',
      '@@',
      '-old',
      '+new',
      '*** Delete File: src/node/src/Deleted.java',
      '*** End Patch',
    ].join('\n');

    const expected = [
      '/repository/src/jvm/src/Added.java',
      '/repository/src/dotnet/src/Updated.java',
      '/repository/src/dotnet/src/Moved.java',
      '/repository/src/node/src/Deleted.java',
    ];

    assert.deepEqual(editedFiles({ command: patch }, '/repository'), expected);
    assert.deepEqual(editedFiles({ patchText: patch }, '/repository'), expected);
  });

  for (const toolInput of [undefined, null, 'text', {}, { file_path: 42 }]) {
    it(`finds no files in tool input ${JSON.stringify(toolInput)}`, () => {
      assert.deepEqual(editedFiles(toolInput, '/repository'), []);
    });
  }
});

describe('package of an edited file', () => {
  let root = '';

  before(async () => {
    root = await mkdtemp(join(tmpdir(), 'edited-packages-unit-'));
    await writeFile(join(root, 'gradlew'), '');
    await mkdir(join(root, 'src/jvm/src/core'), { recursive: true });
    await writeFile(join(root, 'src/jvm/gradlew'), '');
    await mkdir(join(root, 'src/jvm/compat-kotlin/src'), { recursive: true });
    await mkdir(join(root, 'src/jvm/build/generated'), { recursive: true });
    await mkdir(join(root, 'src/jvm/.gradle/caches'), { recursive: true });
    await mkdir(join(root, 'docs'), { recursive: true });
  });

  after(async () => {
    await rm(root, { recursive: true, force: true });
  });

  it('is the nearest folder with the Gradle wrapper', () => {
    assert.equal(
      packageOf(join(root, 'src/jvm/src/core/App.java'), root),
      join('src', 'jvm'),
    );
  });

  it('is the package folder for its own Gradle wrapper', () => {
    assert.equal(packageOf(join(root, 'src/jvm/gradlew'), root), join('src', 'jvm'));
  });

  it('is the Gradle build that includes the edited subproject', () => {
    assert.equal(
      packageOf(join(root, 'src/jvm/compat-kotlin/src/Compat.kt'), root),
      join('src', 'jvm'),
    );
  });

  it('is found for a file that does not exist yet', () => {
    assert.equal(
      packageOf(join(root, 'src/jvm/src/new_folder/New.java'), root),
      join('src', 'jvm'),
    );
  });

  it('is not the repository root', () => {
    assert.equal(packageOf(join(root, 'README.md'), root), undefined);
    assert.equal(packageOf(join(root, 'docs/guide.md'), root), undefined);
  });

  for (const output of ['src/jvm/build/generated/App.java', 'src/jvm/.gradle/caches/file.bin']) {
    it(`is not a Gradle output in ${output}`, () => {
      assert.equal(packageOf(join(root, output), root), undefined);
    });
  }

  it('is not outside the repository', () => {
    assert.equal(packageOf(join(tmpdir(), 'elsewhere/App.java'), root), undefined);
  });
});
