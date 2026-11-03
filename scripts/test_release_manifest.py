import unittest
from release_manifest import make_manifest


class ReleaseManifestTest(unittest.TestCase):
    def test_forward_release_pins_apk_to_commit(self):
        manifest = make_manifest(14, "2026.11.03.10", "a" * 40, 5, b"apk", {"versionCode": 13})
        self.assertEqual(f"https://github.com/rteats/calendarik/releases/download/build-14-{manifest['sha256'][:12]}/Calendarik.apk", manifest["apkUrl"])
        self.assertEqual(64, len(manifest["sha256"]))

    def test_rejects_schema_downgrade_and_old_workflow_reruns(self):
        for code, schema in [(12, 5), (13, 5), (14, 4)]:
            with self.assertRaises(ValueError):
                make_manifest(code, "2026.11.03.10", "a" * 40, schema, b"apk", {"versionCode": 13})

    def test_same_commit_publication_can_retry(self):
        previous = {"versionCode": 14, "sourceCommit": "a" * 40, "databaseVersion": 5}
        self.assertEqual(14, make_manifest(14, "2026.11.03.10", "a" * 40, 5, b"apk", previous)["versionCode"])
