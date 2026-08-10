# Safe Telegram

Aplikasi Android pendamping yang memblokir **Global Search** Telegram
(pencarian username publik, channel publik, grup publik, bot publik) tanpa
mengubah APK atau database Telegram, dan tanpa mengganggu chat, panggilan,
voice note, kirim file, atau pencarian di dalam chat.

Dibangun sesuai PRD v1.0 (Agustus 2026).

## Cara kerja singkat

`TelegramGuardService` (Android `AccessibilityService`) mengawasi jendela
Telegram. Saat mendeteksi tanda-tanda hasil Global Search muncul (section
header seperti "GLOBAL SEARCH" atau area hasil publik), aplikasi langsung
memanggil `GLOBAL_ACTION_BACK` untuk mengembalikan pengguna ke daftar chat —
tanpa popup, dialog, atau notifikasi (Silent Mode, sesuai §10 PRD).

AccessibilityService **tidak bisa** benar-benar "menahan" tap di aplikasi
lain — jadi pendekatannya adalah deteksi-lalu-mundur secepat mungkin
(≈150–300ms), pola yang sama dipakai semua aplikasi pemblokir distraksi
berbasis Accessibility Service.

## Struktur proyek

```
SafeTelegram/
├── .github/workflows/build.yml
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/safetelegram/guard/
│       │   ├── domain/            # Pure Kotlin, no Android deps — testable
│       │   │   ├── ScreenNode.kt
│       │   │   ├── GuardSignatures.kt
│       │   │   ├── GlobalSearchDetector.kt
│       │   │   ├── SearchEntryPointDetector.kt
│       │   │   └── RateLimiter.kt
│       │   ├── infra/              # The only place touching AccessibilityNodeInfo
│       │   │   └── AccessibilityNodeInfoAdapter.kt
│       │   ├── service/            # Thin orchestrator
│       │   │   └── TelegramGuardService.kt
│       │   └── presentation/       # MVVM
│       │       ├── MainActivity.kt
│       │       └── AccessibilityStatusViewModel.kt
│       └── res/
│           ├── xml/accessibility_service_config.xml
│           ├── layout/activity_main.xml
│           └── values/ (strings, colors, themes)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Build via GitHub Actions (tidak perlu Android Studio)

Repo ini sudah menyertakan `.github/workflows/build.yml`. Setiap kamu push ke
branch `main`, GitHub otomatis build APK debug dan menyimpannya sebagai
artifact.

1. Push repo ini ke GitHub (lihat bagian **Push ke GitHub** di bawah).
2. Buka repo di GitHub → tab **Actions** → workflow **"Build APK"** akan
   jalan otomatis. Tunggu sampai centang hijau (±3–5 menit).
3. Klik run yang selesai → di bagian **Artifacts** paling bawah, download
   **SafeTelegram-debug-apk** (berupa .zip berisi `app-debug.apk`).
4. Pindahkan `app-debug.apk` ke HP (lewat Telegram Saved Messages, Google
   Drive, kabel USB, dll).
5. Di HP: buka file APK-nya → izinkan "Install unknown apps" untuk
   sumber file itu kalau diminta → Install.
6. Buka aplikasi Safe Telegram → tekan **"Buka Pengaturan Accessibility"** →
   aktifkan **Safe Telegram** di daftar Accessibility Service → kembali ke
   aplikasi, badge status akan berubah hijau.
7. Buka Telegram, tekan ikon Search di daftar chat → aplikasi otomatis
   mengembalikanmu ke daftar chat begitu hasil Global Search terdeteksi.

Tidak perlu root. Tidak memodifikasi APK/database Telegram (FR-14).

## Build lokal (opsional, kalau nanti butuh Android Studio)

```bash
./gradlew assembleDebug
# APK ada di app/build/outputs/apk/debug/app-debug.apk
```

## Batasan yang perlu kamu ketahui

- **Deteksi berbasis heuristik.** `SEARCH_ENTRY_ID_HINTS` dan
  `GLOBAL_RESULT_SIGNATURES` di `TelegramGuardService.kt` dicocokkan dengan
  resource-id dan teks yang biasa muncul di Telegram resmi
  (`org.telegram.messenger`). Jika Telegram merilis update UI dan pola ini
  berubah, cukup update dua daftar tersebut — tidak perlu mengubah arsitektur.
- **Hanya Telegram resmi** yang terdaftar di `TELEGRAM_PACKAGES`. Untuk
  fork seperti Telegram X (`org.thunderdog.challegram`) atau Nekogram,
  tambahkan package name-nya ke daftar tersebut (strukturnya bisa berbeda
  sehingga sinyal deteksi mungkin perlu disesuaikan juga).
- **Bukan pemblokiran 100% instan.** Ada jeda ±150–300ms sebelum aplikasi
  "mundur" — cukup untuk mencegah interaksi lanjutan, tapi bukan pencegahan
  di level render pertama (keterbatasan API Accessibility Service Android,
  bukan bug).
- Ikon aplikasi memakai vector sederhana (placeholder) — ganti
  `ic_launcher_foreground.xml` / `ic_launcher_background.xml` sesuai selera.

## Push ke GitHub

```bash
cd SafeTelegram
git init
git add .
git commit -m "Initial commit: Safe Telegram (blok Global Search Telegram)"
git branch -M main
git remote add origin <URL_REPO_GITHUB_KAMU>
git push -u origin main
```

`.gitignore` sudah menyertakan `build/`, `.gradle/`, `local.properties`, dan
file APK/AAB sehingga repo tetap bersih.

## Audit keamanan v2 — kenapa aplikasi keuangan sebelumnya memblokir Safe Telegram

### Ringkasan perubahan

| # | Perubahan | Alasan | Dampak |
|---|-----------|--------|--------|
| 1 | Hapus `BootReceiver` + permission `RECEIVE_BOOT_COMPLETED` | Kombinasi *BOOT_COMPLETED + AccessibilityService* adalah pola persistensi klasik malware. Receiver-nya sendiri tidak punya fungsi — sistem Android **otomatis** menyalakan ulang accessibility service yang sudah diizinkan user setiap boot | Satu permission dan satu komponen berisiko hilang total, tanpa kehilangan fungsi (FR-13 tetap terpenuhi) |
| 2 | Hapus flag `flagRetrieveInteractiveWindows` | Flag ini mengizinkan membaca window lain di luar window aktif (multi-window/overlay). Kode tidak pernah memanggil `getWindows()`, hanya `rootInActiveWindow` — flag ini murni bonus yang tidak dipakai, dan kombinasi flag ini + `canRetrieveWindowContent` adalah salah satu ciri khas serangan *overlay/screen-scraping* yang dicek beberapa SDK proteksi perbankan | Mengurangi "capability surface" yang dilaporkan ke sistem — tidak ada perubahan perilaku aplikasi |
| 3 | Hapus flag `flagReportViewIds` | Sebelumnya dipakai untuk mencocokkan resource-id Telegram. Diganti jadi murni deteksi berbasis teks/`contentDescription` ("GLOBAL SEARCH" section header + label "Search") yang terbukti cukup | Tidak perlu lagi meminta akses ke resource-id internal aplikasi lain sama sekali |
| 4 | `eventTypes` dikurangi dari 4 jadi 3 (hapus `typeViewFocused`) | Tidak pernah dipakai untuk logika apa pun — murni event yang diterima tapi tidak diproses | Event exception yang tidak perlu, sedikit mengurangi frekuensi callback |
| 5 | Pre-filter murah sebelum tree walk (`eventLooksRelevant`) | `TYPE_WINDOW_CONTENT_CHANGED` sangat bising (setiap pesan masuk, ketikan, scroll memicu event). Sebelumnya *setiap* event memicu full tree walk rekursif — ini biaya CPU terbesar. Sekarang dicek dulu `event.text`/`event.contentDescription` (sudah tersedia gratis dari event, tanpa API call tambahan) sebelum memutuskan perlu tree walk atau tidak | Penurunan signifikan jumlah tree walk aktual → CPU/baterai jauh lebih hemat |
| 6 | Rate limiter (`RateLimiter`, throttle 250ms) untuk tree walk | Membatasi tree walk maksimal 1x per 250ms meski event datang beruntun | Membatasi *worst case* CPU usage saat chat sangat aktif (grup ramai) |
| 7 | `notificationTimeout` naik dari 50ms → 100ms | Nilai kecil membuat sistem mengirim event nyaris tanpa batching → lebih banyak wake-up CPU. 100ms masih terasa instan untuk mata manusia (Silent Mode tetap terasa seketika) tapi memberi sistem ruang untuk menggabungkan event | Baterai lebih hemat, tanpa penurunan UX yang terasa |
| 8 | `android:packageNames` tetap dipertahankan ketat ke Telegram saja | **Ini jawaban utama untuk Requirement #1.** Pembatasan ini dilakukan di level OS (binder), bukan `if` di kode kita — artinya sistem **tidak pernah mengirim event apa pun** ke service ini selagi aplikasi lain (termasuk BRImo/BCA mobile/dll) di foreground. Tidak ada cara yang lebih kuat dari ini untuk "hanya aktif saat Telegram foreground" | Nol biaya CPU/RAM saat di luar Telegram, dijamin oleh sistem operasi, bukan oleh disiplin kode kita |
| 9 | Refactor ke `domain` / `infra` / `service` / `presentation` (Clean Architecture + MVVM) | Logika deteksi (murni Kotlin, testable) dipisahkan dari framework Android (`infra`) dan dari orkestrasi service (`service`)/UI (`presentation`) | Lebih mudah di-review, di-unit-test, dan diaudit ulang di masa depan — tidak mengubah perilaku |

### Batasan jujur yang TIDAK bisa dihindari (Requirement #10)

Ini bagian paling penting untuk dipahami: **mengecilkan flag/event seperti di atas adalah langkah teknis yang benar dan akan membantu melawan SDK proteksi yang memang mengecek flag/kapabilitas spesifik** (misalnya yang mencurigai `flagRetrieveInteractiveWindows`, atau pola "banyak event + resource-id scraping"). Tapi ada kelas SDK proteksi perbankan lain yang jauh lebih kasar: mereka memanggil `AccessibilityManager.getEnabledAccessibilityServiceList()`/membaca `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, lalu **memblokir jika ADA accessibility service pihak ketiga apa pun terdaftar di sana yang tidak ada di whitelist mereka** — terlepas dari flag, event, atau perilaku sebenarnya. Untuk SDK jenis ini, tidak ada refactor kode di sisi kita yang bisa membuat Safe Telegram "tidak terlihat", karena deteksinya terjadi di level daftar service yang aktif, bukan di level perilaku runtime.

Kenapa BlockP bisa lolos padahal sama-sama pakai Accessibility Service? Kemungkinan penyebabnya (tidak bisa dipastikan tanpa membedah BlockP dan SDK bank yang bersangkutan):
- BlockP mungkin memang tidak meminta `canRetrieveWindowContent`/flag serupa sama sekali (kalau fungsinya cuma memblokir buka-app tanpa membaca isi layar), sehingga tidak cocok dengan pola deteksi berbasis kapabilitas.
- SDK bank tertentu punya *allowlist* nama paket/sertifikat aplikasi populer (BlockP mungkin cukup dikenal), sementara `com.safetelegram.guard` adalah paket baru yang belum "dikenal".
- Perbedaan versi SDK/aplikasi bank antar waktu — device kamu mungkin belum update ke versi yang mendeteksi BlockP juga.

Kami tidak akan membuat *workaround* seperti menyembunyikan service dari daftar sistem, memalsukan nama paket, atau mem-bypass pengecekan bank — selain melanggar kebijakan Google Play & ToS bank, itu juga secara teknis nyaris mustahil dan berisiko tinggi.

### Rekomendasi tambahan (di luar kode)

1. **Nonaktifkan Accessibility Service Safe Telegram sesaat sebelum membuka aplikasi keuangan**, lalu aktifkan lagi setelah selesai — tidak nyaman, tapi 100% menghilangkan sinyal deteksi apa pun karena service benar-benar tidak ada di daftar `ENABLED_ACCESSIBILITY_SERVICES` saat itu.
2. Pertimbangkan atribut `android:isAccessibilityTool="true"` (API 33+) di `accessibility_service_config.xml` — atribut resmi Android untuk menandai service yang *genuinely* untuk aksesibilitas/kesejahteraan digital, yang membuat sistem menampilkannya di kategori terpisah di Settings. **Catatan jujur:** ini bukan trik anti-deteksi terjamin (beberapa SDK bank mengabaikan atribut ini), dan menandai app sebagai "accessibility tool" sebaiknya dilakukan hanya kalau kamu memang menganggap fungsi pemblokir distraksi ini sebagai tujuan aksesibilitas/kesejahteraan digital yang tulus — bukan sekadar demi lolos deteksi.
3. Laporkan ke customer service bank terkait (BRImo/wondr/Bibit/myBCA/BCA mobile) — beberapa bank punya proses whitelist manual untuk accessibility service tertentu bila diajukan dengan penjelasan yang jelas.

## Roadmap (dari PRD §15)

- v1 — Block Global Search ✅ (ini)
- v2 — Block Discover Page, Block Suggested Channel
- v3 — Whitelist Chat
- v4 — AI Impulse Detection
