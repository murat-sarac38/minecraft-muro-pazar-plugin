#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PROJECT="$ROOT/plugins/muroPazar"
OUT="$ROOT/generated-artifacts/muroPazar"
mvn -B -ntp -f "$PROJECT/pom.xml" package -DskipTests
mkdir -p "$OUT"
cp "$PROJECT/target/muroPazar-1.0.0.jar" "$OUT/"
cp "$PROJECT/README.md" "$OUT/KURULUM.md"
ROOT="$ROOT" python3 - <<'PY'
import os, pathlib, zipfile, hashlib
root = pathlib.Path(os.environ["ROOT"])
project = root / "plugins/muroPazar"
out = root / "generated-artifacts/muroPazar"
with zipfile.ZipFile(out / "muroPazar-1.0.0-paket.zip", "w", zipfile.ZIP_DEFLATED) as z:
    z.write(out / "muroPazar-1.0.0.jar", "muroPazar/muroPazar-1.0.0.jar")
    z.write(project / "README.md", "muroPazar/KURULUM.md")
    for path in sorted(project.rglob("*")):
        if path.is_file() and "target" not in path.relative_to(project).parts:
            z.write(path, "muroPazar/kaynak/" + str(path.relative_to(project)))
lines = []
for name in ("muroPazar-1.0.0.jar", "muroPazar-1.0.0-paket.zip"):
    lines.append(hashlib.sha256((out / name).read_bytes()).hexdigest() + "  " + name)
(out / "SHA256SUMS.txt").write_text("\n".join(lines) + "\n")
print("Release:", out)
print("\n".join(lines))
PY
