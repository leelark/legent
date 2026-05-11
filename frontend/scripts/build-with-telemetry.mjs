import { spawn, spawnSync } from 'node:child_process';
import os from 'node:os';

const startedAt = Date.now();
const timeoutMs = Number(process.env.LEGENT_BUILD_TIMEOUT_MS || process.env.BUILD_TIMEOUT_MS || 900_000);
const heartbeatMs = Number(process.env.LEGENT_BUILD_HEARTBEAT_MS || 30_000);
const nextBin = 'node_modules/next/dist/bin/next';

function elapsedSeconds() {
  return Math.round((Date.now() - startedAt) / 1000);
}

function terminateTree(pid) {
  if (!pid) {
    return;
  }
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/pid', String(pid), '/T', '/F'], { stdio: 'inherit' });
    return;
  }
  try {
    process.kill(-pid, 'SIGTERM');
  } catch {
    try {
      process.kill(pid, 'SIGTERM');
    } catch {
      // Child already exited.
    }
  }
}

console.log(`[build] start next build pid=${process.pid}`);
console.log(`[build] node=${process.version} platform=${process.platform} arch=${process.arch} cpus=${os.cpus().length}`);
console.log(`[build] timeoutMs=${timeoutMs}`);

const child = spawn(process.execPath, [nextBin, 'build'], {
  stdio: 'inherit',
  detached: process.platform !== 'win32',
  env: {
    ...process.env,
    NEXT_TELEMETRY_DISABLED: process.env.NEXT_TELEMETRY_DISABLED || '1',
  },
});

let timedOut = false;
const heartbeat = setInterval(() => {
  console.log(`[build] still running elapsed=${elapsedSeconds()}s childPid=${child.pid ?? 'unknown'}`);
}, heartbeatMs);
heartbeat.unref();

const timeout = setTimeout(() => {
  timedOut = true;
  console.error(`[build] timeout elapsed=${elapsedSeconds()}s childPid=${child.pid ?? 'unknown'}`);
  terminateTree(child.pid);
}, timeoutMs);
timeout.unref();

child.on('exit', (code, signal) => {
  clearInterval(heartbeat);
  clearTimeout(timeout);
  const duration = elapsedSeconds();
  if (timedOut) {
    console.error(`[build] failed timeout duration=${duration}s`);
    process.exit(124);
  }
  if (signal) {
    console.error(`[build] failed signal=${signal} duration=${duration}s`);
    process.exit(1);
  }
  console.log(`[build] finished code=${code ?? 0} duration=${duration}s`);
  process.exit(code ?? 0);
});

child.on('error', (error) => {
  clearInterval(heartbeat);
  clearTimeout(timeout);
  console.error(`[build] failed to start: ${error.message}`);
  process.exit(1);
});
