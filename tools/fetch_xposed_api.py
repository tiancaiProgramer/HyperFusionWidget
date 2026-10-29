#!/usr/bin/env python3
"""Fetch the pinned, compile-only libxposed API 102 from Maven Central."""
import hashlib
import io
import pathlib
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
URL = 'https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar'
SHA256 = '423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0'
with urllib.request.urlopen(URL, timeout=60) as response:
    payload = response.read(10 * 1024 * 1024)
if hashlib.sha256(payload).hexdigest() != SHA256:
    raise SystemExit('libxposed AAR checksum mismatch; dependency was not installed.')
with zipfile.ZipFile(io.BytesIO(payload)) as archive:
    classes = archive.read('classes.jar')
result = ROOT / 'libs' / 'libxposed-api-102.jar'
result.parent.mkdir(parents=True, exist_ok=True)
result.write_bytes(classes)
print('Verified libxposed API 102.0.0:', result)
