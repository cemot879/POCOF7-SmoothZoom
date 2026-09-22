# POCO F7 Smooth Zoom — LSPosed

Target verified from the supplied `com.android.camera` APK:

- Camera package: `com.android.camera`
- Camera version: `6.7.000070.0`
- Device target: POCO F7
- HyperOS: 3.0.301.0
- Zoom path found in the APK:
  - `Lk9/k; implements Lj9/a;`
  - `Lk9/k;.onScale(LL8/i;)Z`
  - `Lk9/k;.y0(float,int)Z`

## What it does

The module applies an exponential low-pass filter only while `y0(float,int)` is
called synchronously from `onScale(LL8/i)`. This means it targets pinch-to-zoom
updates instead of globally slowing every camera zoom operation.

Default smoothing:

`ALPHA = 0.22`

- 0.15–0.20: smoother, slower response
- 0.22–0.28: balanced
- 0.30–0.35: more responsive

## Build

Open this folder in Android Studio and build the `app` module.

The LSPosed API is compile-only:

`de.robv.android.xposed:api:82`

## Install

1. Build/install the APK.
2. Enable it in LSPosed.
3. Scope it to `com.android.camera`.
4. Force-stop Camera or reboot.
5. Test pinch-to-zoom in Camera.

## Safety / rollback

If Camera force-closes, disable the module in LSPosed. The hook is deliberately
limited to the exact package and exact method signatures found in the supplied APK.

This is a source build, not a precompiled APK.
