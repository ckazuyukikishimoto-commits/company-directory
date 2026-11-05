// アプリのアイコン（.ico / .png）と起動中の画面（splash.png）を作るスクリプト。
// できあがった画像はコミット済みなので、普段は実行不要。
// 実行: (リポジトリ直下で) node packaging/make-images.js && python3 packaging/make-ico.py
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

(async () => {
  const root = path.resolve(__dirname, '..');
  const out = path.join(__dirname, 'images');
  const svg = fs.readFileSync(path.join(root, 'src/main/resources/static/images/app-icon.svg'), 'utf8');
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 1 });

  for (const size of [16, 24, 32, 48, 64, 128, 256, 512]) {
    await page.setViewportSize({ width: size, height: size });
    await page.setContent(`<html><body style="margin:0;background:transparent">${svg.replace('<svg ', `<svg width="${size}" height="${size}" `)}</body></html>`);
    await page.screenshot({ path: path.join(out, `icon-${size}.png`), omitBackground: true });
  }

  // 起動中の画面。JVM が立ち上がる前に Java が表示する（-splash オプション）
  const fontsCss = path.join(root, 'src/main/resources/static/css/fonts/fonts.css');
  await page.setViewportSize({ width: 480, height: 260 });
  await page.goto('file://' + fontsCss); // フォントの相対パスを解決させるため
  await page.setContent(`
    <html><head><link rel="stylesheet" href="file://${fontsCss}"></head>
    <body style="margin:0;width:480px;height:260px;font-family:'Noto Sans JP';background:#fffbf0;display:flex;align-items:center;justify-content:center;border:1px solid #d6d3c9;box-sizing:border-box">
      <div style="text-align:center">
        <div style="width:88px;height:88px;margin:0 auto 14px">${svg.replace('<svg ', '<svg width="88" height="88" ')}</div>
        <div style="font-size:24px;font-weight:700;color:#222">企業名簿</div>
        <div style="font-size:14px;color:#555;margin-top:6px">起動しています…</div>
      </div>
    </body></html>`);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(out, 'splash.png') });
  await browser.close();
})();
