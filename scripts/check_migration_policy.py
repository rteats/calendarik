"""Fail CI if a change makes existing user data silently disposable."""
from pathlib import Path

project = Path(__file__).resolve().parents[1]
db = (project / "app/src/main/java/com/rteats/calendarik/data/CalendarDatabase.kt").read_text()
gradle = (project / "app/build.gradle.kts").read_text()
import re
assert not re.search(r"(?m)^\s*\.?fallbackToDestructiveMigration\s*\(", db), "Destructive migrations are prohibited"
assert 'exportSchema = true' in db, "Room schema export is mandatory"
assert "room.schemaLocation" in gradle, "Room schema snapshots must be exported"
assert "version = 5" in db, "Expected explicit Room v5 schema"
assert "addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)" in db, "Missing explicit v1->2->3->4->5 migration chain"
assert (project / "app/src/main/java/com/rteats/calendarik/data/Migrations.kt").exists(), "v1->v2 migration missing"
assert (project / "app/src/main/java/com/rteats/calendarik/data/Migration3.kt").exists(), "v2->v3 migration missing"
assert (project / "app/src/main/java/com/rteats/calendarik/data/Migration4.kt").exists(), "v3->v4 migration missing"
assert (project / "app/src/main/java/com/rteats/calendarik/data/Migration5.kt").exists(), "v4->v5 migration missing"
assert 'applicationId = "com.rteats.calendarik"' in gradle, "Changing package ID breaks in-place updates"
print("Migration and package identity policy OK")
