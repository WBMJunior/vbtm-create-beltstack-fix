package io.vbtm.beltstackfix.mixin;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Last line of defence: if a belt item is still oversized when it is serialised (save or client sync),
 * encode a copy clamped to the codec's limit instead of throwing. The live stack is left untouched so the
 * belt-side split in {@link BeltInventoryMixin} can still fix it on the next tick.
 */
@Mixin(value = TransportedItemStack.class, remap = false)
public abstract class TransportedItemStackMixin {
    @Unique private static final Logger VBTM_BELTSTACKFIX$LOGGER = LoggerFactory.getLogger("vbtm-beltstack-fix");
    @Unique private static final int VBTM_BELTSTACKFIX$CODEC_MAX = 99;

    @Redirect(method = "serializeNBT",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;saveOptional(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/Tag;", remap = true))
    private Tag vbtm_beltstackfix$saveClamped(ItemStack stack, HolderLookup.Provider registries) {
        if (!stack.isEmpty() && stack.getCount() > VBTM_BELTSTACKFIX$CODEC_MAX) {
            VBTM_BELTSTACKFIX$LOGGER.warn("Serialising oversized belt stack {} (count {}); writing a copy clamped to {} so the belt can save.",
                    stack.getItem(), stack.getCount(), VBTM_BELTSTACKFIX$CODEC_MAX);
            return stack.copyWithCount(VBTM_BELTSTACKFIX$CODEC_MAX).saveOptional(registries);
        }
        return stack.saveOptional(registries);
    }
}
