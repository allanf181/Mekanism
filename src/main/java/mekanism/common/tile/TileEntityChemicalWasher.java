package mekanism.common.tile;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

import mekanism.api.Coord4D;
import mekanism.api.Range4D;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.IGasItem;
import mekanism.api.gas.ITubeConnection;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.Upgrade;
import mekanism.common.Upgrade.IUpgradeInfoHandler;
import mekanism.common.base.FluidHandlerWrapper;
import mekanism.common.base.IFluidHandlerWrapper;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.ISustainedData;
import mekanism.common.base.ITankManager;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig.general;
import mekanism.common.config.MekanismConfig.usage;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.recipe.machines.WasherRecipe;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.ChargeUtils;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.FluidContainerUtils.FluidChecker;
import mekanism.common.util.GasUtils;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.ListUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.PipeUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class TileEntityChemicalWasher extends TileEntityNoisyElectricBlock implements IGasHandler, ITubeConnection, IRedstoneControl, IFluidHandlerWrapper, IUpgradeTile, ISustainedData, IUpgradeInfoHandler, ITankManager, ISecurityTile
{
	public FluidTank fluidTank = new FluidTank(MAX_FLUID);
	public GasTank inputTank = new GasTank(MAX_GAS);
	public GasTank outputTank = new GasTank(MAX_GAS);

	public static final int MAX_GAS = 10000;
	public static final int MAX_FLUID = 10000;

	public static int WATER_USAGE = 5;

	public int updateDelay;

	public int gasOutput = 256;

	public boolean isActive;

	public boolean clientActive;

	public double prevEnergy;

	public final double BASE_ENERGY_USAGE = usage.chemicalWasherUsage;
	
	public double energyPerTick = BASE_ENERGY_USAGE;

	public WasherRecipe cachedRecipe;
	
	public double clientEnergyUsed;
	
	public TileComponentUpgrade upgradeComponent = new TileComponentUpgrade(this, 4);
	public TileComponentSecurity securityComponent = new TileComponentSecurity(this);

	/** This machine's current RedstoneControl type. */
	public RedstoneControl controlType = RedstoneControl.DISABLED;

	public TileEntityChemicalWasher()
	{
		super("machine.washer", "ChemicalWasher", BlockStateMachine.MachineType.CHEMICAL_WASHER.baseEnergy);
		
		inventory = NonNullList.withSize(5, ItemStack.EMPTY);
		upgradeComponent.setSupported(Upgrade.MUFFLING);
	}

	@Override
	public void onUpdate()
	{
		super.onUpdate();
		
		if(world.isRemote && updateDelay > 0)
		{
			updateDelay--;

			if(updateDelay == 0 && clientActive != isActive)
			{
				isActive = clientActive;
				MekanismUtils.updateBlock(world, getPos());
			}
		}

		if(!world.isRemote)
		{
			if(updateDelay > 0)
			{
				updateDelay--;

				if(updateDelay == 0 && clientActive != isActive)
				{
					Mekanism.packetHandler.sendToReceivers(new TileEntityMessage(Coord4D.get(this), getNetworkedData(new ArrayList())), new Range4D(Coord4D.get(this)));
				}
			}

			ChargeUtils.discharge(3, this);
			manageBuckets();

			if(!inventory.get(2).isEmpty() && outputTank.getGas() != null)
			{
				outputTank.draw(GasUtils.addGas(inventory.get(2), outputTank.getGas()), true);
			}
			
			WasherRecipe recipe = getRecipe();

			if(canOperate(recipe) && getEnergy() >= energyPerTick && MekanismUtils.canFunction(this))
			{
				setActive(true);

				int operations = operate(recipe);
				double prev = getEnergy();

				setEnergy(getEnergy() - energyPerTick*operations);
				clientEnergyUsed = prev-getEnergy();
			}
			else {
				if(prevEnergy >= getEnergy())
				{
					setActive(false);
				}
			}

			if(outputTank.getGas() != null)
			{
				GasStack toSend = new GasStack(outputTank.getGas().getGas(), Math.min(outputTank.getStored(), gasOutput));
				outputTank.draw(GasUtils.emit(toSend, this, ListUtils.asList(MekanismUtils.getRight(facing))), true);
			}

			prevEnergy = getEnergy();
		}
	}

	public WasherRecipe getRecipe()
	{
		GasInput input = getInput();
		
		if(cachedRecipe == null || !input.testEquality(cachedRecipe.getInput()))
		{
			cachedRecipe = RecipeHandler.getChemicalWasherRecipe(getInput());
		}
		
		return cachedRecipe;
	}

	public GasInput getInput()
	{
		return new GasInput(inputTank.getGas());
	}

	public boolean canOperate(WasherRecipe recipe)
	{
		return recipe != null && recipe.canOperate(inputTank, fluidTank, outputTank);
	}

	public int operate(WasherRecipe recipe)
	{
		int operations = getUpgradedUsage();
		
		recipe.operate(inputTank, fluidTank, outputTank, operations);
		
		return operations;
	}

	private void manageBuckets()
	{
		if(FluidContainerUtils.isFluidContainer(inventory.get(0)))
		{
			FluidContainerUtils.handleContainerItemEmpty(this, fluidTank, 0, 1, FluidChecker.check(FluidRegistry.WATER));
		}
	}
	
	public int getUpgradedUsage()
	{
		int possibleProcess = (int)Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED));
		possibleProcess = Math.min(Math.min(inputTank.getStored(), outputTank.getNeeded()), possibleProcess);
		possibleProcess = Math.min((int)(getEnergy()/energyPerTick), possibleProcess);
		
		return Math.min(fluidTank.getFluidAmount()/WATER_USAGE, possibleProcess);
	}
	
	@Override
	public void handlePacketData(ByteBuf dataStream)
	{
		super.handlePacketData(dataStream);

		if(FMLCommonHandler.instance().getEffectiveSide().isClient())
		{
			isActive = dataStream.readBoolean();
			controlType = RedstoneControl.values()[dataStream.readInt()];
			clientEnergyUsed = dataStream.readDouble();
	
			if(dataStream.readBoolean())
			{
				fluidTank.setFluid(new FluidStack(FluidRegistry.getFluid(PacketHandler.readString(dataStream)), dataStream.readInt()));
			}
			else {
				fluidTank.setFluid(null);
			}
	
			if(dataStream.readBoolean())
			{
				inputTank.setGas(new GasStack(GasRegistry.getGas(dataStream.readInt()), dataStream.readInt()));
			}
			else {
				inputTank.setGas(null);
			}
	
			if(dataStream.readBoolean())
			{
				outputTank.setGas(new GasStack(GasRegistry.getGas(dataStream.readInt()), dataStream.readInt()));
			}
			else {
				outputTank.setGas(null);
			}
	
			if(updateDelay == 0 && clientActive != isActive)
			{
				updateDelay = general.UPDATE_DELAY;
				isActive = clientActive;
				MekanismUtils.updateBlock(world, getPos());
			}
		}
	}

	@Override
	public ArrayList<Object> getNetworkedData(ArrayList<Object> data)
	{
		super.getNetworkedData(data);

		data.add(isActive);
		data.add(controlType.ordinal());
		data.add(clientEnergyUsed);

		if(fluidTank.getFluid() != null)
		{
			data.add(true);
			data.add(FluidRegistry.getFluidName(fluidTank.getFluid()));
			data.add(fluidTank.getFluidAmount());
		}
		else {
			data.add(false);
		}

		if(inputTank.getGas() != null)
		{
			data.add(true);
			data.add(inputTank.getGas().getGas().getID());
			data.add(inputTank.getStored());
		}
		else {
			data.add(false);
		}

		if(outputTank.getGas() != null)
		{
			data.add(true);
			data.add(outputTank.getGas().getGas().getID());
			data.add(outputTank.getStored());
		}
		else {
			data.add(false);
		}

		return data;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTags)
	{
		super.readFromNBT(nbtTags);

		isActive = nbtTags.getBoolean("isActive");
		controlType = RedstoneControl.values()[nbtTags.getInteger("controlType")];

		fluidTank.readFromNBT(nbtTags.getCompoundTag("leftTank"));
		inputTank.read(nbtTags.getCompoundTag("rightTank"));
		outputTank.read(nbtTags.getCompoundTag("centerTank"));
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbtTags)
	{
		super.writeToNBT(nbtTags);

		nbtTags.setBoolean("isActive", isActive);
		nbtTags.setInteger("controlType", controlType.ordinal());

		nbtTags.setTag("leftTank", fluidTank.writeToNBT(new NBTTagCompound()));
		nbtTags.setTag("rightTank", inputTank.write(new NBTTagCompound()));
		nbtTags.setTag("centerTank", outputTank.write(new NBTTagCompound()));
		
		return nbtTags;
	}

	@Override
	public boolean canSetFacing(int i)
	{
		return i != 0 && i != 1;
	}

	public GasTank getTank(EnumFacing side)
	{
		if(side == MekanismUtils.getLeft(facing))
		{
			return inputTank;
		}
		else if(side == MekanismUtils.getRight(facing))
		{
			return outputTank;
		}

		return null;
	}

	@Override
	public void setActive(boolean active)
	{
		isActive = active;

		if(clientActive != active && updateDelay == 0)
		{
			Mekanism.packetHandler.sendToReceivers(new TileEntityMessage(Coord4D.get(this), getNetworkedData(new ArrayList())), new Range4D(Coord4D.get(this)));

			updateDelay = 10;
			clientActive = active;
		}
	}

	@Override
	public boolean getActive()
	{
		return isActive;
	}

	@Override
	public boolean renderUpdate()
	{
		return false;
	}

	@Override
	public boolean lightUpdate()
	{
		return true;
	}

	@Override
	public boolean canTubeConnect(EnumFacing side)
	{
		return getTank(side) != null;
	}

	@Override
	public boolean canReceiveGas(EnumFacing side, Gas type)
	{
		if(getTank(side) == inputTank)
        {
            return getTank(side).canReceive(type) && RecipeHandler.Recipe.CHEMICAL_WASHER.containsRecipe(type);
        }

        return false;
	}

	@Override
	public RedstoneControl getControlType()
	{
		return controlType;
	}

	@Override
	public void setControlType(RedstoneControl type)
	{
		controlType = type;
		MekanismUtils.saveChunk(this);
	}

	@Override
	public boolean canPulse()
	{
		return false;
	}

	@Override
	public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer)
	{
		if(canReceiveGas(side, stack != null ? stack.getGas() : null))
		{
			return getTank(side).receive(stack, doTransfer);
		}

		return 0;
	}

	@Override
	public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer)
	{
		if(canDrawGas(side, null))
		{
			return getTank(side).draw(amount, doTransfer);
		}

		return null;
	}

	@Override
	public boolean canDrawGas(EnumFacing side, Gas type)
	{
		return getTank(side) == outputTank && getTank(side).canDraw(type);
	}

	@Override
	public boolean isItemValidForSlot(int slotID, ItemStack itemstack)
	{
		if(slotID == 0)
		{
			return FluidUtil.getFluidContained(itemstack) != null && FluidUtil.getFluidContained(itemstack).getFluid() == FluidRegistry.WATER;
		}
		else if(slotID == 2)
		{
			return ChargeUtils.canBeDischarged(itemstack);
		}

		return false;
	}

	@Override
	public boolean canExtractItem(int slotID, ItemStack itemstack, EnumFacing side)
	{
		if(slotID == 1)
		{
			return !itemstack.isEmpty() && itemstack.getItem() instanceof IGasItem && ((IGasItem)itemstack.getItem()).canProvideGas(itemstack, null);
		}
		else if(slotID == 2)
		{
			return ChargeUtils.canBeOutputted(itemstack, false);
		}

		return false;
	}

	@Override
	public int[] getSlotsForFace(EnumFacing side)
	{
		if(side == MekanismUtils.getLeft(facing))
		{
			return new int[] {0};
		}
		else if(side == MekanismUtils.getRight(facing))
		{
			return new int[] {1};
		}
		else if(side.getAxis() == Axis.Y)
		{
			return new int[2];
		}

		return InventoryUtils.EMPTY;
	}
	
	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing side)
	{
		return capability == Capabilities.GAS_HANDLER_CAPABILITY || capability == Capabilities.TUBE_CONNECTION_CAPABILITY 
				|| capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, side);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing side)
	{
		if(capability == Capabilities.GAS_HANDLER_CAPABILITY || capability == Capabilities.TUBE_CONNECTION_CAPABILITY)
		{
			return (T)this;
		}
		
		if(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
		{
			return (T)new FluidHandlerWrapper(this, side);
		}
		
		return super.getCapability(capability, side);
	}

	@Override
	public int fill(EnumFacing from, FluidStack resource, boolean doFill)
	{
		if(canFill(from, resource.getFluid()))
		{
			return fluidTank.fill(resource, doFill);
		}

		return 0;
	}

	@Override
	public FluidStack drain(EnumFacing from, FluidStack resource, boolean doDrain)
	{
		return null;
	}

	@Override
	public FluidStack drain(EnumFacing from, int maxDrain, boolean doDrain)
	{
		return null;
	}

	@Override
	public boolean canFill(EnumFacing from, Fluid fluid)
	{
		return from == EnumFacing.UP && fluid == FluidRegistry.WATER;
	}

	@Override
	public boolean canDrain(EnumFacing from, Fluid fluid)
	{
		return false;
	}

	@Override
	public FluidTankInfo[] getTankInfo(EnumFacing from)
	{
		if(from == EnumFacing.UP)
		{
			return new FluidTankInfo[] {fluidTank.getInfo()};
		}

		return PipeUtils.EMPTY;
	}

	@Override
	public void writeSustainedData(ItemStack itemStack) 
	{
		if(fluidTank.getFluid() != null)
		{
			ItemDataUtils.setCompound(itemStack, "fluidTank", fluidTank.getFluid().writeToNBT(new NBTTagCompound()));
		}
		
		if(inputTank.getGas() != null)
		{
			ItemDataUtils.setCompound(itemStack, "inputTank", inputTank.getGas().write(new NBTTagCompound()));
		}
		
		if(outputTank.getGas() != null)
		{
			ItemDataUtils.setCompound(itemStack, "outputTank", outputTank.getGas().write(new NBTTagCompound()));
		}
	}

	@Override
	public void readSustainedData(ItemStack itemStack) 
	{
		fluidTank.setFluid(FluidStack.loadFluidStackFromNBT(ItemDataUtils.getCompound(itemStack, "fluidTank")));
		inputTank.setGas(GasStack.readFromNBT(ItemDataUtils.getCompound(itemStack, "inputTank")));
		outputTank.setGas(GasStack.readFromNBT(ItemDataUtils.getCompound(itemStack, "outputTank")));
	}

	@Override
	public TileComponentUpgrade getComponent() 
	{
		return upgradeComponent;
	}
	
	@Override
	public void recalculateUpgradables(Upgrade upgrade)
	{
		super.recalculateUpgradables(upgrade);

		switch(upgrade)
		{
			case ENERGY:
				maxEnergy = MekanismUtils.getMaxEnergy(this, BASE_MAX_ENERGY);
				energyPerTick = MekanismUtils.getBaseEnergyPerTick(this, BASE_ENERGY_USAGE);
			default:
				break;
		}
	}
	
	@Override
	public List<String> getInfo(Upgrade upgrade) 
	{
		return upgrade == Upgrade.SPEED ? upgrade.getExpScaledInfo(this) : upgrade.getMultScaledInfo(this);
	}

	@Override
	public Object[] getTanks() 
	{
		return new Object[] {fluidTank, inputTank, outputTank};
	}
	
	@Override
	public TileComponentSecurity getSecurity()
	{
		return securityComponent;
	}
}
