#!/usr/bin/env python3
"""Generate update.json from the built APK and authoritative Android manifest."""
import argparse, hashlib, json, pathlib, urllib.parse, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--apk',type=pathlib.Path,default=None)
p.add_argument('--url',help='Public HTTPS APK URL, or relative filename beside update.json')
p.add_argument('--output',type=pathlib.Path,default=root/'downloads/update.json')
p.add_argument('--notes',default='Mappa in home, nuova grafica, fonti cartografiche e diagnostica, controlli aggiornamenti.')
a=p.parse_args()
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
if a.apk is None:a.apk=root/'downloads'/('Traccia-GPS-v'+manifest.attrib[ns+'versionName']+'.apk')
url=a.url or a.apk.name
u=urllib.parse.urlparse(url)
if u.scheme and (u.scheme!='https' or not u.hostname or u.username or u.fragment):p.error('APK URL must be HTTPS without credentials/fragments')
if not u.scheme and (u.netloc or url.startswith('/') or '..' in pathlib.PurePosixPath(url).parts):p.error('Use a simple relative filename or full HTTPS URL')
h=hashlib.sha256()
with a.apk.open('rb') as f:
 for block in iter(lambda:f.read(65536),b''):h.update(block)
data={'packageName':'it.lorenzo.tracciagps.beta2','versionCode':int(manifest.attrib[ns+'versionCode']),'versionName':manifest.attrib[ns+'versionName'],'apkUrl':url,'sha256':h.hexdigest(),'notes':a.notes}
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Created {a.output.name}: {data["versionName"]} / build {data["versionCode"]}')
