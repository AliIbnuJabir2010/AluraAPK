# Alura (Android, Kotlin + Jetpack Compose)

Pemutar musik yang semua audionya diambil dari **Audius API** (tidak membaca file audio perangkat). Ada Favorit, Playlist Manual, tiga Playlist Otomatis, statistik pemutaran lokal, dan Download (salinan audio Audius untuk diputar offline).

## Cara menjalankan
1. Buka folder ini di **Android Studio** (Ladybug atau lebih baru, JDK 17). Biarkan Gradle sync (otomatis mengunduh Gradle 8.9).
2. Buat API key gratis di https://api.audius.co/plans lalu salin `local.properties.example` menjadi `local.properties` dan isi `AUDIUS_API_KEY`.
   - Hanya API key yang dipakai di app. Bearer Token / API Secret JANGAN dimasukkan ke aplikasi (pakai backend proxy bila butuh fitur tulis).
3. Run di perangkat/emulator Android 8.0+ (minSdk 26).

## Struktur
- `data/` : model, `AudiusApi` (Retrofit), `AudiusRepository` (cache, timeout, error, empty state), `LibraryStore` (favorit, playlist manual, download, riwayat, statistik pemutaran)
- `player/` : `PlayerHolder` (satu ExoPlayer) + `PlaybackService` (notifikasi/background)
- `ui/` : tema, `GlassNav` (kapsul kaca + gelembung geser), layar Home/Search/Profil/About/Player dan tiga playlist otomatis
- `res/drawable-nodpi/ali_avatar.jpg, ali_banner.jpg` : foto developer

## Catatan
- Nama genre kategori (mis. "Alternative", "Hip-Hop/Rap") dicocokkan dengan swagger Audius; ubah di `AppViewModel.kt` bila perlu.
- Tidak semua lagu populer ada di Audius. Favorit/Playlist Manual/Download di Profil tersimpan di `files/library.json` dan `files/downloads/`.

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

## Playlist otomatis dan statistik lokal
- **Sering Diputar** mengurutkan lagu dari jumlah pemutaran, kebaruan, dan penalti skip. Satu pemutaran baru dihitung setelah minimal 30 detik audio benar-benar berjalan.
- **Baru Didengarkan** diurutkan berdasarkan waktu terakhir diputar.
- **Mix Kamu** menggabungkan kandidat lagu populer (40%), favorit (30%), baru didengarkan (20%), dan penemuan dari trending Audius (10%); duplikat dibuang dan slot yang kosong diisi dari kandidat lain.
- Statistik disimpan di `files/library.json`. Tidak ada akun, server, atau sinkronisasi cloud.
- Playlist manual tetap terpisah dan tidak diubah oleh algoritma.

## Download dan offline
- Alura tidak memindai atau mengambil musik dari folder musik HP.
- Hanya lagu Audius yang pengguna pilih lewat tombol Download yang disalin ke `files/downloads/`.
- Saat lagu sudah diunduh, player memilih file lokal; jika belum diunduh, player melakukan streaming. Lagu YouTube Music tidak mendukung download di aplikasi ini.
