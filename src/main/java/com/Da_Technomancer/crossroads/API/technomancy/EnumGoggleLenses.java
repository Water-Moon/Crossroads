package com.Da_Technomancer.crossroads.API.technomancy;

import com.Da_Technomancer.crossroads.API.effects.goggles.*;
import com.Da_Technomancer.crossroads.Keys;
import com.Da_Technomancer.crossroads.items.ModItems;
import com.Da_Technomancer.crossroads.items.crafting.ItemRecipePredicate;
import com.Da_Technomancer.crossroads.items.crafting.OreDictCraftingStack;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.function.Predicate;

public enum EnumGoggleLenses{
	
	//Don't reorder these unless you want to rename all the goggle texture files.
	RUBY(new OreDictCraftingStack("gemRuby"), "_ruby", new RubyGoggleEffect(), Keys.controlEnergy),
	EMERALD(new OreDictCraftingStack("gemEmerald"), "_emerald", new EmeraldGoggleEffect(), Keys.controlPotential),
	DIAMOND(new OreDictCraftingStack("gemDiamond"), "_diamond", new DiamondGoggleEffect(), Keys.controlStability),
	QUARTZ(new ItemRecipePredicate(ModItems.pureQuartz, 0), "_quartz", new QuartzGoggleEffect(), null),
	VOID(new ItemRecipePredicate(ModItems.voidCrystal, 0), "", new VoidGoggleEffect(), Keys.controlVoid);
	
	private final Predicate<ItemStack> item;
	private final String texturePath;
	private final IGoggleEffect effect;
	private final KeyBinding key;
	
	EnumGoggleLenses(Predicate<ItemStack> item, String texturePath, IGoggleEffect effect, @Nullable KeyBinding toggleKey){
		this.item = item;
		this.texturePath = texturePath;
		this.effect = effect;
		this.key = toggleKey;
	}

	public boolean matchesRecipe(ItemStack stack){
		return item.test(stack);
	}
	
	public String getTexturePath(){
		return texturePath;
	}

	public KeyBinding getKey(){
		return key;
	}
	
	/**
	 * Call on the server side ONLY.
	 */
	public void doEffect(World world, EntityPlayer player, ArrayList<String> chat, RayTraceResult ray){
		effect.armorTick(world, player, chat, ray);
	}
	
	/**This will return the name with all but the first char being lowercase,
	 * so COPPER becomes Copper, which is good for oreDict and registry
	 */
	@Override
	public String toString(){
		String name = name();
		char char1 = name.charAt(0);
		name = name.substring(1);
		name = name.toLowerCase();
		name = char1 + name;
		return name;
	}
}
