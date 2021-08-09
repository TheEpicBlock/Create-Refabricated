package com.simibubi.create.lib.helper;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.lib.mixin.accessor.KeyBindingAccessor;
import com.simibubi.create.lib.utility.MixinHelper;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;

@Environment(EnvType.CLIENT)
public final class KeyBindingHelper {
	public static InputConstants.Key getKeyCode(KeyMapping keyBinding) {
		return get(keyBinding).create$key();
	}

	private static KeyBindingAccessor get(KeyMapping keyBinding) {
		return MixinHelper.cast(keyBinding);
	}

	private KeyBindingHelper() { }
}
