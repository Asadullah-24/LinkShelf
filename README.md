# LinkShelf

Save reels, videos and posts from Instagram, Facebook, YouTube, LinkedIn, TikTok and X in one tap.
Links are sorted into a folder per platform and shown as cards, so you can find them later.
Everything is stored on your phone and works offline.

## Ways to save a link

| Method | How |
| --- | --- |
| Share menu | In any app tap **Share → Save to LinkShelf**. Saves silently and returns you to the app. |
| Floating button + one-tap save | Turn both on in Settings. On any reel or post, tap the button: LinkShelf presses Share → Copy link for you and saves it. Long-press opens LinkShelf; drag to the ✕ at the bottom to turn it off. Opacity is adjustable. |
| Quick Settings tile | Add the **Save link** tile, then tap it after copying a link. |
| In the app | Paste into the box on the home screen, or tap **Paste link**. |

## Features

- Automatic folders: Instagram, Facebook, YouTube, LinkedIn, TikTok, X / Twitter, Other
- Detects content type: Reel, Post, Story, Short, Song, Video, Live, Playlist
- Strips tracking parameters, so the same post is never saved twice
- Thumbnails and titles for every platform (stored on the phone, so they show offline)
- Search, sort and type filters inside each folder
- Multi-select (long-press a card): copy, share or delete many links at once
- Rename and delete single items
- Backup and restore as JSON (same format as the LinkShelf web version)

## Tech

Kotlin · Jetpack Compose (Material 3) · Room · Coil. Min Android 8.0 (API 26), target API 36.

```
app/src/main/java/app/linkshelf/
├── domain/    LinkParser (URL detection + cleanup), Platform
├── data/      Room entity/DAO/database, LinkRepository, SettingsStore
├── capture/   ShareReceiverActivity, ClipboardCaptureActivity, BubbleService, QuickSaveTileService
└── ui/        MainActivity, MainViewModel, screens/, components/, theme/
```

## Flavors

- `play`: Google Play build.
- `full`: GitHub build with every feature (video/audio download is coming here).

## Build

- **GitHub Actions** builds a debug APK on every push to `main` and publishes it to the
  `dev-latest` pre-release under Releases, ready to install from a phone.
- **Locally:** open the folder in Android Studio, or run `./gradlew assembleFullDebug`.

### Play Store bundle

Create an upload key once:

```
keytool -genkeypair -v -keystore release.jks -alias linkshelf -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks   # copy the output
```

Add repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
The workflow then also builds `app-release.aab` (download it from the run's artifacts).

### Play Console notes

- Floating button uses `SYSTEM_ALERT_WINDOW` and a `specialUse` foreground service.
  Declare it in **App content → Foreground service permissions** with the reason:
  "Floating shortcut button that lets the user save the link they just copied while using other apps."
- One-tap save uses an `AccessibilityService`. Declare it in **App content → Accessibility API**
  and keep the in-app disclosure shown before the user turns it on.
- Privacy policy: see [PRIVACY.md](PRIVACY.md).

## License

MIT, see [LICENSE](LICENSE).
