"""Test the exact additive SQLite logic used to upgrade historic heart counters."""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HeartUpgradeTest(unittest.TestCase):
    def test_v3_to_v4_is_additive_and_deterministic(self):
        source = (ROOT / "app/src/main/java/com/rteats/calendarik/data/Migration4.kt").read_text()
        alter = re.search(r'db.execSQL\("(ALTER TABLE daily_reactions ADD COLUMN[^"]+)"\)', source)
        update = re.search(r'"""(UPDATE daily_reactions SET heartChoice =.*?END)"""', source, re.S)
        self.assertIsNotNone(alter)
        self.assertIsNotNone(update)

        with sqlite3.connect(":memory:") as db:
            db.executescript("""
                CREATE TABLE calendar_events (id TEXT PRIMARY KEY, title TEXT, deleted INTEGER);
                INSERT INTO calendar_events VALUES ('unchanged', 'important', 1);
                CREATE TABLE sync_conflicts (eventId TEXT PRIMARY KEY, remoteJson TEXT);
                INSERT INTO sync_conflicts VALUES ('event-id', '{}');
                CREATE TABLE daily_reactions (
                    dateEpochDay INTEGER NOT NULL, authorId INTEGER NOT NULL,
                    redHearts INTEGER NOT NULL, pinkHearts INTEGER NOT NULL,
                    dayEmoji TEXT, revision INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(dateEpochDay, authorId)
                );
                INSERT INTO daily_reactions VALUES
                    (1,1,9,3,'🥰',5,100),
                    (1,2,2,7,'💩',6,101),
                    (2,1,3,3,NULL,7,102),
                    (2,2,0,0,NULL,8,103);
            """)
            db.execute(alter.group(1))
            db.execute(update.group(1))
            actual = db.execute("""
                SELECT dateEpochDay, authorId, redHearts, pinkHearts,
                    dayEmoji, revision, updatedAtMillis, heartChoice
                FROM daily_reactions ORDER BY dateEpochDay, authorId
            """).fetchall()
            self.assertEqual([
                (1, 1, 9, 3, '🥰', 5, 100, 1),
                (1, 2, 2, 7, '💩', 6, 101, 2),
                (2, 1, 3, 3, None, 7, 102, 1),
                (2, 2, 0, 0, None, 8, 103, 0),
            ], actual)
            self.assertEqual(('unchanged', 'important', 1),
                             db.execute("SELECT * FROM calendar_events").fetchone())
            self.assertEqual(1, db.execute("SELECT count(*) FROM sync_conflicts").fetchone()[0])

    def test_network_diagnostics_cannot_log_raw_requests(self):
        source = (ROOT / "app/src/main/java/com/rteats/calendarik/diagnostics/NetworkDiagnostics.kt").read_text()
        self.assertNotIn("failure.message", source)
        self.assertNotIn("error.localizedMessage", source)
        self.assertIn("safeToken(operation", source)
        self.assertIn("512 * 1024", source)


if __name__ == "__main__":
    unittest.main()

