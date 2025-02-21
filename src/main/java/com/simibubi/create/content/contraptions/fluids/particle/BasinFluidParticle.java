package com.simibubi.create.content.contraptions.fluids.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Quaternion;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.processing.BasinTileEntity;
import com.simibubi.create.foundation.utility.VecHelper;
import io.github.fabricators_of_create.porting_lib.transfer.fluid.FluidStack;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class BasinFluidParticle extends FluidStackParticle {

	BlockPos basinPos;
	Vec3 targetPos;
	Vec3 centerOfBasin;
	float yOffset;

	public BasinFluidParticle(ClientLevel world, FluidStack fluid, double x, double y, double z, double vx, double vy,
		double vz) {
		super(world, fluid, x, y, z, vx, vy, vz);
		gravity = 0;
		xd = 0;
		yd = 0;
		zd = 0;
		yOffset = world.random.nextFloat() * 1 / 32f;
		y += yOffset;
		quadSize = 0;
		lifetime = 60;
		Vec3 currentPos = new Vec3(x, y, z);
		basinPos = new BlockPos(currentPos);
		centerOfBasin = VecHelper.getCenterOf(basinPos);

		if (vx != 0) {
			lifetime = 20;
			Vec3 centerOf = VecHelper.getCenterOf(basinPos);
			Vec3 diff = currentPos.subtract(centerOf)
				.multiply(1, 0, 1)
				.normalize()
				.scale(.375);
			targetPos = centerOf.add(diff);
			xo = x = centerOfBasin.x;
			zo = z = centerOfBasin.z;
		}
	}

	@Override
	public void tick() {
		super.tick();
		quadSize = targetPos != null ? Math.max(1 / 32f, ((1f * age) / lifetime) / 8)
			: 1 / 8f * (1 - ((Math.abs(age - (lifetime / 2)) / (1f * lifetime))));

		if (age % 2 == 0) {
			if (!AllBlocks.BASIN.has(level.getBlockState(basinPos))) {
				remove();
				return;
			}

			BlockEntity tileEntity = level.getBlockEntity(basinPos);
			if (tileEntity instanceof BasinTileEntity) {
				float totalUnits = ((BasinTileEntity) tileEntity).getTotalFluidUnits(0);
				if (totalUnits < 1)
					totalUnits = 0;
				float fluidLevel = Mth.clamp(totalUnits / 2000, 0, 1);
				y = 2 / 16f + basinPos.getY() + 12 / 16f * fluidLevel + yOffset;
			}

		}

		if (targetPos != null) {
			float progess = (1f * age) / lifetime;
			Vec3 currentPos = centerOfBasin.add(targetPos.subtract(centerOfBasin)
				.scale(progess));
			x = currentPos.x;
			z = currentPos.z;
		}
	}

	@Override
	public void render(VertexConsumer vb, Camera info, float pt) {
		Quaternion rotation = info.rotation();
		Quaternion prevRotation = new Quaternion(rotation);
		rotation.set(1, 0, 0, 1);
		rotation.normalize();
		super.render(vb, info, pt);
		rotation.set(0, 0, 0, 1);
		rotation.mul(prevRotation);
	}

	@Override
	protected boolean canEvaporate() {
		return false;
	}

}
