package com.ashitu.pocof7smoothzoom;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.util.Log;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * POCO F7 / Xiaomi Camera 6.7.000070.0 LSPosed module.
 *
 * Features:
 *  - Smooth pinch zoom using a low-pass filter.
 *  - Adds 0.6x / 1x / 2x / 5x / 10x tap targets directly over Camera UI.
 *  - Tap targets animate smoothly; no press-and-drag required.
 *  - Max zoom, smoothness and animation duration are read from module prefs.
 */
public final class SmoothZoomHook implements IXposedHookLoadPackage {
    private static final String TAG = "POCOF7SmoothZoom";
    private static final String CAMERA = "com.android.camera";
    private static final String MODULE = "com.ashitu.pocof7smoothzoom";
    private static final String OVERLAY_TAG = "poco_f7_smooth_zoom_buttons";

    private static final float DEFAULT_ALPHA = 0.22f;
    private static final long RESET_AFTER_MS = 180L;
    private static final float[] PRESETS = {0.6f, 1f, 2f, 5f, 10f};

    private static final ThreadLocal<Object> ACTIVE_SCALE = new ThreadLocal<>();
    private static final Map<Object, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<Object, State>());

    private static volatile Object lastZoomManager;
    private static volatile Application cameraApplication;
    private static volatile SharedPreferences prefs;

    private static final class State {
        float value = 1f;
        long lastNs;
        boolean initialized;
        int mode;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!CAMERA.equals(lpparam.packageName)) return;

        try {
            final ClassLoader cl = lpparam.classLoader;
            final Class<?> zoomManager = Class.forName("Lk9.k", false, cl);
            final Class<?> scaleEvent = Class.forName("LL8.i", false, cl);

            // Load module preferences from the module package.
            try {
                cameraApplication = (Application) XposedHelpers.callStaticMethod(
                        Class.forName("android.app.ActivityThread", false, cl),
                        "currentApplication");
                if (cameraApplication != null) loadPrefs(cameraApplication);
            } catch (Throwable ignored) {
                // Preferences are optional; defaults remain active.
            }

            // Keep the latest zoom manager instance.
            XposedHelpers.findAndHookConstructor(zoomManager, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    lastZoomManager = param.thisObject;
                    State state = STATES.get(param.thisObject);
                    if (state == null) STATES.put(param.thisObject, new State());
                }
            });

            XposedHelpers.findAndHookMethod(zoomManager, "onScale", scaleEvent,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            ACTIVE_SCALE.set(param.thisObject);
                        }
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            ACTIVE_SCALE.remove();
                        }
                    });

            XposedHelpers.findAndHookMethod(zoomManager, "onScaleEnd", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    State state = STATES.get(param.thisObject);
                    if (state != null) state.lastNs = 0L;
                }
            });

            XposedHelpers.findAndHookMethod(zoomManager, "y0", float.class, int.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            final float target = ((Float) param.args[0]).floatValue();
                            if (!Float.isFinite(target) || target <= 0f) return;

                            State state = STATES.get(param.thisObject);
                            if (state == null) {
                                state = new State();
                                STATES.put(param.thisObject, state);
                            }
                            state.mode = ((Integer) param.args[1]).intValue();

                            // Keep the current zoom known even for button/programmatic changes.
                            if (ACTIVE_SCALE.get() != param.thisObject) {
                                state.value = target;
                                state.initialized = true;
                                return;
                            }

                            final long now = System.nanoTime();
                            if (!state.initialized || (now - state.lastNs) > RESET_AFTER_MS * 1_000_000L) {
                                state.value = target;
                                state.initialized = true;
                            } else {
                                float alpha = getAlpha();
                                state.value += (target - state.value) * alpha;
                            }
                            state.lastNs = now;

                            float out = state.value;
                            if (Math.abs(target - out) < 0.008f) {
                                out = target;
                                state.value = target;
                            }
                            param.args[0] = Float.valueOf(out);
                        }
                    });

            hookActivityLifecycle(cl);
            log("HOOKED: pinch smoothing + tap zoom UI");
        } catch (Throwable t) {
            log("HOOK FAILED: " + Log.getStackTraceString(t));
        }
    }

    private static void hookActivityLifecycle(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof Application)) return;
                    final Application app = (Application) param.thisObject;
                    if (!CAMERA.equals(app.getPackageName())) return;
                    cameraApplication = app;
                    loadPrefs(app);
                    app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                        @Override public void onActivityResumed(Activity activity) {
                            if (activity != null && CAMERA.equals(activity.getPackageName())) {
                                activity.getWindow().getDecorView().postDelayed(
                                        () -> addZoomButtons(activity), 250L);
                            }
                        }
                        @Override public void onActivityDestroyed(Activity activity) {
                            removeZoomButtons(activity);
                        }
                        @Override public void onActivityCreated(Activity a, Bundle b) {}
                        @Override public void onActivityStarted(Activity a) {}
                        @Override public void onActivityPaused(Activity a) {}
                        @Override public void onActivityStopped(Activity a) {}
                        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                    });
                }
            });
        } catch (Throwable t) {
            log("LIFECYCLE HOOK FAILED: " + Log.getStackTraceString(t));
        }
    }

    private static void loadPrefs(Context cameraContext) {
        try {
            Context module = cameraContext.createPackageContext(
                    MODULE, Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
            prefs = module.getSharedPreferences("settings", Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            prefs = null;
        }
    }

    private static float getAlpha() {
        if (prefs == null) return DEFAULT_ALPHA;
        return prefs.getFloat("alpha", DEFAULT_ALPHA);
    }

    private static float getMaxZoom() {
        if (prefs == null) return 20f;
        return Math.max(2f, prefs.getFloat("max_zoom", 20f));
    }

    private static long getDuration() {
        if (prefs == null) return 450L;
        return Math.max(120L, Math.min(1500L, prefs.getLong("duration", 450L)));
    }

    private static void addZoomButtons(final Activity activity) {
        if (activity == null) return;
        final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        if (decor.findViewWithTag(OVERLAY_TAG) != null) return;

        final LinearLayout row = new LinearLayout(activity);
        row.setTag(OVERLAY_TAG);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        row.setBackgroundColor(Color.argb(90, 0, 0, 0));

        for (final float preset : PRESETS) {
            final Button b = new Button(activity);
            b.setText(formatZoom(preset));
            b.setTextSize(12f);
            b.setTextColor(Color.WHITE);
            b.setAllCaps(false);
            b.setMinWidth(0);
            b.setMinHeight(0);
            b.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
            b.setOnClickListener(v -> {
                Object manager = lastZoomManager;
                if (manager != null) animateTo(manager, Math.min(preset, getMaxZoom()));
            });
            row.addView(b, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 42)));
        }

        TextView label = new TextView(activity);
        label.setText("  ");
        row.addView(label);

        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 50));
        if (lp instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams flp =
                    (android.widget.FrameLayout.LayoutParams) lp;
            flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            flp.bottomMargin = dp(activity, 105);
        }
        decor.addView(row, lp);
    }

    private static void removeZoomButtons(Activity activity) {
        if (activity == null) return;
        try {
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            View v = decor.findViewWithTag(OVERLAY_TAG);
            if (v != null) decor.removeView(v);
        } catch (Throwable ignored) {}
    }

    private static void animateTo(final Object manager, final float requestedTarget) {
        try {
            State state = STATES.get(manager);
            if (state == null) {
                state = new State();
                STATES.put(manager, state);
            }
            final float start = state.initialized ? state.value : 1f;
            final float target = Math.max(0.6f, Math.min(requestedTarget, getMaxZoom()));
            final int mode = state.mode;
            final ValueAnimator animator = ValueAnimator.ofFloat(start, target);
            animator.setDuration(getDuration());
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                float value = (Float) a.getAnimatedValue();
                try {
                    XposedHelpers.callMethod(manager, "y0", Float.valueOf(value), Integer.valueOf(mode));
                    State s = STATES.get(manager);
                    if (s != null) {
                        s.value = value;
                        s.initialized = true;
                    }
                } catch (Throwable t) {
                    animator.cancel();
                    log("BUTTON ZOOM FAILED: " + Log.getStackTraceString(t));
                }
            });
            animator.start();
        } catch (Throwable t) {
            log("ANIMATE FAILED: " + Log.getStackTraceString(t));
        }
    }

    private static String formatZoom(float z) {
        if (z == (int) z) return ((int) z) + "x";
        return String.format(java.util.Locale.US, "%.1fx", z);
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    private static void log(String msg) {
        try { XposedBridge.log(TAG + ": " + msg); }
        catch (Throwable ignored) { Log.d(TAG, msg); }
    }
}
