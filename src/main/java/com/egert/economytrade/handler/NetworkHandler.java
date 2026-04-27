package com.egert.economytrade.network;

import com.egert.economytrade.EconomyTrade;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EconomyTrade.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                TradePacket.class,
                TradePacket::encode,
                TradePacket::new,
                (packet, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> {
                        net.minecraft.server.level.ServerPlayer sender = ctx.getSender();
                        if (sender == null) {
                            ClientPacketHandler.handlePacket(packet, ctxSupplier);
                            return;
                        }

                        com.egert.economytrade.handler.TradeHandler.TradeSession session =
                                com.egert.economytrade.handler.TradeHandler
                                        .getSession(sender.getUUID());

                        if (session == null) return;

                        UUID partnerUUID = session.getPartnerOf(sender.getUUID());
                        net.minecraft.server.level.ServerPlayer partner =
                                sender.getServer().getPlayerList().getPlayer(partnerUUID);

                        switch (packet.getAction()) {
                            case UPDATE -> {
                                com.egert.economytrade.handler.TradeHandler
                                        .updateOffer(sender.getUUID(), packet.getItems());
                                if (partner != null) {
                                    CHANNEL.send(
                                            net.minecraftforge.network.PacketDistributor
                                                    .PLAYER.with(() -> partner),
                                            new TradePacket(TradePacket.Action.UPDATE,
                                                    sender.getName().getString(),
                                                    packet.getItems())
                                    );
                                }
                            }
                            case CONFIRM -> {
                                com.egert.economytrade.handler.TradeHandler
                                        .confirmTrade(sender.getUUID());
                                if (partner != null) {
                                    CHANNEL.send(
                                            net.minecraftforge.network.PacketDistributor
                                                    .PLAYER.with(() -> partner),
                                            new TradePacket(TradePacket.Action.CONFIRM,
                                                    sender.getName().getString())
                                    );
                                }
                            }
                            case CANCEL -> {
                                com.egert.economytrade.handler.TradeHandler
                                        .cancelTrade(sender.getUUID());
                                if (partner != null) {
                                    CHANNEL.send(
                                            net.minecraftforge.network.PacketDistributor
                                                    .PLAYER.with(() -> partner),
                                            new TradePacket(TradePacket.Action.CANCEL,
                                                    sender.getName().getString())
                                    );
                                }
                            }
                            default -> {}
                        }
                    });
                    ctx.setPacketHandled(true);
                }
        );
    }
}