package io.vbtm.beltstackfix.mixin;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Create's BeltInventory never validates ItemStack counts, but vanilla's ItemStack codec rejects counts
 * outside [1;99] when the belt is saved or synced, which kills the server thread. Two guards:
 * <ul>
 *   <li>{@code insert}: an oversized stack entering the belt is split into max-stack-size pieces and the
 *       producer's stack trace is logged, so the mod that handed Create the oversized stack can be identified.</li>
 *   <li>{@code tick}: a stack that was grown in place after insertion (live reference handed out by
 *       {@code ItemHandlerBeltSegment.getStackInSlot}) is split the same way on the next tick.</li>
 * </ul>
 * Nothing is deleted; the pieces travel on as separate belt items.
 */
@Mixin(value = BeltInventory.class, remap = false)
public abstract class BeltInventoryMixin {
    @Unique private static final Logger VBTM_BELTSTACKFIX$LOGGER = LoggerFactory.getLogger("vbtm-beltstack-fix");

    @Shadow @Final BeltBlockEntity belt;
    @Shadow @Final private List<TransportedItemStack> items;
    @Shadow boolean beltMovementPositive;
    @Shadow private native void insert(TransportedItemStack ts);

    @Inject(method = "insert", at = @At("HEAD"))
    private void vbtm_beltstackfix$splitOnInsert(TransportedItemStack ts, CallbackInfo ci) {
        ItemStack stack = ts.stack;
        if (stack == null || stack.isEmpty()) return;
        int max = Math.max(1, stack.getMaxStackSize());
        if (stack.getCount() <= max) return;
        VBTM_BELTSTACKFIX$LOGGER.warn("Oversized stack {} (count {} > max {}) inserted on belt {} in {}; splitting. Producer trace:",
                stack.getItem(), stack.getCount(), max, belt.getBlockPos(), belt.getLevel() == null ? "?" : belt.getLevel().dimension().location(),
                new Throwable("vbtm-beltstack-fix producer trace"));
        vbtm_beltstackfix$splitInto(ts, max, vbtm_beltstackfix$pending());
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void vbtm_beltstackfix$normalizeLiveStacks(CallbackInfo ci) {
        List<TransportedItemStack> extras = null;
        for (TransportedItemStack ts : items) {
            ItemStack stack = ts.stack;
            if (stack == null || stack.isEmpty()) continue;
            int max = Math.max(1, stack.getMaxStackSize());
            if (stack.getCount() <= max) continue;
            if (extras == null) extras = new ArrayList<>();
            VBTM_BELTSTACKFIX$LOGGER.warn("Oversized stack {} (count {} > max {}) found on belt {} in {} after insertion (mutated in place); splitting.",
                    stack.getItem(), stack.getCount(), max, belt.getBlockPos(), belt.getLevel() == null ? "?" : belt.getLevel().dimension().location());
            vbtm_beltstackfix$splitInto(ts, max, extras);
        }
        if (extras != null) {
            for (TransportedItemStack extra : extras) this.insert(extra);
            belt.notifyUpdate();
        }
    }

    @Unique private List<TransportedItemStack> vbtm_beltstackfix$pendingList;

    /** Pieces split during insert() are inserted right after the original by the same insert() call chain. */
    @Unique
    private List<TransportedItemStack> vbtm_beltstackfix$pending() {
        if (vbtm_beltstackfix$pendingList == null) vbtm_beltstackfix$pendingList = new ArrayList<>();
        return vbtm_beltstackfix$pendingList;
    }

    @Inject(method = "insert", at = @At("RETURN"))
    private void vbtm_beltstackfix$flushPending(TransportedItemStack ts, CallbackInfo ci) {
        if (vbtm_beltstackfix$pendingList == null || vbtm_beltstackfix$pendingList.isEmpty()) return;
        List<TransportedItemStack> pieces = vbtm_beltstackfix$pendingList;
        vbtm_beltstackfix$pendingList = null;
        for (TransportedItemStack piece : pieces) this.insert(piece);   // each piece is <= max, so no re-entry
    }

    @Unique
    private void vbtm_beltstackfix$splitInto(TransportedItemStack ts, int max, List<TransportedItemStack> out) {
        float step = beltMovementPositive ? -0.5f : 0.5f;   // trail the pieces behind the original
        int length = Math.max(1, belt.beltLength);
        int k = 1;
        while (ts.stack.getCount() > max) {
            TransportedItemStack piece = ts.getSimilar();
            piece.stack = ts.stack.split(max);
            float pos = ts.beltPosition + step * k++;
            piece.beltPosition = Math.max(0.0f, Math.min(length - 0.001f, pos));
            piece.prevBeltPosition = piece.beltPosition;
            out.add(piece);
        }
    }
}
