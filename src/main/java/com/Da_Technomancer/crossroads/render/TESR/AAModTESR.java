package com.Da_Technomancer.crossroads.render.TESR;

import com.Da_Technomancer.crossroads.blocks.CrossroadsBlocks;
import com.Da_Technomancer.crossroads.tileentities.beams.*;
import com.Da_Technomancer.crossroads.tileentities.electric.DynamoTileEntity;
import com.Da_Technomancer.crossroads.tileentities.electric.TeslaCoilTopTileEntity;
import com.Da_Technomancer.crossroads.tileentities.fluid.RotaryPumpTileEntity;
import com.Da_Technomancer.crossroads.tileentities.heat.HeatingCrucibleTileEntity;
import com.Da_Technomancer.crossroads.tileentities.rotary.*;
import com.Da_Technomancer.crossroads.tileentities.rotary.mechanisms.MechanismTileEntity;
import com.Da_Technomancer.crossroads.tileentities.technomancy.*;
import com.Da_Technomancer.essentials.render.LinkLineRenderer;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.fml.client.registry.ClientRegistry;

/** Before anyone asks, the AA at the beginning of the name is so I can easily find it in the list of classes.*/
public class AAModTESR{

	public static void registerBlockRenderer(){

		ClientRegistry.bindTileEntitySpecialRenderer(MechanismTileEntity.class, new MechanismRenderer());
		reg(CrossroadsBlocks.rotaryPump);
		ClientRegistry.bindTileEntitySpecialRenderer(RotaryPumpTileEntity.class, new RotaryPumpRenderer());
		reg(CrossroadsBlocks.steamTurbine);
		ClientRegistry.bindTileEntitySpecialRenderer(SteamTurbineTileEntity.class, new SteamTurbineRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(LargeGearMasterTileEntity.class, new LargeGearRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(RotaryDrillTileEntity.class, new RotaryDrillRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(BeamExtractorTileEntity.class, new BeamRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(QuartzStabilizerTileEntity.class, new BeamRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(CrystallinePrismTileEntity.class, new BeamRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(BeamReflectorTileEntity.class, new BeamRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(LensFrameTileEntity.class, new LensFrameRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(BeamSiphonTileEntity.class, new BeamRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(BeamRedirectorTileEntity.class, new BeamRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(BeamSplitterTileEntity.class, new BeamRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(BeaconHarnessTileEntity.class, new BeaconHarnessRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(HamsterWheelTileEntity.class, new HamsterWheelRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(GatewayFrameTileEntity.class, new GatewayFrameRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(MechanicalArmTileEntity.class, new MechanicalArmRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(HeatingCrucibleTileEntity.class, new HeatingCrucibleRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(DynamoTileEntity.class, new DynamoRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(PrototypingTableTileEntity.class, new PrototypingTableRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(ClockworkStabilizerTileEntity.class, new BeamRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(PrototypeTileEntity.class, new BeamRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(WindTurbineTileEntity.class, new WindTurbineRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(StampMillTileEntity.class, new StampMillRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(FluxNodeTileEntity.class, new FluxNodeRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(TeslaCoilTopTileEntity.class, new LinkLineRenderer<TeslaCoilTopTileEntity>());
		ClientRegistry.bindTileEntitySpecialRenderer(TemporalAcceleratorTileEntity.class, new TemporalAcceleratorRenderer());
//		ClientRegistry.bindTileEntitySpecialRenderer(RedstoneTransmitterTileEntity.class, new LinkLineRenderer<RedstoneTransmitterTileEntity>());
		ClientRegistry.bindTileEntitySpecialRenderer(StabilizerBeamTileEntity.class, new FluxStabilizerRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(StabilizerElectricTileEntity.class, new FluxStabilizerRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(ChronoHarnessTileEntity.class, new ChronoHarnessRenderer());
	}

	private static void reg(Block block){
		Minecraft.getInstance().getItemRenderer().getItemModelMesher().register(Item.getItemFromBlock(block), new ModelResourceLocation(block.getRegistryName().toString(), "inventory"));
	}
}