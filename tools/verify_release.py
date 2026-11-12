"""Verify the package, version and explicitly selected 0.4.0 publisher certificate."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess

PREVIOUS_CERT = '3af7ab691a8ef8399f433840539e24939c264dc47de2503e77832c3c0c51ba09'
EXPECTED_CERT = 'b81a8671a77c5835b7ab99c47fb74d31915e031ddc6de91dc6020f7339b8131a'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    parser.add_argument('--require-upgrade-from-030', action='store_true')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ['ANDROID_HOME'])
    tools = sdk / 'build-tools' / '36.0.0'
    output = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(args.apk)], text=True)
    certs = re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', output)
    if certs != [EXPECTED_CERT]:
        raise SystemExit('Unexpected 0.4.0 publisher certificate; refusing publication')
    if args.require_upgrade_from_030 and certs != [PREVIOUS_CERT]:
        raise SystemExit('The new signing key cannot update 0.3.0; a fresh installation is required')
    manifest = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(args.apk)], text=True)
    if not re.search(r"package: name='com.immunoblockers' versionCode='4' versionName='0.4.0'", manifest):
        raise SystemExit('Unexpected package or version')
    checksum = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    args.apk.with_suffix('.apk.sha256').write_text(f'{checksum}  {args.apk.name}\n')
    print(f'Certificate, package and version verified. SHA256: {checksum}')
    if certs != [PREVIOUS_CERT]:
        print('New signing key: cannot install over 0.3.0. Do not publish as a compatible OTA update.')


if __name__ == '__main__':
    main()
