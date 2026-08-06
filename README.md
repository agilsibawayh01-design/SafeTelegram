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
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/safetelegram/guard/
│       │   ├── MainActivity.kt          # layar status + tombol buka Settings
│       │   ├── TelegramGuardService.kt  # logika inti pemblokiran
│       │   └── BootReceiver.kt          # hook opsional untuk boot
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

## Roadmap (dari PRD §15)

- v1 — Block Global Search ✅ (ini)
- v2 — Block Discover Page, Block Suggested Channel
- v3 — Whitelist Chat
- v4 — AI Impulse Detection
