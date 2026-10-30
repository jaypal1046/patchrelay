"""Build a native screen base or a compatible Kotlin-only screen patch."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess

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
        if path.suffix in CONFIG["textExtensions"]:
            data = data.replace(b"\r\n", b"\n")
        result[path.relative_to(root).as_posix()] = hashlib.sha256(data).hexdigest()
    return result


def check_native(baseline, current):
    changed = sorted(path for path in set(baseline) | set(current) if baseline.get(path) != current.get(path))
    if changed:
        raise ValueError("Native host/compiler/assets changed; new base required: " + ", ".join(changed[:10]))


def run(*args):
    subprocess.run(list(map(str, args)), cwd=ROOT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("base", "patch"))
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--version", type=int, default=1)
    parser.add_argument("--repository", default="jaypal1046/patchrelay")
    parser.add_argument("--tag")
    parser.add_argument("--key", type=Path, default=Path(".keys/patch-signing.key"))
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    if (out / "baseline.json").exists() or (out / "patch.json").exists():
        parser.error("Signed/base output exists; use a fresh output directory")
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
        baseline = json.loads(args.baseline.read_text())
        if baseline.get("format") != 3:
            parser.error("Unsupported baseline format")
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
        run(gradle, ":sdk:testDebugUnitTest", ":sdk:assembleRelease", ":demo:assembleRelease", "--console=plain", "--max-workers=2")
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
        (out / "baseline.json").write_text(json.dumps(manifest, indent=2) + "\n")
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
