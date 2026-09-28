import path from 'node:path';

// Resolved relative to the process working directory (always `server/`, since both
// `npm run dev` - tsx from source - and `npm start` - node from dist/ - are invoked
// with server/ as cwd via `npm --prefix server`). Deliberately NOT derived from
// import.meta.url: the compiled output nests under dist/server/src/ (see tsconfig's
// rootDir), so a dev-relative path depth silently breaks in the production build.
export const dataDir = path.join(process.cwd(), 'data');

export function dataFilePath(filename: string): string {
  return path.join(dataDir, filename);
}
