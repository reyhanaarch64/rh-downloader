# RH Downloader

<p align="center"><a href="#english">English</a> | <a href="#bahasa-indonesia">Bahasa Indonesia</a> | <a href="#contact">Contact</a></p>

## English

RH Downloader (`RHDOWN`) is an Android app for downloading TikTok videos and YouTube audio/video. Downloads run in a background service with a queue, and the app can receive links shared from other apps.

> **Important: third-party dependency.** The TikTok and YouTube features depend on external services that are not controlled by this project (TikTok via `tikwm.com`, YouTube via `yt-to-mp3.net` and its API backend). These services can change their domain, API, keys, or rules at any time, or stop working altogether. When that happens, the related download feature will fail until the code is updated. This is outside the developer's control, and no uptime or availability is guaranteed.

### Features

- **TikTok**: paste a link and download the HD video through the `tikwm` API
- **YouTube**: paste a link and choose **MP3** (audio) or **MP4** (video)
- **Background queue**: downloads run in a foreground service with a notification, and continue while the app is closed
- **Share intent**: send a link from another app into the downloader
- **Media list**: browse downloaded files, filter by YouTube, TikTok, audio, video, or photo
- **Boot restart**: the background service restarts after the device reboots or after an app update
- **Floating bubble**: optional popup or notification mode for progress, using the overlay permission
- **Light and dark theme**

### Storage

Files are saved to `RHDOWN` in the device's internal storage (`/storage/emulated/0/RHDOWN/`).

### Permissions

Internet, network state, storage access (including `MANAGE_EXTERNAL_STORAGE` for saving to `RHDOWN`), display over other apps (for the bubble), foreground service, boot completed, notifications, and ignoring battery optimizations (so background downloads are not stopped as easily).

### Requirements

- Android 6.0 (API 23) or newer

### Build with AIDE

1. Clone this repository, or download it as a ZIP and extract it.
2. Open the project folder in AIDE.
3. Sources are in `app/src/main/java/app/rh/downloader/`. Layouts and the web UI are in `app/src/main/res/` and `app/src/main/assets/`.
4. Build and install on the device.

The app icon is a vector drawable: `app/src/main/res/drawable/ic_launcher.xml`.

### Main files

- `MainActivity`: main screen (WebView UI)
- `WebAppInterface`: bridge between the web UI and Java
- `BackgroundDownloadService`: download queue, notifications, and floating bubble
- `DownloadBackend`: TikTok download logic
- `YoutubeBackend`: YouTube download logic (MP3/MP4), auth-key retrieval, and conversion/progress polling
- `MediaManager`: list and filter downloaded files
- `StorageHelper`: `RHDOWN` folder and storage access
- `HttpUtil`: HTTP helper
- `BootReceiver`: restarts the service after boot or update
- `AppFileProvider`: file sharing for downloaded files

### YouTube key handling

YouTube requests use an API key that is taken from the `yt-to-mp3.net` page at runtime, not stored in the code. If the key is rejected, the app fetches a new key and retries once. If the site changes its page layout, domain, or API, this mechanism can break.

### License

MIT. See [LICENSE](LICENSE).

---

## Bahasa Indonesia

RH Downloader (`RHDOWN`) adalah aplikasi Android untuk download video TikTok dan audio/video YouTube. Download berjalan di background service dengan antrean, dan aplikasi bisa menerima link yang dibagikan dari aplikasi lain.

> **Penting: dependensi pihak ketiga.** Fitur TikTok dan YouTube bergantung pada layanan eksternal yang tidak dikendalikan project ini (TikTok lewat `tikwm.com`, YouTube lewat `yt-to-mp3.net` dan backend API-nya). Layanan tersebut bisa berubah domain, API, key, atau aturannya kapan saja, atau berhenti bekerja sama sekali. Jika itu terjadi, fitur download terkait akan gagal sampai kode diperbarui. Ini di luar kendali pengembang, dan tidak ada jaminan ketersediaan.

### Fitur

- **TikTok**: tempel link, lalu download video HD lewat API `tikwm`
- **YouTube**: tempel link, lalu pilih **MP3** (audio) atau **MP4** (video)
- **Antrean background**: download berjalan di foreground service dengan notifikasi, dan tetap jalan saat aplikasi ditutup
- **Share intent**: kirim link dari aplikasi lain langsung ke downloader
- **Daftar media**: lihat file hasil download, filter YouTube, TikTok, audio, video, atau foto
- **Restart setelah boot**: service background dinyalakan lagi setelah HP restart atau aplikasi diperbarui
- **Bubble mengambang**: mode popup atau notifikasi untuk progres, memakai izin tampil di atas aplikasi lain
- **Tema terang dan gelap**

### Penyimpanan

File disimpan di folder `RHDOWN` pada penyimpanan internal (`/storage/emulated/0/RHDOWN/`).

### Permission

Internet, status jaringan, akses penyimpanan (termasuk `MANAGE_EXTERNAL_STORAGE` untuk menyimpan ke `RHDOWN`), tampil di atas aplikasi lain (untuk bubble), foreground service, boot selesai, notifikasi, dan abaikan optimasi baterai (agar download di background tidak mudah dihentikan).

### Kebutuhan

- Android 6.0 (API 23) atau lebih baru

### Build dengan AIDE

1. Clone repository ini, atau unduh sebagai ZIP lalu ekstrak.
2. Buka folder project di AIDE.
3. Source ada di `app/src/main/java/app/rh/downloader/`. Layout dan UI web ada di `app/src/main/res/` dan `app/src/main/assets/`.
4. Build lalu pasang di perangkat.

Ikon aplikasi berupa vector drawable: `app/src/main/res/drawable/ic_launcher.xml`.

### File utama

- `MainActivity`: layar utama (UI WebView)
- `WebAppInterface`: jembatan antara UI web dan Java
- `BackgroundDownloadService`: antrean download, notifikasi, dan bubble mengambang
- `DownloadBackend`: logika download TikTok
- `YoutubeBackend`: logika download YouTube (MP3/MP4), pengambilan auth key, dan polling konversi/progres
- `MediaManager`: daftar dan filter file hasil download
- `StorageHelper`: folder `RHDOWN` dan akses penyimpanan
- `HttpUtil`: helper HTTP
- `BootReceiver`: menyalakan ulang service setelah boot atau update
- `AppFileProvider`: berbagi file hasil download

### Penanganan key YouTube

Request YouTube memakai API key yang diambil saat runtime dari halaman `yt-to-mp3.net`, tidak disimpan di kode. Jika key ditolak, aplikasi mengambil key baru dan mencoba sekali lagi. Jika situs mengubah tata letak halaman, domain, atau API-nya, mekanisme ini bisa rusak.

### Lisensi

MIT. Lihat [LICENSE](LICENSE).

---

<a id="contact"></a>

## Contact

Developer: Reyhan Ardiansah. For questions or bug reports, please open a GitHub Issue.

Pengembang: Reyhan Ardiansah. Untuk pertanyaan atau laporan bug, silakan buka GitHub Issue.

[Back to top / Kembali ke atas](#rh-downloader)
