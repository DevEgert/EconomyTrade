package com.egert.economytrade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class TradePacket {

    public enum Action {
        REQUEST,
        ACCEPT,
        DECLINE,
        CONFIRM,
        CANCEL,
        UPDATE,
        COMPLETE
    }

    private final Action action;
    private final String targetPlayer;
    private final List<ItemStack> items;

    public TradePacket(Action action, String targetPlayer) {
        this.action = action;
        this.targetPlayer = targetPlayer;
        this.items = new ArrayList<>();
    }

    public TradePacket(Action action, String targetPlayer, List<ItemStack> items) {
        this.action = action;
        this.targetPlayer = targetPlayer;
        this.items = items;
    }

    public TradePacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.targetPlayer = buf.readUtf();
        int count = buf.readInt();
        this.items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(buf.readItem());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(targetPlayer);
        buf.writeInt(items.size());
        for (ItemStack stack : items) {
            buf.writeItem(stack);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {});
        context.setPacketHandled(true);
    }

    public Action getAction() { return action; }
    public String getTargetPlayer() { return targetPlayer; }
    public List<ItemStack> getItems() { return items; }
}