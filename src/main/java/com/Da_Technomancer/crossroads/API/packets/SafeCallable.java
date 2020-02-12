package com.Da_Technomancer.crossroads.API.packets;

import com.Da_Technomancer.crossroads.API.CRReflection;
import com.Da_Technomancer.crossroads.render.IVisualEffect;
import com.Da_Technomancer.essentials.ReflectionUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;

/**Certain packets need to call or store code in render side only stuff, and can not do so without crashing due to (for example) WorldClient not existing on the server side.
 * In those cases, the packets should call methods in this class
 * This class is for CLIENT SIDE CODE ONLY
 */	
public class SafeCallable{

	public static final ArrayList<IVisualEffect> effectsToRender = new ArrayList<>();
	
	protected static final Method printChatNoLog = ReflectionUtil.reflectMethod(CRReflection.SET_CHAT);
	
	public static int playerTickCount = 0;
}
