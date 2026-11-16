package com.evirapo.diversityofcritters.common.entity.util;

import com.evirapo.diversityofcritters.common.block.BowlBlock;
import com.evirapo.diversityofcritters.common.block.CorpseBlock;
import com.evirapo.diversityofcritters.common.block.entity.CorpseBlockEntity;
import com.evirapo.diversityofcritters.common.block.BowlContent;
import com.evirapo.diversityofcritters.common.block.entity.BowlBlockEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class BowlFeedingHelper {

    /**
     * Si el critter llega al bowl para comer o beber.
     *
     * <p>El alcance sale de dónde puede pararse la navegación: la ruta acaba en la casilla de al lado
     * (centro a 0.5 del bowl) y vanilla da ese último punto por alcanzado a {@code 0.75 - ancho/2} de
     * su centro (a {@code ancho/2} si mide más de 0.75). Con un margen fijo de 0.32 alrededor del
     * cuerpo, una cría juvenil (la mitad de ancha que un adulto) se paraba a 0.725 del bowl con 0.70 de
     * alcance: el camino ya había acabado, no tocaba el bowl, y se moría de hambre al lado.
     */
    public static boolean isWithinReach(DiverseCritter critter, BlockPos bowlPos) {
        double width = critter.getBbWidth();
        double waypointSlack = width > 0.75D ? width / 2.0D : 0.75D - width / 2.0D;
        double reachFromCenter = 0.5D + waypointSlack + 0.05D;
        double inflate = Math.max(0.32D, reachFromCenter - width / 2.0D);
        return critter.getBoundingBox().inflate(inflate).intersects(new AABB(bowlPos));
    }

    public static boolean hasFoodFor(DiverseCritter critter, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CorpseBlock) {
            return critter.getDietConfig().acceptsMeat
                    && level.getBlockEntity(pos) instanceof CorpseBlockEntity corpse && corpse.portions() > 0;
        }
        if (!(state.getBlock() instanceof BowlBlock)) return false;

        BowlContent content = state.getValue(BowlBlock.CONTENT);
        CritterDietConfig diet = critter.getDietConfig();

        return switch (content) {
            case MEAT -> diet.acceptsMeat;
            case VEG  -> diet.acceptsVeg;
            case MIX  -> diet.acceptsMix;
            default   -> false;
        };
    }

    public static boolean hasWaterFor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BowlBlock)) return false;

        BowlContent content = state.getValue(BowlBlock.CONTENT);
        return content == BowlContent.WATER;
    }

    public static boolean consumeFoodFor(DiverseCritter critter, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CorpseBlock) return eatFromCorpse(critter, level, pos);
        if (!(state.getBlock() instanceof BowlBlock)) return false;

        BowlContent content = state.getValue(BowlBlock.CONTENT);
        CritterDietConfig diet = critter.getDietConfig();

        boolean accepts = switch (content) {
            case MEAT -> diet.acceptsMeat;
            case VEG  -> diet.acceptsVeg;
            case MIX  -> diet.acceptsMix;
            default   -> false;
        };
        if (!accepts) return false;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BowlBlockEntity bowlBe)) return false;

        Container inv = bowlBe.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            boolean isMeat = BowlBlockEntity.BowlLogic.isMeat(stack);
            boolean isVeg  = BowlBlockEntity.BowlLogic.isVegOrLeaves(stack);

            boolean matches = switch (content) {
                case MEAT -> isMeat;
                case VEG  -> isVeg;
                case MIX  -> (isMeat || isVeg);
                default   -> false;
            };
            if (!matches) continue;

            var foodProps = stack.isEdible() ? stack.getFoodProperties(critter) : null;
            stack.shrink(1);
            if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);

            critter.onAteFood();
            if (foodProps != null) critter.heal((float) foodProps.getNutrition());
            else critter.heal(1.0F);

            inv.setChanged();
            return true;
        }
        return false;
    }

    /** What one corpse meal heals, the same as the raw meats a bowl holds (about one heart). */
    private static final float CORPSE_MEAL_HEAL = 2.0F;

    /** One portion of a corpse: any meat eater may feed, not only the hunter. */
    private static boolean eatFromCorpse(DiverseCritter critter, Level level, BlockPos pos) {
        if (!critter.getDietConfig().acceptsMeat) return false;
        if (!(level.getBlockEntity(pos) instanceof CorpseBlockEntity corpse)) return false;
        if (!corpse.takePortion()) return false;
        critter.onAteFood();
        critter.heal(CORPSE_MEAL_HEAL);
        return true;
    }

    public static boolean consumeWaterFor(DiverseCritter critter, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BowlBlock)) return false;

        BowlContent content = state.getValue(BowlBlock.CONTENT);
        if (content != BowlContent.WATER) return false;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BowlBlockEntity bowlBe)) return false;

        int chargesBefore = bowlBe.getWaterCharges();
        if (chargesBefore <= 0) {
            level.setBlock(pos, state.setValue(BowlBlock.CONTENT, BowlContent.EMPTY), 3);
            bowlBe.setWaterCharges(0);
            return false;
        }

        critter.setThirst(critter.maxThirst());
        bowlBe.consumeWaterCharge();
        int chargesAfter = bowlBe.getWaterCharges();

        if (chargesAfter < chargesBefore
                && chargesAfter % BowlBlockEntity.WATER_CHARGES_PER_BOTTLE == 0) {
            consumeOneWaterContainer(bowlBe, level, pos);
        }

        if (chargesAfter <= 0) {
            Container inv = bowlBe.getInventory();
            int waterUnits = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && BowlBlockEntity.BowlLogic.isWater(s)) waterUnits += s.getCount();
            }
            if (waterUnits <= 0) {
                level.setBlock(pos, state.setValue(BowlBlock.CONTENT, BowlContent.EMPTY), 3);
                bowlBe.setWaterCharges(0);
            } else {
                int newCharges = waterUnits * BowlBlockEntity.WATER_CHARGES_PER_BOTTLE;
                bowlBe.setWaterCharges(newCharges);
                level.setBlock(pos, state.setValue(BowlBlock.CONTENT, BowlContent.WATER), 3);
            }
        }
        return true;
    }


    private static void consumeOneWaterContainer(BowlBlockEntity bowlBe, Level level, BlockPos pos) {
        Container inv = bowlBe.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (!BowlBlockEntity.BowlLogic.isWater(stack)) continue;

            if (stack.is(Items.POTION)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
                placeOneItem(inv, new ItemStack(Items.GLASS_BOTTLE));
                inv.setChanged();
                return;
            }
        }
    }

    private static void placeOneItem(Container inv, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack current = inv.getItem(i);
            if (current.isEmpty()) {
                inv.setItem(i, stack);
                return;
            }
        }
    }
}
