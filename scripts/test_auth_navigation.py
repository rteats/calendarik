"""Prevent a visual redesign from silently disconnecting auth and sync.

Keep this structural test alongside the Android tests. It verifies that the
actual app entry point wires pairing to its navigation surfaces and writes.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/rteats/calendarik/MainActivity.kt"


class AuthRouteRegressionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = MAIN.read_text(encoding="utf-8")

    def test_entry_points_are_reachable(self):
        self.assertIn('text = { Text(if (accountUid == null) "Вход и синхронизация"', self.source)
        self.assertIn('Text(if (accountUid == null) "Войти или зарегистрироваться"', self.source)
        self.assertIn('Text("Войти или зарегистрироваться")', self.source)
        self.assertIn("PairingDialog(", self.source)
        self.assertIn("onPairChanged = ", self.source)

    def test_account_owns_identity_and_writes_are_synced(self):
        self.assertIn("val activeIdentity = paired?.role ?: identity", self.source)
        self.assertIn("enabled = paired == null", self.source)
        self.assertIn("editRepository.save(record)", self.source)
        self.assertIn("editRepository.save(deleteEvent(old, activeIdentity))", self.source)
        self.assertIn("editRepository.heart(", self.source)
        self.assertIn("editRepository.mood(", self.source)
        self.assertIn("syncNow()", self.source)

    def test_foreground_and_deferred_sync_still_connected(self):
        self.assertIn("CloudSync.schedulePeriodic(context)", self.source)
        self.assertIn("CloudSync.requestSoon(context)", self.source)
        self.assertIn(".addSnapshotListener", self.source)
        self.assertIn("cloudSync.resolve(", self.source)

    def test_no_misleading_local_only_message(self):
        self.assertNotIn("синхронизация пока не включены", self.source)
        self.assertNotIn("Пока это тестовый выбор, а не защищённое подключение", self.source)


if __name__ == "__main__":
    unittest.main()
