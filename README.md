# Alura (Android, Kotlin + Jetpack Compose)

Pemutar musik: **online (Audius API)** + **offline (file audio perangkat)** dengan satu player & UI yang sama.

## Cara menjalankan
1. Buka folder ini di **Android Studio** (Ladybug atau lebih baru, JDK 17). Biarkan Gradle sync (otomatis mengunduh Gradle 8.9).
2. Buat API key gratis di https://api.audius.co/plans lalu salin `local.properties.example` menjadi `local.properties` dan isi `AUDIUS_API_KEY`.
   - Hanya API key yang dipakai di app. Bearer Token / API Secret JANGAN dimasukkan ke aplikasi (pakai backend proxy bila butuh fitur tulis).
3. Run di perangkat/emulator Android 8.0+ (minSdk 26). Izinkan akses audio agar lagu lokal muncul.

## Struktur
- `data/` : model, `AudiusApi` (Retrofit), `AudiusRepository` & `LocalRepository` (cache, timeout, error, empty state)
- `player/` : `PlayerHolder` (satu ExoPlayer) + `PlaybackService` (notifikasi/background)
- `ui/` : tema, `GlassNav` (kapsul kaca + gelembung geser), layar Home/Search/Profil/About/Player
- `res/drawable-nodpi/ali_avatar.jpg, ali_banner.jpg` : foto developer

## Catatan
- Nama genre kategori (mis. "Alternative", "Hip-Hop/Rap") dicocokkan dengan swagger Audius; ubah di `AppViewModel.kt` bila perlu.
- Tidak semua lagu populer ada di Audius. Fitur seperti Favorit/Playlist Saya/Download di Profil masih tampilan statis.

## Build tanpa Android Studio (GitHub Actions)
1. Buat repo baru di GitHub, upload seluruh isi folder ini (termasuk `.github/`).
2. Repo > Settings > Secrets and variables > Actions > New repository secret: nama `AUDIUS_API_KEY`, isi API key Audius.
3. Tab Actions > "Build APK" > Run workflow. Setelah selesai (sekitar 5-10 menit), unduh artifact `Alura-debug-apk`, ekstrak, lalu install `app-debug.apk` di HP.
