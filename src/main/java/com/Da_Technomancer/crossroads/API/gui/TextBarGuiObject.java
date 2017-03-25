package com.Da_Technomancer.crossroads.API.gui;

import java.awt.Color;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.Da_Technomancer.crossroads.Main;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class TextBarGuiObject implements IGuiObject{
	
	private static final ResourceLocation BAR = new ResourceLocation(Main.MODID, "textures/gui/container/search_bar.png");
	private final int x;
	private final int y;
	private final int endX;
	private final int endY;
	private final int maxChar;
	private final String emptyText;
	private final Predicate<Character> acceptedChar;
	private final int baseX;
	private final int baseY;
	
	private boolean selected;
	private String text = "";
	
	/**
	 * 
	 * @param windowX X-coordinate where the GUI starts.
	 * @param windowY Y-coordinate where the GUI starts.
	 * @param x X-coordinate where the text bar starts, relative to the GUI.
	 * @param y Y-coordinate where the text bar starts, relative to the GUI.
	 * @param width Width of the text bar.
	 * @param maxChar Maximum number of characters.
	 * @param emptyText Text to display when the box is empty. May be null for no text. 
	 * @param acceptedChar A predicate to determine if the text box can hold a character.
	 */
	public TextBarGuiObject(int windowX, int windowY, int x, int y, int width, int maxChar, @Nullable String emptyText, Predicate<Character> acceptedChar){
		this.baseX = x;
		this.baseY = y;
		this.x = x + windowX;
		this.y = y + windowY;
		this.endX = width + this.x;
		this.endY = 20 + this.y;
		this.maxChar = maxChar;
		this.emptyText = emptyText;
		this.acceptedChar = acceptedChar;
	}
	
	@Override
	public boolean buttonPress(char key){
		if(selected){
			//Enter & Esc
			if(key == 13 || key == 27){
				selected = false;
				return true;
			//Backspace
			}else if(key == 8){
				if(!text.isEmpty()){
					text = text.substring(0, text.length() - 1);
					return true;
				}
			}else{
				if(acceptedChar.test(key)){
					if(text.length() < maxChar){
						text += key;
					}
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseClicked(int x, int y, int button){
		if(!selected && x >= this.x && x <= endX && y >= this.y && y <= endY){
			selected = !selected;
			return true;
		}
		selected = false;
		return false;
	}

	@Override
	public boolean drawBack(float partialTicks, int mouseX, int mouseY, FontRenderer fontRenderer){
		Minecraft.getMinecraft().getTextureManager().bindTexture(BAR);
		if(selected){
			GlStateManager.color(1, 1, 0);
		}
		Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, 2, 20, 300, 20);
		Gui.drawModalRectWithCustomSizedTexture(x + 2, y, 2, 0, endX - x - 4, 20, 300, 20);
		Gui.drawModalRectWithCustomSizedTexture(endX - 2, y, 298, 0, 2, 20, 300, 20);
		return true;
	}

	@Override
	public boolean drawFore(int mouseX, int mouseY, FontRenderer fontRenderer){
		if(text.isEmpty() && emptyText == null){
			return false;
		}
		fontRenderer.drawStringWithShadow(text.isEmpty() ? emptyText : text, 5 + baseX, 6 + baseY, text.isEmpty() ? Color.GRAY.getRGB() : Color.WHITE.getRGB());
		return true;
	}
	
	public String getText(){
		return text;
	}
	
	public void setText(@Nonnull String text){
		this.text = text;
	}
}
