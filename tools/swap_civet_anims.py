"""Regenera CivetAnims.java desde un export de Blockbench conservando los nombres de campo del mod
y la pose estatica de bebe.

    python tools/swap_civet_anims.py "C:\\ruta\\al\\export.java"

Es el gemelo de swap_squirrel_anims.py, con una diferencia: alli se sustituyen bloques sueltos
dentro del fichero existente porque las tres animaciones de trepada estan afinadas a mano contra un
hueso raiz muerto. Aqui el rig cambio entero (Torse2/Torse3, Tail_I/Tail_L*, Eye1/Eye2/Sleep_eye*),
asi que **todas** las animaciones vienen del export y el fichero se regenera completo. Lo unico que
sobrevive es lo que este en PRESERVE, que se copia tal cual del fichero actual.

Que hace, y por que:

1. **Traduce los nombres.** Blockbench exporta identificadores con espacios y parentesis
   ("Special animation (Sit) (starting)") que ni siquiera son Java valido. MAPPING los lleva al
   nombre de campo que usa el mod.

2. **Quita `.looping()` de los clips de un disparo.** Esto es lo importante. Blockbench marca como
   looping animaciones que son de un solo disparo, y el mezclador de DeluxeLib respeta esa bandera:
   `KeyframeBlender.interpolate` hace `time %= lengthInSeconds` cuando la definicion es looping, asi
   que un clip de salida que sigue avanzando en la pila de desvanecimiento **vuelve a su frame 0** y
   se ve un salto atras a la pose anterior. Sin este paso, cada re-export lo reintroduce en silencio.

3. **Conserva lo escrito a mano.** BABY es una pose estatica que no sale de ningun export.

Ojo con MAPPING: la clave es el nombre CRUDO que sale de Blockbench. Si renombras las animaciones
alli, hay que actualizar la clave, no el valor.

Nota sobre "Ringtailedvontsira": SIT_IDLE trae un canal de posicion bajo ese nombre, que es un grupo
de OTRO proyecto (la vontsira de cola anillada) del que se copio el clip. La raiz de CivetModel se
llama "BandedPlamCivet", asi que `KeyframeBlender` lo descarta y `check_anim_bones.py Civet` lo
denuncia en cada pasada. Se deja descartado a proposito: es un unico keyframe constante de
posVec(0, 0, -1), o sea 1 unidad de modelo (1/32 de bloque a la escala 0.5 del render), y en
Blockbench el grupo tampoco existe, asi que es movimiento que el artista nunca vio. Si algun dia se
quiere recuperar, va aqui como un BONE_MAPPING igual que el de swap_squirrel_anims.py.

Despues:  python tools/check_anim_bones.py Civet   y luego  gradlew build
"""
import re
import sys

CUR = r"src\main\java\com\evirapo\diversityofcritters\client\animations\CivetAnims.java"

HEADER = """package com.evirapo.diversityofcritters.client.animations;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Clips de la civeta adulta. Generado desde el export de Blockbench con
 * {@code tools/swap_civet_anims.py}; no editar a mano salvo {@link #BABY}, que es una pose estatica
 * escrita aqui y que el script conserva.
 *
 * <p>No todos estan registrados en el animator: {@link #ALERT_STARTING} y {@link #ALERT} todavia
 * no tienen comportamiento que los dispare y viven aqui a la espera.
 *
 * <p>"Eating" y "Pet" salen del export pero NO se copian: no las usaba nadie. Estan fuera de
 * MAPPING a proposito, asi que el script las lista como ignoradas en cada pasada.
 */
public class CivetAnims {
"""

# nombre crudo en el export -> nombre de campo en el mod
#
# Igual que en la ardilla, el export trae cruzados erguirse y tumbarse: lo exportado como "Stand up"
# es el estiramiento (la variante de reposo STRETCH) y la cadena "Lay" es la de tumbarse, que el mod
# dispara con la variante llamada STAND_UP. Los nombres de campo son los que ya usaba el mod.
MAPPING = {
    "Idle": "IDLE",
    "Stand up": "STAND_UP",
    "SniffRight": "SNIFF_RIGHT",
    "SniffLeft": "SNIFF_LEFT",
    "Special animation (Sit) (starting)": "SIT_STARTING",
    "Special animation (Sit)": "SIT_IDLE",
    "Special animation (Sit) (ending)": "SIT_ENDING",
    "Special animation (Lay) (starting)": "LAY_STARTING",
    "Special animation (Lay)": "LAY_IDLE",
    "Special animation (Lay) (ending)": "LAY_ENDING",
    "Walk": "WALK",
    "Run": "RUN",
    "Swim": "SWIM",
    "preparing_sleep": "PREPARING_SLEEP",
    "Sleep": "SLEEP",
    "awakening": "AWAKENING",
    "Climbing (UP)": "CLIMBING_UP",
    "Climb": "CLIMB_IDLE",
    "Climbing (DOWN)": "CLIMBING_DOWN",
    "Attack": "ATTACK",
    "Drink (starting)": "DRINK_STARTING",
    "Drink": "DRINK_IDLE",
    "Drink (ending)": "DRINK_ENDING",
    "Scratching (starting)": "SCRATCHING_STARTING",
    "Scratching": "SCRATCHING_IDLE",
    "Scratching (ending)": "SCRATCHING_ENDING",
    "Digging (starting)": "DIGGING_STARTING",
    "Digging": "DIGGING_IDLE",
    "Digging (ending)": "DIGGING_ENDING",
    "Grooming (starting)": "GROOMING_STARTING",
    "Grooming": "GROOMING_IDLE",
    "Grooming (ending)": "GROOMING_ENDING",
    "Alert_starting": "ALERT_STARTING",
    "Alert": "ALERT",
}

# Escritas a mano, no salen de ningun export: se copian del fichero actual y van las primeras.
PRESERVE = ["BABY"]

# Clips de un solo disparo: entradas y salidas de cadena, y gestos sueltos. Se les fuerza a NO
# looping pase lo que pase (ver el punto 2 del encabezado).
FORCE_NON_LOOPING = {
    "STAND_UP", "SNIFF_LEFT", "SNIFF_RIGHT",
    "SIT_STARTING", "SIT_ENDING",
    "LAY_STARTING", "LAY_ENDING",
    "DRINK_STARTING", "DRINK_ENDING",
    "GROOMING_STARTING", "GROOMING_ENDING",
    "DIGGING_STARTING", "DIGGING_ENDING",
    "SCRATCHING_STARTING", "SCRATCHING_ENDING",
    "PREPARING_SLEEP", "AWAKENING", "ATTACK", "ALERT_STARTING",
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
    """[(nombre, primera_linea_cuerpo, lineas_cuerpo)] en orden de aparicion."""
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
        blocks.append((name, m.group(2), lines[i + 1:j + 1]))
        i = j + 1
    return blocks


def reindent(body_lines):
    """Tabs del export -> 4 espacios, mas 4 de sangria para encajar con el fichero del mod."""
    out = []
    for ln in body_lines:
        stripped = ln.lstrip("\t")
        depth = len(ln) - len(stripped)
        out.append(" " * (4 * depth + 4) + stripped)
    return out


def emit(name, first, body):
    return ["    public static final AnimationDefinition " + name + " = " + first.strip()] + body + [""]


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    new_path = sys.argv[1]

    new_blocks = parse_blocks(new_path)
    cur_blocks = {b[0]: b for b in parse_blocks(CUR)}

    new_by_name = {}
    for name, first, body in new_blocks:
        if name in new_by_name:
            sys.exit("ERROR: '%s' duplicado en el export" % name)
        new_by_name[name] = (first, body)

    print("Export nuevo: %d animaciones" % len(new_blocks))
    print("Fichero actual: %d animaciones" % len(cur_blocks))

    for raw in MAPPING:
        if raw not in new_by_name:
            sys.exit("ERROR: '%s' no esta en el export nuevo" % raw)
    for name in PRESERVE:
        if name not in cur_blocks:
            sys.exit("ERROR: no encuentro '%s' en %s para conservarlo" % (name, CUR))

    sobran = set(new_by_name) - set(MAPPING)
    if sobran:
        print("AVISO: en el export hay animaciones sin mapear, se ignoran: %s" % ", ".join(sorted(sobran)))

    out = HEADER.split("\n")
    out.append("")

    for name in PRESERVE:
        _, first, body = cur_blocks[name]
        out += emit(name, first, body)

    written, unlooped = [], []
    for raw, first, body in new_blocks:
        if raw not in MAPPING:
            continue
        name = MAPPING[raw]
        if name in FORCE_NON_LOOPING and ".looping()" in first:
            first = first.replace(".looping()", "")
            unlooped.append(name)
        out += emit(name, first, reindent(body))
        written.append((name, raw, len(body)))

    out.append("}")

    _, cur_eol = read_lines(CUR)
    with open(CUR, "w", encoding="utf-8", newline="") as fh:
        fh.write(cur_eol.join(out))

    print("\nESCRITAS (%d):" % len(written))
    for name, raw, n in written:
        print("  %-22s <- %-36s %4d lineas" % (name, '"%s"' % raw, n))
    print("\nCONSERVADAS (%d): %s" % (len(PRESERVE), ", ".join(PRESERVE)))
    if unlooped:
        print("\n.looping() QUITADO de los clips de un disparo (%d): %s"
              % (len(unlooped), ", ".join(sorted(unlooped))))
    print("\nAhora: python tools/check_anim_bones.py Civet  y luego  gradlew build")


if __name__ == "__main__":
    main()
