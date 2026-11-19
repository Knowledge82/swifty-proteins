import sys
S=sys.argv[1]
sys.path.insert(0, S+'/pylib')
import numpy as np
import biotite.structure.io.pdbx as pdbx

def load():
    f = pdbx.BinaryCIFFile.read(S+'/dl_biotite/x/biotite/structure/info/components.bcif')
    b = f['components']; A=b['chem_comp_atom']; B=b['chem_comp_bond']
    atom={}
    for n in ['comp_id','atom_id','type_symbol','charge']:
        atom[n]=A[n].as_array(str)
    for n in ['model_Cartn_x','model_Cartn_y','model_Cartn_z','pdbx_model_Cartn_x_ideal','pdbx_model_Cartn_y_ideal','pdbx_model_Cartn_z_ideal']:
        atom[n]=A[n].as_array(float, masked_value=np.nan)
    bond={n: B[n].as_array(str) for n in ['comp_id','atom_id_1','atom_id_2','value_order']}
    return atom, bond
