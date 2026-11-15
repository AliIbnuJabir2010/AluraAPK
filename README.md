# Alura (Android, Kotlin + Jetpack Compose)

Pemutar musik yang semua audionya diambil dari **Audius API** (tidak membaca file audio perangkat). Ada Favorit, Playlist Saya, dan Download (salinan dari API untuk diputar offline).

## Cara menjalankan
1. Buka folder ini di **Android Studio** (Ladybug atau lebih baru, JDK 17). Biarkan Gradle sync (otomatis mengunduh Gradle 8.9).
2. Buat API key gratis di https://api.audius.co/plans lalu salin `local.properties.example` menjadi `local.properties` dan isi `AUDIUS_API_KEY`.
   - Hanya API key yang dipakai di app. Bearer Token / API Secret JANGAN dimasukkan ke aplikasi (pakai backend proxy bila butuh fitur tulis).
3. Run di perangkat/emulator Android 8.0+ (minSdk 26).

## Struktur
- `data/` : model, `AudiusApi` (Retrofit), `AudiusRepository` (cache, timeout, error, empty state), `LibraryStore` (favorit, playlist, download)
- `player/` : `PlayerHolder` (satu ExoPlayer) + `PlaybackService` (notifikasi/background)
- `ui/` : tema, `GlassNav` (kapsul kaca + gelembung geser), layar Home/Search/Profil/About/Player
- `res/drawable-nodpi/ali_avatar.jpg, ali_banner.jpg` : foto developer

## Catatan
- Nama genre kategori (mis. "Alternative", "Hip-Hop/Rap") dicocokkan dengan swagger Audius; ubah di `AppViewModel.kt` bila perlu.
- Tidak semua lagu populer ada di Audius. Favorit/Playlist Saya/Download di Profil tersimpan di `files/library.json` dan `files/downloads/`.

## Build tanpa Android Studio (GitHub Actions)
1. Buat repo baru di GitHub, upload seluruh isi folder ini (termasuk `.github/`).
2. Repo > Settings > Secrets and variables > Actions > New repository secret: nama `AUDIUS_API_KEY`, isi API key Audius.
3. Tab Actions > "Build APK" > Run workflow. Setelah selesai (sekitar 5-10 menit), unduh artifact `Alura-debug-apk`, ekstrak, lalu install `app-debug.apk` di HP.

## Prioritas lagu Indonesia & pencarian
- Beranda hanya menampilkan lagu Indonesia. Audius tidak punya filter bahasa, jadi lagu dikenali dari lokasi artis, daftar artis (`INDO_ARTISTS` di `data/Ranking.kt`), tag/deskripsi, dan kata umum di judul. Tambah nama artis di daftar itu agar lebih banyak lagu muncul.
- Lagu barat muncul saat dicari. Urutan hasil: judul/artis paling cocok, lalu lagu lain dari artis yang sama, lalu sisa hasil yang relevan; lagu Indonesia diberi bonus.

## YouTube Music (tidak resmi)
- Sumber kedua, dipilih otomatis (tanpa tombol): cari di Audius dulu; kalau lagunya tidak ada pindah ke YouTube Music; kalau YouTube gagal/limit kembali ke Audius. Lagu YouTube yang gagal diputar juga diganti versi Audius bila ada. Lewat NewPipeExtractor (`data/YouTubeMusic.kt`). Bukan API resmi: melanggar ketentuan YouTube, bisa rusak kapan saja (naikkan versi `NewPipeExtractor` di `app/build.gradle.kts`), dan tidak cocok untuk Play Store.
- Hanya putar (termasuk di latar belakang), tanpa Download. Favorit dan playlist tetap bisa dipakai.
- Build butuh akses ke JitPack (sudah ditambahkan di `settings.gradle.kts`) dan core library desugaring.
