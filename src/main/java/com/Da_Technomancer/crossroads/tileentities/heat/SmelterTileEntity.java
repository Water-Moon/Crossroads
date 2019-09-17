package com.Da_Technomancer.crossroads.tileentities.heat;

import com.Da_Technomancer.crossroads.API.Capabilities;
import com.Da_Technomancer.crossroads.API.heat.HeatUtil;
import com.Da_Technomancer.crossroads.API.templates.InventoryTE;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

public class SmelterTileEntity extends InventoryTE{

	public SmelterTileEntity(){
		super(2);// 0 = Input, 1 = Output
	}

	public static final int REQUIRED = 500;
	public static final int[] TEMP_TIERS = {200, 300};
	public static final int USAGE = 5;

	private int progress = 0;

	@Override
	protected boolean useHeat(){
		return true;
	}

	@Override
	public void update(){
		super.update();
		if(world.isRemote){
			return;
		}

		int tier = HeatUtil.getHeatTier(temp, TEMP_TIERS);
		if(tier != -1){
			temp -= USAGE * (tier + 1);

			ItemStack output = getOutput();
			if(!inventory[0].isEmpty() && !output.isEmpty()){
				progress += USAGE * (tier + 1);
				if(progress >= REQUIRED){
					progress = 0;

					if(inventory[1].isEmpty()){
						inventory[1] = output;
					}else{
						inventory[1].grow(output.getCount());
					}
					inventory[0].shrink(1);
				}
			}else{
				progress = 0;
			}
			markDirty();
		}
	}

	private ItemStack getOutput(){
		ItemStack stack = FurnaceRecipes.instance().getSmeltingResult(inventory[0]);

		if(stack.isEmpty()){
			return ItemStack.EMPTY;
		}

		if(!inventory[1].isEmpty() && !ItemStack.areItemsEqual(stack, inventory[1])){
			return ItemStack.EMPTY;
		}

		if(!inventory[1].isEmpty() && getInventoryStackLimit() - inventory[1].getCount() < stack.getCount()){
			return ItemStack.EMPTY;
		}

		return stack.copy();
	}

	@Override
	public void read(CompoundNBT nbt){
		super.read(nbt);
		progress = nbt.getInt("prog");
	}

	@Override
	public CompoundNBT write(CompoundNBT nbt){
		super.write(nbt);
		nbt.putInt("prog", progress);
		return nbt;
	}

	private ItemHandler itemHandler = new ItemHandler(null);

	@SuppressWarnings("unchecked")
	public <T> T getCapability(Capability<T> cap, Direction side){
		if(cap == Capabilities.HEAT_CAPABILITY && (side == Direction.UP || side == null)){
			return (T) heatHandler;
		}
		if(cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && side != Direction.UP){
			return (T) itemHandler;
		}

		return super.getCapability(cap, side);
	}

	@Override
	public String getName(){
		return "container.smelter";
	}

	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack){
		return index == 0 && !FurnaceRecipes.instance().getSmeltingResult(stack).isEmpty();
	}

	@Override
	public int getField(int id){
		if(id == getFieldCount() - 1){
			return progress;
		}else{
			return super.getField(id);
		}
	}

	@Override
	public void setField(int id, int value){
		super.setField(id, value);

		if(id == getFieldCount() - 1){
			progress = value;
		}
	}

	@Override
	public int getFieldCount(){
		return super.getFieldCount() + 1;
	}

	@Override
	public boolean canExtractItem(int index, ItemStack stack, Direction direction){
		return index == 1;
	}
}
