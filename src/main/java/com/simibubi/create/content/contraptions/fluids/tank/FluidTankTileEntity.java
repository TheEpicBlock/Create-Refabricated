package com.simibubi.create.content.contraptions.fluids.tank;

import static java.lang.Math.abs;

import java.util.List;

import javax.annotation.Nullable;

import com.simibubi.create.content.contraptions.fluids.tank.FluidTankBlock.Shape;
import com.simibubi.create.content.contraptions.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.config.AllConfigs;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.tileEntity.IMultiTileContainer;
import com.simibubi.create.foundation.tileEntity.SmartTileEntity;
import com.simibubi.create.foundation.tileEntity.TileEntityBehaviour;
import com.simibubi.create.foundation.utility.animation.InterpolatedChasingValue;

import io.github.fabricators_of_create.porting_lib.block.CustomRenderBoundingBoxBlockEntity;
import io.github.fabricators_of_create.porting_lib.transfer.TransferUtil;
import io.github.fabricators_of_create.porting_lib.transfer.fluid.FluidStack;
import io.github.fabricators_of_create.porting_lib.transfer.fluid.FluidTank;
import io.github.fabricators_of_create.porting_lib.transfer.fluid.FluidTransferable;
import io.github.fabricators_of_create.porting_lib.transfer.fluid.IFluidHandler;
import io.github.fabricators_of_create.porting_lib.util.FluidTileDataHandler;
import io.github.fabricators_of_create.porting_lib.util.FluidUtil;
import io.github.fabricators_of_create.porting_lib.util.LazyOptional;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class FluidTankTileEntity extends SmartTileEntity implements IHaveGoggleInformation, IMultiTileContainer, FluidTransferable, CustomRenderBoundingBoxBlockEntity {

	private static final int MAX_SIZE = 3;

	protected LazyOptional<IFluidHandler> fluidCapability;
	protected boolean forceFluidLevelUpdate;
	protected FluidTank tankInventory;
	protected BlockPos controller;
	protected BlockPos lastKnownPos;
	protected boolean updateConnectivity;
	protected boolean window;
	protected int luminosity;
	protected int width;
	protected int height;

	private static final int SYNC_RATE = 8;
	protected int syncCooldown;
	protected boolean queuedSync;

	// For rendering purposes only
	private InterpolatedChasingValue fluidLevel;

	public FluidTankTileEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		tankInventory = createInventory();
		fluidCapability = LazyOptional.of(() -> tankInventory);
		forceFluidLevelUpdate = true;
		updateConnectivity = false;
		window = true;
		height = 1;
		width = 1;
		refreshCapability();
	}

	protected SmartFluidTank createInventory() {
		return new SmartFluidTank(getCapacityMultiplier(), this::onFluidStackChanged);
	}

	protected void updateConnectivity() {
		updateConnectivity = false;
		if (level.isClientSide)
			return;
		if (!isController())
			return;
		FluidTankConnectivityHandler.formTanks(this);
	}

	@Override
	public void tick() {
		super.tick();
		if (syncCooldown > 0) {
			syncCooldown--;
			if (syncCooldown == 0 && queuedSync)
				sendData();
		}

		if (lastKnownPos == null)
			lastKnownPos = getBlockPos();
		else if (!lastKnownPos.equals(worldPosition) && worldPosition != null) {
			onPositionChanged();
			return;
		}

		if (updateConnectivity)
			updateConnectivity();
		if (fluidLevel != null)
			fluidLevel.tick();
		if (isController() && !level.isClientSide)
			FluidTileDataHandler.sendDataToClients((ServerLevel) level, this);
	}

	@Override
	public BlockPos getLastKnownPos() {
		return lastKnownPos;
	}

	@Override
	public boolean isController() {
		return controller == null
			|| worldPosition.getX() == controller.getX() && worldPosition.getY() == controller.getY() && worldPosition.getZ() == controller.getZ();
	}

	@Override
	public void initialize() {
		super.initialize();
		sendData();
		if (level.isClientSide)
			invalidateRenderBoundingBox();
	}

	private void onPositionChanged() {
		removeController(true);
		lastKnownPos = worldPosition;
	}

	protected void onFluidStackChanged(FluidStack newFluidStack) {
		if (!hasLevel())
			return;

		int luminosity = (int) (FluidUtil.getLuminosity(newFluidStack.getFluid()) / 1.2f);
		boolean reversed = false; // FIXME: there's no way to get this server-side currently.
		int maxY = (int) ((getFillState() * height) + 1);

		for (int yOffset = 0; yOffset < height; yOffset++) {
			boolean isBright = reversed ? (height - yOffset <= maxY) : (yOffset < maxY);
			int actualLuminosity = isBright ? luminosity : luminosity > 0 ? 1 : 0;

			for (int xOffset = 0; xOffset < width; xOffset++) {
				for (int zOffset = 0; zOffset < width; zOffset++) {
					BlockPos pos = this.worldPosition.offset(xOffset, yOffset, zOffset);
					FluidTankTileEntity tankAt = FluidTankConnectivityHandler.anyTankAt(level, pos);
					if (tankAt == null)
						continue;
					level.updateNeighbourForOutputSignal(pos, tankAt.getBlockState()
						.getBlock());
					if (tankAt.luminosity == actualLuminosity)
						continue;
					tankAt.setLuminosity(actualLuminosity);
				}
			}
		}

		if (!level.isClientSide) {
			setChanged();
			sendData();
		}

		if (isVirtual()) {
			if (fluidLevel == null)
				fluidLevel = new InterpolatedChasingValue().start(getFillState());
			fluidLevel.target(getFillState());
		}
	}

	protected void setLuminosity(int luminosity) {
		if (level.isClientSide)
			return;
		if (this.luminosity == luminosity)
			return;
		this.luminosity = luminosity;
		updateStateLuminosity();
	}

	protected void updateStateLuminosity() {
		if (level.isClientSide)
			return;
		int actualLuminosity = luminosity;
		FluidTankTileEntity controllerTE = getControllerTE();
		if (controllerTE == null || !controllerTE.window)
			actualLuminosity = 0;
		BlockState state = getBlockState();
		if (state.getValue(FluidTankBlock.LIGHT_LEVEL) != actualLuminosity) {
			level.setBlock(worldPosition, state.setValue(FluidTankBlock.LIGHT_LEVEL, actualLuminosity), 22);
		}
	}

	public FluidTankTileEntity getControllerTE() {
		if (isController())
			return this;
		BlockEntity tileEntity = level.getBlockEntity(controller);
		if (tileEntity instanceof FluidTankTileEntity)
			return (FluidTankTileEntity) tileEntity;
		return null;
	}

	public void applyFluidTankSize(int blocks) {
		tankInventory.setCapacity(blocks * getCapacityMultiplier());
		long overflow = tankInventory.getFluidAmount() - tankInventory.getCapacity();
		if (overflow > 0)
			tankInventory.drain(overflow, false);
		forceFluidLevelUpdate = true;
	}

	public void removeController(boolean keepFluids) {
		if (level.isClientSide)
			return;
		updateConnectivity = true;
		if (!keepFluids)
			applyFluidTankSize(1);
		controller = null;
		width = 1;
		height = 1;
		onFluidStackChanged(tankInventory.getFluid());

		BlockState state = getBlockState();
		if (FluidTankBlock.isTank(state)) {
			state = state.setValue(FluidTankBlock.BOTTOM, true);
			state = state.setValue(FluidTankBlock.TOP, true);
			state = state.setValue(FluidTankBlock.SHAPE, window ? Shape.WINDOW : Shape.PLAIN);
			getLevel().setBlock(worldPosition, state, 22);
		}

		refreshCapability();
		setChanged();
		sendData();
	}

	public void toggleWindows() {
		FluidTankTileEntity te = getControllerTE();
		if (te == null)
			return;
		te.setWindows(!te.window);
	}

	public void sendDataImmediately() {
		syncCooldown = 0;
		queuedSync = false;
		sendData();
	}

	@Override
	public void sendData() {
		if (syncCooldown > 0) {
			queuedSync = true;
			return;
		}
		super.sendData();
		queuedSync = false;
		syncCooldown = SYNC_RATE;
	}

	public void setWindows(boolean window) {
		this.window = window;
		for (int yOffset = 0; yOffset < height; yOffset++) {
			for (int xOffset = 0; xOffset < width; xOffset++) {
				for (int zOffset = 0; zOffset < width; zOffset++) {

					BlockPos pos = this.worldPosition.offset(xOffset, yOffset, zOffset);
					BlockState blockState = level.getBlockState(pos);
					if (!FluidTankBlock.isTank(blockState))
						continue;

					Shape shape = Shape.PLAIN;
					if (window) {
						// SIZE 1: Every tank has a window
						if (width == 1)
							shape = Shape.WINDOW;
						// SIZE 2: Every tank has a corner window
						if (width == 2)
							shape = xOffset == 0 ? zOffset == 0 ? Shape.WINDOW_NW : Shape.WINDOW_SW
								: zOffset == 0 ? Shape.WINDOW_NE : Shape.WINDOW_SE;
						// SIZE 3: Tanks in the center have a window
						if (width == 3 && abs(abs(xOffset) - abs(zOffset)) == 1)
							shape = Shape.WINDOW;
					}

					level.setBlock(pos, blockState.setValue(FluidTankBlock.SHAPE, shape), 22);
					FluidTankTileEntity tankAt = FluidTankConnectivityHandler.anyTankAt(level, pos);
					if (tankAt != null)
						tankAt.updateStateLuminosity();
				}
			}
		}
	}

	@Override
	public void setController(BlockPos controller) {
		if (level.isClientSide && !isVirtual())
			return;
		if (controller.equals(this.controller))
			return;
		this.controller = controller;
		refreshCapability();
		setChanged();
		sendData();
	}

	private void refreshCapability() {
		LazyOptional<IFluidHandler> oldCap = fluidCapability;
		fluidCapability = LazyOptional.of(() -> isController() ? tankInventory
			: getControllerTE() != null ? getControllerTE().tankInventory : new FluidTank(0));
		oldCap.invalidate();
	}

	@Override
	public BlockPos getController() {
		return isController() ? worldPosition : controller;
	}

	@Override
	protected AABB createRenderBoundingBox() {
		if (isController())
			return super.createRenderBoundingBox().expandTowards(width - 1, height - 1, width - 1);
		else
			return super.createRenderBoundingBox();
	}

	@Nullable
	public FluidTankTileEntity getOtherFluidTankTileEntity(Direction direction) {
		BlockEntity otherTE = level.getBlockEntity(worldPosition.relative(direction));
		if (otherTE instanceof FluidTankTileEntity)
			return (FluidTankTileEntity) otherTE;
		return null;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		FluidTankTileEntity controllerTE = getControllerTE();
		if (controllerTE == null)
			return false;
		return containedFluidTooltip(tooltip, isPlayerSneaking,
			TransferUtil.getFluidHandler(controllerTE));
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);

		BlockPos controllerBefore = controller;
		int prevSize = width;
		int prevHeight = height;
		int prevLum = luminosity;

		updateConnectivity = compound.contains("Uninitialized");
		luminosity = compound.getInt("Luminosity");
		controller = null;
		lastKnownPos = null;

		if (compound.contains("LastKnownPos"))
			lastKnownPos = NbtUtils.readBlockPos(compound.getCompound("LastKnownPos"));
		if (compound.contains("Controller"))
			controller = NbtUtils.readBlockPos(compound.getCompound("Controller"));

		if (isController()) {
			window = compound.getBoolean("Window");
			width = compound.getInt("Size");
			height = compound.getInt("Height");
			tankInventory.setCapacity(getTotalTankSize() * getCapacityMultiplier());
			tankInventory.readFromNBT(compound.getCompound("TankContent"));
			if (tankInventory.getSpace() < 0)
				tankInventory.drain(-tankInventory.getSpace(), false);
		}

		if (compound.contains("ForceFluidLevel") || fluidLevel == null)
			fluidLevel = new InterpolatedChasingValue().start(getFillState())
				.withSpeed(1 / 2f);

		if (!clientPacket)
			return;

		boolean changeOfController =
			controllerBefore == null ? controller != null : !controllerBefore.equals(controller);
		if (changeOfController || prevSize != width || prevHeight != height) {
			if (hasLevel())
				level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
			if (isController())
				tankInventory.setCapacity(getCapacityMultiplier() * getTotalTankSize());
			invalidateRenderBoundingBox();
		}
		if (isController()) {
			float fillState = getFillState();
			if (compound.contains("ForceFluidLevel") || fluidLevel == null)
				fluidLevel = new InterpolatedChasingValue().start(fillState);
			fluidLevel.target(fillState);
		}
		if (luminosity != prevLum && hasLevel())
			level.getChunkSource()
				.getLightEngine()
				.checkBlock(worldPosition);

		if (compound.contains("LazySync"))
			fluidLevel.withSpeed(compound.contains("LazySync") ? 1 / 8f : 1 / 2f);
	}

	public float getFillState() {
		return (float) tankInventory.getFluidAmount() / tankInventory.getCapacity();
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		if (updateConnectivity)
			compound.putBoolean("Uninitialized", true);
		if (lastKnownPos != null)
			compound.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
		if (!isController())
			compound.put("Controller", NbtUtils.writeBlockPos(controller));
		if (isController()) {
			compound.putBoolean("Window", window);
			compound.put("TankContent", tankInventory.writeToNBT(new CompoundTag()));
			compound.putInt("Size", width);
			compound.putInt("Height", height);
		}
		compound.putInt("Luminosity", luminosity);
		super.write(compound, clientPacket);

		if (!clientPacket)
			return;
		if (forceFluidLevelUpdate)
			compound.putBoolean("ForceFluidLevel", true);
		if (queuedSync)
			compound.putBoolean("LazySync", true);
		forceFluidLevelUpdate = false;
	}

//	@Nonnull
//	@Override
//	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
//		if (!fluidCapability.isPresent())
//			refreshCapability();
//		if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
//			return fluidCapability.cast();
//		return super.getCapability(cap, side);
//	}

	@Override
	public void setRemoved() {
		super.setRemoved();
	}

	@Override
	public void addBehaviours(List<TileEntityBehaviour> behaviours) {}

	public FluidTank getTankInventory() {
		return tankInventory;
	}

	public int getTotalTankSize() {
		return width * width * height;
	}

	public static int getMaxSize() {
		return MAX_SIZE;
	}

	public static long getCapacityMultiplier() {
		return AllConfigs.SERVER.fluids.fluidTankCapacity.get() * FluidConstants.BUCKET;
	}

	public static int getMaxHeight() {
		return AllConfigs.SERVER.fluids.fluidTankMaxHeight.get();
	}

	public InterpolatedChasingValue getFluidLevel() {
		return fluidLevel;
	}

	public void setFluidLevel(InterpolatedChasingValue fluidLevel) {
		this.fluidLevel = fluidLevel;
	}

	@Nullable
	@Override
	public LazyOptional<IFluidHandler> getFluidHandler(@Nullable Direction direction) {
		if (!fluidCapability.isPresent())
			refreshCapability();
		return fluidCapability.cast();
	}
}
