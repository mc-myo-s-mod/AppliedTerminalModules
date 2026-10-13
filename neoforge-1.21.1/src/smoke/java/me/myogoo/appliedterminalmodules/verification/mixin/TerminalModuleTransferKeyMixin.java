package me.myogoo.appliedterminalmodules.verification.mixin;

import me.myogoo.appliedterminalmodules.verification.JeiSmoke;
import me.myogoo.myotus.client.TerminalModuleTransferKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Drives keyboard state only in the opt-in client smoke. */
@Mixin(value = TerminalModuleTransferKey.class, remap = false)
public abstract class TerminalModuleTransferKeyMixin {
    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private static void atm$smokeKey(CallbackInfoReturnable<Boolean> cir) {
        if (JeiSmoke.transferKey != null) cir.setReturnValue(JeiSmoke.transferKey);
    }
}
