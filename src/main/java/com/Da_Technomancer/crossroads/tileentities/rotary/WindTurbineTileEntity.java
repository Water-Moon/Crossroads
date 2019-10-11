package com.Da_Technomancer.crossroads.tileentities.rotary;

import com.Da_Technomancer.crossroads.API.Capabilities;
import com.Da_Technomancer.crossroads.API.CrossroadsProperties;
import com.Da_Technomancer.crossroads.API.templates.ModuleTE;
import com.Da_Technomancer.crossroads.blocks.CrossroadsBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;
import java.util.ArrayList;

public class WindTurbineTileEntity extends ModuleTE{

	private Direction facing = null;
	public static final double MAX_SPEED = 2D;

	public WindTurbineTileEntity(){
		super();
	}

	public WindTurbineTileEntity(boolean newlyPlaced){
		this();
		this.newlyPlaced = newlyPlaced;
	}

	protected Direction getFacing(){
		if(facing == null){
			BlockState state = world.getBlockState(pos);
			if(state.getBlock() != CrossroadsBlocks.windTurbine){
				invalidate();
				return Direction.NORTH;
			}
			facing = state.get(CrossroadsProperties.HORIZ_FACING);
		}

		return facing;
	}

	public void resetCache(){
		facing = null;
	}

	public static final double POWER_PER_LEVEL = 10D;
	private boolean newlyPlaced = false;
	private int level = 1;
	private boolean running = false;

	@Override
	protected boolean useRotary(){
		return true;
	}

	@Override
	protected AxleHandler createAxleHandler(){
		return new AngleAxleHandler();
	}

	@Override
	public void addInfo(ArrayList<String> chat, PlayerEntity player, @Nullable Direction side, BlockRayTraceResult hit){
		chat.add("Power Gen: " + POWER_PER_LEVEL * (double) level + "J/t");
		super.addInfo(chat, player, side, hitX, hitY, hitZ);
	}

	public int getRedstoneOutput(){
		return level < 0 ? 15 : 0;
	}

	@Override
	public void tick(){
		super.tick();

		if(!world.isRemote){
			//Every 30 seconds check whether the placement requirements are valid, and cache the result
			if(newlyPlaced || world.getGameTime() % 600 == 0){
				newlyPlaced = false;
				running = false;
				Direction facing = getFacing();
				BlockPos offsetPos = pos.offset(facing);
				if(world.canSeeSky(offsetPos)){
					running = true;
					outer:
					for(int i = -2; i <= 2; i++){
						for(int j = -2; j <= 2; j++){
							BlockPos checkPos = offsetPos.add(facing.getZOffset() * i, j, facing.getXOffset() * i);
							BlockState checkState = world.getBlockState(checkPos);
							if(!checkState.getBlock().isAir(checkState, world, checkPos)){
								running = false;
								break outer;
							}
						}
					}
				}

				markDirty();
			}

			if(running && axleHandler.axis != null){
				if(world.getGameTime() % 10 == 0 && world.rand.nextInt(240) == 0){
					int prevLevel = level;
					level = (world.rand.nextInt(2) + 1) * (world.rand.nextBoolean() ? -1 : 1);//Gen a random number from -2 to 2, other than 0

					//If the redstone output has changed, update the neighbors
					if(level < 0 != prevLevel < 0){
						world.notifyNeighborsOfStateChange(pos, CrossroadsBlocks.windTurbine, true);
					}
				}

				if(motData[0] * Math.signum(level) < MAX_SPEED){
					motData[1] += (double) level * POWER_PER_LEVEL;
				}
			}
		}
	}

	@Override
	public void read(CompoundNBT nbt){
		super.read(nbt);
		level = nbt.getInt("level");
		running = nbt.getBoolean("running");
	}

	@Override
	public CompoundNBT write(CompoundNBT nbt){
		super.write(nbt);
		nbt.putInt("level", level);
		nbt.putBoolean("running", running);
		return nbt;
	}

	private static final AxisAlignedBB RENDER_BOX = new AxisAlignedBB(-1, -1, -1, 2, 2, 2);

	@Override
	public AxisAlignedBB getRenderBoundingBox(){
		return RENDER_BOX.offset(pos);
	}

	@Override
	public double getMoInertia(){
		return 200;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction facing){
		if(capability == Capabilities.AXLE_CAPABILITY && (facing == null || facing == world.getBlockState(pos).get(CrossroadsProperties.HORIZ_FACING).getOpposite())){
			return (T) axleHandler;
		}
		return super.getCapability(capability, facing);
	}
}
