# AGP's generated rules retain manifest components and their framework constructors.
# The private MessagePort uses normal Java calls, not reflective method-name dispatch.
# Preserve runtime annotations and any explicitly annotated future JS methods only.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# MessagePort callback overrides are framework entry points; fields can still be renamed.
-keepclassmembers class dev.motion2video.app.** extends android.webkit.WebMessagePort$WebMessageCallback {
    public void onMessage(android.webkit.WebMessagePort, android.webkit.WebMessage);
}
