package mcjty.rftoolsbase.modules.infuser.blocks;

import mcjty.lib.api.container.DefaultContainerProvider;
import mcjty.lib.api.infusable.DefaultInfusable;
import mcjty.lib.api.infusable.IInfusable;
import mcjty.lib.blocks.BaseBlock;
import mcjty.lib.builder.BlockBuilder;
import mcjty.lib.container.ContainerFactory;
import mcjty.lib.container.GenericContainer;
import mcjty.lib.container.GenericItemHandler;
import mcjty.lib.setup.Registration;
import mcjty.lib.tileentity.Cap;
import mcjty.lib.tileentity.CapType;
import mcjty.lib.tileentity.GenericEnergyStorage;
import mcjty.lib.tileentity.TickingTileEntity;
import mcjty.lib.varia.TagTools;
import mcjty.rftoolsbase.modules.infuser.MachineInfuserConfiguration;
import mcjty.rftoolsbase.modules.infuser.MachineInfuserModule;
import mcjty.rftoolsbase.modules.infuser.data.InfuserData;
import mcjty.rftoolsbase.modules.various.VariousModule;
import mcjty.rftoolsbase.tools.ManualHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.Lazy;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.function.Function;

import static mcjty.lib.api.container.DefaultContainerProvider.container;
import static mcjty.lib.builder.TooltipBuilder.*;
import static mcjty.lib.container.SlotDefinition.specific;

public class MachineInfuserTileEntity extends TickingTileEntity {

    public static final int SLOT_SHARDINPUT = 0;
    public static final int SLOT_MACHINEOUTPUT = 1;

    public static final Lazy<ContainerFactory> CONTAINER_FACTORY = Lazy.of(() -> new ContainerFactory(2)
            .slot(specific(MachineInfuserTileEntity::isShard).in(), SLOT_SHARDINPUT, 64, 24)
            .slot(specific(MachineInfuserTileEntity::isInfusable).in().out(), SLOT_MACHINEOUTPUT, 118, 24)
            .playerSlots(10, 70));


    private final GenericItemHandler items = GenericItemHandler.create(this, CONTAINER_FACTORY)
            .slotLimit(slot -> slot == SLOT_MACHINEOUTPUT ? 1 : 64)
            .insertable((slot, stack) -> {
                if (slot == SLOT_MACHINEOUTPUT) {
                    return isInfusable(stack);
                } else {
                    return isShard(stack);
                }
            })
            .build();
    @Cap(type = CapType.ITEMS_AUTOMATION)
    private static final Function<MachineInfuserTileEntity, GenericItemHandler> ITEM_HANDLER = be -> be.items;

    private final GenericEnergyStorage energyStorage = new GenericEnergyStorage(this, true, MachineInfuserConfiguration.MAXENERGY.get(), MachineInfuserConfiguration.RECEIVEPERTICK.get());
    @Cap(type = CapType.ENERGY)
    private static final Function<MachineInfuserTileEntity, GenericEnergyStorage> ENERGY_HANDLER = be -> be.energyStorage;

    @Cap(type = CapType.CONTAINER)
    private static final Function<MachineInfuserTileEntity, MenuProvider> screenHandler = be -> (new DefaultContainerProvider<GenericContainer>("Machine Infuser")
            .containerSupplier(container(MachineInfuserModule.CONTAINER_MACHINE_INFUSER, CONTAINER_FACTORY, be))
            .itemHandler(() -> be.items)
            .energyHandler(() -> be.energyStorage)
            .setupSync(be));

    private final DefaultInfusable infusable = new DefaultInfusable(this);
    @Cap(type = CapType.INFUSABLE)
    private static final Function<MachineInfuserTileEntity, IInfusable> INFUSABLE_HANDLER = be -> be.infusable;

    public MachineInfuserTileEntity(BlockPos pos, BlockState state) {
        super(MachineInfuserModule.MACHINE_INFUSER.be().get(), pos, state);
    }

    public static BaseBlock createBlock() {

        return new BaseBlock(new BlockBuilder()
                .tileEntitySupplier(MachineInfuserTileEntity::new)
                .infusable()
                .manualEntry(ManualHelper.create("rftoolsbase:machines/infusing"))
                .info(key("message.rftoolsbase.shiftmessage"))
                .infoShift(header(), gold()));
    }

    @Override
    public void tickServer() {
        var data = getData(MachineInfuserModule.INFUSER_DATA);
        int infusing = data.infusing();
        if (infusing > 0) {
            infusing--;
            if (infusing == 0) {
                ItemStack outputStack = items.getStackInSlot(1);
                finishInfusing(outputStack);
            }
            setData(MachineInfuserModule.INFUSER_DATA, new InfuserData(infusing));
        } else {
            ItemStack inputStack = items.getStackInSlot(0);
            ItemStack outputStack = items.getStackInSlot(1);
            if (isShard(inputStack) && isInfusable(outputStack)) {
                startInfusing();
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        energyStorage.load(tag, "energy", provider);
        items.load(tag, "items", provider);
        infusable.load(tag, "infusable");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        energyStorage.save(tag, "energy", provider);
        items.save(tag, "items", provider);
        infusable.save(tag, "infusable");
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        energyStorage.applyImplicitComponents(input.get(Registration.ITEM_ENERGY));
        items.applyImplicitComponents(input.get(Registration.ITEM_INVENTORY));
        infusable.applyImplicitComponents(input.get(Registration.ITEM_INFUSABLE));
        var data = input.get(MachineInfuserModule.ITEM_INFUSER_DATA);
        if (data != null) {
            setData(MachineInfuserModule.INFUSER_DATA, data);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        energyStorage.collectImplicitComponents(builder);
        items.collectImplicitComponents(builder);
        infusable.collectImplicitComponents(builder);
        var data = getData(MachineInfuserModule.INFUSER_DATA);
        builder.set(MachineInfuserModule.ITEM_INFUSER_DATA, data);
    }

    private static boolean isShard(ItemStack stack) {
        return TagTools.hasTag(stack.getItem(), VariousModule.SHARDS_TAG);
    }

    private static boolean isInfusable(ItemStack stack) {
        return getStackIfInfusable(stack).map(s -> BaseBlock.getInfused(s) < MachineInfuserConfiguration.MAX_INFUSE.get()).orElse(false);
    }

    @Nonnull
    private static Optional<ItemStack> getStackIfInfusable(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        Item item = stack.getItem();
        if (!(item instanceof BlockItem)) {
            return Optional.empty();
        }
        Block block = ((BlockItem) item).getBlock();
        if (block instanceof BaseBlock && ((BaseBlock) block).isInfusable()) {
            return Optional.of(stack);
        } else {
            return Optional.empty();
        }
    }

    private void finishInfusing(ItemStack stack) {
        getStackIfInfusable(stack).ifPresent(s -> {
            BaseBlock.setInfused(s, BaseBlock.getInfused(s)+1);
        });
    }

    private void startInfusing() {
        int defaultCost = MachineInfuserConfiguration.RFPERTICK.get();
        int rf = (int) (defaultCost * (2.0f - infusable.getInfusedFactor()) / 2.0f);

        if (energyStorage.getEnergy() < rf) {
            // Not enough energy.
            return;
        }
        energyStorage.consumeEnergy(rf);

        items.getStackInSlot(0).split(1);
        if (items.getStackInSlot(0).isEmpty()) {
            items.setStackInSlot(0, ItemStack.EMPTY);
        }
        setData(MachineInfuserModule.INFUSER_DATA, new InfuserData(5));
    }
}
