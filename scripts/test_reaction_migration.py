"""Exercise exactly the v1->v2 SQL with a populated historical schema."""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ReactionMigrationTest(unittest.TestCase):
    def test_additive_migration_preserves_all_old_records(self):
        source = (ROOT / "app/src/main/java/com/rteats/calendarik/data/Migrations.kt").read_text()
        match = re.search(r'"""(\s*CREATE TABLE IF NOT EXISTS daily_reactions.*?\))\s*"""', source, re.S)
        self.assertIsNotNone(match, "Migration SQL missing or changed")
        with sqlite3.connect(":memory:") as connection:
            connection.executescript("""
                CREATE TABLE calendar_events (
                  id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL,
                  notes TEXT NOT NULL, startsAtMillis INTEGER NOT NULL,
                  endsAtMillis INTEGER NOT NULL, authorMask INTEGER NOT NULL,
                  lastEditor INTEGER NOT NULL, revision INTEGER NOT NULL,
                  createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL,
                  deleted INTEGER NOT NULL, payloadVersion INTEGER NOT NULL
                );
                INSERT INTO calendar_events VALUES
                    ('old1','Private','',100,200,1,1,7,1,80,0,1),
                    ('old2','Shared','',200,300,3,2,8,1,90,1,1);
            """)
            connection.execute(match.group(1))
            records = connection.execute(
                "SELECT id,title,authorMask,revision,deleted FROM calendar_events ORDER BY id"
            ).fetchall()
            self.assertEqual([
                ("old1", "Private", 1, 7, 0),
                ("old2", "Shared", 3, 8, 1),
            ], records)
            connection.execute(
                """INSERT INTO daily_reactions
                (dateEpochDay,authorId,redHearts,pinkHearts,dayEmoji,revision,updatedAtMillis)
                VALUES (123,1,0,0,NULL,0,0)"""
            )
            connection.execute("UPDATE daily_reactions SET redHearts=redHearts+1 WHERE authorId=1")
            self.assertEqual(1, connection.execute("SELECT redHearts FROM daily_reactions").fetchone()[0])

    def test_firebase_cloud_restricted_to_known_users(self):
        rules = (ROOT / "firestore.rules").read_text()
        self.assertIn("approvedUsers", rules)
        self.assertIn("approved()", rules)
        self.assertIn("request.auth.uid == writerUid", rules)
        self.assertIn("allow read, write: if false", rules)
        self.assertNotIn("allow read, write: if true", rules)

    def test_google_config_matches_installed_package(self):
        import json
        conf = json.loads((ROOT / "app/google-services.json").read_text())
        self.assertEqual("calendarik-bff31", conf["project_info"]["project_id"])
        client_packages = [
            client["client_info"]["android_client_info"]["package_name"] for client in conf["client"]
        ]
        self.assertIn("com.rteats.calendarik", client_packages)


if __name__ == "__main__":
    unittest.main()

