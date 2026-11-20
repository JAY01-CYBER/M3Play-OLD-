#!/usr/bin/env python3
"""Check license notices on tracked and newly added Kotlin sources."""
from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
names = subprocess.check_output(
    ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=root
).decode().split("\0")
missing = []
count = 0
for name in sorted(set(names)):
    path = root / name
    if path.suffix != ".kt" or not path.is_file():
        continue
    count += 1
    header = path.read_text().split("package ", 1)[0]
    if not re.search(r"license|licensed|copyright", header, re.IGNORECASE):
        missing.append(name)
if missing:
    print("Missing Kotlin license notices:\n" + "\n".join(missing))
    sys.exit(1)
print(f"License notices present in all {count} Kotlin files.")
