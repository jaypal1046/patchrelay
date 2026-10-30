import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("screen_build", Path(__file__).with_name("screens.py"))
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class CompatibilityTest(unittest.TestCase):
    def test_rejects_modified_added_and_removed_native_files(self):
        original = {"sdk/Renderer.kt": "a", "demo/Manifest.xml": "b"}
        build.check_native(original, original.copy())
        for changed in ({"sdk/Renderer.kt": "c", "demo/Manifest.xml": "b"},
                        {**original, "demo/res/new.xml": "c"}, {"sdk/Renderer.kt": "a"}):
            with self.assertRaises(ValueError):
                build.check_native(original, changed)

    def test_fingerprint_detects_content_changes_not_just_names(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "native").mkdir()
            file = root / "native/Screen.kt"
            file.write_text("before")
            first = build.fingerprint(root, ("native",))
            file.write_text("after")
            second = build.fingerprint(root, ("native",))
            self.assertNotEqual(first, second)
            self.assertEqual(list(second), ["native/Screen.kt"])

    def test_source_line_endings_do_not_change_compatibility(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            file = root / "Screen.kt"
            file.write_bytes(b"fun screen() = 1\r\n")
            windows = build.fingerprint(root, ("Screen.kt",))
            file.write_bytes(b"fun screen() = 1\n")
            self.assertEqual(windows, build.fingerprint(root, ("Screen.kt",)))


if __name__ == "__main__":
    unittest.main()
