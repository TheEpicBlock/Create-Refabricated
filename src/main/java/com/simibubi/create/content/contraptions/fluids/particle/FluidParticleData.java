package com.simibubi.create.content.contraptions.fluids.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.content.contraptions.particle.ICustomParticleData;
import io.github.fabricators_of_create.porting_lib.transfer.fluid.FluidStack;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.material.Fluids;

public class FluidParticleData implements ParticleOptions, ICustomParticleData<FluidParticleData> {

	private ParticleType<FluidParticleData> type;
	private FluidStack fluid;

	public FluidParticleData() {}

	@SuppressWarnings("unchecked")
	public FluidParticleData(ParticleType<?> type, FluidStack fluid) {
		this.type = (ParticleType<FluidParticleData>) type;
		this.fluid = fluid;
	}

	@Override
	@Environment(EnvType.CLIENT)
	public ParticleProvider<FluidParticleData> getFactory() {
		return this::create;
	}

	// fabric: lambda funk
	@Environment(EnvType.CLIENT)
	private Particle create(FluidParticleData type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
		return FluidStackParticle.create(type.type, level, type.fluid, x, y, z, xSpeed, ySpeed, zSpeed);
	}

	@Override
	public ParticleType<?> getType() {
		return type;
	}

	@Override
	public void writeToNetwork(FriendlyByteBuf buffer) {
		fluid.toBuffer(buffer);
	}

	@Override
	public String writeToString() {
		return Registry.PARTICLE_TYPE.getKey(type) + " " + Registry.FLUID.getKey(fluid.getFluid());
	}

	public static final Codec<FluidParticleData> CODEC = RecordCodecBuilder.create(i -> i
		.group(FluidStack.CODEC.fieldOf("fluid")
			.forGetter(p -> p.fluid))
		.apply(i, fs -> new FluidParticleData(AllParticleTypes.FLUID_PARTICLE.get(), fs)));

	public static final Codec<FluidParticleData> BASIN_CODEC = RecordCodecBuilder.create(i -> i
		.group(FluidStack.CODEC.fieldOf("fluid")
			.forGetter(p -> p.fluid))
		.apply(i, fs -> new FluidParticleData(AllParticleTypes.BASIN_FLUID.get(), fs)));

	public static final Codec<FluidParticleData> DRIP_CODEC = RecordCodecBuilder.create(i -> i
		.group(FluidStack.CODEC.fieldOf("fluid")
			.forGetter(p -> p.fluid))
		.apply(i, fs -> new FluidParticleData(AllParticleTypes.FLUID_DRIP.get(), fs)));

	public static final ParticleOptions.Deserializer<FluidParticleData> DESERIALIZER =
		new ParticleOptions.Deserializer<FluidParticleData>() {

			// TODO Fluid particles on command
			public FluidParticleData fromCommand(ParticleType<FluidParticleData> particleTypeIn, StringReader reader)
				throws CommandSyntaxException {
				return new FluidParticleData(particleTypeIn, new FluidStack(Fluids.WATER, 1));
			}

			public FluidParticleData fromNetwork(ParticleType<FluidParticleData> particleTypeIn, FriendlyByteBuf buffer) {
				return new FluidParticleData(particleTypeIn, FluidStack.fromBuffer(buffer));
			}
		};

	@Override
	public Deserializer<FluidParticleData> getDeserializer() {
		return DESERIALIZER;
	}

	@Override
	public Codec<FluidParticleData> getCodec(ParticleType<FluidParticleData> type) {
		if (type == AllParticleTypes.BASIN_FLUID.get())
			return BASIN_CODEC;
		if (type == AllParticleTypes.FLUID_DRIP.get())
			return DRIP_CODEC;
		return CODEC;
	}

}
