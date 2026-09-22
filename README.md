# POCO F7 Smooth Zoom — LSPosed v2

Target: `com.android.camera` version `6.7.000070.0` on POCO F7 / HyperOS 3.

## Fitur

- Smooth pinch-to-zoom.
- Tombol zoom langsung di UI kamera: **0.6x / 1x / 2x / 5x / 10x**.
- Sekali tap tombol menjalankan animasi smooth; tidak perlu menahan/geser.
- Bisa tap target lain saat animasi berjalan.
- Pengaturan APK yang sekarang bisa dibuka dari launcher:
  - **Smoothness** 0.15–0.50.
  - **Max Zoom** 2x–100x.
  - **Durasi animasi** 120–1500 ms.

## Build

Buka project di Android Studio dan build `app`.

Dependency LSPosed/Xposed API tetap compile-only:
`de.robv.android.xposed:api:82`

## Install

1. Build/install APK.
2. Aktifkan modul di LSPosed.
3. Scope ke `com.android.camera`.
4. Force-stop Camera atau reboot.
5. Buka Camera. Baris tombol zoom akan muncul di bagian bawah layar.

## Catatan

Tombol 5x/10x mengirim target zoom melalui method `Lk9.k.y0(float,int)` yang sudah dipakai oleh versi sebelumnya. `Max Zoom` memungkinkan target lebih tinggi, tetapi batas nyata tetap bergantung pada implementasi digital zoom Camera.
