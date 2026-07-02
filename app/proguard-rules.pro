# Readium uses reflection/serialization in places; keep its public API surface.
-keep class org.readium.** { *; }
-dontwarn org.readium.**
