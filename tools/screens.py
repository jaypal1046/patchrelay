"""Build a native screen base or a compatible Kotlin-only screen patch."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent.parent
PROGRAM = ROOT / "screens/build/compileSync/js/main/productionExecutable/kotlin/PatchRelayScreens.js"
CONFIG = json.loads((ROOT / "runtime.json").read_text(encoding="utf-8"))
if CONFIG.get("format") != 1 or CONFIG.get("screenApi") != 2:
    raise ValueError("Unsupported runtime contract")
NATIVE = tuple(CONFIG["nativeInputs"])


def host_hash(files):
    return hashlib.sha256(json.dumps(files, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def fingerprint(root, paths):
    files = []
    for relative in paths:
        path = root / relative
        if not path.exists():
            raise ValueError("Missing native/source input: " + str(path))
        files.extend([path] if path.is_file() else sorted(p for p in path.rglob("*") if p.is_file()))
    result = {}
    for path in files:
        data = path.read_bytes()
        if any(path.name.endswith(suffix) for suffix in CONFIG["textExtensions"]):
            data = data.replace(b"\r\n", b"\n")
        result[path.relative_to(root).as_posix()] = hashlib.sha256(data).hexdigest()
    return result


def check_native(baseline, current):
    changed = sorted(path for path in set(baseline) | set(current) if baseline.get(path) != current.get(path))
    if changed:
        raise ValueError("Native host/compiler/assets changed; new base required: " + ", ".join(changed[:10]))


def run(*args):
    subprocess.run(list(map(str, args)), cwd=ROOT, check=True)


def save_baseline_sources(root, out, baseline):
    """Preserve declared inputs without including local credentials or signing keys."""
    with zipfile.ZipFile(out / "baseline-sources.zip", "x", zipfile.ZIP_DEFLATED) as archive:
        for relative in sorted(set(baseline["nativeFiles"]) | set(baseline["screenFiles"])):
            source = (root / relative).resolve()
            if not source.is_relative_to(root.resolve()):
                raise ValueError("Baseline input escapes project directory")
            archive.write(source, relative)
    baseline["sourcesSha256"] = hashlib.sha256((out / "baseline-sources.zip").read_bytes()).hexdigest()
    (out / "baseline.json").write_text(json.dumps(baseline, indent=2) + "\n", encoding="utf-8")


def load_baseline(path):
    baseline = json.loads(path.read_text(encoding="utf-8"))
    if baseline.get("format") != 3:
        raise ValueError("Unsupported baseline format")
    if "sourcesSha256" in baseline:
        archive = path.parent / "baseline-sources.zip"
        if not archive.is_file() or hashlib.sha256(archive.read_bytes()).hexdigest() != baseline["sourcesSha256"]:
            raise ValueError("Baseline source archive missing or altered; preserve the complete baseline directory")
    return baseline


def recover_baseline(ref, apk, out):
    if not ref or ref.startswith("-"):
        raise ValueError("Supply an explicit commit, tag or branch with --from-ref")
    expected = hashlib.sha256(apk.read_bytes()).hexdigest()
    commit = subprocess.check_output(["git", "rev-parse", "--verify", "--end-of-options", ref + "^{commit}"], cwd=ROOT, text=True).strip()
    workspace = ROOT / ".tools/baseline-recovery" / uuid.uuid4().hex
    checkout = workspace / "checkout"
    candidate = workspace / "candidate"
    workspace.mkdir(parents=True)
    run("git", "worktree", "add", "--detach", checkout, commit)
    # Keep the recovery checkout for diagnosis; never switch or reset current work.
    print("Recovery checkout: " + str(checkout), flush=True)
    helper = checkout / "tools/screens.py"
    if not helper.is_file():
        raise ValueError("Selected revision has no supported base builder; restore its original build tooling first")
    subprocess.run([sys.executable, str(helper), "base", "--out", str(candidate)], cwd=checkout, check=True)
    baseline = json.loads((candidate / "baseline.json").read_text(encoding="utf-8"))
    (candidate / "baseline.json").rename(candidate / "unverified-baseline.json")
    rebuilt = hashlib.sha256((candidate / "screen-base.apk").read_bytes()).hexdigest()
    if rebuilt != expected or baseline.get("apkSha256") != expected:
        raise ValueError("Recovered APK differs from the supplied original APK; baseline rejected. Keep the candidate for diagnosis, not patching.")
    if baseline.get("format") != 3:
        raise ValueError("Recovered baseline format is unsupported")
    if (candidate / "baseline-sources.zip").exists():
        if hashlib.sha256((candidate / "baseline-sources.zip").read_bytes()).hexdigest() != baseline.get("sourcesSha256"):
            raise ValueError("Recovered source archive hash mismatch")
    else:
        save_baseline_sources(checkout, candidate, baseline)
    baseline["recoveredFromCommit"] = commit
    (candidate / "baseline.json").write_text(json.dumps(baseline, indent=2) + "\n", encoding="utf-8")
    shutil.copytree(candidate, out, dirs_exist_ok=True, ignore=shutil.ignore_patterns('unverified-baseline.json'))
    print("Verified recovered baseline: " + str(out), flush=True)


def check_release_host():
    baseline = json.loads((ROOT / "demo/release-baseline.json").read_text())
    check_native(baseline["nativeFiles"], fingerprint(ROOT, NATIVE))
    source = (ROOT / "demo/build/generated/source/buildConfig/release/dev/patchrelay/screens/demo/BuildConfig.java").read_text()
    actual = re.search(r'HOST_SHA256 = "([a-f0-9]{64})"', source)
    if actual is None:
        generated = re.search(r'HOST_SHA256 = ([\w.]+\.PatchRelayBuild)\.HOST_SHA256;', source)
        if generated:
            path = ROOT / ('demo/build/generated/patchrelay/release/' + generated[1].replace('.', '/') + '.java')
            actual = re.search(r'HOST_SHA256 = "([a-f0-9]{64})"', path.read_text())
    if not actual or actual[1] != baseline["hostSha256"] or actual[1] != host_hash(baseline["nativeFiles"]):
        raise ValueError("Native host fingerprint differs between Gradle, Python and release snapshot")
    print("Native host fingerprint matches release snapshot and Gradle BuildConfig.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("base", "baseline", "recover-baseline", "patch", "check-host"))
    parser.add_argument("--out", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--from-ref", help="Explicit old commit/tag/branch for baseline recovery")
    parser.add_argument("--apk", type=Path, help="Original installed/released APK required for recovery")
    parser.add_argument("--version", type=int, default=1)
    parser.add_argument("--repository", default="jaypal1046/patchrelay")
    parser.add_argument("--tag")
    parser.add_argument("--key", type=Path, default=Path(".keys/patch-signing.key"))
    args = parser.parse_args()
    if args.command == "baseline":
        args.command = "base"
    if args.command == "check-host":
        check_release_host()
        return
    if args.out is None:
        parser.error("--out is required for base/patch builds")
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    if any(out.iterdir()):
        parser.error("Output directory is not empty; use a fresh output directory")
    if args.command == "recover-baseline":
        if not args.from_ref or args.apk is None:
            parser.error("Recovery requires --from-ref and --apk; no baseline is selected automatically")
        recover_baseline(args.from_ref, args.apk.resolve(), out)
        return
    if args.from_ref or args.apk:
        parser.error("--from-ref and --apk are only supported for recover-baseline")
    if args.version <= 0:
        parser.error("Version must be positive")
    gradle = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    baseline = None
    if args.command == "patch":
        if not args.tag or not re.fullmatch(r"[A-Za-z0-9_.-]+", args.tag):
            parser.error("--tag must be a unique release tag containing letters, digits, dots, underscores or hyphens")
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
            parser.error("--repository must be owner/repository")
        if args.baseline is None:
            parser.error("--baseline is required")
        baseline = load_baseline(args.baseline)
        check_native(baseline["nativeFiles"], fingerprint(ROOT, NATIVE))
        if baseline["hostSha256"] != host_hash(baseline["nativeFiles"]) or baseline["screenApi"] != CONFIG["screenApi"]:
            parser.error("Invalid baseline compatibility contract")
        current = fingerprint(ROOT, ("screens/src/commonMain",))
        changed = sorted(path for path in set(current) | set(baseline["screenFiles"]) if current.get(path) != baseline["screenFiles"].get(path))
        if not changed:
            parser.error("No Kotlin screen changes detected")
        print("Changed screen sources: " + ", ".join(changed), flush=True)
    run(gradle, ":screens:jvmTest", ":screens:compileProductionExecutableKotlinJs", "--console=plain", "--max-workers=2")
    run("node", ROOT / "tools/test-screen-program.mjs", PROGRAM, "baseline" if args.command == "base" else "fixed")
    if args.command == "base":
        asset = ROOT / "demo/src/main/assets/baseline-screen.js"
        asset.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(PROGRAM, asset)
        run(gradle, ":sdk:testDebugUnitTest", ":demo:testDebugUnitTest", ":sdk:assembleRelease", ":demo:assembleRelease", "--console=plain", "--max-workers=2")
        apk = out / "screen-base.apk"
        shutil.copyfile(ROOT / "demo/build/outputs/apk/release/demo-release.apk", apk)
        shutil.copyfile(PROGRAM, out / "baseline-screen.js")
        native_files = fingerprint(ROOT, NATIVE)
        manifest = {"format": 3, "appId": CONFIG["appId"], "baseVersion": CONFIG["baseVersion"],
                    "screenApi": CONFIG["screenApi"], "hostSha256": host_hash(native_files),
                    "screenIds": CONFIG["screenIds"], "nativeFiles": native_files,
                    "screenFiles": fingerprint(ROOT, ("screens/src/commonMain",)),
                    "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                    "programSha256": hashlib.sha256(PROGRAM.read_bytes()).hexdigest()}
        save_baseline_sources(ROOT, out, manifest)
        print("Native base and compatibility snapshot ready: " + str(out), flush=True)
    else:
        check_native(baseline["nativeFiles"], fingerprint(ROOT, NATIVE))
        if hashlib.sha256(PROGRAM.read_bytes()).hexdigest() == baseline["programSha256"]:
            parser.error("Compiled program is unchanged")
        shutil.copyfile(PROGRAM, out / "screens.js")
        url = f"https://github.com/{args.repository}/releases/download/{args.tag}/screens.js"
        run("java", ROOT / "tools/Publisher.java", "sign", args.key.resolve(), out / "screens.js",
            baseline["appId"], baseline["baseVersion"], args.version, ",".join(baseline["screenIds"]), url, out / "patch.json",
            baseline["hostSha256"])
        print("Signed Kotlin screen patch ready: " + str(out), flush=True)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as failure:
        raise SystemExit("Build failed: " + str(failure))
