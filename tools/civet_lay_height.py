# -*- coding: utf-8 -*-
"""Ajusta la altura del suelo de las poses tumbadas de la civeta.

    python tools/civet_lay_height.py            # muestra el valor actual
    python tools/civet_lay_height.py -9         # baja los seis clips a -9
    python tools/civet_lay_height.py -12 SLEEP  # baja solo ese clip

Toca UNICAMENTE el canal de posicion del hueso raiz (BandedPlamCivet) dentro de los seis clips
de la familia tumbado/dormido, y solo los keyframes cuya Y no sea 0. Los keyframes a 0 son los
extremos "de pie" de las transiciones: si se mueven, el bicho pega un salto al acostarse o al
levantarse.

NO uses buscar-y-reemplazar a mano sobre el literal: `posVec(0.0F, -7.0F, 0.0F)` aparece decenas
de veces en el archivo, en otros huesos de otras animaciones que estan bien como estan.

Convenio: mas negativo BAJA el modelo. 1 unidad = 1/16 de bloque.

Cuidado: `swap_civet_anims.py` regenera CivetAnims.java entero desde el export de Blockbench, asi
que un reexport devuelve el valor original. Lo definitivo es corregir la altura en Blockbench.
"""
import io
import os
import re
import sys

ANIMS = os.path.join("src", "main", "java", "com", "evirapo", "diversityofcritters",
                     "client", "animations", "CivetAnims.java")

CLIPS = ["LAY_STARTING", "LAY_IDLE", "LAY_ENDING", "PREPARING_SLEEP", "SLEEP", "AWAKENING"]
ROOT_CHANNEL = ('.addAnimation("BandedPlamCivet", '
                'new AnimationChannel(AnimationChannel.Targets.POSITION')
KEYFRAME = re.compile(r"posVec\(0\.0F, (-?\d+(?:\.\d+)?)F, 0\.0F\)")


def root_channel(body):
    """Trozo del clip que corresponde al canal de posicion de la raiz."""
    start = body.index(ROOT_CHANNEL)
    return start, body.index(".addAnimation(", start + 10)


def main():
    target = None
    if len(sys.argv) > 1:
        target = float(sys.argv[1])
        if target > 0:
            sys.exit("La Y baja con valores NEGATIVOS. Querias %s?" % -target)

    # Filtro opcional de clips. Dejar unos a una altura y otros a otra hace que el bicho pegue un
    # salto en la transicion, asi que es para probar, no para dejarlo asi.
    only = [c.upper() for c in sys.argv[2:]] or None
    if only:
        desconocidos = [c for c in only if c not in CLIPS]
        if desconocidos:
            sys.exit("clips desconocidos: %s (validos: %s)" % (desconocidos, CLIPS))

    source = io.open(ANIMS, encoding="utf-8").read()
    out = []
    cursor = 0
    changed = 0

    for clip in CLIPS:
        match = re.search(r"AnimationDefinition " + clip + r" = ", source)
        if not match:
            sys.exit("clip no encontrado: " + clip)
        end = source.index(".build();", match.start())
        body = source[match.start():end]
        a, b = root_channel(body)
        channel = body[a:b]

        values = [float(v) for v in KEYFRAME.findall(channel)]
        down = sorted({v for v in values if v != 0.0})
        print("%-16s actual: %s" % (clip, down if down else "(sin desplazamiento)"))

        if target is not None and (only is None or clip in only):
            def repl(m):
                return m.group(0) if float(m.group(1)) == 0.0 \
                    else "posVec(0.0F, %.1fF, 0.0F)" % target
            new_channel, n = KEYFRAME.subn(repl, channel)
            changed += sum(1 for v in values if v != 0.0)
            out.append(source[cursor:match.start()] + body[:a] + new_channel + body[b:])
            cursor = end

    if target is not None:
        out.append(source[cursor:])
        io.open(ANIMS, "w", encoding="utf-8", newline="").write("".join(out))
        print("\n%d keyframes -> %.1f  (recompila con ./gradlew runClient)" % (changed, target))


if __name__ == "__main__":
    main()
