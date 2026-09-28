// Generates simple placeholder PWA icons (a flat-color square with a plug glyph) with zero
// dependencies, using zlib directly to build valid PNGs. Replace public/icons/*.png with real
// artwork whenever you like - this only exists so the app is installable out of the box.
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const outDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public', 'icons');
mkdirSync(outDir, { recursive: true });

const BG = [37, 99, 235, 255]; // #2563eb
const FG = [255, 255, 255, 255];

function crc32(buf) {
  let c;
  const table = crc32.table ?? (crc32.table = (() => {
    const t = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      t[n] = c >>> 0;
    }
    return t;
  })());
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])));
  return Buffer.concat([len, typeBuf, data, crc]);
}

function pixelColor(x, y, size, maskable) {
  const cx = size / 2;
  const cy = size / 2;
  const safeR = maskable ? size * 0.32 : size * 0.42;
  const dx = x - cx;
  const dy = y - cy;

  // Outer rounded plug body (a rounded rect approximated with a superellipse-ish check)
  const bodyW = safeR * 1.15;
  const bodyH = safeR * 1.5;
  const inBody = Math.abs(dx) < bodyW * 0.55 && Math.abs(dy) < bodyH * 0.55;

  // Two prong slots
  const prongY = cy - bodyH * 0.12;
  const prongHalfW = bodyW * 0.09;
  const prongH = bodyH * 0.28;
  const prongGap = bodyW * 0.26;
  const inLeftProng = Math.abs(dx + prongGap) < prongHalfW && y < prongY + prongH && y > prongY - prongH * 0.2;
  const inRightProng = Math.abs(dx - prongGap) < prongHalfW && y < prongY + prongH && y > prongY - prongH * 0.2;

  if (inBody && (inLeftProng || inRightProng)) return BG;
  if (inBody) return FG;
  return BG;
}

function buildPng(size, maskable) {
  const raw = Buffer.alloc((size * 4 + 1) * size);
  for (let y = 0; y < size; y++) {
    const rowStart = y * (size * 4 + 1);
    raw[rowStart] = 0; // filter: none
    for (let x = 0; x < size; x++) {
      const [r, g, b, a] = pixelColor(x, y, size, maskable);
      const o = rowStart + 1 + x * 4;
      raw[o] = r;
      raw[o + 1] = g;
      raw[o + 2] = b;
      raw[o + 3] = a;
    }
  }
  const idat = deflateSync(raw);

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  return Buffer.concat([signature, chunk('IHDR', ihdr), chunk('IDAT', idat), chunk('IEND', Buffer.alloc(0))]);
}

writeFileSync(path.join(outDir, 'icon-192.png'), buildPng(192, false));
writeFileSync(path.join(outDir, 'icon-512.png'), buildPng(512, false));
writeFileSync(path.join(outDir, 'icon-maskable-512.png'), buildPng(512, true));

console.log('Wrote icon-192.png, icon-512.png, icon-maskable-512.png to public/icons/');
