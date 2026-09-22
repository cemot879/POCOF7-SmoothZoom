package com.ashitu.pocof7smoothzoom;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed hook for Xiaomi Camera 6.7.000070.0 on POCO F7 / HyperOS 3.
 *
 * Analysis of the supplied APK found:
 *   Lk9/k; implements Lj9/a;
 *   Lk9/k;.onScale(LL8/i;)Z
 *   Lk9/k;.y0(FI)Z
 *
 * onScale() calculates the target zoom and calls y0(float,int).
 * We only alter y0 while it is being reached from onScale(), so button
 * zoom / initialization / other programmatic changes are left alone.
 */
public final class SmoothZoomHook implements IXposedHookLoadPackage {
    private static final String TAG = "POCOF7SmoothZoom";

    // 0.18 = moderate smoothing. Lower = smoother/slower, higher = more responsive.
    // Suggested range: 0.15 - 0.35.
    private static final float ALPHA = 0.22f;

    // Ignore a new pinch sequence after this idle time.
    private static final long RESET_AFTER_MS = 180L;

    private static final ThreadLocal<Object> ACTIVE_SCALE = new ThreadLocal<>();

    private static final Map<Object, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<Object, State>());

    private static final class State {
        float value;
        long lastNs;
        boolean initialized;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam)
            throws Throwable {

        if (!"com.android.camera".equals(lpparam.packageName)) {
            return;
        }

        try {
            final ClassLoader cl = lpparam.classLoader;
            final Class<?> zoomManager = Class.forName("k9.k", false, cl);
            final Class<?> scaleEvent = Class.forName("L8.i", false, cl);

            // Marks the synchronous call stack as a pinch-zoom update.
            XposedHelpers.findAndHookMethod(
                    zoomManager,
                    "onScale",
                    scaleEvent,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            ACTIVE_SCALE.set(param.thisObject);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            ACTIVE_SCALE.remove();
                        }
                    });

            // Clears the smoothing state when the pinch ends.
            XposedHelpers.findAndHookMethod(
                    zoomManager,
                    "onScaleEnd",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            STATES.remove(param.thisObject);
                        }
                    });

            // y0(float,int) is the central zoom-target path reached by onScale().
            XposedHelpers.findAndHookMethod(
                    zoomManager,
                    "y0",
                    float.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (ACTIVE_SCALE.get() != param.thisObject) {
                                return;
                            }

                            final float target = ((Float) param.args[0]).floatValue();

                            if (!Float.isFinite(target) || target <= 0.0f) {
                                return;
                            }

                            final long now = System.nanoTime();
                            State state = STATES.get(param.thisObject);
                            if (state == null) {
                                state = new State();
                                STATES.put(param.thisObject, state);
                            }

                            if (!state.initialized
                                    || (now - state.lastNs) > RESET_AFTER_MS * 1_000_000L) {
                                state.value = target;
                                state.initialized = true;
                            } else {
                                // Exponential low-pass filter.
                                state.value += (target - state.value) * ALPHA;
                            }

                            state.lastNs = now;

                            // Snap when close enough to avoid tiny residual motion.
                            float out = state.value;
                            if (Math.abs(target - out) < 0.008f) {
                                out = target;
                                state.value = target;
                            }

                            param.args[0] = Float.valueOf(out);
                        }
                    });

            log("HOOKED: k9.k.onScale -> y0(float,int), Camera 6.7.000070.0");
        } catch (Throwable t) {
            log("HOOK FAILED: " + Log.getStackTraceString(t));
        }
    }

    private static void log(String msg) {
        try {
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
            Log.d(TAG, msg);
        }
    }
}
