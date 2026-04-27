package com.egert.economytrade.screen;

import com.egert.economytrade.network.NetworkHandler;
import com.egert.economytrade.network.TradePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TradeScreen extends Screen {

    private static final int SLOT_SIZE = 16;
    private static final int SLOTS_COLS = 4;
    private static final int SLOTS_ROWS = 3;
    private static final int GAP = 2;
    private static final int INV_COLS = 9;

    private int POPUP_W;
    private int POPUP_H;

    private final String partnerName;
    private final Player player;

    private final List<ItemStack> myOffer = new ArrayList<>();
    private final List<ItemStack> theirOffer = new ArrayList<>();

    private boolean myConfirmed = false;
    private boolean theirConfirmed = false;
    private boolean completed = false;
    private ItemStack heldStack = ItemStack.EMPTY;

    private Button confirmButton;
    private Button cancelButton;

    private int px, py;
    private int mySlotX, mySlotY;
    private int theirSlotX, theirSlotY;
    private int invPY;
    private int invSlotX, invSlotY;

    public TradeScreen(String partnerName, Player player) {
        super(Component.literal("Trade"));
        this.partnerName = partnerName;
        this.player = player;
        for (int i = 0; i < SLOTS_COLS * SLOTS_ROWS; i++) {
            myOffer.add(ItemStack.EMPTY);
            theirOffer.add(ItemStack.EMPTY);
        }
    }

    @Override
    protected void init() {
        super.init();

        int slotGridW = SLOTS_COLS * (SLOT_SIZE + GAP);
        POPUP_W = slotGridW * 2 + 40;

        int tradeH = 22 + SLOTS_ROWS * (SLOT_SIZE + GAP) + 6;
        int invH = 12 + 4 * (SLOT_SIZE + GAP);
        int btnH = 24;
        POPUP_H = Math.min(
                (int)(this.height * 0.95),
                24 + tradeH + 6 + invH + 6 + btnH + 6
        );

        px = (this.width - POPUP_W) / 2;
        py = Math.max(2, (this.height - POPUP_H) / 2);

        mySlotX = px + 12;
        mySlotY = py + 46;
        theirSlotX = px + POPUP_W / 2 + 6;
        theirSlotY = py + 46;

        int tradeSlotsBottom = mySlotY + SLOTS_ROWS * (SLOT_SIZE + GAP);
        invPY = tradeSlotsBottom + 8;
        invSlotX = px + (POPUP_W - INV_COLS * (SLOT_SIZE + GAP)) / 2;
        invSlotY = invPY + 12;

        int btnY = invSlotY + 4 * (SLOT_SIZE + GAP) + 4;

        confirmButton = Button.builder(
                Component.literal("Confirm"), btn -> onConfirm()
        ).pos(px + 8, btnY).size((POPUP_W / 2) - 12, 18).build();

        cancelButton = Button.builder(
                Component.literal("Cancel"), btn -> onCancel()
        ).pos(px + POPUP_W / 2 + 4, btnY).size((POPUP_W / 2) - 12, 18).build();

        addRenderableWidget(confirmButton);
        addRenderableWidget(cancelButton);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim background
        g.fill(0, 0, this.width, this.height, 0xAA000000);

        // Shadow
        g.fill(px + 3, py + 3, px + POPUP_W + 3, py + POPUP_H + 3, 0x88000000);
        // Border
        g.fill(px - 1, py - 1, px + POPUP_W + 1, py + POPUP_H + 1, 0xFF888899);
        // Background
        g.fill(px, py, px + POPUP_W, py + POPUP_H, 0xFF2A2A3A);
        // Title bar
        g.fill(px, py, px + POPUP_W, py + 22, 0xFF1A1A2E);
        // Center divider
        g.fill(px + POPUP_W / 2 - 1, py + 22,
                px + POPUP_W / 2 + 1, invPY - 2, 0xFF555566);
        // Inventory divider
        g.fill(px + 4, invPY - 3,
                px + POPUP_W - 4, invPY - 2, 0xFF555566);

        // Title
        g.drawCenteredString(font, "Economy Trade",
                px + POPUP_W / 2, py + 7, 0xFFFFD700);

        // Your name badge
        int myBadgeW = SLOTS_COLS * (SLOT_SIZE + GAP) + 4;
        g.fill(mySlotX - 2, py + 24,
                mySlotX + myBadgeW, py + 38, 0xFF1E3A1E);
        g.fill(mySlotX - 2, py + 24,
                mySlotX - 1, py + 38, 0xFF44AA44);
        g.drawCenteredString(font, "§a" + player.getName().getString(),
                mySlotX + myBadgeW / 2, py + 28, 0xFFCCFFCC);

        // Partner name badge
        int theirBadgeW = SLOTS_COLS * (SLOT_SIZE + GAP) + 4;
        g.fill(theirSlotX - 2, py + 24,
                theirSlotX + theirBadgeW, py + 38, 0xFF1E1E3A);
        g.fill(theirSlotX - 2, py + 24,
                theirSlotX - 1, py + 38, 0xFF4444AA);
        g.drawCenteredString(font, "§b" + partnerName,
                theirSlotX + theirBadgeW / 2, py + 28, 0xFFCCCCFF);

        // Ready indicators
        if (myConfirmed) {
            g.drawString(font, "§aReady",
                    mySlotX, invPY - 12, 0xFFFFFFFF);
        }
        if (theirConfirmed) {
            g.drawString(font, "§aReady",
                    theirSlotX, invPY - 12, 0xFFFFFFFF);
        }

        // Trade slots
        drawSlots(g, myOffer, mySlotX, mySlotY, true, mouseX, mouseY);
        drawSlots(g, theirOffer, theirSlotX, theirSlotY, false, mouseX, mouseY);

        // Inventory label
        g.drawCenteredString(font, "§7Inventory",
                px + POPUP_W / 2, invPY, 0xFFAAAAAA);

        // Inventory rows
        for (int row = 0; row < 4; row++) {
            int from = row * 9;
            int rowY = invSlotY + row * (SLOT_SIZE + GAP);
            drawInventoryRow(g, from, from + 9, invSlotX, rowY, mouseX, mouseY);
        }

        // Held item follows cursor
        if (!heldStack.isEmpty()) {
            g.renderItem(heldStack, mouseX - 8, mouseY - 8);
            g.renderItemDecorations(font, heldStack, mouseX - 8, mouseY - 8);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawSlots(GuiGraphics g, List<ItemStack> slots,
                           int startX, int startY, boolean mine,
                           int mouseX, int mouseY) {
        for (int row = 0; row < SLOTS_ROWS; row++) {
            for (int col = 0; col < SLOTS_COLS; col++) {
                int idx = row * SLOTS_COLS + col;
                int x = startX + col * (SLOT_SIZE + GAP);
                int y = startY + row * (SLOT_SIZE + GAP);
                boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE
                        && mouseY >= y && mouseY < y + SLOT_SIZE;

                int border = hovered ? 0xFFFFFFAA
                        : (mine ? 0xFF5555BB : 0xFF55BB55);
                int fill = mine ? 0xFF2A2A4A : 0xFF2A4A2A;

                g.fill(x - 1, y - 1, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, border);
                g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, fill);

                ItemStack stack = slots.get(idx);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, x + 1, y + 1);
                    g.renderItemDecorations(font, stack, x + 1, y + 1);
                }
                if (hovered && !stack.isEmpty()) {
                    g.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
        }
    }

    private void drawInventoryRow(GuiGraphics g, int from, int to,
                                  int startX, int startY,
                                  int mouseX, int mouseY) {
        for (int i = from; i < to; i++) {
            int col = i - from;
            int x = startX + col * (SLOT_SIZE + GAP);
            boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE
                    && mouseY >= startY && mouseY < startY + SLOT_SIZE;

            g.fill(x - 1, startY - 1, x + SLOT_SIZE + 1, startY + SLOT_SIZE + 1,
                    hovered ? 0xFFFFFFAA : 0xFF555566);
            g.fill(x, startY, x + SLOT_SIZE, startY + SLOT_SIZE, 0xFF333344);

            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 1, startY + 1);
                g.renderItemDecorations(font, stack, x + 1, startY + 1);
            }
            if (hovered && !stack.isEmpty()) {
                g.renderTooltip(font, stack, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        int mySlot = getSlotAt(mx, my, mySlotX, mySlotY);
        if (mySlot >= 0) {
            handleTradeSlotClick(mySlot, button);
            return true;
        }

        int invSlot = getInventorySlotAt(mx, my);
        if (invSlot >= 0) {
            handleInventorySlotClick(invSlot, button);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleTradeSlotClick(int idx, int button) {
        ItemStack slotStack = myOffer.get(idx);

        if (button == 0) {
            if (!heldStack.isEmpty()) {
                if (slotStack.isEmpty()) {
                    myOffer.set(idx, heldStack.copy());
                    heldStack = ItemStack.EMPTY;
                } else if (slotStack.getItem() == heldStack.getItem()
                        && slotStack.getCount() < slotStack.getMaxStackSize()) {
                    int canAdd = slotStack.getMaxStackSize() - slotStack.getCount();
                    int toAdd = Math.min(canAdd, heldStack.getCount());
                    slotStack.grow(toAdd);
                    heldStack.shrink(toAdd);
                    myOffer.set(idx, slotStack);
                    if (heldStack.isEmpty()) heldStack = ItemStack.EMPTY;
                } else {
                    ItemStack temp = slotStack.copy();
                    myOffer.set(idx, heldStack.copy());
                    heldStack = temp;
                }
            } else if (!slotStack.isEmpty()) {
                heldStack = slotStack.copy();
                myOffer.set(idx, ItemStack.EMPTY);
            }
        } else if (button == 1) {
            if (!heldStack.isEmpty() && slotStack.isEmpty()) {
                // Place one item into empty slot
                ItemStack one = heldStack.copy();
                one.setCount(1);
                myOffer.set(idx, one);
                heldStack.shrink(1);
                if (heldStack.isEmpty()) heldStack = ItemStack.EMPTY;
            } else if (!heldStack.isEmpty() && !slotStack.isEmpty()
                    && slotStack.getItem() == heldStack.getItem()
                    && slotStack.getCount() < slotStack.getMaxStackSize()) {
                // Add one more to existing same-item stack
                slotStack.grow(1);
                heldStack.shrink(1);
                myOffer.set(idx, slotStack);
                if (heldStack.isEmpty()) heldStack = ItemStack.EMPTY;
            } else if (heldStack.isEmpty() && !slotStack.isEmpty()) {
                // Pick up half
                int half = (slotStack.getCount() + 1) / 2;
                heldStack = slotStack.copy();
                heldStack.setCount(half);
                slotStack.shrink(half);
                myOffer.set(idx, slotStack.isEmpty() ? ItemStack.EMPTY : slotStack);
            }
        }

        myConfirmed = false;
        updateConfirmButton();
        sendOfferUpdate();
    }

    private void handleInventorySlotClick(int invSlot, int button) {
        ItemStack invStack = player.getInventory().getItem(invSlot);

        if (button == 0) {
            if (!heldStack.isEmpty()) {
                if (invStack.isEmpty()) {
                    player.getInventory().setItem(invSlot, heldStack.copy());
                    heldStack = ItemStack.EMPTY;
                } else if (invStack.getItem() == heldStack.getItem()
                        && invStack.getCount() < invStack.getMaxStackSize()) {
                    int canAdd = invStack.getMaxStackSize() - invStack.getCount();
                    int toAdd = Math.min(canAdd, heldStack.getCount());
                    invStack.grow(toAdd);
                    heldStack.shrink(toAdd);
                    player.getInventory().setItem(invSlot, invStack);
                    if (heldStack.isEmpty()) heldStack = ItemStack.EMPTY;
                } else {
                    ItemStack temp = invStack.copy();
                    player.getInventory().setItem(invSlot, heldStack.copy());
                    heldStack = temp;
                }
            } else if (!invStack.isEmpty()) {
                heldStack = invStack.copy();
                player.getInventory().setItem(invSlot, ItemStack.EMPTY);
            }
        } else if (button == 1) {
            if (heldStack.isEmpty() && !invStack.isEmpty()) {
                int half = (invStack.getCount() + 1) / 2;
                heldStack = invStack.copy();
                heldStack.setCount(half);
                invStack.shrink(half);
                player.getInventory().setItem(invSlot,
                        invStack.isEmpty() ? ItemStack.EMPTY : invStack);
            } else if (!heldStack.isEmpty() && invStack.isEmpty()) {
                ItemStack one = heldStack.copy();
                one.setCount(1);
                player.getInventory().setItem(invSlot, one);
                heldStack.shrink(1);
                if (heldStack.isEmpty()) heldStack = ItemStack.EMPTY;
            }
        }
    }

    private int getSlotAt(int mx, int my, int startX, int startY) {
        for (int row = 0; row < SLOTS_ROWS; row++) {
            for (int col = 0; col < SLOTS_COLS; col++) {
                int x = startX + col * (SLOT_SIZE + GAP);
                int y = startY + row * (SLOT_SIZE + GAP);
                if (mx >= x && mx < x + SLOT_SIZE
                        && my >= y && my < y + SLOT_SIZE) {
                    return row * SLOTS_COLS + col;
                }
            }
        }
        return -1;
    }

    private int getInventorySlotAt(int mx, int my) {
        for (int row = 0; row < 4; row++) {
            int from = row * 9;
            int rowY = invSlotY + row * (SLOT_SIZE + GAP);
            for (int i = from; i < from + 9; i++) {
                int col = i - from;
                int x = invSlotX + col * (SLOT_SIZE + GAP);
                if (mx >= x && mx < x + SLOT_SIZE
                        && my >= rowY && my < rowY + SLOT_SIZE) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void returnToInventory(ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private void sendOfferUpdate() {
        NetworkHandler.CHANNEL.sendToServer(
                new TradePacket(TradePacket.Action.UPDATE, partnerName,
                        new ArrayList<>(myOffer)));
    }

    private void onConfirm() {
        myConfirmed = true;
        updateConfirmButton();
        NetworkHandler.CHANNEL.sendToServer(
                new TradePacket(TradePacket.Action.CONFIRM, partnerName));
    }

    private void onCancel() {
        NetworkHandler.CHANNEL.sendToServer(
                new TradePacket(TradePacket.Action.CANCEL, partnerName));
        this.onClose();
    }

    private void updateConfirmButton() {
        if (confirmButton != null) {
            confirmButton.setMessage(myConfirmed
                    ? Component.literal("§aConfirmed")
                    : Component.literal("Confirm"));
        }
    }

    public void setTheirOffer(List<ItemStack> items) {
        for (int i = 0; i < Math.min(items.size(), theirOffer.size()); i++) {
            theirOffer.set(i, items.get(i));
        }
    }

    public void setTheirConfirmed(boolean confirmed) {
        this.theirConfirmed = confirmed;
    }

    public void markCompleted() {
        this.completed = true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        super.onClose();
        if (completed) return;
        if (!heldStack.isEmpty()) {
            returnToInventory(heldStack);
            heldStack = ItemStack.EMPTY;
        }
        for (ItemStack stack : myOffer) {
            if (!stack.isEmpty()) returnToInventory(stack);
        }
    }
}
