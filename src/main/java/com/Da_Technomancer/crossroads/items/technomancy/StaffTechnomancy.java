package com.Da_Technomancer.crossroads.items.technomancy;

import com.Da_Technomancer.crossroads.API.Capabilities;
import com.Da_Technomancer.crossroads.API.beams.BeamUnit;
import com.Da_Technomancer.crossroads.API.beams.BeamUtil;
import com.Da_Technomancer.crossroads.API.beams.EnumBeamAlignments;
import com.Da_Technomancer.crossroads.API.beams.IBeamHandler;
import com.Da_Technomancer.crossroads.integration.curios.CurioHelper;
import com.Da_Technomancer.crossroads.items.CRItems;
import com.Da_Technomancer.crossroads.render.CRRenderUtil;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.commons.lang3.tuple.Triple;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class StaffTechnomancy extends BeamUsingItem{

	private static final int MAX_RANGE = 64;
	private final Multimap<Attribute, AttributeModifier> attributeModifiers;

	public StaffTechnomancy(){
		super(new Properties().group(CRItems.TAB_CROSSROADS).maxStackSize(1));
		String name = "staff_technomancy";
		setRegistryName(name);
		CRItems.toRegister.add(this);

		//Attributes
		ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
		builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", 9, AttributeModifier.Operation.ADDITION));
		builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", -3.1D, AttributeModifier.Operation.ADDITION));
		attributeModifiers = builder.build();
	}

	@Override
	public int getUseDuration(ItemStack stack){
		//Any large number works. 72000 is used in vanilla code, so it's used here for consistency.
		return 72000;
	}

	@Override
	public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand hand){
		playerIn.setActiveHand(hand);
		return new ActionResult<>(ActionResultType.SUCCESS, playerIn.getHeldItem(hand));
	}

	@Override
	public void onUsingTick(ItemStack stack, LivingEntity player, int count){
		if(!player.world.isRemote && player.isAlive() && (getUseDuration(stack) - count) % BeamUtil.BEAM_TIME == 0){
			ItemStack cage = CurioHelper.getEquipped(CRItems.beamCage, player);//player.getHeldItem(player.getActiveHand() == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
			byte[] setting = getSetting(stack);
			BeamUnit cageBeam = BeamCage.getStored(cage);
			if(setting[0] <= cageBeam.getEnergy() && setting[1] <= cageBeam.getPotential() && setting[2] <= cageBeam.getStability() && setting[3] <= cageBeam.getVoid() && setting[0] + setting[1] + setting[2] + setting[3] != 0){
				//Handle beam consumption
				BeamCage.storeBeam(cage, new BeamUnit(cageBeam.getEnergy() - setting[0], cageBeam.getPotential() - setting[1], cageBeam.getStability() - setting[2], cageBeam.getVoid() - setting[3]));
				BeamUnit mag = new BeamUnit(setting[0], setting[1], setting[2], setting[3]);

				//Calculate the start and end point of the fired beam
				double heldOffset = .22D * (player.getActiveHand() == Hand.MAIN_HAND ^ player.getPrimaryHand() == HandSide.LEFT ? 1D : -1D);
				Vector3d start = new Vector3d(player.getPosX() - (heldOffset * Math.cos(Math.toRadians(player.rotationYaw))), player.getPosY() + player.getEyeHeight() + 0.4D, player.getPosZ() - (heldOffset * Math.sin(Math.toRadians(player.rotationYaw))));

				Triple<BlockPos, Vector3d, Direction> beamHitResult = rayTraceBeams(mag, player.world, start, player.getEyePosition(1), player.getLookVec(), player, null, MAX_RANGE);
				BlockPos endPos = beamHitResult.getLeft();
				Direction effectDir = beamHitResult.getRight();

//				double[] end = new double[] {player.getPosX(), player.getEyeHeight() + player.getPosY(), player.getPosZ()};
//				BlockPos endPos = null;
//				Vector3d look = player.getLookVec().scale(0.2D);
//				Direction collisionDir = Direction.getFacingFromVector(look.x, look.y, look.z);//Used for beam collision detection
//				Direction effectDir = null;
//				//Raytrace manually along the look direction
//				for(double d = 0; d < MAX_RANGE; d += 0.2D){
//					end[0] += look.x;
//					end[1] += look.y;
//					end[2] += look.z;
//					//Look for entities along the firing path to collide with
//					List<Entity> ents = player.world.getEntitiesInAABBexcluding(player, new AxisAlignedBB(end[0] - 0.1D, end[1] - 0.1D, end[2] - 0.1D, end[0] + 0.1D, end[1] + 0.1D, end[2] + 0.1D), EntityPredicates.IS_ALIVE);
//					if(!ents.isEmpty()){
//						Optional<Vector3d> res = ents.get(0).getBoundingBox().rayTrace(start, new Vector3d(end[0], end[1], end[2]));
//						if(res.isPresent()){
//							Vector3d hitVec = res.get();
//							end[0] = hitVec.x;
//							end[1] = hitVec.y;
//							end[2] = hitVec.z;
//						}
//						break;
//					}
//
//					BlockPos newEndPos = new BlockPos(end[0], end[1], end[2]);
//					//Speed things up a bit by not rechecking blocks
//					if(newEndPos.equals(endPos) || World.isOutsideBuildHeight(newEndPos)){
//						continue;
//					}
//					endPos = newEndPos;
//					BlockState state = player.world.getBlockState(endPos);
//					if(BeamUtil.solidToBeams(state, player.world, endPos, collisionDir, mag.getPower())){
//						//Note: this VoxelShape has no offset
//						VoxelShape shape = state.getRenderShape(player.world, endPos);//.getBoundingBox(player.world, endPos).offset(endPos);
//						BlockRayTraceResult res = shape.rayTrace(start, new Vector3d(end[0] + look.x * 5D, end[1] + look.y * 5D, end[2] + look.z * 5D), endPos);//bb.calculateIntercept(start, new Vec3d(end[0] + look.x * 5D, end[1] + look.y * 5D, end[2] + look.z * 5D));
//						if(res != null){
//							Vector3d hitVec = res.getHitVec();
//							end[0] = hitVec.x;
//							end[1] = hitVec.y;
//							end[2] = hitVec.z;
//							effectDir = res.getFace();
//							break;
//						}
//					}
//				}

				if(endPos != null){//Should always be true
					TileEntity te = player.world.getTileEntity(endPos);
					LazyOptional<IBeamHandler> opt;
					if(te != null && (opt = te.getCapability(Capabilities.BEAM_CAPABILITY, effectDir)).isPresent()){
						opt.orElseThrow(NullPointerException::new).setBeam(mag);
					}else{
						EnumBeamAlignments align = EnumBeamAlignments.getAlignment(mag);
						if(!World.isOutsideBuildHeight(endPos)){
							align.getEffect().doBeamEffect(align, mag.getVoid() != 0, Math.min(64, mag.getPower()), player.world, endPos, effectDir);
						}
					}
				}

				Vector3d beamVec = beamHitResult.getMiddle().subtract(start);
				CRRenderUtil.addBeam(player.world, start.x, start.y, start.z, beamVec.length(), (float) Math.toDegrees(Math.atan2(-beamVec.y, Math.sqrt(beamVec.x * beamVec.x + beamVec.z * beamVec.z))), (float) Math.toDegrees(Math.atan2(-beamVec.x, beamVec.z)), (byte) Math.round(Math.sqrt(mag.getPower())), mag.getRGB().getRGB());
			}
		}
	}

	public static Triple<BlockPos, Vector3d, Direction> rayTraceBeams(BeamUnit beam, World world, Vector3d startPos, Vector3d endSourcePos, Vector3d ray, @Nullable Entity excludedEntity, @Nullable BlockPos ignorePos, double maxRange){
		ray = ray.scale(0.2D);
		Direction collisionDir = Direction.getFacingFromVector(ray.x, ray.y, ray.z);//Used for beam collision detection
		Direction effectDir = null;
		BlockPos endPos = null;
		double[] end = new double[] {endSourcePos.x, endSourcePos.y, endSourcePos.z};
		//Raytrace manually along the look direction
		for(double d = 0; d < maxRange; d += 0.2D){
			end[0] += ray.x;
			end[1] += ray.y;
			end[2] += ray.z;
			//Look for entities along the firing path to collide with
			List<Entity> ents = world.getEntitiesInAABBexcluding(excludedEntity, new AxisAlignedBB(end[0] - 0.1D, end[1] - 0.1D, end[2] - 0.1D, end[0] + 0.1D, end[1] + 0.1D, end[2] + 0.1D), EntityPredicates.IS_ALIVE);
			if(!ents.isEmpty()){
				Optional<Vector3d> res = ents.get(0).getBoundingBox().rayTrace(startPos, new Vector3d(end[0], end[1], end[2]));
				if(res.isPresent()){
					Vector3d hitVec = res.get();
					end[0] = hitVec.x;
					end[1] = hitVec.y;
					end[2] = hitVec.z;
				}
				break;
			}

			BlockPos newEndPos = new BlockPos(end[0], end[1], end[2]);
			//Speed things up a bit by not rechecking blocks
			if(newEndPos.equals(endPos) || World.isOutsideBuildHeight(newEndPos) || newEndPos.equals(ignorePos)){
				continue;
			}
			endPos = newEndPos;
			BlockState state = world.getBlockState(endPos);
			if(BeamUtil.solidToBeams(state, world, endPos, collisionDir, beam.getPower())){
				//Note: this VoxelShape has no offset
				VoxelShape shape = state.getRenderShape(world, endPos);//.getBoundingBox(player.world, endPos).offset(endPos);
				BlockRayTraceResult res = shape.rayTrace(startPos, new Vector3d(end[0] + ray.x * 5D, end[1] + ray.y * 5D, end[2] + ray.z * 5D), endPos);
				if(res != null){
					Vector3d hitVec = res.getHitVec();
					end[0] = hitVec.x;
					end[1] = hitVec.y;
					end[2] = hitVec.z;
					effectDir = res.getFace();
					break;
				}
			}
		}

		return Triple.of(endPos, new Vector3d(end[0], end[1], end[2]), effectDir);
	}

	@Override
	public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlotType slot, ItemStack stack){
		//Acts as a melee weapon; absolutely a DiscWorld reference
		return slot == EquipmentSlotType.MAINHAND ? attributeModifiers : super.getAttributeModifiers(slot, stack);
	}

	@Override
	protected byte maxSetting(){
		return 8;
	}
}
