import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const standaloneDir = path.join(root, '.next', 'standalone');
const serverPath = path.join(standaloneDir, 'server.js');
const copyLockPath = path.join(standaloneDir, '.asset-copy.lock');

if (!fs.existsSync(serverPath)) {
  console.error('[start] .next/standalone/server.js missing. Run npm run build first.');
  process.exit(1);
}

function copyIfExists(source, target) {
  if (!fs.existsSync(source)) {
    return;
  }
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.cpSync(source, target, { recursive: true, force: true });
}

async function wait(ms) {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function withCopyLock(action) {
  let fd;
  const deadline = Date.now() + 30_000;
  while (!fd) {
    try {
      fd = fs.openSync(copyLockPath, 'wx');
    } catch (error) {
      if (error?.code !== 'EEXIST') {
        throw error;
      }
      try {
        const ageMs = Date.now() - fs.statSync(copyLockPath).mtimeMs;
        if (ageMs > 120_000) {
          fs.rmSync(copyLockPath, { force: true });
          continue;
        }
      } catch {
        // Lock disappeared between checks.
      }
      if (Date.now() > deadline) {
        throw new Error('Timed out waiting for standalone asset copy lock');
      }
      await wait(250);
    }
  }

  try {
    action();
  } finally {
    fs.closeSync(fd);
    fs.rmSync(copyLockPath, { force: true });
  }
}

await withCopyLock(() => {
  copyIfExists(path.join(root, '.next', 'static'), path.join(standaloneDir, '.next', 'static'));
  copyIfExists(path.join(root, 'public'), path.join(standaloneDir, 'public'));
});

const child = spawn(process.execPath, ['server.js'], {
  cwd: standaloneDir,
  stdio: 'inherit',
  env: {
    ...process.env,
    PORT: process.env.PORT || '3000',
    HOSTNAME: process.env.HOSTNAME || '127.0.0.1',
  },
});

child.on('exit', (code, signal) => {
  if (signal) {
    process.exit(1);
  }
  process.exit(code ?? 0);
});

child.on('error', (error) => {
  console.error(`[start] failed to start standalone server: ${error.message}`);
  process.exit(1);
});
