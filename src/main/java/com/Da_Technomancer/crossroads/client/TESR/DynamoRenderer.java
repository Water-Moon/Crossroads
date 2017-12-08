package com.Da_Technomancer.crossroads.client.TESR;

import java.awt.Color;

import com.Da_Technomancer.crossroads.Main;
import com.Da_Technomancer.crossroads.API.Properties;
import com.Da_Technomancer.crossroads.API.rotary.GearTypes;
import com.Da_Technomancer.crossroads.blocks.ModBlocks;
import com.Da_Technomancer.crossroads.client.TESR.models.ModelAxle;
import com.Da_Technomancer.crossroads.client.TESR.models.ModelGearOctagon;
import com.Da_Technomancer.crossroads.tileentities.alchemy.DynamoTileEntity;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

public class DynamoRenderer extends TileEntitySpecialRenderer<DynamoTileEntity>{

	private final ResourceLocation textureAx = new ResourceLocation(Main.MODID, "textures/model/axle.png");
	private final ModelAxle modelAx = new ModelAxle();
	private final ModelGearOctagon modelOct = new ModelGearOctagon();
	private final ResourceLocation textureGear = new ResourceLocation(Main.MODID, "textures/model/gear_oct.png");

	@Override
	public void render(DynamoTileEntity dynamo, double x, double y, double z, float partialTicks, int destroyStage, float alpha){
		if(!dynamo.getWorld().isBlockLoaded(dynamo.getPos(), false) || dynamo.getWorld().getBlockState(dynamo.getPos()).getBlock() != ModBlocks.dynamo){
			return;
		}

		EnumFacing facing = dynamo.getWorld().getBlockState(dynamo.getPos()).getValue(Properties.HORIZONTAL_FACING);
		GlStateManager.pushMatrix();
		GlStateManager.disableLighting();
		GlStateManager.translate(x + .5F, y + .5F, z + .5F);
		GlStateManager.rotate(270F - facing.getHorizontalAngle(), 0, 1, 0);
		GlStateManager.rotate(90, 0, 0, 1);
		GlStateManager.rotate(-(float) ((dynamo.nextAngle - dynamo.angle) * partialTicks + dynamo.angle), 0F, 1F, 0F);
		modelAx.render(textureAx, textureAx, Color.WHITE);
		
		GlStateManager.pushMatrix();
		GlStateManager.translate(0, 0.5F, 0);
		GlStateManager.scale(0.7D, 0.7D, 0.7D);
		modelOct.render(textureGear, GearTypes.COPPER.getColor());
		GlStateManager.popMatrix();
		GlStateManager.enableLighting();
		GlStateManager.popMatrix();
	}
}
