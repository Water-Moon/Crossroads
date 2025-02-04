package com.Da_Technomancer.crossroads.tileentities.technomancy;

import com.Da_Technomancer.crossroads.API.CRProperties;
import com.Da_Technomancer.crossroads.API.Capabilities;
import com.Da_Technomancer.crossroads.API.MiscUtil;
import com.Da_Technomancer.crossroads.API.beams.BeamUnit;
import com.Da_Technomancer.crossroads.API.beams.EnumBeamAlignments;
import com.Da_Technomancer.crossroads.API.beams.IBeamHandler;
import com.Da_Technomancer.crossroads.API.packets.CRPackets;
import com.Da_Technomancer.crossroads.API.rotary.IAxisHandler;
import com.Da_Technomancer.crossroads.API.rotary.IAxleHandler;
import com.Da_Technomancer.crossroads.API.rotary.RotaryUtil;
import com.Da_Technomancer.crossroads.API.technomancy.*;
import com.Da_Technomancer.crossroads.CRConfig;
import com.Da_Technomancer.crossroads.Crossroads;
import com.Da_Technomancer.crossroads.blocks.CRBlocks;
import com.Da_Technomancer.essentials.packets.SendLongToClient;
import com.Da_Technomancer.essentials.tileentities.ILinkTE;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.EntityPredicates;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.ObjectHolder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ObjectHolder(Crossroads.MODID)
public class GatewayControllerTileEntity extends TileEntity implements IGateway, ITickableTileEntity, IFluxLink{

	@ObjectHolder("gateway_frame")
	public static TileEntityType<GatewayControllerTileEntity> type = null;
	public static final int INERTIA = 0;//Moment of inertia
	public static final int FLUX_PER_CYCLE = 4;
	private static final float ROTATION_SPEED = (float) Math.PI / 40F;//Rate of convergence between angle and axle 'speed' in radians/tick. Yes, this terminology is confusing

	//These fields are only correct for the top center block of the multiblock (isActive() returns true)
	//They will not necessarily be null/empty/0 if this inactive- always check isActive()
	private final FluxHelper fluxHelper = new FluxHelper(this, Behaviour.SOURCE);
	private GatewayAddress address = null;//The address of THIS gateway
	private double rotaryEnergy = 0;//Rotary energy
	private float angle = 0;//Used for rendering and dialing chevrons. Because it's used for logic, we don't use the master axis angle syncing, which is render-based
	private float clientAngle = 0;//Angle on the client. On the server, acts as a record of value sent to client
	private float clientW = 0;//Speed on the client (post adjustment). On the server, acts as a record of value sent to client
	private float referenceSpeed = 0;//Speed which angles will be defined relative to on the server
	private long lastTick = 0;
	//Visible for rendering
	public EnumBeamAlignments[] chevrons = new EnumBeamAlignments[4];//Current values locked into chevrons. Null for unset chevrons
	private boolean origin = false;//Whether this gateway started the connection in dialed (determines which side has flux)

	private IAxleHandler axleHandler = null;
	private LazyOptional<IAxleHandler> axleOpt = null;
	private LazyOptional<IBeamHandler> beamOpt = null;

	private int size = 0;//Diameter of the multiblock, from top center to bottom center
	private Direction.Axis plane = null;//Legal values are null (unformed), x (for structure in x-y plane), and z (for structure in y-z plane). This should never by y

	public GatewayControllerTileEntity(){
		super(type);
	}

	@Override
	public void addInfo(ArrayList<ITextComponent> chat, PlayerEntity player, BlockRayTraceResult hit){
		if(isActive() && address != null){
			//Address of this gateway
			String[] names = new String[4];
			for(int i = 0; i < 4; i++){
				EnumBeamAlignments align = address.getEntry(i);
				if(align == null){
					align = EnumBeamAlignments.NO_MATCH;//Should never ahppen
				}
				names[i] = align.getLocalName(false);
			}
			chat.add(new TranslationTextComponent("tt.crossroads.gateway.chevron.address", names[0], names[1], names[2], names[3]));

			//Chevrons
			boolean addedPotential = false;//Whether we have added the name of the potentially next alignment to dial
			for(int i = 0; i < 4; i++){
				if(chevrons[i] == null){
					if(addedPotential){
						names[i] = MiscUtil.localize("tt.crossroads.gateway.chevron.none");
					}else{
						addedPotential = true;
						names[i] = String.format("[%s]", GatewayAddress.getLegalEntry(Math.round(angle * 8F / 2F / (float) Math.PI)).getLocalName(false));
					}
				}else{
					names[i] = chevrons[i].getLocalName(false);
				}
			}
			chat.add(new TranslationTextComponent("tt.crossroads.gateway.chevron.dialed", names[0], names[1], names[2], names[3]));
			genOptionals();
			RotaryUtil.addRotaryInfo(chat, axleHandler, true);
			FluxUtil.addFluxInfo(chat, this, chevrons[3] != null && origin ? FLUX_PER_CYCLE : 0);
			FluxUtil.addLinkInfo(chat, this);
		}
	}

	/**
	 * Used for rendering
	 * @return The size of the formed multiblock. Only valid on the client for the top-center block of the formed multiblock
	 */
	public int getSize(){
		return size;
	}

	/**
	 * Used for rendering
	 * @return The plane of the formed multiblock. Only valid on the client for the top-center block of the formed multiblock
	 */
	@Nullable
	public Direction.Axis getPlane(){
		return plane;
	}

	/**
	 * Used for rendering
	 * @param partialTicks The partial ticks in [0, 1]
	 * @return The angle of the octagonal ring used for dialing
	 */
	public double getAngle(float partialTicks){
		return calcAngleChange(clientW, clientAngle) * partialTicks + clientAngle;
	}

	/**
	 * Determines whether this TE should do anything
	 * @return Whether this block is formed into a multiblock and is the top center block (which handles all the logic)
	 */
	public boolean isActive(){
		BlockState state = getBlockState();
		return state.getBlock() == CRBlocks.gatewayController && getBlockState().get(CRProperties.ACTIVE);
	}

	//Gateway connection management

	@Nullable
	@Override
	public GatewayAddress getAddress(){
		return address;
	}

	private void undialLinkedGateway(){
		GatewayAddress prevDialed = new GatewayAddress(chevrons);
		GatewayAddress.Location prevLinkLocation = GatewaySavedData.lookupAddress((ServerWorld) world, prevDialed);
		if(prevLinkLocation != null){
			IGateway prevLink = prevLinkLocation.evalTE(world.getServer());
			if(prevLink != null){
				prevLink.undial(address);
			}
		}
	}

	@Override
	public void undial(GatewayAddress other){
		GatewayAddress prevDialed = new GatewayAddress(chevrons);
		if(prevDialed.fullAddress() && prevDialed.equals(other)){
			//Wipe the chevrons
			for(int i = 0; i < 4; i++){
				chevrons[i] = null;
			}
			origin = false;
			referenceSpeed = 0;
			resyncToClient();
			markDirty();
			CRPackets.sendPacketAround(world, pos, new SendLongToClient(3, 0L, pos));
		}
	}

	@Override
	public void dialTo(GatewayAddress other, boolean cost){
		//Disconnect from any previous connection
		GatewayAddress prevDialed = new GatewayAddress(chevrons);
		if(prevDialed.fullAddress() && !prevDialed.equals(other)){
			//Undial the connected gateway
			undialLinkedGateway();
			//Undial this gateway
			undial(prevDialed);
		}

		//Create the new connection
		//Set the chevrons
		for(int i = 0; i < 4; i++){
			chevrons[i] = other.getEntry(i);
		}
		origin = cost;
		playEffects(true);
		//Send chevrons to client
		CRPackets.sendPacketAround(world, pos, new SendLongToClient(3, new GatewayAddress(chevrons).serialize(), pos));
	}

	@Override
	public void teleportEntity(Entity entity, float horizontalRelPos, float verticalRelPos, Direction.Axis sourceAxis){
		Vector3d centerPos = new Vector3d(pos.getX() + 0.5D, pos.getY() - size / 2D + 0.5D, pos.getZ() + 0.5D);
		float scalingRadius = (size - 2) / 2F;
		if(plane == Direction.Axis.X){
			IGateway.teleportEntityTo(entity, (ServerWorld) world, centerPos.x + scalingRadius * horizontalRelPos, centerPos.y + scalingRadius * verticalRelPos, centerPos.z, sourceAxis == plane ? 0 : 90);
		}else{
			IGateway.teleportEntityTo(entity, (ServerWorld) world, centerPos.x, centerPos.y + scalingRadius * verticalRelPos, centerPos.z + scalingRadius * horizontalRelPos, sourceAxis == plane ? 0 : -90);
		}
		playTPEffect(world, entity.getPosX(), entity.getPosY(), entity.getPosZ());
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox(){
		//Increase render BB to include links and the entire formed frame
		return new AxisAlignedBB(pos).grow(Math.max(getRange(), isActive() ? size : 0));
	}

	/**
	 * Creates purely aesthetic sounds/particles
	 * Virtual-server side only
	 * @param success Whether this is for a successful action (like connecting) or an unsucessful action (like dialing a fake address)
	 */
	private void playEffects(boolean success){
		world.playSound(pos.getX() + 0.5F, pos.getY() - 1.5F, pos.getZ() + 0.5F, success ? SoundEvents.BLOCK_END_PORTAL_FRAME_FILL : SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1F, world.rand.nextFloat(), true);
	}

	//Multiblock management

	@Override
	public void dismantle(){
		if(!world.isRemote && isActive()){
			//The head dismantles the entire multiblock, restoring inactive states

			//Cancel our connection
			undialLinkedGateway();
			undial(new GatewayAddress(chevrons));

			//Release our address back into the pool
			GatewaySavedData.releaseAddress((ServerWorld) world, address);

			BlockPos.Mutable mutPos = new BlockPos.Mutable(pos.getX(), pos.getY(), pos.getZ());
			Direction horiz = plane == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;//horizontal direction
			int preSize = size;//We have to store this, as the field will be modified in the loop
			mutPos.move(horiz, -preSize / 2);
			for(int i = 0; i < preSize; i++){
				for(int j = 0; j < preSize; j++){
					//Iterate over a size-by-size square (technically excessive as the multiblock is hollow) and disable each individually (including this)
					BlockState otherState = world.getBlockState(mutPos);
					if(otherState.getBlock() == CRBlocks.gatewayEdge){
						world.setBlockState(mutPos, otherState.with(CRProperties.ACTIVE, false));
					}
					TileEntity te = world.getTileEntity(mutPos);
					if(te instanceof GatewayEdgeTileEntity){
						GatewayEdgeTileEntity otherTE = (GatewayEdgeTileEntity) te;
						otherTE.reset();
					}
					mutPos.move(horiz, 1);
				}
				mutPos.move(horiz, -preSize);
				mutPos.move(Direction.DOWN, 1);
			}

			//Reset this block
			BlockState state = getBlockState();
			if(state.getBlock() == CRBlocks.gatewayController){
				world.setBlockState(pos, getBlockState().with(CRProperties.ACTIVE, false));
			}
			size = 0;
			plane = null;
			axleOpt = null;
			beamOpt = null;
			address = null;
			origin = false;
			markDirty();
			updateContainingBlockInfo();
		}
	}

	/**
	 * Attempts to assemble this into a multiblock
	 * This will only work if this is the top-center block
	 * @return Whether this succeeded at forming the multiblock
	 */
	public boolean assemble(PlayerEntity player){
		if(world.isRemote){
			return false;//Server side only
		}
		if(getBlockState().get(CRProperties.ACTIVE)){
			return false;//This is already part of a multiblock
		}

		//First step is to determine the size
		int newSize = 0;
		BlockPos.Mutable mutPos = new BlockPos.Mutable(pos.getX(), pos.getY(), pos.getZ());
		//Maximum size is a 63x63, odd sized squares only
		boolean foundAir = false;//Indicates we have passed the top section of the frame
		int foundThickness = 1;
		for(int i = 1; i < 63; i++){
			mutPos.move(Direction.DOWN);
			BlockState state = world.getBlockState(mutPos);
			if(legalForGateway(state)){
				if(foundAir){
					newSize = i + foundThickness;
					break;
				}else{
					foundThickness++;
				}
//			}else if(!state.isAir(world, mutPos)){
//				return false;//There is an obstruction
			}else{
				foundAir = true;
			}
		}
		if(newSize < 5 || newSize % 2 == 0){
			MiscUtil.chatMessage(player, new TranslationTextComponent("tt.crossroads.gateway.size_wrong"));
			return false;//Even sizes are not allowed
		}

		Direction.Axis axis;
		if(legalForGateway(world.getBlockState(pos.east())) && legalForGateway(world.getBlockState(pos.west()))){
			axis = Direction.Axis.X;
		}else if(legalForGateway(world.getBlockState(pos.south())) && legalForGateway(world.getBlockState(pos.north()))){
			axis = Direction.Axis.Z;
		}else{
			return false;//There wasn't even enough of the structure to determine what orientation it's supposed to have
		}

		size = newSize;
		plane = axis;
		int thickness = Math.max(1, size / 5);//required thickness of frame blocks

		//First pass over the area is to confirm this is a legal structure
		Direction horiz = Direction.getFacingFromAxis(Direction.AxisDirection.POSITIVE, axis);
		mutPos.setPos(pos).move(horiz, -size / 2);
		for(int i = 0; i < size; i++){
			for(int j = 0; j < size; j++){
				//Iterate over a size-by-size square and check each pos
				BlockState otherState = world.getBlockState(mutPos);

				if(i < thickness || size - i <= thickness || j < thickness || size - j <= thickness){
					//We are on the edges, and expect a frame block
					if((i != 0 || j != size / 2) && !legalForGateway(otherState)){
						MiscUtil.chatMessage(player, new TranslationTextComponent("tt.crossroads.gateway.thickness", thickness));
						return false;
					}
				}
				//Removed hollow requirement
//				else if(!otherState.isAir(world, mutPos)){
//					return false;//We are on the inside, and expect air
//				}

				mutPos.move(horiz, 1);
			}
			mutPos.move(horiz, -size);
			mutPos.move(Direction.DOWN, 1);
		}

		//Configure this TE
		//Request an address- fail if we can't get one
		address = GatewaySavedData.requestAddress((ServerWorld) world, pos);
		if(address == null){
			MiscUtil.chatMessage(player, new TranslationTextComponent("tt.crossroads.gateway.address_taken"));
			return false;
		}
		//Resetting the optionals to null forces the optional cache to regenerate
		axleOpt = null;
		beamOpt = null;

		//Second pass is to actually assemble the structure
		mutPos.setPos(pos).move(horiz, -size / 2);
		for(int i = 0; i < size; i++){
			for(int j = 0; j < size; j++){
				//Iterate over a size-by-size square and modify each edge
				BlockState otherState = world.getBlockState(mutPos);
				if(i < thickness || size - i <= thickness || j < thickness || size - j <= thickness){
					//We are on the edges
					world.setBlockState(mutPos, otherState.with(CRProperties.ACTIVE, true));
					TileEntity te = world.getTileEntity(mutPos);
					if(te instanceof GatewayEdgeTileEntity){
						GatewayEdgeTileEntity otherTE = (GatewayEdgeTileEntity) te;
						otherTE.setKey(pos.subtract(mutPos));
						otherTE.updateContainingBlockInfo();
					}
				}

				mutPos.move(horiz, 1);
			}
			mutPos.move(horiz, -size);
			mutPos.move(Direction.DOWN, 1);
		}

		//Update this block
		world.setBlockState(pos, getBlockState().with(CRProperties.ACTIVE, true));
		updateContainingBlockInfo();

		//Send a packet to the client with the size and orientation info
		CRPackets.sendPacketAround(world, pos, new SendLongToClient(5, plane.ordinal() | (size << 2), pos));

		return true;
	}

	private static boolean legalForGateway(BlockState state){
		return state.getBlock() == CRBlocks.gatewayEdge && !state.get(CRProperties.ACTIVE);
	}

	@Override
	public void tick(){
		if(isActive()){
			//This TE only ticks if it is active

			//Perform angle movement on the client, and track what the client is probably doing on the server
			clientAngle += calcAngleChange(clientW, clientAngle);

			if(!world.isRemote){
				genOptionals();
				//Perform angle movement on the server
				float angleTarget = (float) axleHandler.getSpeed() - referenceSpeed;
				angle += calcAngleChange(angleTarget, angle);

				//Check for resyncing angle data to client
				final double errorMargin = Math.PI / 32D;
				if(Math.abs(clientAngle - angle) >= errorMargin || Math.abs(clientW - angleTarget) >= errorMargin / 2D){
					//Resync the speed and angle to the client
					resyncToClient();
				}

				//Teleportation
				if(chevrons[3] != null && plane != null && !fluxHelper.isShutDown()){
					Direction horiz = Direction.getFacingFromAxis(Direction.AxisDirection.POSITIVE, plane);
					AxisAlignedBB area = new AxisAlignedBB(pos.down(size).offset(horiz, -size / 2), pos.offset(horiz, size / 2 + 1));
					//We use the timeUntilPortal field in Entity to not spam TP entities between two portals
					//This is both not what it's for, and exactly what it's for
					List<Entity> entities = world.getEntitiesWithinAABB(Entity.class, area, EntityPredicates.IS_ALIVE.and(e -> IGateway.isAllowedToTeleport(e, world)));
					if(!entities.isEmpty()){
						GatewayAddress.Location loc = GatewaySavedData.lookupAddress((ServerWorld) world, new GatewayAddress(chevrons));
						IGateway otherTE;
						if(loc != null && (otherTE = loc.evalTE(world.getServer())) != null){
							Vector3d centerPos = new Vector3d(pos.getX() + 0.5D, pos.getY() - size / 2D + 0.5D, pos.getZ() + 0.5D);
							float scalingRadius = (size - 2) / 2F;
							for(Entity e : entities){
								float relPosH = MathHelper.clamp(plane == Direction.Axis.X ? ((float) (e.getPosX() - centerPos.x) / scalingRadius) : ((float) (e.getPosZ() - centerPos.z) / scalingRadius), -1, 1);
								float relPosV = MathHelper.clamp((float) (e.getPosY() - centerPos.y) / scalingRadius, -1, 1);
								playTPEffect(world, e.getPosX(), e.getPosY(), e.getPosZ());//Play effects at the start position
								otherTE.teleportEntity(e, relPosH, relPosV, plane);
							}
						}
					}
				}

				//Handle flux
				fluxHelper.tick();
				long currTick = world.getGameTime();
				if(currTick % FluxUtil.FLUX_TIME == 0 && origin && lastTick != currTick){
					fluxHelper.addFlux(FLUX_PER_CYCLE);
					lastTick = currTick;
				}
			}
		}
	}

	/**
	 * Calculates the change in angle each tick, based on the target angle and current angle
	 * Takes the shortest path, has a maximum angle change per tick
	 * @param target The target angle
	 * @param current The current angle
	 * @return The change in angle to occur this tick. Positive is counter-clockwise, negative is clockwise
	 */
	private static float calcAngleChange(float target, float current){
		final float pi2 = (float) Math.PI * 2F;
		//Due to circular path, the two routes to the target need to be compared, and the shortest taken
		float angleChange = MiscUtil.clockModulus(target, pi2) - MiscUtil.clockModulus(current, pi2);
		if(angleChange > Math.PI || angleChange < -Math.PI){
			if(angleChange > 0){
				angleChange -= pi2;
			}else{
				angleChange += pi2;
			}
		}
		angleChange = MathHelper.clamp(angleChange, -ROTATION_SPEED, ROTATION_SPEED);
		return angleChange;
	}

	private static void playTPEffect(World world, double xPos, double yPos, double zPos){
		//Spawn smoke particles
		for(int i = 0; i < 10; i++){
			world.addOptionalParticle(ParticleTypes.SMOKE, xPos + Math.random() - 0.5D, yPos + Math.random() - 0.5D, zPos + Math.random() - 0.5D, Math.random() - 0.5F, Math.random() - 0.5F, Math.random() - 0.5F);
		}
		//play a sound
		world.playSound(xPos, yPos, zPos, SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.BLOCKS, 1, (float) Math.random(), true);
	}

	@Override
	public void read(BlockState state, CompoundNBT nbt){
		super.read(state, nbt);
		//Active only
		fluxHelper.read(nbt);
		address = nbt.contains("address") ? GatewayAddress.deserialize(nbt.getInt("address")) : null;
		clientW = nbt.getFloat("client_speed");
		rotaryEnergy = nbt.getDouble("rot_1");
		for(int i = 0; i < 4; i++){
			chevrons[i] = nbt.contains("chev_" + i) ? EnumBeamAlignments.values()[nbt.getInt("chev_" + i)] : null;
		}
		angle = nbt.getFloat("angle");
		clientAngle = angle;
		referenceSpeed = nbt.getFloat("reference");
		origin = nbt.getBoolean("origin");

		//Generic
		size = nbt.getInt("size");
		plane = nbt.contains("plane") ? Direction.Axis.values()[nbt.getInt("plane")] : null;
	}

	@Override
	public CompoundNBT write(CompoundNBT nbt){
		super.write(nbt);
		//Active only
		fluxHelper.write(nbt);
		if(address != null){
			nbt.putInt("address", address.serialize());
		}
		for(int i = 0; i < 4; i++){
			if(chevrons[i] != null){
				nbt.putInt("chev_" + i, chevrons[i].ordinal());
			}
		}
		nbt.putFloat("client_speed", axleHandler == null ? clientW : (float) axleHandler.getSpeed());
		nbt.putDouble("rot_1", rotaryEnergy);
		nbt.putFloat("angle", angle);
		nbt.putFloat("reference", referenceSpeed);
		nbt.putBoolean("origin", origin);

		//Generic
		nbt.putInt("size", size);
		if(plane != null){
			nbt.putInt("plane", plane.ordinal());
		}

		return nbt;
	}

	@Override
	public CompoundNBT getUpdateTag(){
		CompoundNBT nbt = super.getUpdateTag();
		fluxHelper.write(nbt);
		for(int i = 0; i < 4; i++){
			if(chevrons[i] != null){
				nbt.putInt("chev_" + i, chevrons[i].ordinal());
			}
		}
		nbt.putFloat("client_speed", clientW);
		nbt.putDouble("rot_1", rotaryEnergy);
		nbt.putFloat("angle", angle);

		nbt.putInt("size", size);
		if(plane != null){
			nbt.putInt("plane", plane.ordinal());
		}
		return nbt;
	}

	private void resyncToClient(){
		genOptionals();
		clientAngle = angle;
		clientW = (float) axleHandler.getSpeed() - referenceSpeed;
		long packet = (Integer.toUnsignedLong(Float.floatToRawIntBits(clientAngle)) << 32L) | Integer.toUnsignedLong(Float.floatToRawIntBits(clientW));
		CRPackets.sendPacketAround(world, pos, new SendLongToClient(4, packet, pos));
	}

	//Flux boilerplate

	@Override
	public int getFlux(){
		return fluxHelper.getFlux();
	}

	@Override
	public boolean canBeginLinking(){
		return fluxHelper.canBeginLinking();
	}

	@Override
	public boolean canLink(ILinkTE otherTE){
		return fluxHelper.canLink(otherTE);
	}

	@Override
	public Set<BlockPos> getLinks(){
		return fluxHelper.getLinks();
	}

	@Override
	public boolean createLinkSource(ILinkTE endpoint, @Nullable PlayerEntity player){
		return fluxHelper.createLinkSource(endpoint, player);
	}

	@Override
	public void removeLinkSource(BlockPos end){
		fluxHelper.removeLinkSource(end);
	}

	@Override
	public int getReadingFlux(){
		return fluxHelper.getReadingFlux();
	}

	@Override
	public void addFlux(int deltaFlux){
		fluxHelper.addFlux(deltaFlux);
	}

	@Override
	public boolean canAcceptLinks(){
		return fluxHelper.canAcceptLinks();
	}

	//Capabilities

	private void genOptionals(){
		if(axleOpt == null){
			if(isActive()){
				axleHandler = new AxleHandler();
				axleOpt = LazyOptional.of(() -> axleHandler);
				beamOpt = LazyOptional.of(BeamHandler::new);
			}else{
				axleOpt = LazyOptional.empty();
				beamOpt = LazyOptional.empty();
			}
		}
	}

	@Override
	public void remove(){
		super.remove();
		if(axleOpt != null){
			axleOpt.invalidate();
		}
		if(beamOpt != null){
			beamOpt.invalidate();
		}
	}

	@Nonnull
	@Override
	@SuppressWarnings("unchecked")
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side){
		if(isActive()){
			genOptionals();
			if(cap == Capabilities.AXLE_CAPABILITY && (side == null || side == Direction.UP)){
				return (LazyOptional<T>) axleOpt;
			}
			if(cap == Capabilities.BEAM_CAPABILITY){
				return (LazyOptional<T>) beamOpt;
			}
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void receiveLong(byte identifier, long message, @Nullable ServerPlayerEntity player){
		fluxHelper.receiveLong(identifier, message, player);
		switch(identifier){
			case 3:
				GatewayAddress add = GatewayAddress.deserialize((int) message);
				for(int i = 0; i < 4; i++){
					chevrons[i] = add.getEntry(i);
				}
				break;
			case 4:
				clientAngle = Float.intBitsToFloat((int) (message >>> 32L));
				clientW = Float.intBitsToFloat((int) (message & 0xFFFFFFFFL));
				break;
			case 5:
				//size and orientation for rendering
				plane = Direction.Axis.values()[(int) (message & 3)];
				size = (int) (message >>> 2);
				break;
		}
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public double getMaxRenderDistanceSquared(){
		return 65536;//Same as beacon
	}

	private class AxleHandler implements IAxleHandler{

		//Fairly generic implementation that leaves angle management to tick()

		public double rotRatio;
		public byte updateKey;
		public IAxisHandler axis;

		@Override
		public void propagate(IAxisHandler masterIn, byte key, double rotRatioIn, double lastRadius, boolean renderOffset){
			//If true, this has already been checked.
			if(key == updateKey || masterIn.addToList(this)){
				return;
			}

			rotRatio = rotRatioIn == 0 ? 1 : rotRatioIn;
			updateKey = key;
			axis = masterIn;
		}

		@Override
		public double getRotationRatio(){
			return rotRatio;
		}

		@Override
		public float getAngle(float partialTicks){
			return clientAngle + partialTicks * clientW / 20F;
		}

		@Override
		public void disconnect(){
			axis = null;
		}

		@Override
		public double getSpeed(){
			return axis == null ? 0 : rotRatio * axis.getBaseSpeed();
		}

		@Override
		public double getEnergy(){
			return rotaryEnergy;
		}

		@Override
		public void setEnergy(double newEnergy){
			rotaryEnergy = newEnergy;
			markDirty();
		}

		@Override
		public double getMoInertia(){
			return INERTIA;
		}
	}

	private class BeamHandler implements IBeamHandler{

		@Override
		public void setBeam(@Nonnull BeamUnit mag){
			if(mag.isEmpty()){
				return;
			}

			if(chevrons[3] != null){
				//We're dialed into something. Reset
				undialLinkedGateway();
				undial(new GatewayAddress(chevrons));
				return;
			}

			int index = 0;//Find the first undialed chevron
			for(int i = 0; i < 4; i++){
				if(chevrons[i] == null){
					index = i;
					break;
				}
			}

			EnumBeamAlignments alignment = GatewayAddress.getLegalEntry(Math.round(angle * 8F / 2F / (float) Math.PI));

			if(CRConfig.hardGateway.get() && alignment != EnumBeamAlignments.getAlignment(mag)){
				//Optional hardmode (off by default)
				chevrons[0] = chevrons[1] = chevrons[2] = chevrons[3] = null;
				return;
			}

			chevrons[index] = alignment;//Dial in a new chevron
			referenceSpeed = (float) axleHandler.getSpeed();//Re-define our reference to the current input speed
			if(index == 3){
				//If this is the final chevron, make the connection and reset the target
				GatewayAddress targetAddress = new GatewayAddress(chevrons);
				GatewayAddress.Location location = GatewaySavedData.lookupAddress((ServerWorld) world, targetAddress);
				IGateway otherGateway;
				if(location != null && (otherGateway = location.evalTE(world.getServer())) != null){
					otherGateway.dialTo(address, false);
					dialTo(targetAddress, true);
				}else{
					//Invalid address; reset
					chevrons[0] = chevrons[1] = chevrons[2] = chevrons[3] = null;
				}

				//Reset reference speed state
				referenceSpeed = 0;
			}
			resyncToClient();//Force a resync of the speed and angle to the client
			CRPackets.sendPacketAround(world, pos, new SendLongToClient(3, new GatewayAddress(chevrons).serialize(), pos));
			markDirty();
		}
	}
}
