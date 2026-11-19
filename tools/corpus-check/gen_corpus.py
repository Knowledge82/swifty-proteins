"""Writes one text CIF per ligand of ligands.txt, in the layout of the real RCSB file 001, from the binary
CCD copy. Two variants: A = every category as loop_, B = single-row categories as tag/value pairs.
Also writes expected.tsv computed INDEPENDENTLY of the Kotlin code (numpy, own centering and set choice)."""
import sys, os, math, collections
S=sys.argv[1]; sys.path.insert(0,S)
import ccd_load, numpy as np
atom,bond=ccd_load.load()
ids=[l.strip() for l in open(S+'/ligands.txt') if l.strip()]
ga=collections.defaultdict(list); gb=collections.defaultdict(list)
for k,v in enumerate(atom['comp_id'].tolist()): ga[v].append(k)
for k,v in enumerate(bond['comp_id'].tolist()): gb[v].append(k)

def q(v):
    if v=='': return "''"
    special = any(c.isspace() for c in v) or v[0] in "_#$'\";[]" or "'" in v or '"' in v \
              or v.lower().startswith(('data_','loop_','save_','global_','stop_'))
    if not special: return v
    if "'" not in v: return "'"+v+"'"
    if '"' not in v: return '"'+v+'"'
    raise SystemExit('cannot quote '+v)
def num(x): return '?' if math.isnan(x) else '%.3f'%x

AT=['comp_id','atom_id','alt_atom_id','type_symbol','charge','pdbx_align','pdbx_aromatic_flag','pdbx_leaving_atom_flag',
    'pdbx_stereo_config','model_Cartn_x','model_Cartn_y','model_Cartn_z','pdbx_model_Cartn_x_ideal',
    'pdbx_model_Cartn_y_ideal','pdbx_model_Cartn_z_ideal','pdbx_component_atom_id','pdbx_component_comp_id','pdbx_ordinal']
BT=['comp_id','atom_id_1','atom_id_2','value_order','pdbx_aromatic_flag','pdbx_stereo_config','pdbx_ordinal']

def atom_rows(cid):
    rows=[]
    for n,k in enumerate(ga[cid],1):
        a=atom
        rows.append([q(cid),q(a['atom_id'][k]),q(a['atom_id'][k]),q(a['type_symbol'][k]),a['charge'][k],'1','N','N','N',
          num(a['model_Cartn_x'][k]),num(a['model_Cartn_y'][k]),num(a['model_Cartn_z'][k]),
          num(a['pdbx_model_Cartn_x_ideal'][k]),num(a['pdbx_model_Cartn_y_ideal'][k]),num(a['pdbx_model_Cartn_z_ideal'][k]),
          q(a['atom_id'][k]),q(cid),str(n)])
    return rows
def bond_rows(cid):
    return [[q(cid),q(bond['atom_id_1'][k]),q(bond['atom_id_2'][k]),bond['value_order'][k],'N','N',str(n)]
            for n,k in enumerate(gb[cid],1)]

def table(cat,cols,rows,pairs):
    out=[]
    if pairs and len(rows)==1:
        w=max(len(cat)+len(c)+1 for c in cols)
        for c,v in zip(cols,rows[0]): out.append(f'{cat}.{c}'.ljust(w+2)+v)
    else:
        out.append('loop_'); out+= [f'{cat}.{c}' for c in cols]
        for r in rows: out.append('  '.join(r))
    return out

def cif(cid,pairs):
    L=[f'data_{cid}','#',f'_chem_comp.id {q(cid)}','_chem_comp.name ?','#']
    L+=table('_chem_comp_atom',AT,atom_rows(cid),pairs); L.append('#')
    b=bond_rows(cid)
    if b: L+=table('_chem_comp_bond',BT,b,pairs); L.append('#')
    return '\n'.join(L)+'\n'

def expected(cid):
    ks=ga[cid]; a=atom
    names=[a['atom_id'][k] for k in ks]; elem=[a['type_symbol'][k].capitalize() for k in ks]
    ideal=np.array([[a['pdbx_model_Cartn_%s_ideal'%c][k] for c in 'xyz'] for k in ks],float)
    model=np.array([[a['model_Cartn_%s'%c][k] for c in 'xyz'] for k in ks],float)
    # values the text carries are rounded to 3 decimals: compare what the text says, not the raw float
    r3=lambda m: np.round(m,3)
    ideal,model=r3(ideal),r3(model)
    idx0={n:i for i,n in enumerate(names)}
    pairs=[(idx0[bond['atom_id_1'][k]],idx0[bond['atom_id_2'][k]]) for k in gb[cid]]
    def longest(M): return max((float(np.linalg.norm(M[i]-M[j])) for i,j in pairs),default=0.0)
    complete=[(n,M) for n,M in (('ideal',ideal),('model',model)) if not np.isnan(M).any()]
    if not complete: return None
    chosen=next(((n,M) for n,M in complete if longest(M)<=5.0), complete[0])
    which,P=chosen
    P=P-P.mean(axis=0)
    idx={n:i for i,n in enumerate(names)}
    B=[(idx[bond['atom_id_1'][k]],idx[bond['atom_id_2'][k]],{'SING':'SINGLE','DOUB':'DOUBLE','TRIP':'TRIPLE'}[bond['value_order'][k]]) for k in gb[cid]]
    return names,elem,P,B,which

for variant,pairs in (('A',False),('B',True)):
    d=f'{S}/corpus_{variant}'; os.makedirs(d,exist_ok=True)
    for cid in ids:
        if cid in ga: open(f'{d}/{cid}.cif','w',encoding='utf-8',newline='\n').write(cif(cid,pairs))
with open(S+'/expected.tsv','w') as f:
    which=collections.Counter()
    for cid in ids:
        if cid not in ga: continue
        e=expected(cid)
        if e is None: f.write(f'L\t{cid}\tFAIL\n'); which['none']+=1; continue
        names,elem,P,B,w=e; which[w]+=1
        f.write(f'L\t{cid}\tOK\t{len(names)}\t{len(B)}\n')
        for n,el,p in zip(names,elem,P): f.write(f'A\t{n}\t{el}\t{p[0]:.6f}\t{p[1]:.6f}\t{p[2]:.6f}\n')
        for i,j,o in B: f.write(f'B\t{i}\t{j}\t{o}\n')
print('files per variant:',len(os.listdir(S+'/corpus_A')), 'sets used:',dict(which))
