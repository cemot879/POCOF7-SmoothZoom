package com.ashitu.pocof7smoothzoom;

import android.util.Log;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class SmoothZoomHook implements IXposedHookLoadPackage {

    private static final String TAG = "POCOF7SmoothZoom";

    // Lower = smoother/slower, higher = more responsive.
    private static final float ALPHA = 0.22f;

    // If no pinch update arrives for this duration,
    // the next update starts a fresh smoothing sequence.
    private static final long RESET_AFTER_MS = 180L;

    private static final ThreadLocal<Object> ACTIVE_SCALE =
            new ThreadLocal<>();

    private static final Map<Object, State> STATES =
            Collections.synchronizedMap(
                    new WeakHashMap<Object, State>()
            );

    private static final class State {
        float value;
        long lastNs;
        boolean initialized;
    }

    @Override
    public void handleLoadPackage(
            final XC_LoadPackage.LoadPackageParam lpparam)
            throws Throwable {

        if (!"com.android.camera".equals(lpparam.packageName)) {
            return;
        }

        try {
            final ClassLoader cl = lpparam.classLoader;

            // DEX Lk9/k; -> Java k9.k
            final Class<?> zoomManager =
                    Class.forName("k9.k", false, cl);

            // DEX LL8/i; -> Java L8.i
            final Class<?> scaleEvent =
                    Class.forName("L8.i", false, cl);

            log("Camera classes found: k9.k / L8.i");

            /*
             * onScale(L8.i)
             *
             * Marks the synchronous call stack as a pinch update.
             */
            XposedHelpers.findAndHookMethod(
                    zoomManager,
                    "onScale",
                    scaleEvent,
                    new XC_MethodHook() {

                        @Override
                        protected void beforeHookedMethod(
                                MethodHookParam param) {

                            ACTIVE_SCALE.set(param.thisObject);
                        }

                        @Override
                        protected void afterHookedMethod(
                                MethodHookParam param) {

                            ACTIVE_SCALE.remove();
                        }
                    });

            log("HOOKED: k9.k.onScale(L8.i)");

            /*
             * y0(float,int)
             *
             * This is the zoom target path reached synchronously
             * from onScale().
             */
            XposedHelpers.findAndHookMethod(
                    zoomManager,
                    "y0",
                    float.class,
                    int.class,
                    new XC_MethodHook() {

                        @Override
                        protected void beforeHookedMethod(
                                MethodHookParam param) {

                            // Only modify y0() when it is called
                            // synchronously from onScale().
                            if (ACTIVE_SCALE.get()
                                    != param.thisObject) {
                                return;
                            }

                            final float target =
                                    ((Float) param.args[0]).floatValue();

                            if (!Float.isFinite(target)
                                    || target <= 0.0f) {
                                return;
                            }

                            final long now =
                                    System.nanoTime();

                            State state =
                                    STATES.get(param.thisObject);

                            if (state == null) {
                                state = new State();
                                STATES.put(
                                        param.thisObject,
                                        state
                                );
                            }

                            /*
                             * Start a new smoothing sequence if:
                             * - this is the first update, or
                             * - the previous update was idle
                             *   for more than RESET_AFTER_MS.
                             */
                            if (!state.initialized
                                    || (now - state.lastNs)
                                    > RESET_AFTER_MS * 1_000_000L) {

                                state.value = target;
                                state.initialized = true;

                            } else {

                                // Exponential low-pass filter.
                                state.value +=
                                        (target - state.value)
                                                * ALPHA;
                            }

                            state.lastNs = now;

                            float out = state.value;

                            /*
                             * Snap when sufficiently close to
                             * the target.
                             */
                            if (Math.abs(target - out) < 0.008f) {
                                out = target;
                                state.value = target;
                            }

                            param.args[0] =
                                    Float.valueOf(out);
                        }
                    });

            log("HOOKED: k9.k.y0(float,int)");

            log("HOOK SUCCESS: Smooth Zoom active");

        } catch (Throwable t) {

            log("HOOK FAILED: "
                    + Log.getStackTraceString(t));
        }
    }

    private static void log(String msg) {

        try {
            XposedBridge.log(
                    TAG + ": " + msg
            );
        } catch (Throwable ignored) {

            Log.d(TAG, msg);
        }
    }
}
