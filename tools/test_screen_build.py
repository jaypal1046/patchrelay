import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("screen_build", Path(__file__).with_name("screens.py"))
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class CompatibilityTest(unittest.TestCase):
    def test_release_host_matches_snapshot_and_generated_config(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "native.kt").write_text("native host")
            native = build.fingerprint(root, ("native.kt",))
            digest = build.host_hash(native)
            config = root / "demo/build/generated/source/buildConfig/release/dev/patchrelay/screens/demo/BuildConfig.java"
            config.parent.mkdir(parents=True)
            (root / "demo/release-baseline.json").write_text(json.dumps({"nativeFiles": native, "hostSha256": digest}))
            with patch.object(build, "ROOT", root), patch.object(build, "NATIVE", ("native.kt",)):
                config.write_text('HOST_SHA256 = "' + digest + '";')
                build.check_release_host()
                config.write_text('HOST_SHA256 = "' + "0" * 64 + '";')
                with self.assertRaises(ValueError):
                    build.check_release_host()
                config.write_text('HOST_SHA256 = "' + digest + '";')
                (root / "native.kt").write_text("different host")
                with self.assertRaises(ValueError):
                    build.check_release_host()

    def test_host_hash_is_order_independent_and_content_bound(self):
        self.assertEqual(build.host_hash({"b": "2", "a": "1"}), build.host_hash({"a": "1", "b": "2"}))
        self.assertNotEqual(build.host_hash({"a": "1"}), build.host_hash({"a": "2"}))

    def test_missing_input_cannot_silently_disappear(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(ValueError):
                build.fingerprint(Path(folder), ("missing",))

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
