package com.Da_Technomancer.crossroads.tileentities.electric;

import com.Da_Technomancer.crossroads.API.IInfoTE;
import com.Da_Technomancer.crossroads.API.MiscUtil;
import com.Da_Technomancer.crossroads.Crossroads;
import com.Da_Technomancer.crossroads.blocks.electric.TeslaCoilTop;
import com.Da_Technomancer.crossroads.render.CRRenderUtil;
import com.Da_Technomancer.essentials.tileentities.ILinkTE;
import com.Da_Technomancer.essentials.tileentities.LinkHelper;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.EntityPredicates;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.registries.ObjectHolder;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ObjectHolder(Crossroads.MODID)
public class TeslaCoilTopTileEntity extends TileEntity implements IInfoTE, ILinkTE{

	@ObjectHolder("tesla_coil_top")
	public static TileEntityType<TeslaCoilTopTileEntity> type = null;

	public static final int[] COLOR_CODES = {0xFFECCFFF, 0xFFFCDFFF, 0xFFFFFAFF};
	private static final int[] ATTACK_COLOR_CODES = {0xFFFFCCCC, 0xFFFFFFCC, 0xFFFFFAFA};
	private static final Color LINK_COLOR = new Color(COLOR_CODES[0]);

	private final LinkHelper linkHelper = new LinkHelper(this);

	private TeslaCoilTop.TeslaCoilVariants variant = null;

	public TeslaCoilTopTileEntity(){
		super(type);
	}

	private TeslaCoilTop.TeslaCoilVariants getVariant(){
		if(variant == null){
			BlockState state = getBlockState();
			if(state.getBlock() instanceof TeslaCoilTop){
				variant = ((TeslaCoilTop) state.getBlock()).variant;
			}else{
				remove();
				return TeslaCoilTop.TeslaCoilVariants.NORMAL;
			}
		}
		return variant;
	}

	@Override
	public void receiveLong(byte identifier, long message, @Nullable ServerPlayerEntity sendingPlayer){
		linkHelper.handleIncomingPacket(identifier, message);
	}

	protected void jolt(TeslaCoilTileEntity coilTE){
		TeslaCoilTop.TeslaCoilVariants variant = getVariant();
		int range = variant.range;
		int joltQty = variant.joltAmt;

		if(variant == TeslaCoilTop.TeslaCoilVariants.ATTACK){
			if(world.isRemote){
				return;
			}

			//ATTACK
			List<LivingEntity> ents = world.getEntitiesWithinAABB(LivingEntity.class, new AxisAlignedBB(pos.getX() - range, pos.getY() - range, pos.getZ() - range, pos.getX() + range, pos.getY() + range, pos.getZ() + range), EntityPredicates.IS_ALIVE);

			if(!ents.isEmpty() && coilTE.getStored() >= joltQty){
				LivingEntity ent = ents.get(world.rand.nextInt(ents.size()));
				coilTE.setStored(coilTE.getStored() - joltQty);
				markDirty();

				CRRenderUtil.addArc(world, pos.getX() + 0.5F, pos.getY() + 0.75F, pos.getZ() + 0.5F, (float) ent.getPosX(), (float) ent.getPosY(), (float) ent.getPosZ(), 5, 0.2F, ATTACK_COLOR_CODES[(int) (world.getGameTime() % 3)]);
				MiscUtil.attackWithLightning(ent, 0, null);
			}
		}else if(variant == TeslaCoilTop.TeslaCoilVariants.DECORATIVE){
			if(coilTE.getStored() >= TeslaCoilTop.TeslaCoilVariants.DECORATIVE.joltAmt){
				if(world.isRemote){
					//Spawn the purely decorative bolts on the client side directly to reduce packet load
					int count = world.rand.nextInt(5) + 1;
					Vector3d start = new Vector3d(pos.getX() + 0.5F, pos.getY() + 0.75F, pos.getZ() + 0.5F);
					for(int i = 0; i < count; i++){
						float angle = world.rand.nextFloat() * 2F * (float) Math.PI;
						float rad = world.rand.nextFloat() * 2F + 3F;
						Vector3d end = start.add(new Vector3d(rad * Math.cos(angle), world.rand.nextFloat() * 2F - 1F, rad * Math.sin(angle)));
						CRRenderUtil.addArc(world, start, end, 6, 0.6F, COLOR_CODES[world.rand.nextInt(COLOR_CODES.length)]);
					}
				}else{
					coilTE.setStored(coilTE.getStored() - TeslaCoilTop.TeslaCoilVariants.DECORATIVE.joltAmt);
				}
			}
		}else if(!world.isRemote){
			//TRANSFER
			for(BlockPos linkPos : linkHelper.getLinksRelative()){
				if(linkPos != null && coilTE.getStored() >= joltQty && linkPos.distanceSq(0, 0, 0, false) <= range * range){
					BlockPos actualPos = linkPos.add(pos.getX(), pos.getY() - 1, pos.getZ());
					TileEntity te = world.getTileEntity(actualPos);
					if(te instanceof TeslaCoilTileEntity && world.getTileEntity(actualPos.up()) instanceof TeslaCoilTopTileEntity){
						TeslaCoilTileEntity tcTe = (TeslaCoilTileEntity) te;
						if(tcTe.getCapacity() - tcTe.getStored() > joltQty * (double) variant.efficiency / 100D){
							tcTe.setStored(tcTe.getStored() + (int) (joltQty * (double) variant.efficiency / 100D));
							tcTe.markDirty();
							coilTE.setStored(coilTE.getStored() - joltQty);
							markDirty();

							CRRenderUtil.addArc(world, pos.getX() + 0.5F, pos.getY() + 0.75F, pos.getZ() + 0.5F, actualPos.getX() + 0.5F, actualPos.getY() + 1.75F, actualPos.getZ() + 0.5F, 5, (100F - variant.efficiency) / 100F, COLOR_CODES[(int) (world.getGameTime() % 3)]);
							break;
						}
					}
				}
			}
		}
	}

	@Override
	public void addInfo(ArrayList<ITextComponent> chat, PlayerEntity player, BlockRayTraceResult hit){
		for(BlockPos link : linkHelper.getLinksAbsolute()){
			chat.add(new TranslationTextComponent("tt.crossroads.boilerplate.link", link.getX(), link.getY(), link.getZ()));
		}
	}

	@Override
	public CompoundNBT getUpdateTag(){
		CompoundNBT nbt = super.getUpdateTag();
		linkHelper.writeNBT(nbt);
		return nbt;
	}

	@Override
	public void read(BlockState state, CompoundNBT nbt){
		super.read(state, nbt);
		int i = 0;
		while(nbt.contains("link" + i)){
			//TODO remove: backwards compatibility nbt format
			//Convert from the pre-2.6.0 format used by tesla coils to the format used by LinkHelper
			nbt.putLong("link_" + i, nbt.getLong("link" + i));
			i++;
		}
		linkHelper.readNBT(nbt);
	}

	@Override
	public CompoundNBT write(CompoundNBT nbt){
		super.write(nbt);
		linkHelper.writeNBT(nbt);
		return nbt;
	}

	@Override
	public TileEntity getTE(){
		return this;
	}

	@Override
	public boolean canBeginLinking(){
		return getVariant() != TeslaCoilTop.TeslaCoilVariants.ATTACK && getVariant() != TeslaCoilTop.TeslaCoilVariants.DECORATIVE;
	}

	@Override
	public boolean canLink(ILinkTE otherTE){
		return otherTE instanceof TeslaCoilTopTileEntity && getVariant() != TeslaCoilTop.TeslaCoilVariants.ATTACK;
	}

	@Override
	public Set<BlockPos> getLinks(){
		return linkHelper.getLinksRelative();
	}

	@Override
	public boolean createLinkSource(ILinkTE endpoint, @Nullable PlayerEntity player){
		return linkHelper.addLink(endpoint, player);
	}

	@Override
	public void removeLinkSource(BlockPos end){
		linkHelper.removeLink(end);
	}

	@Override
	public int getRange(){
		return getVariant().range;
	}

	@Override
	public Color getColor(){
		return LINK_COLOR;
	}
}
