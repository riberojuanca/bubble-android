"""Check automatic release numbering without creating repository tags."""

import importlib.util
from pathlib import Path
import unittest


spec = importlib.util.spec_from_file_location(
    "release_version", Path(__file__).with_name("release-version.py")
)
release_version = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_version)


class ReleaseVersionTest(unittest.TestCase):
    def test_first_release(self):
        self.assertEqual(release_version.choose_version([], []), "0.1.0")

    def test_next_patch(self):
        self.assertEqual(release_version.choose_version(["v0.1.0"], []), "0.1.1")

    def test_numeric_order_and_unrelated_tags(self):
        tags = ["v0.1.9", "v0.1.10", "backup", "v2.0.0-beta.1"]
        self.assertEqual(release_version.choose_version(tags, []), "0.1.11")

    def test_retry_keeps_the_original_version(self):
        tags = ["v0.1.0", "v0.1.1", "v0.1.2"]
        self.assertEqual(release_version.choose_version(tags, ["v0.1.1"]), "0.1.1")

    def test_patch_rollover_preserves_android_order(self):
        self.assertEqual(release_version.choose_version(["v0.1.99"], []), "0.2.0")

    def test_minor_rollover_preserves_android_order(self):
        self.assertEqual(release_version.choose_version(["v0.99.99"], []), "1.0.0")

    def test_invalid_android_components_fail(self):
        with self.assertRaises(ValueError):
            release_version.choose_version(["v0.1.100"], [])

    def test_android_version_limit_fails(self):
        with self.assertRaises(ValueError):
            release_version.choose_version(["v209999.99.99"], [])


if __name__ == "__main__":
    unittest.main()
