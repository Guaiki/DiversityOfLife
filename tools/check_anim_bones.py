"""Comprueba que cada hueso animado existe de verdad en el modelo.

    python tools/check_anim_bones.py                 # Squirrel (el par por defecto)
    python tools/check_anim_bones.py Civet Sifaka    # otros pares

Por que hace falta. `KeyframeBlender.blend` (DeluxeLib) aplica cada canal asi:

    model.getAnyDescendantWithName(boneName).ifPresent(part -> ...)

Si el nombre no existe en el `LayerDefinition`, el canal **se descarta en silencio**. No hay
excepcion, no hay log, no hay nada: la animacion simplemente no mueve ese hueso. Un export de
Blockbench que arrastre grupos de otro proyecto (por duplicar el .bbmodel, o por importar un clip
de otro bicho) mete animadores con nombres que aqui no resuelven, y el clip se ve "a medias" sin
que nada avise.

Fue exactamente el bug del sueno de la ardilla: `preparing_sleep` y `Sleep` movian la raiz bajo el
nombre "BandedPlamCivet" y `awakening` bajo "gray fox", cuando la raiz de SquirrelModel se llama
"Giant flying squirrel". Ese canal es el que baja el cuerpo 3 unidades al tumbarse y el que lo
vuelve a subir al levantarse, o sea todo el recorrido vertical del despertar. Al descartarse, el
despertar quedaba en un giro de torso sin desplazamiento: mecanico.

Como leer la salida. Se agrupa por hueso inexistente, y de cada uno se da el recorrido maximo que
se esta perdiendo (el keyframe mas alejado del neutro, en grados o unidades de modelo):

  amplitud 0.0   restos de otro rig: el canal no movia nada, arreglar el nombre no cambiaria nada.
  amplitud > 0   se esta perdiendo movimiento real en los clips listados.

Ojo: los modelos de bebe son rigs distintos con su propia jerarquia; comparar un Anims de adulto
contra un modelo de bebe da ruido, por eso cada par esta emparejado a mano en PAIRS.
"""
import re
import sys

BASE = "src/main/java/com/evirapo/diversityofcritters/client"

# animaciones -> modelo que las dibuja
PAIRS = {
    "Squirrel":     ("animations/SquirrelAnims.java",     "models/SquirrelModel.java"),
    "SquirrelBaby": ("animations/SquirrelBabyAnims.java", "models/SquirrelBabyModel.java"),
    "Civet":        ("animations/CivetAnims.java",        "models/CivetModel.java"),
    "CivetBaby":    ("animations/CivetBabyAnims.java",    "models/CivetBabyModel.java"),
    "Sifaka":       ("animations/SifakaAnims.java",       "models/SifakaModel.java"),
    "SifakaBaby":   ("animations/SifakaBabyAnims.java",   "models/SifakaBabyModel.java"),
}
DEFAULT = ["Squirrel"]

DECL = re.compile(r"AnimationDefinition\s+(\w+)\s*=\s*AnimationDefinition\.Builder")
CHANNEL = re.compile(
    r'addAnimation\("([^"]*)",\s*new AnimationChannel\(\s*AnimationChannel\.Targets\.(\w+)\s*,(.*?)\n\s*\)\)',
    re.S,
)
KEYFRAME = re.compile(r"KeyframeAnimations\.\w+\(([^)]*)\)")


def model_parts(path):
    with open(path, encoding="utf-8") as fh:
        return set(re.findall(r'addOrReplaceChild\("([^"]*)"', fh.read()))


def clips(path):
    """[(nombre, cuerpo)] en orden de aparicion."""
    with open(path, encoding="utf-8") as fh:
        src = fh.read()
    hits = list(DECL.finditer(src))
    return [
        (m.group(1), src[m.end():hits[i + 1].start() if i + 1 < len(hits) else len(src)])
        for i, m in enumerate(hits)
    ]


def amplitude(raw_keyframes):
    """Recorrido maximo del canal respecto al neutro. `scaleVec` ya exporta el offset, asi que en
    los tres targets el neutro es 0."""
    peak = 0.0
    for kf in KEYFRAME.findall(raw_keyframes):
        for value in kf.split(","):
            try:
                peak = max(peak, abs(float(value.strip().rstrip("Ff"))))
            except ValueError:
                return float("inf")  # no es literal: no se puede descartar
    return peak


def check(label, anim_path, model_path):
    parts = model_parts(model_path)
    dead = {}  # hueso -> {clip: amplitud}

    for clip, body in clips(anim_path):
        for bone, _target, keyframes in CHANNEL.findall(body):
            if bone in parts:
                continue
            per_clip = dead.setdefault(bone, {})
            per_clip[clip] = max(per_clip.get(clip, 0.0), amplitude(keyframes))

    print("== %s  (%d huesos en el modelo)" % (label, len(parts)))
    if not dead:
        print("   ok\n")
        return 0

    broken = 0
    for bone in sorted(dead, key=lambda b: -max(dead[b].values())):
        per_clip = dead[bone]
        peak = max(per_clip.values())
        movers = sorted(c for c in per_clip if per_clip[c] > 0)
        if movers:
            broken += 1
        print('   %-20s amplitud %-7s en %d clips%s'
              % ('"%s"' % bone, round(peak, 3), len(per_clip),
                 ("  ->  " + ", ".join(movers)) if movers else "  (solo restos neutros)"))
    print()
    return broken


def main():
    wanted = sys.argv[1:] or DEFAULT
    total = 0
    for label in wanted:
        if label not in PAIRS:
            sys.exit("ERROR: no conozco '%s'. Disponibles: %s" % (label, ", ".join(PAIRS)))
        anim, model = PAIRS[label]
        total += check(label, "%s/%s" % (BASE, anim), "%s/%s" % (BASE, model))
    print("Huesos inexistentes que se llevan movimiento real: %d" % total)
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
