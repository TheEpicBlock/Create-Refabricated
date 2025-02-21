package com.simibubi.create.content.contraptions.fluids.pipes;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY;

import com.simibubi.create.AllFluids;
import io.github.fabricators_of_create.porting_lib.transfer.fluid.FluidStack;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

public class VanillaFluidTargets {

	public static boolean shouldPipesConnectTo(BlockState state) {
		if (state.hasProperty(BlockStateProperties.LEVEL_HONEY))
			return true;
		if (state.is(BlockTags.CAULDRONS))
			return true;
		return false;
	}

	public static FluidStack drainBlock(Level level, BlockPos pos, BlockState state, boolean simulate) {
		if (state.hasProperty(BlockStateProperties.LEVEL_HONEY) && state.getValue(LEVEL_HONEY) >= 5) {
			if (!simulate)
				level.setBlock(pos, state.setValue(LEVEL_HONEY, 0), 3);
			return new FluidStack(AllFluids.HONEY.get()
				.getSource(), FluidConstants.BOTTLE);
		}

		if (state.getBlock() == Blocks.LAVA_CAULDRON) {
			if (!simulate)
				level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
			return new FluidStack(Fluids.LAVA, FluidConstants.BUCKET);
		}

		if (state.getBlock() == Blocks.WATER_CAULDRON) {
			if (!simulate)
				level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
			return new FluidStack(Fluids.WATER, FluidConstants.BUCKET);
		}

		return FluidStack.EMPTY;
	}

}
