package com.evirapo.diversityofcritters.common.entity.custom;

import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.item.DOCItems;
import com.evirapo.diversityofcritters.common.item.TranquilizerHelper;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * El dardo en vuelo. Golpea como una bola de nieve —knockback y parpadeo rojo, cero daño— y si lo
 * que golpea es un critter, le administra la dosis.
 *
 * <p>Más tenso que una bola de nieve (gravedad 0.01 contra 0.03): se apunta con mira y tiene que caer
 * donde se apuntó. Se descarta a los {@link #MAX_LIFE_TICKS} para que un disparo al cielo no se quede
 * volando hasta que se descargue el chunk.
 */
public class TranquilizerDartEntity extends ThrowableItemProjectile {

    private static final int MAX_LIFE_TICKS = 100;
    /** Evento de entidad de "se ha roto", el mismo número que usan bola de nieve y huevo. */
    private static final byte EVENT_BREAK = 3;

    public TranquilizerDartEntity(EntityType<? extends TranquilizerDartEntity> type, Level level) {
        super(type, level);
    }

    public TranquilizerDartEntity(Level level, LivingEntity shooter) {
        super(DOCEntities.TRANQUILIZER_DART.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return DOCItems.TRANQUILIZER_DART.get();
    }

    @Override
    protected float getGravity() {
        return 0.01F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > MAX_LIFE_TICKS) {
            this.discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_BREAK) {
            ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, this.getItem());
            for (int i = 0; i < 8; ++i) {
                this.level().addParticle(particle, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        target.hurt(this.damageSources().thrown(this, owner), 0.0F);
        if (!this.level().isClientSide && target instanceof DiverseCritter critter) {
            TranquilizerHelper.dose(critter, owner instanceof Player player ? player : null);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, EVENT_BREAK);
            this.discard();
        }
    }
}
