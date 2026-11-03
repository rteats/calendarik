"""Next rolling-release name using Moscow time; code is a separate monotonic value."""
from datetime import datetime
from zoneinfo import ZoneInfo
import re
import sys

RE = re.compile(r"^Calendarik (\d{4}\.\d{2}\.\d{2})\.(\d+)$")


def next_version(previous="", now=None):
    date = (now or datetime.now(ZoneInfo("Europe/Moscow"))).astimezone(
        ZoneInfo("Europe/Moscow")
    ).strftime("%Y.%m.%d")
    match = RE.fullmatch(previous.strip())
    counter = int(match.group(2)) if match and match.group(1) == date else 0
    return f"{date}.{counter + 1}"


if __name__ == "__main__":
    print(next_version(sys.argv[1] if len(sys.argv) > 1 else ""))
