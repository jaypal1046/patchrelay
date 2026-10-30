"""Check that CI built the same native source contract as the saved release."""
import json
from pathlib import Path
import re
import screens

baseline = json.loads((screens.ROOT / "demo/release-baseline.json").read_text())
screens.check_native(baseline["nativeFiles"], screens.fingerprint(screens.ROOT, screens.NATIVE))
source = (screens.ROOT / "demo/build/generated/source/buildConfig/release/dev/patchrelay/screens/demo/BuildConfig.java").read_text()
actual = re.search(r'HOST_SHA256 = "([a-f0-9]{64})"', source)
if not actual or actual[1] != baseline["hostSha256"] or actual[1] != screens.host_hash(baseline["nativeFiles"]):
    raise SystemExit("Native host fingerprint differs between Gradle, Python and release snapshot")
print("Native host fingerprint matches release snapshot and Gradle BuildConfig.")
