// npm のフォントパッケージから、使う woff2 と @font-face の CSS を
// src/main/resources/static/css/fonts/ にコピーする（Google Fonts の CDN を使わないため）。
import { readFileSync, writeFileSync, mkdirSync, copyFileSync, rmSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const outDir = new URL('../../src/main/resources/static/css/fonts/', import.meta.url).pathname;
rmSync(outDir, { recursive: true, force: true });
mkdirSync(outDir, { recursive: true });

// [パッケージ名, CSS ファイル, 残す unicode サブセット（null なら全部）]
const sources = [
  ['material-symbols', 'outlined.css', null],
  ['@fontsource-variable/inter', 'wght.css', ['latin', 'latin-ext']],
  ['@fontsource-variable/space-grotesk', 'wght.css', ['latin', 'latin-ext']],
  ['@fontsource-variable/noto-sans-jp', 'wght.css', null],
];

let css = '/* このファイルは frontend/scripts/copy-fonts.mjs で自動生成。直接編集しない。 */\n';
for (const [pkg, file, subsets] of sources) {
  const pkgDir = dirname(require.resolve(`${pkg}/package.json`));
  let text = readFileSync(join(pkgDir, file), 'utf8');
  if (subsets) {
    // fontsource の CSS は「/* subset名 */ @font-face {...}」の並び。要らないサブセットを落とす
    text = text
      .split(/(?=\/\* )/)
      .filter((block) => subsets.some((s) => block.startsWith(`/* ${pkg.split("/")[1]}-${s}-wght`)))
      .join('');
  }
  text = text.replace(/url\((["']?)\.\/(?:files\/)?([^)"']+)\1\)/g, (_, _q, name) => {
    copyFileSync(join(pkgDir, name.startsWith('material-') ? name : join('files', name)), join(outDir, name));
    return `url("./${name}")`;
  });
  // 'Inter Variable' → 'Inter' のように、元の HTML が使っていた名前に合わせる
  text = text.replace(/font-family: '([^']+) Variable'/g, "font-family: '$1'");
  css += `\n/* ---- ${pkg} ---- */\n${text}`;
}
writeFileSync(join(outDir, 'fonts.css'), css);
copyFileSync(join(dirname(require.resolve('material-symbols/package.json')), 'LICENSE'), join(outDir, 'LICENSE-material-symbols.txt'));
copyFileSync(join(dirname(require.resolve('@fontsource-variable/noto-sans-jp/package.json')), 'LICENSE'), join(outDir, 'LICENSE-OFL.txt'));
console.log(`fonts: ${readdirSync(outDir).length} files -> ${outDir}`);
