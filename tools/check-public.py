#!/usr/bin/env python3
"""Audit source directory or ZIP without printing credential values."""
import argparse,pathlib,re,zipfile
p=argparse.ArgumentParser(description=__doc__);p.add_argument('target',type=pathlib.Path);args=p.parse_args();findings=[]
text_types={'.java','.js','.html','.json','.xml','.gradle','.md','.sh','.py','.properties','.yml','.yaml'}
patterns=[('private key',re.compile(r'BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY')),('GitHub token',re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{25,}|github_pat_[A-Za-z0-9_]{30,})'))]
def audit(name,data):
 path=pathlib.PurePosixPath(name);base=path.name.lower()
 # Same filename rule as the inspected pubblica.html, plus actual signing files.
 if base.startswith('.env') or base.endswith('.env'):findings.append((name,'filename blocked by publisher'))
 if path.suffix.lower() in {'.keystore','.jks','.p12','.pfx'}:findings.append((name,'signing material'))
 if path.suffix.lower()=='.apk':findings.append((name,'APK excluded from this source-only release'))
 if path.suffix.lower() in text_types:
  s=data.decode('utf-8',errors='replace')
  for kind,pattern in patterns:
   if pattern.search(s):findings.append((name,kind))
if args.target.is_file():
 with zipfile.ZipFile(args.target) as z:
  for entry in z.infolist():
   if not entry.is_dir():audit(entry.filename,z.read(entry))
else:
 for f in args.target.rglob('*'):
  if f.is_file() and not any(part in {'build','.git','.gradle'} for part in f.relative_to(args.target).parts):audit(str(f.relative_to(args.target)),f.read_bytes())
for name,kind in findings:print(name+': '+kind)
if findings:raise SystemExit(1)
print('PASS: no publisher-blocked env files, signing files, APKs, recognized private keys or GitHub tokens.')
