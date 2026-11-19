import os
S=os.environ.get('WORK','.')
def load(p):
    d={};cur=None
    for l in open(p):
        t=l.rstrip('\n').split('\t')
        if t[0]=='L': cur=t[1]; d[cur]={'st':t[2],'info':t[3:],'A':[],'B':[]}
        else: d[cur][t[0]].append(t[1:])
    return d
exp=load(S+'/expected.tsv')
import sys
for v in sys.argv[1:]:
    got=load(f'{S}/got_{v}.tsv')
    problems=[]; maxdiff=0; natoms=0; nbonds=0
    for cid,e in exp.items():
        g=got.get(cid)
        if g is None: problems.append((cid,'missing')); continue
        if g['st']!=e['st']: problems.append((cid,'status',g['st'],g['info'])); continue
        if g['info']!=e['info']: problems.append((cid,'counts',g['info'],e['info'])); continue
        for a,b in zip(g['A'],e['A']):
            natoms+=1
            if a[0]!=b[0] or a[1]!=b[1]: problems.append((cid,'atom',a[:2],b[:2])); break
            d=max(abs(float(a[k])-float(b[k])) for k in (2,3,4)); maxdiff=max(maxdiff,d)
            if d>1e-3: problems.append((cid,'coords',a[0],d)); break
        if g['B']!=e['B']: problems.append((cid,'bonds'))
        nbonds+=len(e['B'])
    extra=set(got)-set(exp)
    print(f'variant {v}: ligands {len(exp)}, parsed OK {sum(1 for x in got.values() if x["st"]=="OK")}, problems {len(problems)}, extra {len(extra)}, atoms compared {natoms}, bonds compared {nbonds}, max coordinate diff {maxdiff:.2e}')
    print('  ', problems[:5])
