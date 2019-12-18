package com.Da_Technomancer.crossroads.API.alchemy;

import com.Da_Technomancer.crossroads.API.Capabilities;
import com.Da_Technomancer.crossroads.API.IInfoTE;
import com.Da_Technomancer.crossroads.API.MiscUtil;
import com.Da_Technomancer.crossroads.API.heat.HeatUtil;
import com.Da_Technomancer.crossroads.items.alchemy.AbstractGlassware;
import com.Da_Technomancer.crossroads.particles.CRParticles;
import com.Da_Technomancer.crossroads.particles.ColorParticleData;
import com.Da_Technomancer.crossroads.particles.ColorParticleType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ITickableTileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

/**
 * Implementations must implement getCapability directly.
 */
public abstract class AlchemyCarrierTE extends TileEntity implements ITickableTileEntity, IInfoTE{

	protected boolean init = false;
	protected double cableTemp = 0;
	protected boolean glass;
	protected ReagentMap contents = new ReagentMap();
	protected boolean dirtyReag = false;

	/**
	 * Position to spawn particles for contents
	 * @return Position
	 */
	protected Vec3d getParticlePos(){
		return new Vec3d(pos).add(0.5D, 0.5D, 0.5D);
	}

	protected boolean useCableHeat(){
		return false;
	}

	protected void initHeat(){

	}

	@Override
	public void addInfo(ArrayList<ITextComponent> chat, PlayerEntity player, BlockRayTraceResult hit){
		double temp = correctTemp();
		if(contents.getTotalQty() != 0 || temp != HeatUtil.ABSOLUTE_ZERO){
			chat.add("Temp: " + MiscUtil.betterRound(temp, 3) + "°C (" + MiscUtil.betterRound(HeatUtil.toKelvin(temp), 3) + "K)");
		}else{
			chat.add("No reagents");
		}
		for(IReagent reag : contents.keySet()){
			ReagentStack stack = contents.getStack(reag);
			if(stack != null && !stack.isEmpty()){
				chat.add(stack.toString());
			}
		}
	}

	protected AlchemyCarrierTE(TileEntityType<? extends AlchemyCarrierTE> type){
		super(type);
	}

	protected AlchemyCarrierTE(TileEntityType<? extends AlchemyCarrierTE> type, boolean glass){
		this(type);
		this.glass = glass;
	}

	/**
	 * @return What the current temperature of this machine should be. Can be overwritten to allow external control of temperature
	 */
	protected double correctTemp(){
		if(useCableHeat()){
			initHeat();
			//Shares heat between internal cable & contents
			cableTemp = HeatUtil.toCelcius((HeatUtil.toKelvin(cableTemp) * AlchemyUtil.ALCHEMY_TEMP_CONVERSION + contents.getTempK() * contents.getTotalQty()) / (AlchemyUtil.ALCHEMY_TEMP_CONVERSION + contents.getTotalQty()));
			contents.setTemp(cableTemp);
			return cableTemp;
		}else{
			return contents.getTempC();
		}
	}

	/**
	 * Cleans up temperatures vs heat, refreshes amount based on contents, etc. Should be called after all changes to contents. Setting dirtyReag to true will queue up a correctReag call.
	 */
	protected void correctReag(){
		dirtyReag = false;
		contents.refresh();
		contents.setTemp(correctTemp());
	}

	@Override
	public void tick(){
		if(world.isRemote){
			return;
		}

		if(!init && useCableHeat()){
			cableTemp = HeatUtil.convertBiomeTemp(world, pos);
		}
		init = true;

		if(dirtyReag){
			correctReag();
		}

		if(world.getGameTime() % AlchemyUtil.ALCHEMY_TIME == 0){
			spawnParticles();
			performTransfer();
		}
	}

	/**
	 * Spawns cosmetic particles representing contents
	 */
	protected void spawnParticles(){
		double temp = handler.getTemp();
		ServerWorld server = (ServerWorld) world;
		float liqAmount = 0;
		float[] liqCol = new float[4];
		float gasAmount = 0;
		float[] gasCol = new float[4];
		float flameAmount = 0;
		float[] flameCol = new float[4];
		float solAmount = 0;
		float[] solCol = new float[4];
		for(IReagent type : contents.keySet()){
			ReagentStack r = contents.getStack(type);
			if(!r.isEmpty()){
				Color col = r.getType().getColor(type.getPhase(temp));
				switch(type.getPhase(temp)){
					case LIQUID:
						liqAmount += r.getAmount();
						liqCol[0] += r.getAmount() * (double) col.getRed();
						liqCol[1] += r.getAmount() * (double) col.getGreen();
						liqCol[2] += r.getAmount() * (double) col.getBlue();
						liqCol[3] += r.getAmount() * (double) col.getAlpha();
						break;
					case GAS:
						gasAmount += r.getAmount();
						gasCol[0] += r.getAmount() * (double) col.getRed();
						gasCol[1] += r.getAmount() * (double) col.getGreen();
						gasCol[2] += r.getAmount() * (double) col.getBlue();
						gasCol[3] += r.getAmount() * (double) col.getAlpha();
						break;
					case FLAME:
						flameAmount += r.getAmount();
						flameCol[0] += r.getAmount() * (double) col.getRed();
						flameCol[1] += r.getAmount() * (double) col.getGreen();
						flameCol[2] += r.getAmount() * (double) col.getBlue();
						flameCol[3] += r.getAmount() * (double) col.getAlpha();
						break;
					case SOLID:
						solAmount += r.getAmount();
						solCol[0] += r.getAmount() * (double) col.getRed();
						solCol[1] += r.getAmount() * (double) col.getGreen();
						solCol[2] += r.getAmount() * (double) col.getBlue();
						solCol[3] += r.getAmount() * (double) col.getAlpha();
						break;
					default:
						break;
				}
			}
		}

		Vec3d particlePos = getParticlePos();

		if(liqAmount > 0){
			server.spawnParticle(new ColorParticleData(CRParticles.COLOR_LIQUID, new Color((int) (liqCol[0] / liqAmount), (int) (liqCol[1] / liqAmount), (int) (liqCol[2] / liqAmount), (int) (liqCol[3] / liqAmount))), particlePos.x, particlePos.y, particlePos.z, 0, (Math.random() * 2D - 1D) * 0.02D, (Math.random() - 1D) * 0.02D, (Math.random() * 2D - 1D) * 0.02D, 1F);
		}
		if(gasAmount > 0){
			server.spawnParticle(new ColorParticleData(CRParticles.COLOR_GAS, new Color((int) (gasCol[0] / gasAmount), (int) (gasCol[1] / gasAmount), (int) (gasCol[2] / gasAmount), (int) (gasCol[3] / gasAmount))), particlePos.x, particlePos.y, particlePos.z, 0, (Math.random() * 2D - 1D) * 0.015D, Math.random() * 0.015D, (Math.random() * 2D - 1D) * 0.015D, 1F);
		}
		if(flameAmount > 0){
			server.spawnParticle(new ColorParticleData(CRParticles.COLOR_FLAME, new Color((int) (flameCol[0] / flameAmount), (int) (flameCol[1] / flameAmount), (int) (flameCol[2] / flameAmount), (int) (flameCol[3] / flameAmount))), particlePos.x, particlePos.y, particlePos.z, 0, (Math.random() * 2D - 1D) * 0.015D, Math.random() * 0.015D, (Math.random() * 2D - 1D) * 0.015D, 1F);
		}
		if(solAmount > 0){
			server.spawnParticle(new ColorParticleData(CRParticles.COLOR_SOLID, new Color((int) (solCol[0] / solAmount), (int) (solCol[1] / solAmount), (int) (solCol[2] / solAmount), (int) (solCol[3] / solAmount))), particlePos.x - 0.25D + world.rand.nextFloat() / 2F, particlePos.y - 0.1F, particlePos.z - 0.25D + world.rand.nextFloat() / 2F, 0, 0, 0, 0, 1F);
		}
	}

	/*
	 * Helper method for moving reagents with glassware/solid items. Use is optional, and must be added in the block if used.
	 */
	@Nonnull
	public ItemStack rightClickWithItem(ItemStack stack, boolean sneaking, PlayerEntity player, Hand hand){
		if(dirtyReag){
			correctReag();
		}

		double temp = HeatUtil.toKelvin(correctTemp());
		ItemStack out = stack.copy();

		//Move solids from carrier into hand
		if(stack.isEmpty()){
			for(IReagent type : contents.keySet()){
				ReagentStack rStack = contents.getStack(type);
				if(!rStack.isEmpty() && type.getPhase(HeatUtil.toCelcius(temp)) == EnumMatterPhase.SOLID){
					out = type.getStackFromReagent(rStack);
					out.setCount(Math.min(out.getMaxStackSize(), out.getCount()));
					if(!out.isEmpty()){
						contents.removeReagent(type, out.getCount());
						break;
					}
				}
			}
		}else if(stack.getItem() instanceof AbstractGlassware){
			boolean crystal = ((AbstractGlassware) stack.getItem()).isCrystal();
			//Move reagents between glassware and carrier
			ReagentMap phial = ((AbstractGlassware) stack.getItem()).getReagants(stack);
			if(phial.getTotalQty() <= 0){
				phial.clear();
				//Move from carrier to glassware
				if(!crystal){
					//Refuse if made of glass and cannot hold contents
					for(IReagent r : contents.keySet()){
						if(r != null && contents.getQty(r) > 0 && !r.canGlassContain()){
							return stack;
						}
					}
				}

				double portion = Math.min(1D, (double) ((AbstractGlassware) stack.getItem()).getCapacity() / (double) contents.getTotalQty());

				for(IReagent type : contents.keySet()){
					phial.transferReagent(type, (int) (contents.getQty(type) * portion), contents);
				}

				((AbstractGlassware) out.getItem()).setReagents(out, phial);
			}else{
				//Move from glassware to carrier
				double portion = Math.max(0, Math.min(1D, (double) (transferCapacity() - contents.getTotalQty()) / (double) phial.getTotalQty()));

				for(IReagent type : phial.keySet()){
					contents.transferReagent(type, (int) (phial.getQty(type) * portion), phial);
				}

				((AbstractGlassware) out.getItem()).setReagents(out, phial);
			}
		}else if(FluidUtil.interactWithFluidHandler(player, hand, falseFluidHandler)){
			//Attempt to interact with fluid carriers
			out = player.getHeldItem(hand);
		}else{
			//Move solids from hand into carrier
			IReagent typeProduced = AlchemyCore.ITEM_TO_REAGENT.get(stack.getItem());
			if(typeProduced != null && contents.getTotalQty() < transferCapacity()){
				double itemTemp;
				double biomeTemp = HeatUtil.convertBiomeTemp(world, pos);
				if(biomeTemp < typeProduced.getMeltingPoint()){
					itemTemp = biomeTemp;
				}else{
					itemTemp = Math.max(typeProduced.getMeltingPoint() - 100D, HeatUtil.ABSOLUTE_ZERO);
				}

				out.shrink(1);
				contents.addReagent(typeProduced, 1, itemTemp);
			}
		}

		if(!ItemStack.areItemsEqual(out, stack) || !ItemStack.areItemStackTagsEqual(out, stack) || out.getCount() != stack.getCount()){
			markDirty();
			dirtyReag = true;
		}

		return out;
	}

	/**
	 * Returned array must be size six.
	 * @return An array where each EnumTransferMode specifies the mode of the side with the same index. 
	 */
	@Nonnull
	protected abstract EnumTransferMode[] getModes();

	/**
	 * Controls maximum amount of reagent this block can hold before it stops accepting more
	 * @return Maximum capacity
	 */
	protected int transferCapacity(){
		return 10;
	}

	protected void performTransfer(){
		EnumTransferMode[] modes = getModes();
		for(int i = 0; i < 6; i++){
			if(modes[i].isOutput()){
				Direction side = Direction.byIndex(i);
				TileEntity te = world.getTileEntity(pos.offset(side));
				LazyOptional<IChemicalHandler> otherOpt;
				if(contents.getTotalQty() <= 0 || te == null || !(otherOpt = te.getCapability(Capabilities.CHEMICAL_CAPABILITY, side.getOpposite())).isPresent()){
					continue;
				}
				IChemicalHandler otherHandler = otherOpt.orElseThrow(NullPointerException::new);

				EnumContainerType cont = otherHandler.getChannel(side.getOpposite());
				if(cont != EnumContainerType.NONE && ((cont == EnumContainerType.GLASS) != glass) || otherHandler.getMode(side.getOpposite()) == EnumTransferMode.BOTH && modes[i] == EnumTransferMode.BOTH){
					continue;
				}

				if(otherHandler.insertReagents(contents, side.getOpposite(), handler)){
					correctReag();
					markDirty();
				}
			}
		}
	}

	@Override
	public void read(CompoundNBT nbt){
		super.read(nbt);
		glass = nbt.getBoolean("glass");
		contents = ReagentMap.readFromNBT(nbt);
		cableTemp = nbt.getDouble("temp");
		init = nbt.getBoolean("initHeat");

		dirtyReag = true;
	}

	@Override
	public CompoundNBT write(CompoundNBT nbt){
		super.write(nbt);
		nbt.putBoolean("glass", glass);
		contents.write(nbt);
		nbt.putDouble("temp", cableTemp);
		nbt.putBoolean("initHeat", init);

		return nbt;
	}

	protected EnumContainerType getChannel(){
		return glass ? EnumContainerType.GLASS : EnumContainerType.CRYSTAL;
	}

	protected IChemicalHandler handler = new AlchHandler();
	protected LazyOptional<IChemicalHandler>

	protected class AlchHandler implements IChemicalHandler{

		public AlchHandler(){

		}

		@Override
		public double getTemp(){
			return correctTemp();
		}

		@Override
		public EnumTransferMode getMode(Direction side){
			return getModes()[side.getIndex()];
		}

		@Override
		public EnumContainerType getChannel(Direction side){
			return AlchemyCarrierTE.this.getChannel();
		}

		@Override
		public int getTransferCapacity(){
			return transferCapacity();
		}

		@Override
		public boolean insertReagents(ReagentMap reag, Direction side, IChemicalHandler caller, boolean ignorePhase){
			if(getMode(side).isInput()){
				int space = getTransferCapacity() - contents.getTotalQty();
				if(space <= 0){
					return false;
				}
				double callerTemp = reag.getTempC();
				boolean changed = false;

				HashSet<String> validIds = new HashSet<>(4);
				int totalValid = 0;

				for(IReagent type : reag.keySet()){
					ReagentStack r = reag.getStack(type);
					if(!r.isEmpty()){
						EnumMatterPhase phase = type.getPhase(callerTemp);
						if(ignorePhase || (phase.flows() && (side != Direction.UP || phase.flowsDown()) && (side != Direction.DOWN || phase.flowsUp()))){
							validIds.add(type.getId());
							totalValid += r.getAmount();
						}
					}
				}

				double portion = Math.min(1D, (double) space / (double) totalValid);
				for(String id : validIds){
					int moved = (int) (reag.getQty(id) * portion);
					if(moved <= 0){
						continue;
					}
					changed = true;

					contents.transferReagent(id, moved, reag);
				}

				if(changed){
					dirtyReag = true;
					markDirty();
				}
				return changed;
			}

			return false;
		}

		@Override
		public int getContent(IReagent id){
			return contents.getQty(id);
		}
	}

	protected final FalseFluidHandler falseFluidHandler = new FalseFluidHandler();

	private class FalseFluidHandler implements IFluidHandler{

		@Override
		public IFluidTankProperties[] getTankProperties(){
			return new FluidTankProperties[] {new FluidTankProperties(null, 100, true, true)};
		}

		@Override
		public int fill(FluidStack resource, boolean doFill){
			IReagent typ;
			if(resource != null && (typ = AlchemyCore.FLUID_TO_LIQREAGENT.get(resource.getFluid())) != null){
				int canAccept = Math.min((int) ((handler.getTransferCapacity() - contents.getTotalQty()) * AlchemyUtil.MB_PER_REAG), resource.amount);
				canAccept -= canAccept % AlchemyUtil.MB_PER_REAG;
				if(canAccept > 0){
					if(doFill){
						int reagToFill = canAccept / AlchemyUtil.MB_PER_REAG;
						double fluidTemp;
						double biomeTemp = HeatUtil.convertBiomeTemp(world, pos);
						if(biomeTemp < typ.getBoilingPoint() && biomeTemp >= typ.getMeltingPoint()){
							fluidTemp = biomeTemp;
						}else if(resource.getFluid().getTemperature(resource) < typ.getBoilingPoint() && resource.getFluid().getTemperature(resource) >= typ.getMeltingPoint()){
							fluidTemp = resource.getFluid().getTemperature(resource);
						}else if(typ.getMeltingPoint() + 100D < typ.getBoilingPoint()){
							fluidTemp = typ.getMeltingPoint() + 100D;
						}else{
							fluidTemp = (typ.getMeltingPoint() + typ.getBoilingPoint()) / 2D;
						}

						contents.addReagent(typ, reagToFill, fluidTemp);
						dirtyReag = true;
						markDirty();
					}
					return canAccept;
				}
			}
			return 0;
		}

		@Override
		public FluidStack drain(FluidStack resource, boolean doDrain){
			if(resource == null || resource.amount <= 0 || !AlchemyCore.FLUID_TO_LIQREAGENT.containsKey(resource.getFluid())){
				return null;
			}

			IReagent type = AlchemyCore.FLUID_TO_LIQREAGENT.get(resource.getFluid());
			if(contents.getQty(type) > 0 && type.getPhase(handler.getTemp()) == EnumMatterPhase.LIQUID){
				int toDrain = Math.min(resource.amount, contents.getQty(type) * AlchemyUtil.MB_PER_REAG);
				int reagToDrain = toDrain / AlchemyUtil.MB_PER_REAG;
				toDrain = reagToDrain * AlchemyUtil.MB_PER_REAG;
				if(doDrain){
					contents.removeReagent(type, reagToDrain);
					dirtyReag = true;
					markDirty();
				}
				return new FluidStack(resource.getFluid(), toDrain);
			}

			return null;
		}

		@Override
		public FluidStack drain(int maxDrain, boolean doDrain){
			if(maxDrain <= 0){
				return null;
			}

			//The Fluid-IReagentType BiMap is guaranteed to be equal in length to or shorter than REAGENT_COUNT (and in practice is substantially shorter),
			//so it's more efficient to iterate over the BiMap and check each IReagentType's index than to iterate over the reagent array and check each reagent in the BiMap.
			for(Map.Entry<Fluid, IReagent> entry : AlchemyCore.FLUID_TO_LIQREAGENT.entrySet()){
				IReagent type = entry.get();
				if(contents.getQty(type) > 0 && type.getPhase(handler.getTemp()) == EnumMatterPhase.LIQUID){
					int toDrain = Math.min(maxDrain, contents.getQty(type) * AlchemyUtil.MB_PER_REAG);
					int reagToDrain = toDrain / AlchemyUtil.MB_PER_REAG;
					toDrain = reagToDrain * AlchemyUtil.MB_PER_REAG;
					if(doDrain){
						contents.removeReagent(type, reagToDrain);
						dirtyReag = true;
						markDirty();
					}
					return new FluidStack(entry.getKey(), toDrain);
				}
			}

			return null;
		}
	}
}
