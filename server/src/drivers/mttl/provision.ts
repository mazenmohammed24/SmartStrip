import net from 'node:net';
import { MTTL_SETUP_HOST, MTTL_SETUP_PORT } from './protocol.js';

/**
 * Provisioning client for a strip in SoftAP setup mode.
 *
 * Provisioning is a one-time step that writes two things into the strip's flash:
 * which Wi-Fi to join, and which server IP to dial. It is spoken to the strip's own
 * setup access point on 192.168.1.1:30300 - one command per TCP connection, and
 * each command must be answered with an exact confirmation token. The strip stores
 * exactly one server address, so moving it to a different host means repeating this.
 */
export interface ProvisionInput {
  /** IPv4 the strip should dial. This is the server's address, not the provisioning machine's. */
  serverIp: string;
  ssid: string;
  password: string;
  /** Setup AP address. Only change it for testing against a fake strip. */
  host?: string;
  timeoutMs?: number;
  /** How long to wait for the setup service to appear. */
  waitMs?: number;
}

export interface ProvisionResult {
  ipReply: string;
  connectReply: string;
}

/**
 * The command grammar is colon-separated with no escaping, so a credential
 * containing a colon would silently split into the wrong fields. Refuse instead.
 *
 * The passphrase is additionally held to the WPA2 rules the strip's supplicant enforces: a
 * passphrase it cannot accept is accepted here, written to flash, and then shows up as a strip
 * that never joins the network and never registers - with nothing in the reply to say why.
 */
export function assertProvisionableCredentials(ssid: string, password: string): void {
  for (const [label, value] of [['SSID', ssid], ['password', password]] as const) {
    if (!value) throw new Error(`${label} must not be empty`);
    if (/[:\r\n]/.test(value)) {
      throw new Error(`${label} cannot contain ":" or a newline - the strip's provisioning protocol has no escaping`);
    }
  }
  assertWpa2Passphrase(password);
}

/** A WPA2 passphrase is 8-63 characters, or a 64-digit hex PSK. */
const WPA2_HEX_PSK = /^[0-9A-Fa-f]{64}$/;

export function assertWpa2Passphrase(password: string): void {
  if (WPA2_HEX_PSK.test(password)) return;
  if (password.length < 8 || password.length > 63) {
    throw new Error(`Wi-Fi password must be 8-63 characters (or 64 hex digits), but this one is ${password.length}`);
  }
  if (/[^\x20-\x7e]/.test(password)) {
    throw new Error('Wi-Fi password can only contain printable ASCII characters');
  }
}

/** Strict IPv4 validation: the value is concatenated straight into a strip command. */
export function assertIpv4(value: string): string {
  const parts = value.split('.');
  if (parts.length !== 4) throw new Error(`Server IP must be an IPv4 address, got "${value}"`);
  for (const part of parts) {
    if (!/^\d{1,3}$/.test(part)) throw new Error(`Server IP must be an IPv4 address, got "${value}"`);
    if (Number(part) > 255) throw new Error(`Server IP must be an IPv4 address, got "${value}"`);
  }
  return parts.join('.');
}

export async function provisionStrip(input: ProvisionInput): Promise<ProvisionResult> {
  const host = input.host ?? MTTL_SETUP_HOST;
  const timeoutMs = input.timeoutMs ?? 6000;
  assertIpv4(input.serverIp);
  assertProvisionableCredentials(input.ssid, input.password);

  await waitForSetupService(host, input.waitMs ?? 20000, timeoutMs);

  // `label` is deliberately a redacted description of the command rather than the
  // command itself: the connect command embeds the plaintext Wi-Fi password, and
  // these errors are returned to the browser and printed to the console.
  const ipReply = await setupCommand(host, `up:ip:${input.serverIp}`, 'up:ip', timeoutMs);
  expectReply(ipReply, 'up:ip:ip_ok', 'up:ip');

  const connectReply = await setupCommand(host, `up:connect:${input.ssid}:${input.password}`, 'up:connect', timeoutMs);
  expectReply(connectReply, 'up:connect:connect_ok', 'up:connect', true);

  return { ipReply, connectReply };
}

async function waitForSetupService(host: string, waitMs: number, timeoutMs: number): Promise<void> {
  const deadline = Date.now() + waitMs;
  let lastError: unknown;
  while (Date.now() < deadline) {
    try {
      await tryConnect(host, timeoutMs);
      return;
    } catch (err) {
      lastError = err;
      await delay(1500);
    }
  }
  throw new SetupServiceUnreachableError(host, lastError);
}

/** Opens and immediately closes a connection, purely to prove the setup service answers. */
function tryConnect(host: string, timeoutMs: number): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    const socket = net.createConnection({ host, port: MTTL_SETUP_PORT });
    const done = (err?: Error) => {
      socket.destroy();
      if (err) reject(err);
      else resolve();
    };
    socket.setTimeout(timeoutMs);
    socket.once('connect', () => done());
    socket.once('error', (err) => done(err));
    socket.once('timeout', () => done(new Error(`Timed out connecting to ${host}:${MTTL_SETUP_PORT}`)));
  });
}

export class SetupServiceUnreachableError extends Error {
  constructor(host: string, cause?: unknown) {
    super(
      `The strip's setup service at ${host}:${MTTL_SETUP_PORT} is not reachable. ` +
        'Hold the strip\'s main button for about 10 seconds until the LED blinks fast, then join the ' +
        'Wi-Fi network "TONLY_TAP_XXXXXXX" from the machine running this command ' +
        '(password: LGU_XXXXXXX - the same 7 characters as in the network name), then try again.',
    );
    this.name = 'SetupServiceUnreachableError';
    this.cause = cause;
  }
}

async function setupCommand(host: string, command: string, label: string, timeoutMs: number): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const socket = connect(host, timeoutMs);
    let buffer = '';
    let settled = false;

    const finish = (err: Error | null, value = '') => {
      if (settled) return;
      settled = true;
      socket.destroy();
      if (err) reject(err);
      else resolve(value);
    };

    socket.setEncoding('utf8');
    // Keep reading past the first line: the setup service may emit a banner or an
    // echo before the confirmation, and stopping early would report a successful
    // provisioning as a mismatch.
    socket.on('data', (chunk: string) => {
      buffer += chunk;
      if (/\r?\n/.test(buffer)) finish(null, buffer.trim());
    });
    socket.on('error', (err) => finish(err));
    socket.on('close', () => finish(null, buffer.trim()));
    socket.on('timeout', () => finish(new Error(`The strip did not answer ${label} within ${timeoutMs}ms`)));
    socket.write(`${command}\r\n`);
  });
}

/**
 * Confirms the strip accepted the command. `sensitive` suppresses the raw reply in
 * the error, because a setup service that echoes the request back would otherwise
 * put the Wi-Fi password into the error text.
 */
function expectReply(reply: string, expected: string, label: string, sensitive = false): void {
  if (!reply.includes(expected)) {
    const shown = sensitive ? '(redacted)' : reply || '(nothing)';
    throw new Error(`The strip answered "${shown}" to ${label}, expected "${expected}"`);
  }
}

function connect(host: string, timeoutMs: number): net.Socket {
  const socket = net.createConnection({ host, port: MTTL_SETUP_PORT });
  socket.setTimeout(timeoutMs);
  return socket;
}


function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
