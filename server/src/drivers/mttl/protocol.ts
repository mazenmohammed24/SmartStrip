/**
 * Wire protocol for the LG U+ / Jinheung MTTL-W01 4-outlet smart strip.
 *
 * The strip speaks plain CRLF-terminated ASCII lines ("up:...") over a TCP
 * session that it opens *to* us. We never connect to it, so no discovery,
 * no cloud account and no inbound port on the home network are involved.
 *
 * Server -> strip:
 *   up:getinfo:all                 ask for a full state snapshot (all channels)
 *   up:onoff:<1-4>:on|off          switch one relay
 *   up:power_report:1:vol          ask for line voltage
 *   up:query:wifirssi              ask for Wi-Fi signal strength
 *
 * Strip -> server:
 *   up:bootinfo:<model>;<mac>;<mac>;<fw>;connect
 *   up:getinfo:<ch>:<runtime>;<relay on/off>;<state>;<overload>;<overheat>;<power mW>;<energy hex Wh>;<prev hex>;<config hex>;<status>;<event hex>;<temp C>   (x4, channels 1-4, plus an aggregate channel 5)
 *   up:power_report:<ch>:<mV>   (>=50000 -> volts)  or  <mA> (below 50000, per-outlet current)
 *   up:query:<rssi dBm>
 *   up:event:onoff:<1-4>:on|off     a physical button press
 *   up:onoff:<1-4>:on|off            acknowledgement of our own command
 *
 * Setup mode (strip's own SoftAP, 192.168.1.1:30300), one command per connection:
 *   up:ip:<server ipv4>            -> up:ip:ip_ok
 *   up:connect:<ssid>:<password>   -> up:connect:connect_ok
 *
 * Two quirks worth remembering, both learned the hard way from the hardware:
 *  - There is no `up:onoff:0`. "All outlets" must be sent as a loop over 1..4.
 *  - Frames are NUL-padded and the port is fixed at 10086 by the firmware.
 */

export const MTTL_DEVICE_PORT = 10086;
export const MTTL_SETUP_HOST = '192.168.1.1';
export const MTTL_SETUP_PORT = 30300;
export const MTTL_RELAY_COUNT = 4;

/** Channel numbers are 1-based on the wire and 0-based in the app. */
export function channelToRelayIndex(channel: number): number {
  return channel - 1;
}

export function relayIndexToChannel(relayIndex: number): number {
  return relayIndex + 1;
}

export interface MttlBootInfo {
  model: string;
  mac: string;
  fw: string;
}

export interface MttlOutletReading {
  relayIndex: number;
  channel: number;
  on: boolean;
  /** Instantaneous power, watts (wire value is mW). */
  watts: number;
  /** Lifetime energy for this outlet, kWh (wire value is hex Wh). */
  energyKwh: number;
  temperatureC: number;
  overload: boolean;
  overheat: boolean;
}

export interface MttlEvent {
  relayIndex: number;
  on: boolean;
}

const BOOTINFO_RE = /^up:bootinfo:([^;\r\n]+);([0-9A-Fa-f]{12});([0-9A-Fa-f]{12});([^;\r\n]+);connect$/;

const GETINFO_RE = new RegExp(
  [
    '(?<channel>[1-5]):(?<runtime>-?\\d+)',
    ';(?<relay>on|off)',
    ';(?<state>-?\\d+)',
    ';(?<overload>[^;:]+)',
    ';(?<overheat>[^;:]+)',
    ';(?<power>-?\\d+)',
    ';(?<energy>[0-9A-Fa-f]{8})',
    ';(?<previous>[0-9A-Fa-f]{8})',
    ';(?<config>[0-9A-Fa-f]{8})',
    ';(?<status>[^;:]+)',
    ';(?<event>[0-9A-Fa-f]{2})',
    ';(?<temperature>-?\\d+)',
  ].join(''),
  'gi',
);

const POWER_REPORT_RE = /^up:power_report:([1-5]):(-?\d+)$/i;
const QUERY_RE = /^up:query:(-?\d+)$/i;
const EVENT_RE = /^up:event:onoff:([0-4]):(on|off)$/i;

/** Strips a frame of the NUL padding and stray whitespace the firmware adds. */
export function normalizeFrame(raw: string): string {
  return raw.replace(/\0/g, '').trim();
}

export function parseBootInfo(line: string): MttlBootInfo | null {
  const m = BOOTINFO_RE.exec(line);
  if (!m) return null;
  // The frame carries two MACs; the second is the one the device identifies with.
  return { model: m[1], mac: m[3].toUpperCase(), fw: m[4] };
}

function truthyFlag(value: string): boolean {
  const n = Number(value);
  return Number.isFinite(n) ? n !== 0 : value.toLowerCase() === 'true' || value.toLowerCase() === 'on';
}

/**
 * Parses one `up:getinfo:` frame into per-outlet readings. Channel 5 is the
 * strip's aggregate and is intentionally dropped: the app derives the total
 * from the four real outlets, so keeping it would double-count.
 */
export function parseGetInfo(line: string): MttlOutletReading[] {
  const body = line.startsWith('up:getinfo:') ? line.slice('up:getinfo:'.length) : line;
  const readings: MttlOutletReading[] = [];
  for (const m of body.matchAll(GETINFO_RE)) {
    const g = m.groups!;
    const channel = Number(g.channel);
    if (channel < 1 || channel > MTTL_RELAY_COUNT) continue;
    readings.push({
      relayIndex: channelToRelayIndex(channel),
      channel,
      on: g.relay.toLowerCase() === 'on',
      watts: round2(Number(g.power) / 1000),
      energyKwh: round3(parseInt(g.energy, 16) / 1000),
      temperatureC: Number(g.temperature),
      overload: truthyFlag(g.overload),
      overheat: truthyFlag(g.overheat),
    });
  }
  return readings;
}

/** Only channel 1 answers with whole-strip voltage; per-outlet values are current in mA. */
export function parsePowerReport(line: string): { channel: number; volts: number } | null {
  const m = POWER_REPORT_RE.exec(line);
  if (!m) return null;
  const value = Number(m[2]);
  if (value < 50000) return null;
  return { channel: Number(m[1]), volts: round1(value / 1000) };
}

export function parseQuery(line: string): number | null {
  const m = QUERY_RE.exec(line);
  return m ? Number(m[1]) : null;
}

export function parseEvent(line: string): MttlEvent | null {
  const m = EVENT_RE.exec(line);
  if (!m) return null;
  const channel = Number(m[1]);
  if (channel < 1 || channel > MTTL_RELAY_COUNT) return null;
  return { relayIndex: channelToRelayIndex(channel), on: m[2].toLowerCase() === 'on' };
}

export function buildGetInfoCommand(): string {
  return 'up:getinfo:all';
}

export function buildOnOffCommand(relayIndex: number, on: boolean): string {
  const channel = relayIndexToChannel(relayIndex);
  if (!Number.isInteger(channel) || channel < 1 || channel > MTTL_RELAY_COUNT) {
    throw new RangeError(`Relay index out of range for an MTTL-W01: ${relayIndex}`);
  }
  return `up:onoff:${channel}:${on ? 'on' : 'off'}`;
}

export function buildVoltageCommand(): string {
  return 'up:power_report:1:vol';
}

export function buildRssiCommand(): string {
  return 'up:query:wifirssi';
}

function round1(n: number): number {
  return Math.round(n * 10) / 10;
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

function round3(n: number): number {
  return Math.round(n * 1000) / 1000;
}
