package com.Da_Technomancer.crossroads.API.effects;

import com.Da_Technomancer.crossroads.Crossroads;
import com.Da_Technomancer.crossroads.CRConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.Direction;
import net.minecraft.util.EntityPredicates;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.List;

public class PlaceEffect implements IEffect{

	@Override
	public void doEffect(World worldIn, BlockPos pos, int mult, Direction dir){
		List<ItemEntity> items = worldIn.getEntitiesWithinAABB(ItemEntity.class, new AxisAlignedBB(pos.add(-mult, -mult, -mult), pos.add(mult, mult, mult)), EntityPredicates.IS_ALIVE);
		if(items.size() != 0){
			FakePlayer placer = FakePlayerFactory.get((ServerWorld) worldIn, new GameProfile(null, Crossroads.MODID + "-place_effect-" + worldIn.getDimension().getType().getId()));
			for(ItemEntity ent : items){
				ItemStack stack = ent.getItem();
				if(!stack.isEmpty() && stack.getItem() instanceof BlockItem){
					BlockItemUseContext context = new BlockItemUseContext(new ItemUseContext(placer, Hand.MAIN_HAND, new BlockRayTraceResult(new Vec3d(ent.posX, ent.posY, ent.posZ), Direction.DOWN, ent.getPosition(), false)));
					BlockState state = ((BlockItem) stack.getItem()).getBlock().getStateForPlacement(context);
					if(state.getBlock().isValidPosition(state, worldIn, ent.getPosition())){
						worldIn.setBlockState(ent.getPosition(), state);
						state.getBlock().onBlockPlacedBy(worldIn, ent.getPosition(), worldIn.getBlockState(ent.getPosition()), placer, stack);
						SoundType soundtype = state.getBlock().getSoundType(worldIn.getBlockState(pos), worldIn, pos, placer);
						worldIn.playSound(null, pos, soundtype.getPlaceSound(), SoundCategory.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);
						stack.shrink(1);
						if(stack.getCount() <= 0){
							ent.remove();
						}
					}
				}
			}
		}
	}

	public static class BreakEffect implements IEffect{

		@Override
		public void doEffect(World worldIn, BlockPos pos, int mult, Direction dir){
			if(!CRConfig.isProtected(worldIn, pos, worldIn.getBlockState(pos))){
				worldIn.destroyBlock(pos, true);
			}
		}
	}
}