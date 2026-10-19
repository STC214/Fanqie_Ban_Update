import zipfile,re,os,collections
apk=next(x for x in os.listdir('.') if x.lower().endswith('.apk'))
need=re.compile(rb'(?i)(?:https?://|[a-z0-9_-]{2,}\.)[a-z0-9._~:/?#\[\]@!$&\'()*+,;=%-]{4,}')
keys=('update','upgrade','version','apk','download','install','check')
hits=set()
with zipfile.ZipFile(apk) as z:
  for n in z.namelist():
    if n.endswith('.dex') or n.endswith('.xml') or n.endswith('.json'):
      b=z.read(n)
      for m in need.finditer(b):
        s=m.group().decode('utf-8','ignore').strip('.:,;')
        if any(k in s.lower() for k in keys): hits.add((n,s[:500]))
for n,s in sorted(hits, key=lambda x:(x[0],x[1])):
 print(f'{n}\t{s}')
