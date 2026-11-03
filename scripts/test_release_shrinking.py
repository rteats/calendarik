"""Regression checks for the signed APK optimization pipeline.

CI also runs the real R8 shrink task (without packaging an APK).
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ReleaseShrinkingPolicyTests(unittest.TestCase):
    def test_release_uses_both_shrinkers(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn('providers.gradleProperty("OPTIMIZE_RELEASE")', gradle)
        self.assertIn("isMinifyEnabled = optimizeRelease", gradle)
        self.assertIn("isShrinkResources = optimizeRelease", gradle)
        self.assertIn('getDefaultProguardFile("proguard-android-optimize.txt")', gradle)

    def test_signed_main_build_turns_on_shrinking(self):
        main = (ROOT / ".github/workflows/main.yml").read_text()
        self.assertIn("assembleRelease", main)
        self.assertIn("-POPTIMIZE_RELEASE=true", main)
        self.assertIn("Report APK size compared with previous release", main)

    def test_pull_requests_check_r8_without_building_apk(self):
        pr = (ROOT / ".github/workflows/pr.yml").read_text()
        self.assertIn("minifyReleaseWithR8", pr)
        self.assertNotIn("assembleRelease", pr)
        self.assertNotIn("assembleDebug", pr)


if __name__ == "__main__":
    unittest.main()
