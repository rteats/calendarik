"""Validate the actual SQLite migration SQL for shared daily hearts."""
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class SharedHeartMigrationTest(unittest.TestCase):
    def test_v4_to_v5_adds_one_shared_row_without_erasing_old_moods(self):
        source = (ROOT / "app/src/main/java/com/rteats/calendarik/data/Migration5.kt").read_text()
        sql = re.search(r'"""(\s*CREATE TABLE IF NOT EXISTS shared_hearts.*?\))\s*"""', source, re.S)
        self.assertIsNotNone(sql)
        with sqlite3.connect(":memory:") as db:
            db.executescript("""
                CREATE TABLE daily_reactions (
                    dateEpochDay INTEGER NOT NULL,
                    authorId INTEGER NOT NULL,
                    dayEmoji TEXT,
                    heartChoice INTEGER NOT NULL,
                    PRIMARY KEY(dateEpochDay, authorId)
                );
                INSERT INTO daily_reactions VALUES
                    (42,1,'fa:face-smile',1),
                    (42,2,'fa:poo-storm',2);
            """)
            db.execute(sql.group(1))
            db.execute("INSERT INTO shared_hearts VALUES (42,2,1,2,100)")
            db.execute("UPDATE shared_hearts SET heartChoice=0,revision=2 WHERE dateEpochDay=42")
            self.assertEqual([(42, 0, 2, 2, 100)], db.execute(
                "SELECT * FROM shared_hearts").fetchall())
            self.assertEqual([(42, 1, "fa:face-smile", 1),
                              (42, 2, "fa:poo-storm", 2)],
                             db.execute("SELECT * FROM daily_reactions ORDER BY authorId").fetchall())
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute("INSERT INTO shared_hearts VALUES (42,1,3,1,101)")


if __name__ == "__main__":
    unittest.main()
