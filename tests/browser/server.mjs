import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { delimiter, join, resolve } from 'node:path';

const directory = await mkdtemp(join(tmpdir(), 'remind-browser-'));
const classpathFile = join(directory, 'classpath.txt');
let child;
let stopping = false;

async function stop() {
  if (stopping) return;
  stopping = true;
  if (child && child.exitCode === null && child.signalCode === null) {
    const closed = once(child, 'close');
    child.kill('SIGTERM');
    const timeout = setTimeout(() => child.kill('SIGKILL'), 5000);
    await closed;
    clearTimeout(timeout);
  }
  await rm(directory, { recursive: true, force: true });
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, async () => {
    await stop();
    process.exit(0);
  });
}

try {
  // Compile without replacing the JAR that a separately running demo may use.
  child = spawn(process.platform === 'win32' ? 'mvnw.cmd' : './mvnw', [
    '-q', 'compile', 'dependency:build-classpath', '-DincludeScope=runtime',
    '-Dmdep.outputFile=' + classpathFile
  ], { stdio: 'inherit', shell: process.platform === 'win32' });
  const [buildExit] = await once(child, 'close');
  if (buildExit !== 0) throw new Error('Maven compilation failed. Use a Java 17+ JDK.');

  const dependencies = (await readFile(classpathFile, 'utf8')).trim();
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
  child = spawn(java, [
    '-cp', resolve('target/classes') + delimiter + dependencies,
    'io.workshop.reminders.ReminderApplication',
    '--server.port=18081', '--server.address=127.0.0.1',
    '--reminders.seed-data=false', '--reminders.data-file=' + join(directory, 'reminders.json')
  ], { stdio: 'inherit' });
  const [appExit] = await once(child, 'close');
  if (!stopping) process.exitCode = appExit || 1;
} finally {
  await stop();
}
