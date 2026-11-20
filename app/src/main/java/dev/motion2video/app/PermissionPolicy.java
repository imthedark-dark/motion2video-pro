package dev.motion2video.app;
/** API bounds are shared by initial rendering and saving a completed private result. */
public final class PermissionPolicy {
 public static boolean legacyStorage(int sdk){return sdk>=26&&sdk<=28;}
 public static boolean notifications(int sdk){return sdk>=33;}
}
