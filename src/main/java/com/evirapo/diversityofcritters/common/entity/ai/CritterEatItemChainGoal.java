package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.darkblade.deluxelib.entity.ai.goal.PhasedActivityGoal;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Comer un item del suelo. Sustituye a {@link CritterEatItemGoal} en las especies que ya tienen la
 * cadena {@code eat_*} registrada.
 *
 * <p>Arregla tres cosas del goal anterior:
 *
 * <ol>
 *   <li><b>Llamaba a {@code this.stop()} desde {@code tick()}</b>, lo cual no lo quita del
 *       {@code GoalSelector}: al tick siguiente el selector volvía a llamarlo, así que
 *       {@code onStopEatingItem()} corría dos veces y el cooldown se reiniciaba dos veces.</li>
 *   <li><b>Apagaba {@code IS_EATING_PLANT} sin comprobar de quién era.</b> Si en ese momento la
 *       cadena de comer hojas era la dueña del flag, se lo pisaba y le cortaba la animación a media
 *       cadena. Ahora las dos son {@code PhasedActivityGoal} y compiten por MOVE, así que no pueden
 *       solaparse.</li>
 *   <li><b>No reproducía la cadena completa.</b> Sólo levantaba el flag, con lo que el bucle
 *       auto-arrancaba sin entrada y paraba en seco sin salida. Ahora hace entrada → bucle → salida
 *       como el resto.</li>
 * </ol>
 *
 * <p>El {@code trigger()} externo desaparece: existía para forzar la búsqueda desde
 * {@code triggerFoodSearch()}, que se llamaba con un 20% de probabilidad por tick y hacía
 * {@code navigation.stop() + moveTo()} unas cinco veces por segundo desde fuera de todo goal. Eso
 * destrozaba la ruta continuamente y hacía parpadear las animaciones de locomoción. Aquí la fase
 * APPROACH de la clase base es la dueña de la navegación, y {@link #shouldStart()} ya escanea en
 * cada evaluación.
 */
public class CritterEatItemChainGoal extends PhasedActivityGoal<DiverseCritter> {

    private static final double REACH = 1.2D;
    private static final int SEARCH_RADIUS_XZ = 32;
    private static final int SEARCH_RADIUS_Y = 8;

    private final TagKey<Item> foodTag;
    private @Nullable ItemEntity targetItem;

    public CritterEatItemChainGoal(DiverseCritter critter, TagKey<Item> foodTag) {
        super(critter, "eat_enter", "eat_loop", "eat_exit");
        this.foodTag = foodTag;
        // EATING_IDLE dura 0.5 s (10 ticks); 6 pasadas = los 60 ticks del EAT_DURATION anterior.
        repeat(6, 6);
        cooldown(100, 100);
        approach((float) REACH, 1.1D);
    }

    @Override
    protected boolean shouldStart() {
        if (mob.isNewborn() || !mob.isHungry()) return false;
        if (mob.isOrderedToSit() || mob.getSleepState() != DiverseCritter.SleepState.AWAKE) return false;
        if (mob.isDoingSpecialActivity()) return false;   // incluye trepar, planear y estar comiendo

        this.targetItem = findFoodItem();
        return this.targetItem != null;
    }

    /**
     * Durante la aproximación el item tiene que seguir existiendo — otra ardilla puede habérselo
     * comido. Una vez empezada la cadena ya no: el bocado se consumió en {@link #onActivityStart()},
     * así que exigirlo aquí cortaría la animación a mitad.
     */
    @Override
    protected boolean shouldContinue() {
        if (phase() != Phase.APPROACH) {
            return true;
        }
        return this.targetItem != null
                && this.targetItem.isAlive()
                && !this.targetItem.getItem().isEmpty();
    }

    @Override
    protected @Nullable Vec3 approachTarget() {
        return this.targetItem == null ? null : this.targetItem.position();
    }

    @Override
    protected boolean faceTarget() {
        return true;
    }

    /** Se coge el bocado justo al llegar, que es cuando arranca la animación de comer. */
    @Override
    protected void onActivityStart() {
        if (this.targetItem == null) {
            return;
        }
        ItemStack bite = this.targetItem.getItem().copy();
        bite.setCount(1);
        this.targetItem.getItem().shrink(1);
        if (this.targetItem.getItem().isEmpty()) {
            this.targetItem.discard();
        }
        this.targetItem = null;
        mob.onStartEatingItem(bite);
    }

    @Override
    protected void onActivityDone(boolean interrupted) {
        mob.onStopEatingItem();
        // Igual que antes: el hambre se saciaba al agotarse el temporizador, no al interrumpirse.
        if (!interrupted) {
            mob.onAteFood();
        }
        this.targetItem = null;
    }

    private @Nullable ItemEntity findFoodItem() {
        List<? extends ItemEntity> items = mob.level().getEntitiesOfClass(
                ItemEntity.class,
                mob.getBoundingBox().inflate(SEARCH_RADIUS_XZ, SEARCH_RADIUS_Y, SEARCH_RADIUS_XZ),
                e -> e.isAlive() && e.getItem().is(this.foodTag));
        return items.isEmpty() ? null : items.get(0);
    }
}
