/**
 * エラー画面・メンテナンス画面用。以前は設定なし・プラグインなしの CDN 版を使っていたので、
 * 見た目が変わらないよう既定の設定のまま CSS を作る。
 */
module.exports = {
  content: [
    '../src/main/resources/templates/error/**/*.html',
    '../src/main/resources/templates/maintenance.html',
  ],
  theme: { extend: {} },
  plugins: [],
};
