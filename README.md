# TripClip

日籍學生參訪 職類體驗

TripClip 是一款旅行手帳 App：拍照或從相簿加入照片，App 會依照日期、地點與時間整理成拍立得風格的旅程紀錄，還能寫下心得、做成每日拼貼分享出去。

## 功能

- **旅程列表**：依日期瀏覽照片，可依「地點」或「時段」分組
- **拍照記錄**：拍立得相框取景，拍完可重拍或確認加入旅程，並自動記錄地點
- **加入照片**：從最近 30 天的相簿或系統相簿挑選，自動讀取拍攝時間與 GPS 地點
- **查看照片**：左右滑動瀏覽當天照片，點照片寫心得
- **月曆**：左右滑動切換月份，選日期時展開當天的照片
- **每日拼貼**：散落、格狀、底片三種版型，可存到相簿或分享
- **多語系**：繁體中文、English、日本語

## 技術

- Kotlin、Jetpack Compose（Material 3）
- Navigation 3、Shared Element Transition
- Room、CameraX、Coil
- Android 13（API 33）以上

## 開始使用

1. 用 Android Studio 開啟專案
2. 連接手機或啟動模擬器
3. 執行 `app`，或在終端機輸入：

```sh
./gradlew :app:installDebug
```

## 學生練習：在哪裡寫自己的程式

請打開 `app/src/main/java/edu/saihs/tripclip/ui/settings/SettingsScreen.kt`，找到 `AboutSection`：

```kotlin
/** 在這裡放置icon與簡介還有寫你的小心得 */
@Composable
fun AboutSection(modifier: Modifier = Modifier) {
    Box(modifier)
}
```

這個區塊會顯示在 App 的 **設定 → 關於**（右上角齒輪進入）。請在這裡放上你的 icon、自我介紹，還有這次參訪的小心得。

範例：

```kotlin
@Composable
fun AboutSection(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.my_icon),
            contentDescription = null,
            modifier = Modifier.size(96.dp).clip(CircleShape),
        )
        Spacer(Modifier.height(12.dp))
        Text("山田太郎", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("來自東京，喜歡拍照與旅行")
        Spacer(Modifier.height(12.dp))
        Text("小心得：第一次用 Jetpack Compose 寫 App，很有趣！")
    }
}
```

## 測試資料

Debug 版本可以載入一組台北旅遊的範例照片：

**設定 → 開發者 → 插入測試資料**，再選擇「繁體中文」「English」或「日本語」。再選另一種語言會把已插入的測試資料換成該語言，不會重複新增。

範例照片與資料放在 `app/src/debug/assets/mock/`，只會包含在 debug 版本中：

- `mock_photos.json`：繁體中文
- `mock_photos_en.json`：English
- `mock_photos_ja.json`：日本語

三個檔案的照片、時間與 GPS 都相同，只有地點名稱（`placeName`）與心得（`note`）不同。

## 專案結構

```
app/src/main/java/edu/saihs/tripclip/
├── data/        資料層：Room 資料庫、相簿讀取、定位、匯入照片
└── ui/
    ├── journal/   旅程列表
    ├── camera/    拍照記錄
    ├── picker/    加入照片
    ├── viewer/    查看照片
    ├── note/      寫下心得
    ├── calendar/  月曆
    ├── collage/   每日拼貼
    ├── settings/  設定
    └── navigation/ 導覽與轉場
```
