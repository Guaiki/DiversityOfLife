"""Mete un export nuevo de Blockbench en SquirrelAnims.java conservando los nombres de campo
del mod, las animaciones de trepada y la pose estatica de bebe.

    python tools/swap_squirrel_anims.py "C:\\ruta\\al\\export.java"

Que hace, y por que:

1. **Traduce los nombres.** Blockbench exporta identificadores con espacios y parentesis
   ("Special animation (Sit) (starting)") que ni siquiera son Java valido. MAPPING los lleva al
   nombre de campo que usa el mod.

2. **Respeta lo que no viene del export.** BABY es una pose estatica escrita a mano, y las tres de
   trepada estan afinadas contra la rotacion de -90 grados del renderer: se dejan intactas.

3. **Quita `.looping()` de los clips de un disparo.** Esto es lo importante. Blockbench marca como
   looping animaciones que son de un solo disparo, y el mezclador de DeluxeLib respeta esa bandera:
   `KeyframeBlender.interpolate` hace `time %= lengthInSeconds` cuando la definicion es looping, asi
   que un clip de salida que sigue avanzando en la pila de desvanecimiento **vuelve a su frame 0** y
   se ve un salto atras a la pose anterior. Fue el bug de la cola al salir de comer hojas. Sin este
   paso, cada re-export lo reintroduce en silencio.

Ojo con MAPPING: la clave es el nombre CRUDO que sale de Blockbench. Si renombras las animaciones
alli, hay que actualizar la clave, no el valor.
"""
import re
import sys

CUR = r"src\main\java\com\evirapo\diversityofcritters\client\animations\SquirrelAnims.java"

# nombre crudo en el export -> nombre de campo en el mod
#
# Los de erguirse y estirarse estaban cruzados en el export original: lo exportado como "Lay" es en
# realidad erguirse sobre las patas traseras, y "Stand up" es estirarse. Se corrige aqui.
MAPPING = {
    "Idle": "Idle",
    "Stand up": "stretch",
    "SniffRight": "SniffRight",
    "SniffLeft": "SniffLeft",
    "Special animation (Sit) (starting)": "SIT_STARTING",
    "Special animation (Sit)": "SIT_IDLE",
    "Special animation (Sit) (ending)": "SIT_ENDING",
    "Special animation (Lay) (starting)": "STAND_UP_STARTING",
    "Special animation (Lay)": "STAND_UP_IDLE",
    "Special animation (Lay) (ending)": "STAND_UP_ENDING",
    "Walk": "Walk",
    "Run": "Run",
    "Swim": "Swim",
    "Pet": "Pet",
    "preparing_sleep": "preparing_sleep",
    "Sleep": "Sleep",
    "awakening": "awakening",
    "Attack": "Attack",
    "Drink (starting)": "DRINK_STARTING",
    "Drink": "DRINK_IDLE",
    "Drink (ending)": "DRINK_ENDING",
    "Eating (starting)": "EATING_STARTING",
    "Eating": "EATING_IDLE",
    "Eating (ending)": "EATING_ENDING",
    "Grooming (starting)": "GROOMING_STARTING",
    "Grooming": "GROOMING_IDLE",
    "Grooming (ending)": "GROOMING_ENDING",
    "Gliding": "GLIDING",
}

# Se quedan exactamente como estan
SKIP = {"CLIMBING_UP", "CLIMB_IDLE", "CLIMBING_DOWN", "BABY"}

# nombre de hueso en el export -> nombre real en SquirrelModel, por animacion.
#
# El .bbmodel de la ardilla arrastra grupos de los proyectos de los que se copiaron los clips, y
# los tres del sueno mueven la raiz bajo el nombre de OTRO bicho: "BandedPlamCivet" (el civet) en
# preparing_sleep y Sleep, "gray fox" en awakening. La raiz de SquirrelModel se llama "Giant
# flying squirrel", asi que `KeyframeBlender.blend` no resolvia el hueso y **descartaba el canal
# en silencio**. Ese canal baja el cuerpo 3 unidades al tumbarse y lo sube al despertar; ademas es
# el unico que mueve cuerpo y patas a la vez, porque las cuatro patas cuelgan de la raiz y no de
# Body. Sin el, el despertar era un giro de torso sin recorrido vertical: mecanico.
#
# Se hace por animacion y no en global a proposito: los clips de trepada estan afinados a mano
# CONTRA la raiz muerta (ver SKIP), y activarles el canal les metería una rotacion de 180 grados.
# Por eso aqui solo van los tres del sueno. `tools/check_anim_bones.py` lista el resto.
BONE_MAPPING = {
    "preparing_sleep": {"BandedPlamCivet": "Giant flying squirrel"},
    "Sleep":           {"BandedPlamCivet": "Giant flying squirrel"},
    "awakening":       {"gray fox": "Giant flying squirrel"},
}

# Clips de un solo disparo: entradas y salidas de cadena, y gestos sueltos. Se les fuerza a NO
# looping pase lo que pase (ver el punto 3 del encabezado).
FORCE_NON_LOOPING = {
    "stretch", "SniffLeft", "SniffRight",
    "SIT_STARTING", "SIT_ENDING",
    "STAND_UP_STARTING", "STAND_UP_ENDING",
    "DRINK_STARTING", "DRINK_ENDING",
    "EATING_STARTING", "EATING_ENDING",
    "GROOMING_STARTING", "GROOMING_ENDING",
    "preparing_sleep", "awakening", "Attack",
}

DECL = re.compile(r'^\s*public static final AnimationDefinition\s+(.+?)\s*=\s*(AnimationDefinition\.Builder.*)$')


def read_lines(path):
    """Lee normalizando a \\n y devuelve tambien el terminador original, para escribirlo igual."""
    with open(path, encoding="utf-8", newline="") as fh:
        raw = fh.read()
    if raw.startswith("\ufeff"):
        raw = raw[1:]
    eol = "\r\n" if "\r\n" in raw else "\n"
    return raw.replace("\r\n", "\n").split("\n"), eol


def parse_blocks(path):
    """[(nombre, inicio, fin_exclusivo, primera_linea_cuerpo, lineas_cuerpo)]"""
    lines, _ = read_lines(path)
    blocks = []
    i = 0
    while i < len(lines):
        m = DECL.match(lines[i])
        if not m:
            i += 1
            continue
        name = m.group(1).strip()
        j = i + 1
        while j < len(lines) and lines[j].strip() != ".build();":
            j += 1
        if j >= len(lines):
            sys.exit("ERROR: bloque '%s' en %s sin .build();" % (name, path))
        blocks.append((name, i, j + 1, m.group(2), lines[i + 1:j + 1]))
        i = j + 1
    return lines, blocks


def reindent(body_lines):
    """Tabs del export -> 4 espacios, mas 4 de sangria para encajar con el fichero del mod."""
    out = []
    for ln in body_lines:
        stripped = ln.lstrip("\t")
        depth = len(ln) - len(stripped)
        out.append(" " * (4 * depth + 4) + stripped)
    return out


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    new_path = sys.argv[1]

    _, new_blocks = parse_blocks(new_path)
    cur_lines, cur_blocks = parse_blocks(CUR)

    new_by_name = {}
    for name, _s, _e, first, body in new_blocks:
        if name in new_by_name:
            sys.exit("ERROR: '%s' duplicado en el export" % name)
        new_by_name[name] = (first, body)

    print("Export nuevo: %d animaciones" % len(new_blocks))
    print("Fichero actual: %d animaciones" % len(cur_blocks))

    cur_names = {b[0] for b in cur_blocks}
    for raw, field in MAPPING.items():
        if raw not in new_by_name:
            sys.exit("ERROR: '%s' no esta en el export nuevo" % raw)
        if field not in cur_names:
            sys.exit("ERROR: el campo '%s' no existe en SquirrelAnims" % field)
    sobran = set(new_by_name) - set(MAPPING)
    if sobran:
        print("AVISO: en el export hay animaciones sin mapear, se ignoran: %s" % ", ".join(sorted(sobran)))

    field_to_raw = {v: k for k, v in MAPPING.items()}

    result = list(cur_lines)
    changed, kept, unlooped, rebound = [], [], [], []
    # De atras hacia delante, para que los indices no se desplacen
    for name, start, end, first_body, body in sorted(cur_blocks, key=lambda b: -b[1]):
        if name in SKIP or name not in field_to_raw:
            kept.append(name)
            continue
        n_first, n_body = new_by_name[field_to_raw[name]]

        if name in FORCE_NON_LOOPING and ".looping()" in n_first:
            n_first = n_first.replace(".looping()", "")
            unlooped.append(name)

        for stale, real in BONE_MAPPING.get(name, {}).items():
            hits = sum(1 for ln in n_body if 'addAnimation("%s"' % stale in ln)
            if hits:
                n_body = [ln.replace('addAnimation("%s"' % stale, 'addAnimation("%s"' % real)
                          for ln in n_body]
                rebound.append("%s: %s -> %s (%d)" % (name, stale, real, hits))

        indent = cur_lines[start][:len(cur_lines[start]) - len(cur_lines[start].lstrip())]
        block = [indent + "public static final AnimationDefinition " + name + " = " + n_first]
        block += reindent(n_body)

        note = ""
        if first_body.strip() != n_first.strip():
            note = "   <-- %s  =>  %s" % (first_body.strip(), n_first.strip())
        changed.append((name, len(body), len(n_body), note))
        result[start:end] = block

    _, cur_eol = read_lines(CUR)
    with open(CUR, "w", encoding="utf-8", newline="") as fh:
        fh.write(cur_eol.join(result))

    print("\nSUSTITUIDAS (%d):" % len(changed))
    for name, oldn, newn, note in sorted(changed):
        print("  %-20s %4d -> %4d lineas%s" % (name, oldn, newn, note))
    if unlooped:
        print("\n.looping() QUITADO de los clips de un disparo (%d): %s"
              % (len(unlooped), ", ".join(sorted(unlooped))))
    if rebound:
        print("\nHUESOS REAPUNTADOS (%d):" % len(rebound))
        for line in sorted(rebound):
            print("  " + line)
    print("\nINTACTAS (%d): %s" % (len(kept), ", ".join(sorted(kept))))
    print("\nAhora: python tools/check_anim_bones.py  y luego  gradlew build")


if __name__ == "__main__":
    main()
