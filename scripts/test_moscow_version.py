from datetime import datetime, timezone
import unittest
from moscow_version import next_version


class VersionTest(unittest.TestCase):
    def test_first_build(self):
        self.assertEqual("2026.11.03.1", next_version("", datetime(2026, 11, 3, 7, 0, tzinfo=timezone.utc)))

    def test_rollover(self):
        self.assertEqual("2026.11.04.1", next_version("Calendarik 2026.11.03.3", datetime(2026, 11, 3, 21, 0, tzinfo=timezone.utc)))

    def test_increment(self):
        self.assertEqual("2026.11.03.4", next_version("Calendarik 2026.11.03.3", datetime(2026, 11, 3, 9, 0, tzinfo=timezone.utc)))


if __name__ == "__main__":
    unittest.main()
