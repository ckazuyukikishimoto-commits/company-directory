# frontend（画面の CSS とフォントを作るところ）

以前は各画面が Tailwind CSS と Google Fonts を **インターネット（CDN）から** 読み込んでいました。
デスクトップアプリとしてオフラインでも崩れずに開けるように、CSS とフォントを **事前に作ってアプリの中に同梱** しています。

- できあがったファイル（コミット済み）
  - `src/main/resources/static/css/app.css` … ログイン画面と `companies/` の画面用（Tailwind + forms / container-queries プラグイン）
  - `src/main/resources/static/css/plain.css` … エラー画面・メンテナンス画面用（Tailwind の既定設定のみ）
  - `src/main/resources/static/css/fonts/` … Material Symbols（アイコン）、Noto Sans JP、Inter、Space Grotesk
- **Java のビルド（`./gradlew build`）だけなら Node.js は要りません。**

## HTML で新しい Tailwind のクラスを使ったとき

事前に作る方式なので、テンプレートに **今まで使っていなかったクラス** を書いたら CSS を作り直します。

```bash
cd frontend
npm ci
npm run build
```

作り直した `src/main/resources/static/css/` をコミットしてください。

## 注意

- Tailwind はテンプレートの中の文字列からクラス名を探します。JavaScript で `'text-' + color` のように **クラス名を組み立てると見つけられません**。`'text-red-600'` のように完全な名前で書いてください。
- 色や角丸などの設定は `tailwind.app.config.js` にまとめました（以前は各 HTML の `tailwind.config = {...}` に書いていたもの）。
- フォントのライセンス: Material Symbols は Apache-2.0、Noto Sans JP / Inter / Space Grotesk は SIL Open Font License 1.1（`static/css/fonts/LICENSE-*.txt`）。
