package com.Da_Technomancer.crossroads.API.rotary;

import com.Da_Technomancer.crossroads.API.Capabilities;
import com.Da_Technomancer.crossroads.API.packets.CRPackets;
import com.Da_Technomancer.crossroads.API.packets.SendMasterKeyToClient;
import com.Da_Technomancer.crossroads.CRConfig;
import com.Da_Technomancer.crossroads.blocks.CRBlocks;
import com.Da_Technomancer.crossroads.blocks.rotary.LargeGearMaster;
import com.Da_Technomancer.crossroads.blocks.rotary.LargeGearSlave;
import com.Da_Technomancer.essentials.blocks.ESProperties;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;
import java.util.List;

public class RotaryUtil{

	/**
	 * The masterKey is a way of tracking when Master Axes should regenerate/recheck their networks
	 * Master axes are allowed to ignore this, but it allows for significant optimizations by reducing unnecessary checks, as it will be incremented every time a gear/component is broken/updated
	 */
	private static int masterKey = 1;

	/**
	 * Adds information about an axle handler to chat/tooltip
	 * @param chat The text list. One entry per line, will be modified
	 * @param axle The axle being added to the info chat. This method does nothing if null
	 * @param compact Whether to compact the output into one line of chat
	 */
	public static void addRotaryInfo(List<ITextComponent> chat, @Nullable IAxleHandler axle, boolean compact){
		if(axle == null){
			return;
		}
		if(compact){
			//Print speed, energy, power, inertia, and rot ratio
			chat.add(new TranslationTextComponent("tt.crossroads.boilerplate.rotary.compact", CRConfig.formatVal(axle.getSpeed()), CRConfig.formatVal(axle.getEnergy()), CRConfig.formatVal(axle.getMoInertia()), CRConfig.formatVal(axle.getRotationRatio())));
		}else{
			//Prints full data
			double axleSpeed = axle.getSpeed();
			chat.add(new TranslationTextComponent("tt.crossroads.boilerplate.rotary.speed", CRConfig.formatVal(axleSpeed), CRConfig.formatVal(axleSpeed * 60D / (Math.PI * 2D))));
			chat.add(new TranslationTextComponent("tt.crossroads.boilerplate.rotary.energy", CRConfig.formatVal(axle.getEnergy())));
			chat.add(new TranslationTextComponent("tt.crossroads.boilerplate.rotary.setup", CRConfig.formatVal(axle.getMoInertia()), CRConfig.formatVal(axle.getRotationRatio())));
		}
	}

	public static double getDirSign(Direction oldGearFacing, Direction newGearFacing){
		return -getCCWSign(oldGearFacing) * getCCWSign(newGearFacing);
	}

	public static double findEfficiency(double speedIn, double lowerLimit, double upperLimit){
		speedIn = Math.abs(speedIn);
		return speedIn < lowerLimit ? 0 : (speedIn >= upperLimit ? 1 : (speedIn - lowerLimit) / (upperLimit - lowerLimit));
	}

	/**
	 * Returns the total energy, adjusted for energy loss, of the passed IAxleHandlers
	 * @param axles A list of IAxleHandlers to have their energies summed and adjusted
	 * @param allowLoss Whether to perform energy loss
	 * @return A size 2 array, containing the total energy adjusted for energy loss, total energy change due to loss (0 if !allowLoss), resulting base system speed
	 */
	public static double[] getTotalEnergy(List<IAxleHandler> axles, boolean allowLoss){
		double sumEnergy  = 0;
		double sumInertia = 0;
		double sumIW = 0;
		double sumIRot = 0;//I * R^2
		int lossMode = allowLoss ? CRConfig.rotaryLossMode.get() : 0;
		double lossCoeff = CRConfig.rotaryLoss.get();
		double lost = 0;

		for(IAxleHandler axle : axles){
			if(axle == null){
				continue;
			}
			//Adds energy of the gear
			if(lossMode == 3){
				//Lose -(a*w) of gear energy each tick
				lost += Math.signum(axle.getEnergy()) * axle.getSpeed() * lossCoeff;
			}
			//Tracks inertia of the system
			double moIntertia = axle.getMoInertia();
			double rotRatio = axle.getRotationRatio();
			sumInertia += moIntertia;
			sumIW += moIntertia * Math.abs(axle.getSpeed());
			sumIRot += moIntertia * Math.pow(rotRatio, 2);
			sumEnergy += axle.getEnergy() * Math.signum(rotRatio);
		}

		if(sumInertia <= 0){
			//Totally zero mass systems must have 0 energy by definition
			return new double[3];
		}

		if(lossMode == 2){
			//Lose -(a%) of total energy each tick
			lost = sumEnergy * Math.max(lossCoeff / 100D, 0D);
		}else if(lossMode == 1){
			//Lose -(a * w^2) of energy each tick, where w is the I-weighted average speed of the entire system
			lost = Math.signum(sumEnergy) * lossCoeff * Math.pow(sumIW / sumInertia, 2);
		}

		if(Math.signum(sumEnergy) != Math.signum(sumEnergy - lost)){
			lost = sumEnergy;//Don't allow flipping sign from loss
		}

		sumEnergy -= lost;//Apply the loss

		double baseSpeed = Math.signum(sumEnergy) * Math.sqrt(Math.abs(sumEnergy) * 2D / sumIRot);

		return new double[] {sumEnergy, lost, baseSpeed};
	}

	/**
	 * I keep changing my mind about how to determine whether gears can connect diagonally through a block.
	 * Implementers of IAxleHandler should use this to determine whether they can connect diagonally through a block.
	 * @param world The World.
	 * @param pos The BlockPos of the block space that is being connected through.
	 * @param fromDir The direction from pos that the caller is located.
	 * @param toDir The direction from pos that the end point of the connection is located.
	 * @return Whether a connection is allowed. Does not verify that the start/endpoints are valid.
	 */
	public static boolean canConnectThrough(World world, BlockPos pos, Direction fromDir, Direction toDir){
		BlockState state = world.getBlockState(pos);
		return !state.isNormalCube(world, pos) && state.getBlock() != CRBlocks.largeGearSlave && state.getBlock() != CRBlocks.largeGearMaster;
	}

	private static final VoxelShape GEAR_ANCHOR_SHAPE = Block.makeCuboidShape(7.05D, 7.05D, 7.05D, 8.95D, 8.95D, 8.95D);

	/**
	 * Returns whether the block at the provided position is solid to gears on the specified side
	 * @param world The World
	 * @param pos The block's position
	 * @param side The side the gear will be placed against
	 * @return Whether it should be solid to small gears
	 */
	public static boolean solidToGears(World world, BlockPos pos, Direction side){
		//The current definition of "solid":
		//Block collision shape contains the 2x2 of pixels in the center of the face in side
		//And block is not the back of a large gear or leaves
		BlockState state = world.getBlockState(pos);
		if(state.getBlock() instanceof LargeGearSlave || (state.getBlock() instanceof LargeGearMaster && side != state.get(ESProperties.FACING).getOpposite())){
			return false;
		}
		if(state.isIn(BlockTags.LEAVES)){
			return false;//Vanilla convention has leaves as non-solid
		}

		//This is where the magic happens
		//Projections remove all cuboids that don't touch the passed side, and extend those that remain into a full column from one side to the opposite (the project method is poorly named)
		//Projections are cached by default, so this operation is fast
		//We have a reference anchor shape, which should fit neatly inside the projected shape if this is a solid surface
		return !VoxelShapes.compare(state.getCollisionShape(world, pos).project(side), GEAR_ANCHOR_SHAPE, IBooleanFunction.ONLY_SECOND);
	}

	/**
	 * Increases the masterKey by one
	 * @param sendPacket If true, sends a packet to the client forcing the masterKey to increase
	 */
	public static void increaseMasterKey(boolean sendPacket){
		masterKey++;
		if(sendPacket){
			CRPackets.sendPacketToAll(new SendMasterKeyToClient(masterKey));
		}
	}

	public static int getMasterKey(){
		return masterKey;
	}

	public static void setMasterKey(int masterKey){
		RotaryUtil.masterKey = masterKey;
	}

	/**
	 * Returns either 1 or -1, and represents the sign for rotation to be in the counter-clockwise direction
	 * @param dir The direction along the axis of rotation, with the direction being the 'front' of the axis
	 * @return The value to multiply the energy or speed by for CCW rotation
	 */
	public static double getCCWSign(Direction dir){
		return dir.getAxisDirection().getOffset();
	}

	/**
	 * Connect axially to a tile entity
	 * Handles both IAxleHandler and IAxisHandler
	 * Does not connect via cog capability
	 * @param te The tile entity being connected to
	 * @param direction The side of the tile entity being connected to
	 * @param srcHandler The handler calling this
	 * @param master The master axis being propagated
	 * @param shouldRenderOffset Whether angles should be rendered with an offset
	 */
	public static void propagateAxially(@Nullable TileEntity te, Direction direction, IAxleHandler srcHandler, IAxisHandler master, byte key, boolean shouldRenderOffset){
		if(te != null){
			LazyOptional<IAxisHandler> axisOpt = te.getCapability(Capabilities.AXIS_CAPABILITY, direction);
			if(axisOpt.isPresent()){
				axisOpt.orElseThrow(NullPointerException::new).trigger(master, key);
			}

			LazyOptional<IAxleHandler> axleOpt = te.getCapability(Capabilities.AXLE_CAPABILITY, direction);
			if(axleOpt.isPresent()){
				axleOpt.orElseThrow(NullPointerException::new).propagate(master, key, srcHandler.getRotationRatio(), 0, shouldRenderOffset);
			}
		}
	}
}
