import subprocess
from pathlib import Path
import sys
import unittest


class MigrationPolicyTest(unittest.TestCase):
    def test_safe_configuration(self):
        root = Path(__file__).resolve().parents[1]
        subprocess.run([sys.executable, str(root / "scripts/check_migration_policy.py")],
                       check=True, cwd=root)


if __name__ == "__main__":
    unittest.main()
