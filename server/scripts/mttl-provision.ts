/**
 * One-time provisioning of an MTTL-W01 strip from the command line.
 *
 *   npm run mttl-provision --prefix server -- --ip 192.168.1.10 --ssid "MyWifi" --password "secret"
 *
 * The machine running this must be joined to the strip's "TONLY_TAP_XXXXXXX"
 * setup network (password LGU_XXXXXXX). `--ip` is the address the *server* will
 * have afterwards - the strip dials the server, not the other way round.
 */
import { provisionStrip, SetupServiceUnreachableError } from '../src/drivers/mttl/provision.js';
import { MTTL_DEVICE_PORT, MTTL_SETUP_HOST } from '../src/drivers/mttl/protocol.js';
import { localIPv4s } from '../src/utils/localIp.js';

interface Args {
  ip?: string;
  ssid?: string;
  password?: string;
  host: string;
  wait: number;
}

function parseArgs(argv: string[]): Args {
  const args: Args = { host: MTTL_SETUP_HOST, wait: 20 };
  for (let i = 0; i < argv.length; i++) {
    const flag = argv[i];
    const value = argv[++i];
    if (flag === '--ip') args.ip = value;
    else if (flag === '--ssid') args.ssid = value;
    else if (flag === '--password') args.password = value;
    else if (flag === '--host') args.host = value ?? MTTL_SETUP_HOST;
    else if (flag === '--wait') args.wait = Number(value ?? 20);
    else {
      console.error(`Unknown argument: ${flag}`);
      usage();
      process.exit(2);
    }
  }
  return args;
}

function usage(): void {
  console.log(`
Usage:
  npm run mttl-provision --prefix server -- --ip <SERVER_IPV4> --ssid <WIFI> --password <WIFI_PASSWORD> [options]

Options:
  --ip        IPv4 the strip should dial (this server's address, public or LAN)
  --ssid      home Wi-Fi name the strip should join
  --password  home Wi-Fi password (8-63 characters, or 64 hex digits)
  --host      strip setup service address (default ${MTTL_SETUP_HOST})
  --wait      seconds to wait for the setup service (default 20)

The strip dials the server on TCP ${MTTL_DEVICE_PORT} once provisioned. It stores exactly
one server address, so re-run this to move it.`);
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));
  if (!args.ip || !args.ssid || !args.password) {
    const ips = localIPv4s();
    if (!args.ip) console.log(`No --ip given. IPv4 addresses on this machine right now: ${ips.join(', ') || 'none'}`);
    usage();
    process.exit(2);
  }

  console.log(`Looking for the strip's setup service at ${args.host} (up to ${args.wait}s)...`);
  console.log('  1. hold the strip\'s main button ~10s until the LED blinks fast');
  console.log('  2. join the Wi-Fi network "TONLY_TAP_XXXXXXX" from this machine (password: LGU_XXXXXXX)');
  console.log('  3. leave this command running\n');

  try {
    const result = await provisionStrip({
      serverIp: args.ip,
      ssid: args.ssid,
      password: args.password,
      host: args.host,
      waitMs: args.wait * 1000,
    });
    console.log(`  ${result.ipReply}`);
    console.log(`  ${result.connectReply}`);
    console.log(`\nDone. Reconnect this machine to your normal network and start the server.`);
    console.log(`The strip will dial ${args.ip}:${MTTL_DEVICE_PORT} within a minute and appear in the web UI.`);
  } catch (err) {
    if (err instanceof SetupServiceUnreachableError) {
      console.error(`\n${err.message}`);
      process.exit(1);
    }
    console.error(`\nProvisioning failed: ${err instanceof Error ? err.message : err}`);
    process.exit(1);
  }
}

void main();
