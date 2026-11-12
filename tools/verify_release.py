"""Reject an APK that cannot update the shipped 0.3.0 installation."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess

EXPECTED_CERT = '3af7ab691a8ef8399f433840539e24939c264dc47de2503e77832c3c0c51ba09'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ['ANDROID_HOME'])
    tools = sdk / 'build-tools' / '36.0.0'
    output = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(args.apk)], text=True)
    certs = re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', output)
    if certs != [EXPECTED_CERT]:
        raise SystemExit('Signing certificate differs from 0.3.0; refusing publication')
    manifest = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(args.apk)], text=True)
    if not re.search(r"package: name='com.immunoblockers' versionCode='4' versionName='0.4.0'", manifest):
        raise SystemExit('Unexpected package or version')
    checksum = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    args.apk.with_suffix('.apk.sha256').write_text(f'{checksum}  {args.apk.name}\n')
    print(f'Certificate, package and version verified. SHA256: {checksum}')


if __name__ == '__main__':
    main()
