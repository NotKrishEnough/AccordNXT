# Add project specific ProGuard rules here.

-keepattributes SourceFile,LineNumberTable
-allowaccessmodification

# NewPipeExtractor bundles Rhino, which references optional Java SE
# scripting/beans APIs that are not present on Android.
-dontwarn java.beans.**
-dontwarn javax.script.**

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
