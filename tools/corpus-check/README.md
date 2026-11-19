# Comprobación del parser contra el corpus completo (herramienta de verificación, no forma parte de la app)

Genera un `.cif` de texto por cada ligando de `ligands.txt` a partir de la copia binaria del diccionario de componentes
(`components.bcif`, que incluye la biblioteca `biotite` de PyPI), ejecuta `CifParser.parse` sobre todos y lo compara con una
**implementación independiente en Python** (otra elección de juego de coordenadas, otro centrado, otro mapeo de enlaces).

Pasos (directorio de trabajo `W`, con `ligands.txt`, la carpeta `pylib` con biotite instalada y `dl_biotite/x/biotite/structure/info/components.bcif`):

    python3 gen_corpus.py W                 # escribe W/corpus_A, W/corpus_B y W/expected.tsv
    kotlinc ... Corpus.kt                   # compilar junto a las fuentes de app/src/main (ver docs/03a, sección 11)
    java -cp ... CorpusKt W/corpus_A > W/got_A.tsv
    java -cp ... CorpusKt W/corpus_B > W/got_B.tsv
    WORK=W python3 compare.py A B

Variante A: todas las categorías como `loop_`. Variante B: las categorías de una sola fila como pares `etiqueta valor`.
Limitación: el texto lo escribe el generador (imita el fichero real `001`, comprobado celda a celda), no el servidor de RCSB.
