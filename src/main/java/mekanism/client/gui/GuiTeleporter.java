package mekanism.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.client.MekanismClient;
import mekanism.client.gui.element.GuiPowerBar;
import mekanism.client.gui.element.GuiPowerBar.IPowerInfoHandler;
import mekanism.client.gui.element.GuiRedstoneControl;
import mekanism.client.gui.element.GuiScrollList;
import mekanism.client.gui.element.GuiSlot;
import mekanism.client.gui.element.GuiSlot.SlotOverlay;
import mekanism.client.gui.element.GuiSlot.SlotType;
import mekanism.client.gui.element.GuiUpgradeTab;
import mekanism.client.sound.SoundHandler;
import mekanism.common.Mekanism;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.inventory.container.ContainerTeleporter;
import mekanism.common.item.ItemPortableTeleporter;
import mekanism.common.network.PacketPortableTeleporter.PortableTeleporterMessage;
import mekanism.common.network.PacketPortableTeleporter.PortableTeleporterPacketType;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.security.IOwnerItem;
import mekanism.common.tile.TileEntityTeleporter;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class GuiTeleporter extends GuiMekanism
{
	public EnumHand currentHand;
	
	public ResourceLocation resource;
	
	public TileEntityTeleporter tileEntity;
	public ItemStack itemStack = ItemStack.EMPTY;
	
	public EntityPlayer entityPlayer;
	
	public GuiButton publicButton;
	public GuiButton privateButton;
	
	public GuiButton setButton;
	public GuiButton deleteButton;
	
	public GuiButton teleportButton;
	
	public GuiScrollList scrollList;
	
	public GuiTextField frequencyField;
	
	public boolean privateMode;
	
	public Frequency clientFreq;
	public byte clientStatus;
	
	public List<Frequency> clientPublicCache = new ArrayList<Frequency>();
	public List<Frequency> clientPrivateCache = new ArrayList<Frequency>();
	
	public boolean isInit = true;

	public GuiTeleporter(InventoryPlayer inventory, TileEntityTeleporter tentity)
	{
		super(tentity, new ContainerTeleporter(inventory, tentity));
		tileEntity = tentity;
		resource = MekanismUtils.getResource(ResourceType.GUI, "GuiTeleporter.png");

		guiElements.add(new GuiRedstoneControl(this, tileEntity, resource));
		guiElements.add(new GuiUpgradeTab(this, tileEntity, resource));
		guiElements.add(new GuiPowerBar(this, new IPowerInfoHandler() {
			@Override
			public String getTooltip()
			{
				return MekanismUtils.getEnergyDisplay(getEnergy());
			}
			
			@Override
			public double getLevel()
			{
				return getEnergy()/getMaxEnergy();
			}
		}, resource, 158, 26));
		guiElements.add(new GuiSlot(SlotType.NORMAL, this, resource, 152, 6).with(SlotOverlay.POWER));
		guiElements.add(scrollList = new GuiScrollList(this, resource, 28, 37, 120, 4));
		
		if(tileEntity.frequency != null)
		{
			privateMode = !tileEntity.frequency.publicFreq;
		}
		
		ySize+=64;
	}
	
	public GuiTeleporter(EntityPlayer player, EnumHand hand, ItemStack stack)
	{
		super(new ContainerNull());
		currentHand = hand;
		itemStack = stack;
		entityPlayer = player;
		resource = MekanismUtils.getResource(ResourceType.GUI, "GuiPortableTeleporter.png");
		
		guiElements.add(new GuiPowerBar(this, new IPowerInfoHandler() {
			@Override
			public String getTooltip()
			{
				return MekanismUtils.getEnergyDisplay(getEnergy());
			}
			
			@Override
			public double getLevel()
			{
				return getEnergy()/getMaxEnergy();
			}
		}, resource, 158, 26));
		guiElements.add(scrollList = new GuiScrollList(this, resource, 28, 37, 120, 4));
		
		ItemPortableTeleporter item = (ItemPortableTeleporter)itemStack.getItem();
		
		if(item.getFrequency(stack) != null)
		{
			privateMode = item.isPrivateMode(itemStack);
			setFrequency(item.getFrequency(stack));
		}
		else {
			Mekanism.packetHandler.sendToServer(new PortableTeleporterMessage(PortableTeleporterPacketType.DATA_REQUEST, currentHand, clientFreq));
		}
		
		ySize = 175;
	}
	
	@Override
	public void initGui()
	{
		super.initGui();

		int guiWidth = (width - xSize) / 2;
		int guiHeight = (height - ySize) / 2;

		buttonList.clear();
		
		publicButton = new GuiButton(0, guiWidth + 27, guiHeight + 14, 60, 20, LangUtils.localize("gui.public"));
		privateButton = new GuiButton(1, guiWidth + 89, guiHeight + 14, 60, 20, LangUtils.localize("gui.private"));
		
		setButton = new GuiButton(2, guiWidth + 27, guiHeight + 116, 60, 20, LangUtils.localize("gui.set"));
		deleteButton = new GuiButton(3, guiWidth + 89, guiHeight + 116, 60, 20, LangUtils.localize("gui.delete"));
		
		if(!itemStack.isEmpty())
		{
			teleportButton = new GuiButton(4, guiWidth + 42, guiHeight + 140, 92, 20, LangUtils.localize("gui.teleport"));
		}

		frequencyField = new GuiTextField(5, fontRendererObj, guiWidth + 50, guiHeight + 104, 86, 11);
		frequencyField.setMaxStringLength(FrequencyManager.MAX_FREQ_LENGTH);
		
		frequencyField.setEnableBackgroundDrawing(false);
		
		updateButtons();

		buttonList.add(publicButton);
		buttonList.add(privateButton);
		buttonList.add(setButton);
		buttonList.add(deleteButton);
		
		if(!itemStack.isEmpty())
		{
			buttonList.add(teleportButton);
			
			if(!isInit)
			{
				Mekanism.packetHandler.sendToServer(new PortableTeleporterMessage(PortableTeleporterPacketType.DATA_REQUEST, currentHand, clientFreq));
			}
			else {
				isInit = false;
			}
		}
	}
	
	public void setFrequency(String freq)
	{
		if(freq.isEmpty())
		{
			return;
		}
		
		if(tileEntity != null)
		{
			ArrayList data = new ArrayList();
			data.add(0);
			data.add(freq);
			data.add(!privateMode);
			
			Mekanism.packetHandler.sendToServer(new TileEntityMessage(Coord4D.get(tileEntity), data));
		}
		else {
			Frequency newFreq = new Frequency(freq, null).setPublic(!privateMode);
			
			Mekanism.packetHandler.sendToServer(new PortableTeleporterMessage(PortableTeleporterPacketType.SET_FREQ, currentHand, newFreq));
		}
	}
	
	public String getSecurity(Frequency freq)
	{
		return !freq.publicFreq ? EnumColor.DARK_RED + LangUtils.localize("gui.private") : LangUtils.localize("gui.public");
	}
	
	public void updateButtons()
	{
		if(getOwner() == null)
		{
			return;
		}
		
		List<String> text = new ArrayList<String>();
		
		if(privateMode)
		{
			for(Frequency freq : getPrivateCache())
			{
				text.add(freq.name);
			}
		}
		else {
			for(Frequency freq : getPublicCache())
			{
				text.add(freq.name + " (" + freq.clientOwner + ")");
			}
		}
		
		scrollList.setText(text);
		
		if(privateMode)
		{
			publicButton.enabled = true;
			privateButton.enabled = false;
		}
		else {
			publicButton.enabled = false;
			privateButton.enabled = true;
		}
		
		if(scrollList.hasSelection())
		{
			Frequency freq = privateMode ? getPrivateCache().get(scrollList.selected) : getPublicCache().get(scrollList.selected);
			
			if(getFrequency() == null || !getFrequency().equals(freq))
			{
				setButton.enabled = true;
			}
			else {
				setButton.enabled = false;
			}
			
			if(getOwner().equals(freq.ownerUUID))
			{
				deleteButton.enabled = true;
			}
			else {
				deleteButton.enabled = false;
			}
		}
		else {
			setButton.enabled = false;
			deleteButton.enabled = false;
		}
		
		if(!itemStack.isEmpty())
		{
			if(clientFreq != null && clientStatus == 1)
			{
				teleportButton.enabled = true;
			}
			else {
				teleportButton.enabled = false;
			}
		}
	}
	
	@Override
	public void updateScreen()
	{
		super.updateScreen();
		
		updateButtons();
		
		frequencyField.updateCursorCounter();
	}
	
	@Override
	public void mouseClicked(int mouseX, int mouseY, int button) throws IOException
	{
		super.mouseClicked(mouseX, mouseY, button);
		
		updateButtons();

		frequencyField.mouseClicked(mouseX, mouseY, button);
		
		if(button == 0)
		{
			int xAxis = (mouseX - (width - xSize) / 2);
			int yAxis = (mouseY - (height - ySize) / 2);
			
			if(xAxis >= 137 && xAxis <= 148 && yAxis >= 103 && yAxis <= 114)
			{
				setFrequency(frequencyField.getText());
				frequencyField.setText("");
	            SoundHandler.playSound(SoundEvents.UI_BUTTON_CLICK);
			}
		}
	}
	
	@Override
	public void keyTyped(char c, int i) throws IOException
	{
		if(!frequencyField.isFocused() || i == Keyboard.KEY_ESCAPE)
		{
			super.keyTyped(c, i);
		}
		
		if(i == Keyboard.KEY_RETURN)
		{
			if(frequencyField.isFocused())
			{
				setFrequency(frequencyField.getText());
				frequencyField.setText("");
			}
		}

		if(Character.isDigit(c) || Character.isLetter(c) || isTextboxKey(c, i) || FrequencyManager.SPECIAL_CHARS.contains(c))
		{
			frequencyField.textboxKeyTyped(c, i);
		}
		
		updateButtons();
	}
	
	@Override
	protected void actionPerformed(GuiButton guibutton) throws IOException
	{
		super.actionPerformed(guibutton);

		if(guibutton.id == 0)
		{
			privateMode = false;
		}
		else if(guibutton.id == 1)
		{
			privateMode = true;
		}
		else if(guibutton.id == 2)
		{
			int selection = scrollList.getSelection();
			
			if(selection != -1)
			{
				Frequency freq = privateMode ? getPrivateCache().get(selection) : getPublicCache().get(selection);
				setFrequency(freq.name);
			}
		}
		else if(guibutton.id == 3)
		{
			int selection = scrollList.getSelection();
			
			if(selection != -1)
			{
				Frequency freq = privateMode ? getPrivateCache().get(selection) : getPublicCache().get(selection);
				
				if(tileEntity != null)
				{
					ArrayList data = new ArrayList();
					data.add(1);
					data.add(freq.name);
					data.add(freq.publicFreq);
					
					Mekanism.packetHandler.sendToServer(new TileEntityMessage(Coord4D.get(tileEntity), data));
				}
				else {
					Mekanism.packetHandler.sendToServer(new PortableTeleporterMessage(PortableTeleporterPacketType.DEL_FREQ, currentHand, freq));
					Mekanism.packetHandler.sendToServer(new PortableTeleporterMessage(PortableTeleporterPacketType.DATA_REQUEST, currentHand, null));
				}
				
				scrollList.selected = -1;
			}
		}
		else if(guibutton.id == 4)
		{
			if(clientFreq != null && clientStatus == 1)
			{
				mc.setIngameFocus();
				Mekanism.packetHandler.sendToServer(new PortableTeleporterMessage(PortableTeleporterPacketType.TELEPORT, currentHand, clientFreq));
			}
		}
		
		updateButtons();
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY)
	{
		int xAxis = (mouseX-(width-xSize)/2);
		int yAxis = (mouseY-(height-ySize)/2);

		fontRendererObj.drawString(getName(), (xSize/2)-(fontRendererObj.getStringWidth(getName())/2), 4, 0x404040);
		fontRendererObj.drawString(LangUtils.localize("gui.owner") + ": " + (getOwnerUsername() != null ? getOwnerUsername() : LangUtils.localize("gui.none")), 8, !itemStack.isEmpty() ? ySize-12 : (ySize-96)+4, 0x404040);
		
		fontRendererObj.drawString(LangUtils.localize("gui.freq") + ":", 32, 81, 0x404040);
		fontRendererObj.drawString(LangUtils.localize("gui.security") + ":", 32, 91, 0x404040);
		
		fontRendererObj.drawString(" " + (getFrequency() != null ? getFrequency().name : EnumColor.DARK_RED + LangUtils.localize("gui.none")), 32 + fontRendererObj.getStringWidth(LangUtils.localize("gui.freq") + ":"), 81, 0x797979);
		fontRendererObj.drawString(" " + (getFrequency() != null ? getSecurity(getFrequency()) : EnumColor.DARK_RED + LangUtils.localize("gui.none")), 32 + fontRendererObj.getStringWidth(LangUtils.localize("gui.security") + ":"), 91, 0x797979);
		
		String str = LangUtils.localize("gui.set") + ":";
		renderScaledText(str, 27, 104, 0x404040, 20);
		
		if(xAxis >= 6 && xAxis <= 24 && yAxis >= 6 && yAxis <= 24)
		{
			if(getFrequency() == null)
			{
				drawCreativeTabHoveringText(EnumColor.DARK_RED + LangUtils.localize("gui.teleporter.noFreq"), xAxis, yAxis);
			}
			else {
				drawCreativeTabHoveringText(getStatusDisplay(), xAxis, yAxis);
			}
		}

		super.drawGuiContainerForegroundLayer(mouseX, mouseY);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY)
	{
		mc.renderEngine.bindTexture(resource);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		int guiWidth = (width-xSize)/2;
		int guiHeight = (height-ySize)/2;
		drawTexturedModalRect(guiWidth, guiHeight, 0, 0, xSize, ySize);
		
		int xAxis = (mouseX - (width - xSize) / 2);
		int yAxis = (mouseY - (height - ySize) / 2);
		
		if(xAxis >= 137 && xAxis <= 148 && yAxis >= 103 && yAxis <= 114)
		{
			drawTexturedModalRect(guiWidth + 137, guiHeight + 103, xSize, 0, 11, 11);
		}
		else {
			drawTexturedModalRect(guiWidth + 137, guiHeight + 103, xSize, 11, 11, 11);
		}
		
		int y = getFrequency() == null ? 94 : (getStatus() == 2 ? 22 : (getStatus() == 3 ? 40 : 
			(getStatus() == 4 ? 58 : 76)));
		
		drawTexturedModalRect(guiWidth + 6, guiHeight + 6, 176, y, 18, 18);

		super.drawGuiContainerBackgroundLayer(partialTick, mouseX, mouseY);
		
		frequencyField.drawTextBox();
	}
	
	public String getStatusDisplay()
	{
		switch(getStatus())
		{
			case 1:
				return EnumColor.DARK_GREEN + LangUtils.localize("gui.teleporter.ready");
			case 2:
				return EnumColor.DARK_RED + LangUtils.localize("gui.teleporter.noFrame");
			case 3:
				return EnumColor.DARK_RED + LangUtils.localize("gui.teleporter.noLink");
			case 4:
				return EnumColor.DARK_RED + LangUtils.localize("gui.teleporter.needsEnergy");
		}
		
		return EnumColor.DARK_RED + LangUtils.localize("gui.teleporter.noLink");
	}
	
	private UUID getOwner()
	{
		if(tileEntity != null)
		{
			return tileEntity.getSecurity().getOwnerUUID();
		}
		else {
			return ((IOwnerItem)itemStack.getItem()).getOwnerUUID(itemStack);
		}
	}
	
	private String getOwnerUsername()
	{
		if(tileEntity != null)
		{
			return tileEntity.getSecurity().getClientOwner();
		}
		else {
			return MekanismClient.clientUUIDMap.get(((IOwnerItem)itemStack.getItem()).getOwnerUUID(itemStack));
		}
	}
	
	private byte getStatus()
	{
		return tileEntity != null ? tileEntity.status : clientStatus;
	}
	
	private List<Frequency> getPublicCache()
	{
		return tileEntity != null ? tileEntity.publicCache : clientPublicCache;
	}
	
	private List<Frequency> getPrivateCache()
	{
		return tileEntity != null ? tileEntity.privateCache : clientPrivateCache;
	}
	
	private Frequency getFrequency()
	{
		return tileEntity != null ? tileEntity.frequency : clientFreq;
	}
	
	private String getName()
	{
		return tileEntity != null ? tileEntity.getName() : itemStack.getDisplayName();
	}
	
	private double getEnergy()
	{
		if(!itemStack.isEmpty())
		{
			return ((ItemPortableTeleporter)itemStack.getItem()).getEnergy(itemStack);
		}
		
		return tileEntity.getEnergy();
	}
	
	private double getMaxEnergy()
	{
		if(!itemStack.isEmpty())
		{
			return ((ItemPortableTeleporter)itemStack.getItem()).getMaxEnergy(itemStack);
		}
		
		return tileEntity.getMaxEnergy();
	}
}
