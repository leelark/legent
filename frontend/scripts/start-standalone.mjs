import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const standaloneDir = path.join(root, '.next', 'standalone');
const serverPath = path.join(standaloneDir, 'server.js');

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

copyIfExists(path.join(root, '.next', 'static'), path.join(standaloneDir, '.next', 'static'));
copyIfExists(path.join(root, 'public'), path.join(standaloneDir, 'public'));

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
