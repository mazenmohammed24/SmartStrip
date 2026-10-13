/**
 * Offline check of the MTTL-W01 driver: protocol parsing plus a full command
 * round-trip against a stand-in strip. No hardware and no ports below 1024 needed.
 *
 *   npm run mttl-selftest --prefix server
 *
 * The fake strip implements exactly the behaviour documented in the real firmware:
 * it answers `up:getinfo:all` with four channel frames, tracks onoff commands, and
 * answers the voltage/RSSI queries.
 */
import net from 'node:net';
import assert from 'node:assert/strict';
import { initMainDb } from '../src/db/mainDb.js';
import { MttlDriver } from '../src/drivers/mttl/MttlDriver.js';
import { MTTL_DEVICE_PORT, normalizeFrame, parseBootInfo, parseGetInfo, parsePowerReport, parseQuery } from '../src/drivers/mttl/protocol.js';
import { assertIpv4, assertProvisionableCredentials } from '../src/drivers/mttl/provision.js';

const MAC = '91C0C4C4AABB';
const SECOND_MAC = '1122334455AA';
const MODEL = 'LGU+-TAP-HW002';
const FW = '0.1.54-1.0.66';

function testParser(): void {
  const boot = parseBootInfo(`up:bootinfo:${MODEL};001122334455;${MAC};${FW};connect`);
  assert.ok(boot, 'bootinfo should parse');
  assert.equal(boot.model, MODEL);
  assert.equal(boot.mac, MAC);
  assert.equal(boot.fw, FW);

  // NUL padding and a missing trailing newline are both normal on this wire format.
  assert.equal(normalizeFrame(`up:query:-52\r\n\0\0`), 'up:query:-52');
  assert.equal(parseQuery('up:query:-52'), -52);

  const frame = `up:getinfo:${[1, 2, 3, 4]
    .map((n) => `${n}:${n * 111};${n === 2 ? 'on' : 'off'};${n === 2 ? 1 : 0};0;0;${n === 2 ? 5300 : 0};0000000A;00000000;00000000;0;00;${30 + n}`)
    .join(':')}:5:0;off;0;0;0;5300;0000000A;00000000;00000000;0;00;31`;

  const readings = parseGetInfo(frame);
  assert.equal(readings.length, 4, 'the aggregate channel 5 must be dropped');
  assert.equal(readings[1].on, true);
  assert.equal(readings[1].watts, 5.3, 'mW must convert to W');
  assert.equal(readings[1].energyKwh, 0.01, 'hex Wh must convert to kWh');
  assert.equal(readings[3].temperatureC, 34);
  assert.equal(readings.map((r) => r.relayIndex).join(','), '0,1,2,3');

  // Values below 50000 are per-outlet current in mA, not line voltage.
  assert.equal(parsePowerReport('up:power_report:1:224500')?.volts, 224.5);
  assert.equal(parsePowerReport('up:power_report:2:820'), null);

  assert.throws(() => assertIpv4('999.1.1.1'));
  assert.throws(() => assertProvisionableCredentials('Home:WiFi', 'secret'));
  assert.doesNotThrow(() => assertProvisionableCredentials('Home WiFi', 'secret'));
}

/**
 * A stand-in for the strip: same protocol, same relay semantics, no hardware.
 * Like the real device it dials *into* the server rather than being dialled.
 */
interface FakeStrip {
  stop: () => Promise<void>;
  relaysOn: Set<number>;
  voltage: number;
  rssi: number;
  /** Simulates someone pressing the strip's physical button. */
  pressButton: (relayIndex: number, on: boolean) => void;
  /** Makes the next onoff for this outlet a no-op, as a refused or child-locked command would be. */
  ignoreNextCommand: (relayIndex: number) => void;
  /** Cuts the connection, to exercise the mid-command disconnect path. */
  dropConnection: () => void;
  /** Sets the overload flag the strip reports on this outlet. */
  setOverload: (relayIndex: number, on: boolean) => void;
}

async function startFakeStrip(port: number, mac = MAC): Promise<FakeStrip> {
  const relaysOn = new Set<number>([2]);
  const state = { voltage: 224500, rssi: -52 };
  const ignored = new Set<number>();
  const overloaded = new Set<number>();

  const socket = net.createConnection({ host: '127.0.0.1', port });
  socket.setEncoding('utf8');
  await new Promise<void>((resolve, reject) => {
    socket.once('connect', () => resolve());
    socket.once('error', reject);
  });
  socket.write(`up:bootinfo:${MODEL};001122334455;${mac};${FW};connect\r\n`);

  let buffer = '';
  socket.on('data', (chunk: string) => {
    buffer += chunk;
    const parts = buffer.split(/\r?\n/);
    buffer = parts.pop() ?? '';
    for (const raw of parts) {
      const line = normalizeFrame(raw);
      if (line === 'up:getinfo:all') {
        const frame = [1, 2, 3, 4]
          .map((n) => {
            const on = relaysOn.has(n);
            const fault = overloaded.has(n) ? 1 : 0;
            return `${n}:${n * 111};${on ? 'on' : 'off'};${on ? 1 : 0};${fault};0;${on ? 1000 * n : 0};0000000A;00000000;00000000;0;00;${30 + n}`;
          })
          .join(':');
        socket.write(`up:getinfo:${frame}\0\0\r\n`);
      } else if (line.startsWith('up:onoff:')) {
        const [, , channel, value] = line.split(':');
        const n = Number(channel);
        if (ignored.delete(n)) {
          // Still acknowledge, but do not change state: a refused command.
          socket.write(`${line}\r\n`);
          continue;
        }
        if (value === 'on') relaysOn.add(n);
        else relaysOn.delete(n);
        socket.write(`${line}\r\n`);
      } else if (line.startsWith('up:power_report:')) {
        socket.write(`up:power_report:1:${state.voltage}\r\n`);
      } else if (line.startsWith('up:query:')) {
        socket.write(`up:query:${state.rssi}\r\n`);
      }
    }
  });
  socket.on('error', () => undefined);

  return {
    relaysOn,
    get voltage() {
      return state.voltage;
    },
    get rssi() {
      return state.rssi;
    },
    pressButton(relayIndex: number, on: boolean) {
      const channel = relayIndex + 1;
      if (on) relaysOn.add(channel);
      else relaysOn.delete(channel);
      socket.write(`up:event:onoff:${channel}:${on ? 'on' : 'off'}\r\n`);
    },
    ignoreNextCommand(relayIndex: number) {
      ignored.add(relayIndex + 1);
    },
    dropConnection() {
      socket.destroy();
    },
    setOverload(relayIndex: number, on: boolean) {
      if (on) overloaded.add(relayIndex + 1);
      else overloaded.delete(relayIndex + 1);
    },
    async stop() {
      await new Promise<void>((resolve) => {
        socket.end(() => resolve());
        socket.destroy();
        resolve();
      });
    },
  };
}

async function testDriver(): Promise<void> {
  // Port 0 asks the OS for a free port. The real driver binds the fixed 10086, so
  // reusing it here would fail with an opaque EADDRINUSE whenever the server runs.
  const driver = new MttlDriver({ port: 0, pollIntervalMs: 200 });

  const connectivity: string[] = [];
  driver.onEvent((event) => {
    if (event.type === 'connectivity') connectivity.push(event.connectivity);
  });
  await driver.init();
  const devicePort = driver.listeningPort();
  const strip = await startFakeStrip(devicePort);
  try {
    const externals: string[] = [];
    driver.onEvent((event) => {
      if (event.type === 'connectivity') connectivity.push(event.connectivity);
      if (event.type === 'relay-changed' && event.cause === 'external') externals.push(`${event.relayIndex}:${event.state}`);
    });

    await waitFor(() => driver.listKnownDevices().length === 1, 5000, 'strip to dial in');

    const discovered = await driver.scanForStrips();
    assert.equal(discovered.length, 1, 'a strip that has dialled in must be discoverable');
    assert.equal(discovered[0].driverStripId, MAC);
    assert.equal(discovered[0].relayCount, 4);

    await driver.adoptStrip(MAC);
    // getStatus is deliberately non-blocking now, so wait for the poll to land the
    // strip's real state rather than assuming it is already there.
    await waitFor(
      () => driver.listKnownDevices()[0]?.outlets[1].on === true,
      5000,
      'initial state to be read',
    );
    const status = await driver.getStatus(MAC);
    assert.equal(status.connectivity, 'online');
    assert.equal(status.relays.length, 4);
    assert.equal(status.relays[1].state, 'on', 'initial state must come from the strip, not from us');
    assert.equal(status.relays[1].watts, 2, '1000 * channel 2 mW -> 2 W');
    assert.equal(status.totalWatts, 2);

    // Energy is on every snapshot; voltage and RSSI arrive with the periodic
    // diagnostics round-trip, so wait for that rather than expecting them immediately.
    assert.equal(driver.getTelemetry(MAC)?.outlets[1].energyKwh, 0.01);
    await waitFor(() => driver.getTelemetry(MAC)?.voltageV === 224.5, 4000, 'voltage to be reported');
    assert.equal(driver.getTelemetry(MAC)?.rssi, -52);
    assert.equal(driver.getTelemetry(MAC)?.currentA, 0.01, 'current is derived from watts and line voltage');

    // An adopted strip disappears from discovery.
    assert.equal((await driver.scanForStrips()).length, 0);

    const off = await driver.setRelay(MAC, 1, 'off');
    assert.equal(off.success, true);
    assert.equal(off.newState, 'off');
    assert.equal(strip.relaysOn.has(2), false, 'the fake strip must have received the command');

    const allOn = await driver.setAllRelays(MAC, 'on');
    assert.ok(allOn.every((r) => r.success && r.newState === 'on'));
    assert.deepEqual([...strip.relaysOn].sort(), [1, 2, 3, 4]);

    const allOff = await driver.setAllRelays(MAC, 'off');
    assert.ok(allOff.every((r) => r.success && r.newState === 'off'));
    assert.equal(strip.relaysOn.size, 0);

    // An out-of-range relay must be rejected by the protocol layer, not sent.
    const bad = await driver.setRelay(MAC, 4, 'on');
    assert.equal(bad.success, false);

    // A physical button press is reported as external, not as one of our commands.
    strip.pressButton(0, true);
    await waitFor(() => externals.includes('0:on'), 3000, 'the button press to be reported as external');
    assert.equal(strip.relaysOn.has(1), true, 'the button press must reach the strip state');

    // A command the strip accepts but does not apply must be reported as a failure,
    // never optimistically as the state that was requested.
    await driver.setAllRelays(MAC, 'off');
    strip.ignoreNextCommand(0);
    const refused = await driver.setRelay(MAC, 0, 'on');
    assert.equal(refused.success, false, 'a refused command must not be reported as successful');
    assert.equal(refused.newState, 'off', 'the reported state must be the one the strip actually has');
    assert.equal(strip.relaysOn.has(1), false);

    // Overload is a level, so it must be reported on the rising edge only, otherwise
    // every poll would fire another critical notification.
    const overloads: number[] = [];
    driver.onEvent((event) => {
      if (event.type === 'overload') overloads.push(event.relayIndex);
    });
    strip.setOverload(1, true);
    await waitFor(() => overloads.filter((i) => i === 1).length >= 1, 3000, 'the overload to be reported');
    // Let several more polls run with the fault still set.
    await sleep(1000);
    assert.equal(overloads.filter((i) => i === 1).length, 1, 'a persistent overload must only be reported once');

    // Clearing and re-applying the fault is a new edge, and must be reported again.
    strip.setOverload(1, false);
    await sleep(500);
    strip.setOverload(1, true);
    await waitFor(() => overloads.filter((i) => i === 1).length >= 2, 3000, 'the overload to be reported again after clearing');
    strip.setOverload(1, false);

    // Dropping the connection mid-command must not confirm the command. This goes
    // last because it ends the session.
    strip.ignoreNextCommand(2);
    const dropping = driver.setRelay(MAC, 2, 'on');
    strip.dropConnection();
    const dropped = await dropping;
    assert.equal(dropped.success, false, 'a command interrupted by a disconnect must not be reported as successful');
    await waitFor(
      () => driver.getStatus(MAC).then((s) => s.connectivity === 'offline'),
      3000,
      'the strip to be reported offline after disconnecting',
    );
  } finally {
    await driver.shutdown();
    await strip.stop();
  }

  assert.ok(connectivity.includes('online'), `expected an online connectivity event, got ${JSON.stringify(connectivity)}`);
}

/**
 * The adoption path a server restart depends on: a strip already in the fleet dials
 * in and must be adopted and polled without any call from the caller.
 */
async function testAdoptionSurvivesDialIn(): Promise<void> {
  const driver = new MttlDriver({ port: 0, pollIntervalMs: 200 });
  await driver.init();
  const strip = await startFakeStrip(driver.listeningPort(), SECOND_MAC);
  try {
    // Adopt before the strip has ever connected, exactly as a fleet resync does.
    await driver.adoptStrip(SECOND_MAC);
    assert.equal((await driver.scanForStrips()).length, 0, 'an adopted id must not be offered for adoption');

    await waitFor(() => driver.listKnownDevices().length === 1, 5000, 'the strip to dial in');
    // It must not reappear as a new device, or the UI would offer to adopt a strip
    // that is already in the fleet and the user would get "already in fleet".
    assert.equal((await driver.scanForStrips()).length, 0, 'a dialled-in adopted strip must not be re-discoverable');

    const status = await driver.getStatus(SECOND_MAC);
    assert.equal(status.connectivity, 'online', 'an adopted strip must come back online on its own');
  } finally {
    await driver.shutdown();
    await strip.stop();
  }
}

/** A strip the server has never seen must be reported offline, not throw. */
async function testUnknownStripStatus(): Promise<void> {
  const driver = new MttlDriver({ port: 0, pollIntervalMs: 200 });
  await driver.init();
  try {
    const status = await driver.getStatus('FEEDFACEBEEF');
    assert.equal(status.connectivity, 'offline');
    assert.equal(status.relays.length, 4);
    assert.equal(status.totalWatts, 0);
  } finally {
    await driver.shutdown();
  }
}

/** Accepts an async predicate so callers can await the driver's own getters. */
async function waitFor(predicate: () => boolean | Promise<boolean>, timeoutMs: number, what: string): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await predicate()) return;
    await sleep(50);
  }
  throw new Error(`Timed out after ${timeoutMs}ms waiting for ${what}`);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main(): Promise<void> {
  testParser();
  console.log('  parser OK');
  // The driver reads the fleet database on init to learn which strips are already
  // adopted, so stand the database up before constructing one.
  await initMainDb();
  await testDriver();
  console.log('  driver OK');
  await testAdoptionSurvivesDialIn();
  console.log('  adoption on dial-in OK');
  await testUnknownStripStatus();
  console.log('  unknown strip status OK');
  console.log(`MTTL selftest passed. Production device port is ${MTTL_DEVICE_PORT}; this run used an ephemeral port.`);
}

main().catch((err) => {
  console.error(`MTTL selftest FAILED: ${err instanceof Error ? err.stack : err}`);
  process.exit(1);
});
