# デスクトップ版（Windows アプリ）について

企業名簿アプリを、**Windows でダブルクリックするとすぐ開けるアプリ**にしました。
中身は今までと同じ Spring Boot のアプリです。それを Java ごと 1 つのアプリにまとめています（`jpackage`。C# でいうと `dotnet publish --self-contained` と MSI 作成を合わせたようなもの）。
DB は今までどおり Supabase（PostgreSQL）を使い、ログイン画面やアカウントロックもそのまま残っています。

> Web 版（Docker・Render）は今までどおり動きます。デスクトップ版の設定は `desktop` プロファイル（`application-desktop.properties`。C# の `appsettings.Development.json` のように、環境ごとに設定を重ねる仕組み）に分けてあり、Web 版には影響しません。

---

## 1. しくみ（何をどう変えたか）

```
CompanyDirectory.exe をダブルクリック
  │
  ├─ すぐに「起動しています…」の画像が出る（Java が動き出す前に出せる -splash という機能）
  ├─ 二重起動していないか確かめる（動いていれば、その画面を開くだけで終わる）
  ├─ %APPDATA%\CompanyDirectory\.env から DB の接続情報を読む
  ├─ 「DB につながるか」の確認と「Spring Boot の起動」を同時に始める
  │     └─ つながらなければ、日本語のダイアログで理由を知らせる（Supabase の休止など）
  ├─ 準備ができたら、Edge の「アプリモード」で専用ウィンドウを開く（アドレスバーなし）
  └─ ウィンドウを閉じると Spring Boot も止まって終了する
        （タスクトレイの「企業名簿」アイコンからも「画面を開く」「終了」ができる）
```

| 変えたところ | 内容 |
|---|---|
| `desktop/DesktopLauncher.java` ほか | デスクトップ版の入口。上の図の流れを担当 |
| `application-desktop.properties` | デスクトップ版だけの設定（`127.0.0.1` だけで待ち受け、デバッグログを止める、遅延初期化 など） |
| `frontend/`, `static/css/` | Tailwind CSS と Google Fonts を CDN から読むのをやめて、作っておいた CSS とフォントをアプリに入れた（オフラインでも崩れない） |
| `packaging/desktop.gradle` | `jlink`（Java を必要な分だけにする）と `jpackage`（アプリにまとめる）の Gradle タスク |
| `.github/workflows/desktop-build.yml` | GitHub Actions の Windows マシンで `.zip` と `.msi` を作る |
| `.github/workflows/supabase-keepalive.yml` | Supabase が休止しないよう、週 2 回 DB に軽いクエリを送る（Secrets を設定するまで動かない） |
| `GeminiService.java` | API キーを Spring の設定から読むように（デスクトップ版の `.env` でも使えるように）。初めて使うときに作る |
| `TrashPurgeScheduler.java` | 遅延初期化でも定期実行が動くよう `@Lazy(false)` を付けた |

### 専用ウィンドウにした理由（既定のブラウザとの比較）

| 方法 | 手間 | 使い勝手 | 選んだか |
|---|---|---|---|
| **Edge のアプリモード**（`msedge --app=URL`） | 少ない（Windows に必ず入っている） | アプリらしい見た目。ウィンドウを閉じたら終了できる。Excel のダウンロード・アップロードもふつうに動く | ✅ 選んだ |
| 既定のブラウザのタブで開く | いちばん少ない | 他のタブに紛れる。「閉じた＝終了」が分からないので、終了はトレイから | Edge が見つからないときの予備 |
| JavaFX の WebView など、Java の中に画面を作る | 多い | 古いブラウザ部品なので Tailwind の表示や、ファイルの保存・選択に手当てが要る | 選ばない |

- 専用のプロファイル（`%APPDATA%\CompanyDirectory\browser-profile`）を使うので、ふだん使っている Edge とは別に動きます。ログイン状態の保持（remember-me）もこの中に保存されます。
- いつも既定のブラウザで開きたいときは、環境変数 `DESKTOP_UI=browser` を設定します。

### ポートと二重起動

- `http://127.0.0.1:18080` を使います（Web 版の開発でよく使う 8080 とぶつからないように）。18080 が使われていれば、空いている番号を自動で選びます。環境変数 `DESKTOP_PORT` で変えられます。
- 待ち受けは `127.0.0.1` だけなので、**同じ LAN の他の PC からは見えません**。
- 2 回目にダブルクリックしたときは、新しく起動せず、動いているアプリの画面を開きます（`app.lock` というファイルで判定）。

---

## 2. 起動を速くした工夫と、計測結果

クラウドの Linux マシンで、ローカルの PostgreSQL につないで計りました（各 3 回）。
「`/login` が開けるようになるまで」の時間です。**Supabase につなぐと、ネットワークの分（だいたい 0.5〜2 秒）がこれに足されます**。

| やり方 | 起動時間 | 入れたか |
|---|---|---|
| 元の状態（Web 版の設定：デバッグログと SQL 表示あり） | 約 7.1 秒（Spring の準備完了まで） | − |
| デスクトップ用の設定（デバッグログを止める）＋遅延初期化なし | 約 6.7 秒 | − |
| ＋ **遅延初期化**（`spring.main.lazy-initialization=true`。Bean を使うときまで作らない。DI コンテナの Lazy 登録に近い） | 約 5.2 秒 | ✅ |
| ＋ **CDS**（クラスの読み込み結果をファイルに保存して使い回す JVM の機能。.NET の ReadyToRun に近い発想。2 回目以降） | **約 2.9 秒** | ✅ |
| ＋ C1 コンパイラだけを使う（`-XX:TieredStopAtLevel=1`） | 約 2.6 秒 | ❌（差が小さいわりに、Excel 取り込みなど重い処理が遅くなるおそれがある） |
| SerialGC に変える | ほぼ変わらず | ❌ |
| Spring AOT | 試していない | ❌（プロファイルがビルド時に固定されて Web 版と分けにくくなる。今後の候補） |

- **パッケージ版でも確かめました**: 1 回目は約 7 秒（CDS ファイルを作るため）、2 回目からは約 3 秒でした。
- **GitHub Actions の Windows マシン**（`windows-latest`、Temurin 21.0.13）でも、パッケージ版を 2 回起動して確かめました。1 回目は約 28 秒（CI のマシンは遅く、CDS ファイルも作るため）、**2 回目は約 3.7 秒**で `/login` が開きました。ふつうの PC ではもっと速いはずです。
- CDS ファイル（約 90MB）は、アプリのフォルダの中に、**1 回目に正しく終了したとき**自動で作られます。タスクマネージャーで強制終了すると作られません。
- 新しい版を入れると、CDS ファイルの名前が変わるので、また 1 回目だけ遅くなります（古いファイルは自動で消します）。
- 遅延初期化の注意: 設定ミスなどのエラーが「起動時」ではなく「その画面を初めて開いたとき」に出ることがあります。また、DB まわりの準備の一部が後ろに回るので、**最初のログイン操作だけ、数秒よけいにかかる**ことがあります。

---

## 3. Windows PC での導入と確認の手順

### 3-1. できあがったアプリをダウンロードする

1. GitHub のリポジトリの **Actions** タブ → 左の **Desktop build** → いちばん新しい実行（緑のチェックのもの）を開く
   （PR を見ているときは、PR の下のほうの「Checks」からも開けます）
2. ページの下の **Artifacts** から、どちらかをダウンロードする
   - `CompanyDirectory-windows-msi` … インストーラー（おすすめ）
   - `CompanyDirectory-windows-zip` … インストール不要のフォルダ版（msi が作れなかったときの予備）
3. ダウンロードしたファイルは zip になっているので、右クリック →「すべて展開」

### 3-2. インストールする

- **msi の場合**: `CompanyDirectory-1.0.xx.msi` をダブルクリック → 画面の指示どおり進める。
  管理者権限は要りません（ユーザーごとのインストール）。スタートメニューとデスクトップにショートカットができます。
- **zip の場合**: 中の `CompanyDirectory` フォルダを、たとえば `C:\Users\あなた\Apps\` に置き、`CompanyDirectory.exe` をダブルクリック。
- 「Windows によって PC が保護されました」と出たら、「詳細情報」→「実行」を押します（署名していないアプリなので出ます）。

### 3-3. `.env` を置く

1. 一度アプリを起動すると「データベースの接続情報が設定されていません」と出て、フォルダ
   `C:\Users\あなた\AppData\Roaming\CompanyDirectory` が開きます（中に `.env` の雛形ができています）。
2. 今 Web 版で使っている `.env` と同じ値を書きます（**メモ帳で開いて、UTF-8 のまま保存**）。

   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://（Supabase のホスト）:5432/postgres?sslmode=require
   SPRING_DATASOURCE_USERNAME=（ユーザー名）
   SPRING_DATASOURCE_PASSWORD=（パスワード）
   HOJIN_BANGOU_APP_ID=（法人番号 API のキー）
   GEMINI_API_KEY=（Gemini のキー）
   ```

   - 自宅の回線が IPv6 に対応していないと、Supabase の「Direct connection」につながらないことがあります。そのときは Supabase の画面の **Connect → Session pooler** の接続情報を使います（ユーザー名が `postgres.プロジェクトID` の形になります）。
   - `.env` は秘密の値なので、人に送ったり Git に入れたりしないでください。
3. もう一度アプリを起動します。

### 3-4. 動作確認のチェックリスト

- [ ] ダブルクリックしてすぐに「起動しています…」の画像が出る
- [ ] 数秒でログイン画面が専用ウィンドウ（アドレスバーなし）で開く
- [ ] ログインできる。パスワードを 5 回まちがえるとロックされる（今までどおり）
- [ ] 企業一覧・新規登録・編集・削除（ゴミ箱）・復元ができる
- [ ] Excel の取り込み（プレビュー → 登録）と、Excel のダウンロードができる（保存先は Edge の設定によります。ふつうは「ダウンロード」フォルダ）
- [ ] 郵便番号から住所の自動入力ができる
- [ ] 画像からの読み取り（Gemini）ができる
- [ ] アイコン（虫めがねなど）が四角や英単語にならず、ちゃんと表示される
- [ ] Wi-Fi を切っても、画面のデザインが崩れない（DB が要る操作はエラーになってよい）
- [ ] ウィンドウを閉じると、タスクトレイのアイコンも消えて終了する（**とくに大事**。Edge の設定によっては裏で残るかもしれず、クラウドでは確かめられませんでした）
- [ ] 一覧・ゴミ箱・まとめて追加の画面で、表の罫線や余白が以前と同じに見える
- [ ] **もう一度起動すると、1 回目より速い**（CDS が効いている）。時間を計ってメモしておく
- [ ] アプリが動いているときにもう一度ダブルクリックすると、新しく起動せず画面だけ開く
- [ ] インターネットを切った状態で起動すると、日本語で「データベースのサーバーが見つかりません」と出る
- [ ] タスクトレイのアイコンを右クリック →「終了」で止まる
- [ ] 30 日より前にゴミ箱に入れたデータがあれば、起動して 10 秒ほどで消える（`app.log` に「ゴミ箱自動パージ完了: 削除件数 = …」と出る）

うまくいかないときは、`C:\Users\あなた\AppData\Roaming\CompanyDirectory\logs\app.log` を見てください（トレイのメニュー「ログのフォルダを開く」からも開けます）。

### 3-5. アンインストール

「設定」→「アプリ」→ `CompanyDirectory` → アンインストール。
そのあと、必要なら次の 2 つのフォルダも手で消します。
- `C:\Users\あなた\AppData\Local\CompanyDirectory`（CDS ファイルが残ることがあります）
- `C:\Users\あなた\AppData\Roaming\CompanyDirectory`（`.env`、ログ、ブラウザのデータ）

---

## 4. Supabase の休止への対策

Supabase の無料プランは、**約 7 日間 DB が使われないとプロジェクトが一時停止**します（1 週間ほど前に警告メールが来ます）。
止まったら、Supabase のダッシュボードにログインして「Resume（再開）」を押すまで使えません。
（数値は 2026-11 時点の Supabase 公式ドキュメント〈https://supabase.com/docs/guides/platform/free-project-pausing 、https://supabase.com/pricing 〉によります。変わることがあるので、公式ページでも確認してください）

### 案の比較

| 案 | 手間 | 費用 | 弱点 |
|---|---|---|---|
| **A. GitHub Actions で週 2 回 `select 1` を送る** | 小（このPRで用意済み。Secrets を 1 つ設定するだけ） | 0 円 | 公開リポジトリは 60 日動きが無いと定期実行が止まる。cron は遅れることがある |
| B. Supabase の有料プラン（Pro） | 最小 | 月 25 ドルくらい | 個人で使うには高い |
| C. PC にローカルの PostgreSQL を入れて移す | 中（インストール、データの移行、バックアップを自分で） | 0 円 | 複数の PC で共有できない。バックアップを自分でとる必要がある |
| D. Neon など、休止しても自動で起きるサービスに移す | 中（データの移行、接続先の変更） | 0 円 | 最初の接続が少し遅い。移行の作業がある |
| **E. 起動時に止まっていたら分かりやすく知らせる** | 小（このPRで実装済み） | 0 円 | 知らせるだけで、再開は手で行う |

### おすすめ: A ＋ E（どちらもこの PR で用意済み）

- お金がかからず、今の構成（Supabase）を変えずに済みます。
- A で休止を防ぎ、それでも止まったとき（長期休みなど）は E のダイアログで気づけます。ダイアログの「Supabase を開く」ボタンからダッシュボードへ行けます。
- 将来、データを 1 台の PC だけで使うと決めたら C、複数の PC で本格的に使うなら D を考えるとよいです。

### A を動かす設定（Secrets の登録）

1. Supabase のダッシュボード → プロジェクト → 上の **Connect** → **Session pooler** を選び、URI をコピーする
   （例: `postgresql://postgres.abcd1234:[YOUR-PASSWORD]@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres`）
   - `[YOUR-PASSWORD]` を本当のパスワードに置きかえ、最後に `?sslmode=require` を付ける
   - **Direct connection（`db.xxxx.supabase.co`）は使わない**でください。GitHub のマシンは IPv6 につながらないことが多く、失敗します
2. GitHub のリポジトリ → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**
   - Name: `SUPABASE_DB_URL`
   - Secret: 1 でコピーした URI
3. **Actions** タブ → **Supabase keepalive** → **Run workflow** で手で 1 回動かし、緑のチェックになるか確かめる
4. あとは毎週 月曜・木曜の 12:17（日本時間）に自動で動きます

Secrets を設定していない間は、このワークフローは「何もしないで成功」として終わります。

---

## 5. 開発者向けメモ

```bash
# デスクトップ版をパッケージにせずに動かす（.env は %APPDATA%\CompanyDirectory\.env か、環境変数）
./gradlew desktopRun

# いま使っている OS 向けのフォルダ版を作る → build/desktop/dist/CompanyDirectory
./gradlew desktopAppImage

# インストーラー（Windows は .msi。WiX Toolset 3 が必要）
./gradlew desktopInstaller -PdesktopVersion=1.0.1
```

- `jpackage` は「今動いている OS 向け」しか作れません。Windows 版は GitHub Actions の `windows-latest` で作ります。
- 同梱する Java は、Actions が毎回とってくる **Temurin 21 の最新パッチ版** です（`check-latest: true`）。JRE の脆弱性が見つかったときは、Actions を動かし直して新しい版を入れれば更新できます。使った版は Artifacts の `java-version.txt` に記録しています。
- 画面のテンプレートで新しい Tailwind のクラスを使ったときは、CSS を作り直します（`frontend/README.md`）。
- アイコン・起動画面の画像は `packaging/make-images.js` で作っています。

---

## 6. 残っている課題

- **Windows の実機ではまだ動かしていません**（クラウドは Linux のため）。GitHub Actions の Windows マシンで、起動して `/login` が開くところまでは自動で確かめています（専用ウィンドウ・トレイ・ダイアログは画面が必要なので未確認）。3-4 のチェックリストで確認をお願いします。
- `.env` は「キー=値」だけを読みます。行の途中の `# コメント` は値の一部になるので、コメントは別の行に書いてください。前後に空白がある値は `"` で囲みます。
- **Supabase につないだときの起動時間**は計れていません（クラウドから Supabase へつなぐ接続情報が無いため）。
- コード署名をしていないので、初回に Windows の SmartScreen の警告が出ます。
- アプリ名・ショートカット名は、文字化けなどの問題を避けるため英語の `CompanyDirectory` にしています。日本語名にしたい場合は試してから変えるのが安全です。
- ~~ゴミ箱の自動削除（毎日 3:00）は、アプリが動いていないと実行されない~~ → **対応済み**: 起動が終わって 10 秒後に、裏で 1 回「30 日を過ぎた分」を削除するようにしました（`TrashStartupPurge`）。Web 版でも同じように動きますが、もう消えたものは消えないだけなので、定期実行と重なっても困りません。止めたいときは `app.trash.purge-on-startup=false`。
- 1 回目の起動は CDS ファイルを作るので遅い（約 7 秒）です。
- `TieredStopAtLevel=1` や Spring AOT など、さらに速くする手は残っています（効果と副作用を見て判断）。
