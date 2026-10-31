import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import subprocess
import unittest
import zipfile
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("screen_build", Path(__file__).with_name("screens.py"))
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class CompatibilityTest(unittest.TestCase):
    def test_saved_sources_have_hash_and_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            out = root / 'output'
            out.mkdir()
            (root / 'Native.kt').write_text('native')
            (root / 'Screen.kt').write_text('screen')
            baseline = {'nativeFiles': {'Native.kt': 'a'}, 'screenFiles': {'Screen.kt': 'b'}}
            build.save_baseline_sources(root, out, baseline)
            with zipfile.ZipFile(out / 'baseline-sources.zip') as archive:
                self.assertEqual(archive.namelist(), ['Native.kt', 'Screen.kt'])
                self.assertEqual(archive.read('Screen.kt'), b'screen')
            self.assertEqual(baseline['sourcesSha256'], hashlib.sha256((out / 'baseline-sources.zip').read_bytes()).hexdigest())
            with self.assertRaises(FileExistsError):
                build.save_baseline_sources(root, out, baseline)

    def test_source_archive_rejects_paths_outside_project(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            with self.assertRaises(ValueError):
                build.save_baseline_sources(root, root, {'nativeFiles': {'../outside': 'x'}, 'screenFiles': {}})

    def test_patch_baseline_requires_its_unaltered_source_archive(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            baseline = {'format': 3, 'nativeFiles': {}, 'screenFiles': {}}
            build.save_baseline_sources(root, root, baseline)
            self.assertEqual(build.load_baseline(root / 'baseline.json'), baseline)
            (root / 'baseline-sources.zip').write_bytes(b'altered')
            with self.assertRaisesRegex(ValueError, 'missing or altered'):
                build.load_baseline(root / 'baseline.json')
            (root / 'baseline-sources.zip').unlink()
            with self.assertRaisesRegex(ValueError, 'missing or altered'):
                build.load_baseline(root / 'baseline.json')
            (root / 'baseline.json').write_text(json.dumps({'format': 3}))
            self.assertEqual(build.load_baseline(root / 'baseline.json'), {'format': 3})

    def test_recovery_requires_exact_original_apk_and_preserves_current_sources(self):
        for rebuilt in (b'original apk', b'wrong apk'):
            with self.subTest(rebuilt=rebuilt), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                (root / 'Current.kt').write_text('uncommitted changes')
                original = root / 'original.apk'
                original.write_bytes(b'original apk')
                out = root / 'recovered'
                out.mkdir()

                def create_checkout(*args):
                    checkout = Path(args[-2])
                    (checkout / 'tools').mkdir(parents=True)
                    (checkout / 'tools/screens.py').write_text('historical builder')
                    (checkout / 'Native.kt').write_text('old native')

                def build_candidate(args, **kwargs):
                    candidate = Path(args[-1])
                    candidate.mkdir()
                    (candidate / 'screen-base.apk').write_bytes(rebuilt)
                    (candidate / 'baseline.json').write_text(json.dumps({
                        'format': 3, 'apkSha256': hashlib.sha256(rebuilt).hexdigest(),
                        'nativeFiles': {'Native.kt': 'a'}, 'screenFiles': {}}))

                with patch.object(build, 'ROOT', root), patch.object(build, 'run', side_effect=create_checkout), \
                     patch.object(build.subprocess, 'check_output', return_value='a' * 40), \
                     patch.object(build.subprocess, 'run', side_effect=build_candidate):
                    if rebuilt == b'original apk':
                        build.recover_baseline('release-v1', original, out)
                        result = json.loads((out / 'baseline.json').read_text())
                        self.assertEqual(result['recoveredFromCommit'], 'a' * 40)
                        self.assertTrue((out / 'baseline-sources.zip').is_file())
                    else:
                        with self.assertRaisesRegex(ValueError, 'differs'):
                            build.recover_baseline('release-v1', original, out)
                        self.assertEqual(list(out.iterdir()), [])
                self.assertEqual((root / 'Current.kt').read_text(), 'uncommitted changes')

    def test_recovery_rejects_missing_or_option_like_ref(self):
        for ref in ('', '--help'):
            with self.assertRaises(ValueError):
                build.recover_baseline(ref, Path('unused.apk'), Path('unused'))

    def test_recovery_uses_real_detached_git_worktree(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'tools').mkdir()
            (root / 'host.txt').write_text('released native')
            (root / 'screen.kt').write_text('released screen')
            (root / 'tools/screens.py').write_text('''import sys,json,hashlib
from pathlib import Path
out=Path(sys.argv[sys.argv.index('--out')+1]); out.mkdir(parents=True)
(out/'screen-base.apk').write_bytes(b'original apk')
(out/'baseline.json').write_text(json.dumps({'format':3,'apkSha256':hashlib.sha256(b'original apk').hexdigest(),
'nativeFiles':{'host.txt':'hash'},'screenFiles':{'screen.kt':'hash'}}))
''')
            def git(*args):
                return subprocess.check_output(['git', *args], cwd=root, text=True, stderr=subprocess.PIPE).strip()
            git('init')
            git('add', '.')
            git('-c', 'user.name=Baseline Test', '-c', 'user.email=baseline@example.invalid', 'commit', '-m', 'released base')
            ref = git('rev-parse', 'HEAD')
            (root / 'host.txt').write_text('current uncommitted changes')
            original = root / 'original.apk'
            original.write_bytes(b'original apk')
            out = root / 'result'
            out.mkdir()
            with patch.object(build, 'ROOT', root):
                build.recover_baseline(ref, original, out)
            self.assertEqual(git('rev-parse', 'HEAD'), ref)
            self.assertEqual((root / 'host.txt').read_text(), 'current uncommitted changes')
            with zipfile.ZipFile(out / 'baseline-sources.zip') as archive:
                self.assertEqual(archive.read('host.txt'), b'released native')

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
