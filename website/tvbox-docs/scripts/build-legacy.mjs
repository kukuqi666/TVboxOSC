import { readFileSync, writeFileSync, mkdirSync, cpSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = resolve(root, '../..');
execFileSync('python3', [resolve(repoRoot, 'scripts/audit_configs.py')], { cwd: repoRoot, stdio: 'inherit' });
const configOutput = resolve(root, 'docs/public/configs');
rmSync(configOutput, { recursive: true, force: true });
cpSync(resolve(repoRoot, 'website/tvbox-config'), configOutput, { recursive: true });
// 图片只在 website/images 维护，构建时同步到站点公开目录。
const imageOutput = resolve(root, 'docs/public/images');
rmSync(imageOutput, { recursive: true, force: true });
cpSync(resolve(repoRoot, 'website/images'), imageOutput, { recursive: true });
const srcDir = resolve(root, 'legacy-src');
const outFile = resolve(root, 'docs/public/legacy/index.html');

const template = readFileSync(resolve(srcDir, 'index.template.html'), 'utf8');
const configs = JSON.parse(readFileSync(resolve(srcDir, 'configs.json'), 'utf8'));
const links = JSON.parse(readFileSync(resolve(srcDir, 'links.json'), 'utf8'));
const slogans = JSON.parse(readFileSync(resolve(srcDir, 'slogans.json'), 'utf8'));

const escapeHtml = (value) => String(value)
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;');

const configHtml = configs.map((item, index) => {
  const id = escapeHtml(item.id || `config-${index + 1}`);
  const name = escapeHtml(item.name);
  const url = escapeHtml(item.url);
  const color = escapeHtml(item.color || '#28a9e0');
  return `          <div class="item"><div class="bg" style="background-color:${color}"></div><div class="inner copy-btn" id="${id}" data-clipboard-text="${url}"><span>${name}</span></div></div>`;
}).join('\n');

const linkHtml = links.map((item) => {
  const name = escapeHtml(item.name);
  const desc = escapeHtml(item.desc);
  const url = escapeHtml(item.url);
  const icon = escapeHtml(item.icon);
  return `          <a href="${url}" target="_blank"><div class="item"><div class="avatar"><img src="${icon}"></div><div class="inner"><h5>${name}</h5><p>${desc}</p></div></div></a>`;
}).join('\n');

const replaceBetween = (input, start, end, content) => {
  const begin = input.indexOf(start);
  const finish = input.indexOf(end, begin);
  if (begin === -1 || finish === -1) {
    throw new Error(`Missing marker: ${start} / ${end}`);
  }
  return input.slice(0, begin + start.length) + `\n${content}\n` + input.slice(finish);
};

let output = template;
output = replaceBetween(output, '<!-- LEGACY_CONFIGS_START -->', '<!-- LEGACY_CONFIGS_END -->', configHtml);
output = replaceBetween(output, '<!-- LEGACY_LINKS_START -->', '<!-- LEGACY_LINKS_END -->', linkHtml);

const sloganLiteral = JSON.stringify(slogans)
  .replace(/</g, '\\u003c')
  .replace(/>/g, '\\u003e')
  .replace(/\\n/g, '\\n');
const sloganStart = 'function o(){var e=[';
const sloganEnd = '],t=i(0,e.length-1)';
const sloganStartIndex = output.indexOf(sloganStart);
const sloganEndIndex = sloganStartIndex === -1
  ? -1
  : output.indexOf(sloganEnd, sloganStartIndex + sloganStart.length);
if (sloganStartIndex === -1 || sloganEndIndex === -1) {
  throw new Error('Unable to locate slogan array in legacy script');
}
output = `${output.slice(0, sloganStartIndex + sloganStart.length)}${sloganLiteral.slice(1, -1)}${output.slice(sloganEndIndex)}`;

mkdirSync(dirname(outFile), { recursive: true });
writeFileSync(outFile, output);
console.log(`Generated ${outFile} from ${configs.length} configs, ${links.length} links and ${slogans.length} slogans.`);
