# AndroidX Room, Firebase and Glance bring their own R8 consumer rules.
# Keep this file narrow so release shrinking remains effective.
#
# Preserve names of persisted Room model fields. Room's schema and migrations
# must continue to work when the APK is optimized.
-keepclassmembers class com.rteats.calendarik.data.CalendarEvent {
    <fields>;
}
-keepclassmembers class com.rteats.calendarik.data.DailyReaction {
    <fields>;
}
