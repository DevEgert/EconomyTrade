package com.egert.economytrade.network;

import com.egert.economytrade.screen.TradeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientPacketHandler {

    public static void handlePacket(TradePacket packet,
                                    Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            switch (packet.getAction()) {
                case ACCEPT -> {
                    mc.setScreen(new TradeScreen(
                            packet.getTargetPlayer(), mc.player));
                }
                case UPDATE -> {
                    if (mc.screen instanceof TradeScreen ts) {
                        ts.setTheirOffer(packet.getItems());
                    }
                }
                case CONFIRM -> {
                    if (mc.screen instanceof TradeScreen ts) {
                        ts.setTheirConfirmed(true);
                    }
                }
                case COMPLETE -> {
                    if (mc.screen instanceof TradeScreen ts) {
                        ts.markCompleted();
                        mc.setScreen(null);
                    }
                    mc.player.sendSystemMessage(
                            Component.literal("§aTrade completed successfully!"));
                }
                case CANCEL -> {
                    if (mc.screen instanceof TradeScreen) {
                        mc.setScreen(null);
                        mc.player.sendSystemMessage(
                                Component.literal("§cTrade was cancelled."));
                    }
                }
                case REQUEST -> {
                    mc.player.sendSystemMessage(
                            Component.literal(
                                    "§aTrade request sent! Waiting for response..."));
                }
                default -> {}
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
