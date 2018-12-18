package com.Da_Technomancer.crossroads.tileentities.technomancy;

import com.Da_Technomancer.crossroads.API.technomancy.FluxTE;
import com.Da_Technomancer.crossroads.API.technomancy.FluxUtil;

public class FluxConsumerTileEntity extends FluxTE{

	@Override
	public void update(){
		super.update();

		if(world.getTotalWorldTime() % FluxUtil.FLUX_TIME == 0){
			flux = Math.max(0, flux - 4);
		}
	}

	@Override
	public int addFlux(int fluxIn){
		flux += fluxIn;
		markDirty();
		return flux;
	}

	@Override
	public boolean isFluxEmitter(){
		return false;
	}

	@Override
	public boolean canReceiveFlux(){
		return flux < getCapacity();
	}

	@Override
	public boolean isFluxReceiver(){
		return true;
	}
}