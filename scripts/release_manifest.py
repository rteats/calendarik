"""Create the release manifest only after checking the previous publication."""
import hashlib
import json
from pathlib import Path
import re
import sys


def make_manifest(code, name, commit, schema, apk, previous):
    if code < previous["versionCode"] or (
        code == previous["versionCode"] and commit != previous.get("sourceCommit")
    ):
        raise ValueError("Release version must advance; only the same commit may retry a publication")
    if schema < previous.get("databaseVersion", 5):
        raise ValueError("Release would downgrade the published database schema")
    if not re.fullmatch(r"[a-f0-9]{40}", commit):
        raise ValueError("Expected full source commit")
    digest = hashlib.sha256(apk).hexdigest()
    tag = f"build-{code}-{digest[:12]}"
    return dict(versionCode=code, versionName=name, sourceCommit=commit,
                databaseVersion=schema, sha256=digest,
                apkUrl=f"https://github.com/rteats/calendarik/releases/download/{tag}/Calendarik.apk")


if __name__ == "__main__":
    code, name, commit = sys.argv[1:]
    schema = int(re.search(r"version = (\d+)", Path(
        "app/src/main/java/com/rteats/calendarik/data/CalendarDatabase.kt").read_text()).group(1))
    manifest = make_manifest(int(code), name, commit, schema, Path("Calendarik.apk").read_bytes(),
                             json.loads(Path("previous-update.json").read_text()))
    Path("update.json").write_text(json.dumps(manifest) + "\n")
