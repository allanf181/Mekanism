package mekanism.common.content.transporter;

import java.util.ArrayList;

import mekanism.common.util.InventoryUtils;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;

public final class InvStack
{
	public TileEntity tileEntity;
	public ArrayList<ItemStack> itemStacks;
	public ArrayList<Integer> slotIDs;
	public EnumFacing side;

	public InvStack(TileEntity inv, EnumFacing facing)
	{
		tileEntity = inv;
		itemStacks = new ArrayList<ItemStack>();
		slotIDs = new ArrayList<Integer>();
		side = facing;
	}

	public InvStack(TileEntity inv, int id, ItemStack stack, EnumFacing facing)
	{
		tileEntity = inv;
		itemStacks = new ArrayList<ItemStack>();
		slotIDs = new ArrayList<Integer>();
		side = facing;

		appendStack(id, stack);
	}

	public ItemStack getStack()
	{
		int size = 0;

		for(ItemStack stack : itemStacks)
		{
			size += stack.getCount();
		}

		if(!itemStacks.isEmpty())
		{
			ItemStack ret = itemStacks.get(0).copy();
			ret.setCount(size);

			return ret;
		}

		return ItemStack.EMPTY;
	}

	public void appendStack(int id, ItemStack stack)
	{
		slotIDs.add(id);
		itemStacks.add(stack);
	}

	public void use(int amount)
	{
		if(InventoryUtils.isItemHandler(tileEntity, side))
		{
			IItemHandler handler = InventoryUtils.getItemHandler(tileEntity, side);
			
			for(int i = 0; i < slotIDs.size(); i++)
			{
				ItemStack stack = itemStacks.get(i);
				int toUse = Math.min(amount, stack.getCount());
				handler.extractItem(slotIDs.get(i), toUse, false);
				amount -= toUse;
				
				if(amount == 0)
				{
					return;
				}
			}
		}
		else if(tileEntity instanceof IInventory)
		{
			IInventory inventory = InventoryUtils.checkChestInv((IInventory)tileEntity);
			
			for(int i = 0; i < slotIDs.size(); i++)
			{
				ItemStack stack = itemStacks.get(i);
				
				if(inventory.getStackInSlot(slotIDs.get(i)).getCount() == stack.getCount() && stack.getCount() <= amount)
				{
					inventory.setInventorySlotContents(slotIDs.get(i), ItemStack.EMPTY);
					amount -= stack.getCount();
				}
				else {
					ItemStack ret = stack.copy();
					ret.setCount(inventory.getStackInSlot(slotIDs.get(i)).getCount() - stack.getCount());
					inventory.setInventorySlotContents(slotIDs.get(i), ret);
					amount -= stack.getCount();
				}
				
				if(amount == 0)
				{
					return;
				}
			}
		}
	}

	public void use()
	{
		use(getStack().getCount());
	}
}
