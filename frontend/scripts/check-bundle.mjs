import { readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

// Conservative guardrails based on the measured code-split build.
// Protect against accidentally importing all deferred routes at startup.
const maxEntryBytes = 950_000;
const maxEntryGzipBytes = 255_000;
const html = readFileSync(join('dist', 'index.html'), 'utf8');
const sources = [...html.matchAll(/<script\\b[^>]*\\bsrc=["']([^"']+\\.js)["']/g)]
  .map(match => match[1]);
const entry = sources.find(src => /(?:^|\\/)assets\\/index-[^/]+\\.js$/.test(src));
if (!entry) {
  throw new Error('Cannot identify Vite entry JavaScript from dist/index.html');
}
const path = join('dist', entry.replace(/^\\/+/, ''));
const minifiedBytes = statSync(path).size;
const gzipBytes = gzipSync(readFileSync(path)).byteLength;
console.log(
  `ChairX initial JS budget: ${(minifiedBytes / 1000).toFixed(1)} KB / ${(gzipBytes / 1000).toFixed(1)} KB gzip`
);
if (minifiedBytes > maxEntryBytes || gzipBytes > maxEntryGzipBytes) {
  console.error(
    `Initial JavaScript exceeds budget: max ${maxEntryBytes / 1000} KB, ${maxEntryGzipBytes / 1000} KB gzip. ` +
    'Defer optional route modules instead of bundling them into startup.'
  );
  process.exitCode = 1;
}
