package mekanism.client.render.transmitter;

import mekanism.client.render.MekanismRenderer;
import mekanism.common.ColourRGBA;
import mekanism.common.config.MekanismConfig.client;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.util.EnumFacing;

import org.lwjgl.opengl.GL11;

public class RenderUniversalCable extends RenderTransmitterBase<TileEntityUniversalCable>
{
	public RenderUniversalCable()
	{
		super();
	}
	
	@Override
	public void renderTileEntityAt(TileEntityUniversalCable cable, double x, double y, double z, float partialTick, int destroyStage)
	{
		if(client.opaqueTransmitters || cable.currentPower == 0)
		{
			return;
		}

		push();
		Tessellator tessellator = Tessellator.getInstance();
		VertexBuffer worldRenderer = tessellator.getBuffer();
		GL11.glTranslated(x + 0.5, y+0.5, z + 0.5);

		for(EnumFacing side : EnumFacing.VALUES)
		{
			renderEnergySide(worldRenderer, side, cable);
		}

		MekanismRenderer.glowOn();

		tessellator.draw();

		MekanismRenderer.glowOff();
		pop();
	}
	
	public void renderEnergySide(VertexBuffer renderer, EnumFacing side, TileEntityUniversalCable cable)
	{
		bindTexture(MekanismRenderer.getBlocksTexture());
		renderTransparency(renderer, MekanismRenderer.energyIcon, getModelForSide(cable, side), new ColourRGBA(1.0, 1.0, 1.0, cable.currentPower));
	}
}
