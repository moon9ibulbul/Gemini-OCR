# AstralOCR

AstralOCR adalah aplikasi Android berbasis Jetpack Compose untuk melakukan OCR pada manhwa dengan bantuan Google Gemini. Aplikasi ini mampu mengklasifikasikan teks menjadi bubble speech bulat, bubble speech kotak, SFX, dan teks di luar bubble sesuai format khusus yang memudahkan proses typesetting.

## Fitur utama

- 🔮 Antarmuka modern dengan tema kosmik dan ikon kustom "A".
- 🗨️ Deteksi berbagai jenis teks manhwa (bubble bulat `()`, bubble kotak `[]`, SFX `//`, dan teks luar `''`).
- 🧠 Integrasi dengan Gemini Vision melalui API key dan model yang dapat dikonfigurasi pengguna.
- 📁 Mode Single dan Bulk OCR untuk memproses satu atau banyak gambar sekaligus.
- 💾 Simpan hasil ekstraksi dalam format `.txt`, baik per gambar maupun keseluruhan hasil bulk.
- 🚨 Snackbar notifikasi ketika terjadi kesalahan ataupun limit API.

## Menjalankan proyek

Pastikan Anda menggunakan Android Studio Giraffe (atau lebih baru) serta JDK 17.

1. Buka folder ini di Android Studio.
2. Sinkronkan Gradle dan jalankan aplikasi pada emulator atau perangkat fisik.
3. Masukkan API key dan nama model Gemini pada menu **Pengaturan** sebelum melakukan OCR.

## Membangun APK debug

Untuk menghasilkan berkas APK debug yang bisa langsung dipasang di perangkat:

```bash
./gradlew assembleDebug
```

Gradle wrapper akan mengunduh berkas pendukung (`gradle-wrapper.jar`) secara otomatis pada pemakaian pertama sehingga repositori tetap bebas dari file biner. Setelah proses selesai, APK dapat ditemukan pada `app/build/outputs/apk/debug/`. Nama berkas mengikuti pola default Gradle (`app-debug.apk`) sehingga mudah diunduh ke perangkat Anda.

## Konfigurasi Gemini

- API endpoint yang digunakan: `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
- Aplikasi membutuhkan akses internet serta izin membaca media gambar.

## Otomasi CI

Repositori ini dilengkapi workflow GitHub Actions yang menjalankan `assembleDebug` dan mengunggah APK debug sebagai artefak pada setiap push atau pull request. Anda dapat mengunduh APK terbaru langsung dari halaman workflow tanpa perlu membangun secara manual.
