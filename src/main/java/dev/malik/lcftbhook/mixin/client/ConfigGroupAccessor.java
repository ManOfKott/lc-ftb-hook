package dev.malik.lcftbhook.mixin.client;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Exposes the (insertion-ordered) subgroup map so the team properties menu can
 * reorder the protection sections after they have been built.
 */
@Mixin(value = ConfigGroup.class, remap = false)
public interface ConfigGroupAccessor {
    @Accessor(value = "subgroups", remap = false)
    Map<String, ConfigGroup> lcFtbHook$getSubgroups();
}
