// Minimal .xlsx reader for an UNZIPPED workbook folder (unzip the .xlsx first; Node has no zip reader built in).
// Only what CoFID needs: shared strings, numbers, and sheets by name. Dev-machine script, never shipped.
import fs from 'node:fs';
import path from 'node:path';

const unescapeXml = (s) => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');

function sharedStrings(dir) {
  const xml = fs.readFileSync(path.join(dir, 'xl/sharedStrings.xml'), 'utf8');
  return [...xml.matchAll(/<si>([\s\S]*?)<\/si>/g)].map((m) =>
    unescapeXml([...m[1].matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)].map((t) => t[1]).join('')));
}

const colIndex = (ref) => [...ref.replace(/\d+/g, '')].reduce((n, c) => n * 26 + c.charCodeAt(0) - 64, 0) - 1;

/** Returns { sheetName: string[][] } (rows of cell text; empty string for blanks). */
export function readWorkbook(dir) {
  const strings = sharedStrings(dir);
  const wb = fs.readFileSync(path.join(dir, 'xl/workbook.xml'), 'utf8');
  const rels = fs.readFileSync(path.join(dir, 'xl/_rels/workbook.xml.rels'), 'utf8');
  const target = new Map([...rels.matchAll(/<Relationship [^>]*>/g)].map((m) => {
    const id = /Id="([^"]+)"/.exec(m[0])?.[1];
    const t = /Target="([^"]+)"/.exec(m[0])?.[1];
    return [id, t];
  }));
  const out = {};
  for (const m of wb.matchAll(/<sheet [^>]*>/g)) {
    const name = unescapeXml(/name="([^"]+)"/.exec(m[0])[1]);
    const file = target.get(/r:id="([^"]+)"/.exec(m[0])[1]);
    const xml = fs.readFileSync(path.join(dir, 'xl', file), 'utf8');
    const rows = [];
    for (const r of xml.matchAll(/<row [^>]*>([\s\S]*?)<\/row>/g)) {
      const row = [];
      for (const c of r[1].matchAll(/<c ([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
        const ref = /r="([A-Z]+\d+)"/.exec(c[1])[1];
        const type = /t="([^"]+)"/.exec(c[1])?.[1];
        const v = /<v>([\s\S]*?)<\/v>/.exec(c[2] ?? '')?.[1];
        const inline = /<is>[\s\S]*?<t[^>]*>([\s\S]*?)<\/t>/.exec(c[2] ?? '')?.[1];
        row[colIndex(ref)] = type === 's' && v !== undefined ? strings[Number(v)] : type === 'inlineStr' ? unescapeXml(inline ?? '') : v === undefined ? '' : unescapeXml(v);
      }
      for (let i = 0; i < row.length; i++) row[i] ??= '';
      rows.push(row);
    }
    out[name] = rows;
  }
  return out;
}
