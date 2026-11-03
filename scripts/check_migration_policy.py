"""Fail CI if a change makes existing user data silently disposable."""
from pathlib import Path

project = Path(__file__).resolve().parents[1]
db = (project / "app/src/main/java/com/rteats/calendarik/data/CalendarDatabase.kt").read_text()
gradle = (project / "app/build.gradle.kts").read_text()
import re
assert not re.search(r"(?m)^\s*\.?fallbackToDestructiveMigration\s*\(", db), "Destructive migrations are prohibited"
assert 'exportSchema = true' in db, "Room schema export is mandatory"
assert "room.schemaLocation" in gradle, "Room schema snapshots must be exported"
assert "version = 2" in db, "Expected explicit Room v2 schema"
assert "addMigrations(MIGRATION_1_2)" in db, "Missing v1 to v2 migration registration"
assert (project / "app/src/main/java/com/rteats/calendarik/data/Migrations.kt").exists(), "Migration source missing"
assert 'applicationId = "com.rteats.calendarik"' in gradle, "Changing package ID breaks in-place updates"
print("Migration and package identity policy OK")
