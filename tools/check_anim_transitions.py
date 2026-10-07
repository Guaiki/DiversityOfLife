"""Revisa las transiciones de las cadenas de animacion de una especie.

    python tools/check_anim_transitions.py              # Sifaka
    python tools/check_anim_transitions.py Civet        # otra especie de CHAINS

Dos comprobaciones, las dos por fallos que ya se vieron en juego:

1. **Clips de un disparo marcados como bucle.** El mezclador de DeluxeLib hace
   `time %= lengthInSeconds` en las definiciones looping. Un clip de entrada o de salida sigue
   avanzando mientras se desvanece (follow-through), asi que si esta marcado como bucle vuelve a su
   frame 0 a mitad del fundido y se ve un salto atras. Blockbench marca muchos clips asi al exportar.

2. **Saltos de pose en las fronteras.** Para cada cadena compara la pose del final de un clip con la
   del principio del siguiente (idle -> entrada, entrada -> bucle, bucle -> salida, salida -> idle).
   El fundido los suaviza, pero un salto grande en una frontera corta se ve como un tiron. Solo
   cuenta huesos que existen en el modelo: los demas se descartan en juego (ver check_anim_bones.py).

La interpolacion se aproxima a lineal entre keyframes; para comparar fronteras basta.
"""
import re
import sys

ANIMS = "src/main/java/com/evirapo/diversityofcritters/client/animations/{}Anims.java"
MODEL = "src/main/java/com/evirapo/diversityofcritters/client/models/{}Model.java"

# especie -> (clip de reposo, [(nombre de la cadena, entrada, bucle, salida)], clips sueltos de un disparo)
CHAINS = {
    "Sifaka": ("Idle", [
        ("ordered_sit / idle_sit", "SIT_STARTING", "SIT_IDLE", "SIT_ENDING"),
        ("idle_stand_up (lay)", "LAY_STARTING", "LAY_IDLE", "LAY_ENDING"),
        ("drink", "DRINK_STARTING", "DRINK_IDLE", "DRINK_ENDING"),
        ("eat", "EATING_STARTING", "EATING_IDLE", "EATING_ENDING"),
        ("clean", "GROOMING_STARTING", "GROOMING_IDLE", "GROOMING_ENDING"),
        ("sleep", "preparing_sleep", "Sleep", "awakening"),
    ], ["stand_up", "SniffRight", "SniffRight2", "Attack"]),
    "Civet": ("IDLE", [
        ("sit", "SIT_STARTING", "SIT_IDLE", "SIT_ENDING"),
        ("lay", "LAY_STARTING", "LAY_IDLE", "LAY_ENDING"),
        ("drink", "DRINK_STARTING", "DRINK_IDLE", "DRINK_ENDING"),
        ("clean", "GROOMING_STARTING", "GROOMING_IDLE", "GROOMING_ENDING"),
        ("sleep", "PREPARING_SLEEP", "SLEEP", "AWAKENING"),
    ], ["STAND_UP", "SNIFF_LEFT", "SNIFF_RIGHT", "ATTACK"]),
}

JUMP_DEG = 20.0   # rotacion: salto que ya se nota en un fundido de 120-250 ms
JUMP_POS = 1.5    # posicion, unidades de modelo

CLIP_RE = re.compile(r"public static final AnimationDefinition (\w+)\s*=\s*AnimationDefinition\.Builder"
                     r"\.withLength\(([\d.]+)F?\)(\.looping\(\))?(.*?)\.build\(\);", re.S)
ANIM_RE = re.compile(r'\.addAnimation\("([^"]+)",\s*new AnimationChannel\(AnimationChannel\.Targets\.(\w+),(.*?)\)\)', re.S)
KEY_RE = re.compile(r"new Keyframe\(([-\d.]+)F?,\s*KeyframeAnimations\.\w+\(([-\d.]+)F?,\s*([-\d.]+)F?,\s*([-\d.]+)F?\)")


def parse_clips(path):
    src = open(path, encoding="utf-8").read()
    clips = {}
    for m in CLIP_RE.finditer(src):
        channels = {}
        for a in ANIM_RE.finditer(m.group(4)):
            keys = [(float(k[0]), (float(k[1]), float(k[2]), float(k[3]))) for k in KEY_RE.findall(a.group(3))]
            if keys:
                channels.setdefault((a.group(1), a.group(2)), []).extend(sorted(keys))
        clips[m.group(1)] = {"length": float(m.group(2)), "looping": bool(m.group(3)), "channels": channels}
    return clips


def model_bones(path):
    return set(re.findall(r'getChild\("([^"]+)"\)', open(path, encoding="utf-8").read()))


def sample(keys, t):
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return tuple(a + (b - a) * f for a, b in zip(v0, v1))
    return keys[-1][1]


def pose(clip, t, bones):
    """{(hueso, canal): valor}, con los canales que el clip no anima en su neutro."""
    out = {}
    for (bone, target), keys in clip["channels"].items():
        if bone in bones:
            out[(bone, target)] = sample(keys, t)
    return out


def neutral(target):
    return (1.0, 1.0, 1.0) if target == "SCALE" else (0.0, 0.0, 0.0)


def biggest_jump(a, b):
    worst = None
    for key in set(a) | set(b):
        va = a.get(key, neutral(key[1]))
        vb = b.get(key, neutral(key[1]))
        d = max(abs(x - y) for x, y in zip(va, vb))
        limit = JUMP_DEG if key[1] == "ROTATION" else JUMP_POS
        if key[1] == "SCALE":
            limit = 0.2
        if d > limit and (worst is None or d / limit > worst[0]):
            worst = (d / limit, key, d)
    return worst


def main():
    species = sys.argv[1] if len(sys.argv) > 1 else "Sifaka"
    rest, chains, singles = CHAINS[species]
    clips = parse_clips(ANIMS.format(species))
    bones = model_bones(MODEL.format(species))
    idle0 = pose(clips[rest], 0.0, bones)

    print("== %s: clips de un disparo marcados como bucle" % species)
    one_shot = [c for _, e, _, x in chains for c in (e, x)] + singles
    bad = [c for c in one_shot if clips[c]["looping"]]
    print("   " + (", ".join(bad) if bad else "ninguno"))

    print("\n== %s: saltos de pose en las fronteras (rotacion > %.0f grados, posicion > %.1f)" % (species, JUMP_DEG, JUMP_POS))
    for name, enter, loop, exit_ in chains:
        e, l, x = clips[enter], clips[loop], clips[exit_]
        steps = [
            ("reposo -> entrada", idle0, pose(e, 0.0, bones)),
            ("entrada -> bucle", pose(e, e["length"], bones), pose(l, 0.0, bones)),
            ("bucle -> salida", pose(l, 0.0, bones), pose(x, 0.0, bones)),
            ("salida -> reposo", pose(x, x["length"], bones), idle0),
        ]
        print("   " + name)
        for label, a, b in steps:
            j = biggest_jump(a, b)
            if j is None:
                print("      %-18s ok" % label)
            else:
                print("      %-18s SALTO  %s %s  %.1f" % (label, j[1][0], j[1][1].lower(), j[2]))


if __name__ == "__main__":
    main()
