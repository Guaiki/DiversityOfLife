package com.evirapo.diversityofcritters.mixin;

import com.evirapo.diversityofcritters.common.item.custom.TranquilizerRifleItem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Apuntar con el rifle tranquilizante es mirar por el catalejo.
 *
 * <p>Vanilla responde {@code isScoping()} sólo para {@code Items.SPYGLASS}, y de esa respuesta cuelga
 * todo lo del catalejo: el overlay ({@code ForgeGui}), el zoom ({@code AbstractClientPlayer}), la
 * sensibilidad del ratón ({@code MouseHandler}) y no dibujar la mano ({@code ItemInHandRenderer}).
 * Contestar que sí también con el rifle trae las cuatro cosas de una vez, en vez de copiarlas por
 * eventos de Forge, que además no cubren la sensibilidad.
 *
 * <p>Común y no sólo de cliente porque {@code Player} lo es; en servidor nadie lo consulta.
 */
@Mixin(Player.class)
public abstract class PlayerScopingMixin {

    @Inject(method = "isScoping", at = @At("HEAD"), cancellable = true)
    private void doc$scopeWithRifle(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.isUsingItem() && self.getUseItem().getItem() instanceof TranquilizerRifleItem) {
            cir.setReturnValue(true);
        }
    }
}
