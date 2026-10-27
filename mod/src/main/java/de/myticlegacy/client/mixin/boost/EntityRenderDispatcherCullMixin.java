package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.Culling;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS-Boost: weit entfernte oder verdeckte Entities gar nicht erst zeichnen */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherCullMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void mytic$cull(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (Culling.cullEntity(entity)) cir.setReturnValue(false);
    }
}
