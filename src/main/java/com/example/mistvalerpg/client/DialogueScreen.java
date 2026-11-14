package com.example.mistvalerpg.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.dialogue.DialogueNode;
import com.example.mistvalerpg.dialogue.DialoguePayloads;
import com.example.mistvalerpg.dialogue.DialogueScene;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The dialogue box: a small old pixel-RPG text box low on the left of the screen, with a square portrait
 * beside it, a name plate on its top edge, three lines of text that type themselves in with little voice
 * sounds, and the answers stacked small on the right-hand side of the screen.
 *
 * The text panel is old pixel paper; the answers are navy plates. A long text is cut into pages of three lines. Space, Enter or a click first finishes the typing, then turns the page,
 * then moves on. Number keys, W/S or the arrow keys and a click pick an answer.
 * Escape closes the box (the scene then does not count as seen).
 */
public class DialogueScreen extends Screen {
    // These are "GUI sprites": pictures in textures/gui/sprites that the game stretches to any size
    // by repeating their middle (a "nine-slice"). The .mcmeta file next to each one says how.
    private static final ResourceLocation PANEL = sprite("dialogue/panel"); // the old pixel paper
    private static final ResourceLocation PLATE = sprite("dialogue/name_plate");
    private static final ResourceLocation PORTRAIT_FRAME = sprite("dialogue/portrait_frame");
    private static final ResourceLocation CHOICE = sprite("dialogue/choice");
    private static final ResourceLocation CHOICE_HOVER = sprite("dialogue/choice_hover");
    private static final ResourceLocation ARROW = sprite("dialogue/arrow");

    // Sizes are in "GUI pixels", the units Minecraft's own menus use, so the box grows with the GUI scale setting.
    private static final int MARGIN = 12;          // space between the portrait and the left edge of the screen
    private static final int FRAME_SIZE = 54;      // the portrait frame is a 54 x 54 square
    private static final int PANEL_HEIGHT = 54;    // the text panel is exactly as tall as the portrait frame
    private static final int LINE_HEIGHT = 10;
    private static final int PAGE_LINES = 3;
    private static final float CHOICE_SCALE = 0.55F; // the answers are drawn at about half size, tucked into the corner

    private static final int TEXT_COLOUR = 0x3B2A1A;        // dark brown ink on the paper
    private static final int CHOICE_COLOUR = 0xD9D2C0;
    private static final int CHOICE_HOVER_COLOUR = 0xFFFFFF;

    private DialogueScene scene;
    private DialogueNode node;
    private List<String> allLines = List.of();     // the whole text, cut into lines
    private List<String> lines = List.of();        // the lines of the page being shown
    private int page;
    private int pages = 1;
    private int totalChars;
    private int shown;          // how many characters of the page are visible so far
    private float speedBank;    // typing speed is 1.6 characters per tick: this collects the fractions
    private int pause;          // ticks to wait after a full stop or comma
    private int bipCounter;
    private int selected;       // the answer the keyboard is pointing at
    private boolean waiting;    // we told the server what the player did and are waiting for the next box
    private boolean closedByServer;
    private int age;

    // the layout, worked out in layout()
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int textX;
    private int textWidth;
    private int frameX;
    private final List<int[]> choiceBoxes = new ArrayList<>();           // {x, y, width, height} in the small (scaled) units
    private final List<List<String>> choiceLines = new ArrayList<>();

    public DialogueScreen(DialogueScene scene, DialogueNode node) {
        super(Component.empty());
        this.scene = scene;
        this.node = node;
    }

    private static ResourceLocation sprite(String path) {
        return ResourceLocation.fromNamespaceAndPath(MistvaleRpg.MODID, path);
    }

    /** Moves the box on to a new dialogue box and starts typing it. */
    public void show(DialogueScene scene, DialogueNode node) {
        this.scene = scene;
        this.node = node;
        this.waiting = false;
        if (minecraft != null) {
            layout();
        }
    }

    @Override
    protected void init() {
        layout(); // now that the screen has a font and a size we can work out where everything goes
    }

    // ---- Layout ----

    private String playerName() {
        return Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString();
    }

    private List<String> split(Component text, int width) {
        List<String> result = new ArrayList<>();
        for (FormattedText line : font.getSplitter().splitLines(text, Math.max(40, width), Style.EMPTY)) {
            result.add(line.getString());
        }
        return result;
    }

    // Works out where the portrait, panel, text and answers go on THIS screen size. The panel is about 200 pixels wide,
    // starts just right of the portrait and sits at 78.5% of the screen height, which is above the hotbar.
    private void layout() {
        frameX = MARGIN;
        panelX = MARGIN + FRAME_SIZE - 4;                 // tucked 4 pixels under the portrait frame
        panelWidth = Math.min(width - 24, 250) - 50;
        panelY = (int) (height * 0.785F) - PANEL_HEIGHT;
        textX = panelX + 15;
        textWidth = panelX + panelWidth - 13 - textX;

        // We cut the FULL text first, so words never jump to the next line halfway through being typed.
        allLines = split(Component.translatable(scene.textKey(node.id()), playerName()), textWidth);
        pages = Math.max(1, (allLines.size() + PAGE_LINES - 1) / PAGE_LINES);

        // The answers: small boxes stacked down the right-hand edge, starting a fifth of the way down the screen.
        choiceBoxes.clear();
        choiceLines.clear();
        int count = node.choices().size();
        if (count > 0) {
            int boxWidth = (int) (Math.min(width * 0.24F, 112.0F) / CHOICE_SCALE);
            int x = (int) ((width - 6) / CHOICE_SCALE) - boxWidth;
            int y = (int) (height * 0.21F / CHOICE_SCALE);
            for (int i = 0; i < count; i++) {
                Component text = Component.literal((i + 1) + ".  ").append(Component.translatable(scene.choiceKey(node.id(), i)));
                List<String> wrapped = split(text, boxWidth - 28);
                int boxHeight = 10 + Math.max(1, wrapped.size()) * LINE_HEIGHT;
                choiceBoxes.add(new int[] { x, y, boxWidth, boxHeight });
                choiceLines.add(wrapped);
                y += boxHeight + 5;
            }
        }
        selected = 0;
        startPage(0);
    }

    private boolean hasMore() {
        return page < pages - 1;
    }

    private void startPage(int newPage) {
        page = newPage;
        shown = 0;
        speedBank = 0.0F;
        pause = 8;
        bipCounter = 0;
        lines = allLines.subList(Math.min(allLines.size(), page * PAGE_LINES), Math.min(allLines.size(), (page + 1) * PAGE_LINES));
        totalChars = lines.stream().mapToInt(String::length).sum();
    }

    // ---- Typing ----

    @Override
    public void tick() {
        age++;
        if (waiting || shown >= totalChars) {
            return;
        }
        if (pause > 0) {
            pause--;
            return;
        }
        speedBank += 1.6F;
        while (speedBank >= 1.0F && shown < totalChars) {
            speedBank -= 1.0F;
            char c = charAt(shown);
            shown++;
            if (!Character.isWhitespace(c) && ++bipCounter % 2 == 0) {
                float pitch = node.emotion().voicePitch() * (0.92F + 0.2F * minecraft.level.random.nextFloat());
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(node.speaker().voice(), pitch, 0.6F));
            }
            // a short breath after punctuation
            if (c == '.' || c == '!' || c == '?') {
                pause = 7;
                break;
            } else if (c == ',' || c == ';' || c == ':') {
                pause = 3;
                break;
            }
        }
    }

    private char charAt(int index) {
        int left = index;
        for (String line : lines) {
            if (left < line.length()) {
                return line.charAt(left);
            }
            left -= line.length();
        }
        return ' ';
    }

    private boolean typingDone() {
        return shown >= totalChars;
    }

    // The answers only show once the last page has been typed.
    private boolean showingChoices() {
        return typingDone() && !waiting && !hasMore() && !choiceBoxes.isEmpty();
    }

    // ---- Drawing ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(PANEL, panelX, panelY, panelWidth, PANEL_HEIGHT);

        // portrait: frame first, then the picture inside it, with a tiny bounce while the speaker is talking
        graphics.blitSprite(PORTRAIT_FRAME, frameX, panelY, FRAME_SIZE, FRAME_SIZE);
        int bounce = !typingDone() && (age / 3) % 2 == 0 ? -1 : 0;
        graphics.blit(node.speaker().portrait(node.emotion()), frameX + 4, panelY + 4 + bounce, 46, 46, 0.0F, 0.0F, 128, 128, 128, 128);

        // name plate on the top edge of the panel
        Component name = node.speaker().displayName();
        int plateWidth = font.width(name) + 22;
        graphics.blitSprite(PLATE, panelX + 8, panelY - 11, plateWidth, 16);
        graphics.drawString(font, name, panelX + 19, panelY - 6, 0xFFFFFF, false);

        // the text of this page, only as far as it has been typed
        int left = shown;
        int y = panelY + 12;
        for (String line : lines) {
            if (left <= 0) {
                break;
            }
            graphics.drawString(font, line.substring(0, Math.min(left, line.length())), textX, y, TEXT_COLOUR, false);
            left -= line.length();
            y += LINE_HEIGHT;
        }

        if (!typingDone() || waiting) {
            return;
        }
        if (showingChoices()) {
            drawChoices(graphics, mouseX, mouseY);
        } else {
            // a little arrow that bobs up and down: "press a key to continue"
            int bob = (int) (Mth.sin(age * 0.3F) * 1.5F);
            graphics.blitSprite(ARROW, panelX + panelWidth - 22, panelY + PANEL_HEIGHT - 17 + bob, 11, 9);
        }
    }

    // The answers are drawn at 55% size, so we shrink the drawing area and divide the mouse position by the same amount.
    private void drawChoices(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().scale(CHOICE_SCALE, CHOICE_SCALE, 1.0F);
        double mx = mouseX / CHOICE_SCALE;
        double my = mouseY / CHOICE_SCALE;
        for (int i = 0; i < choiceBoxes.size(); i++) {
            int[] box = choiceBoxes.get(i);
            boolean hover = mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3];
            if (hover) {
                selected = i;
            }
            boolean active = i == selected;
            graphics.blitSprite(active ? CHOICE_HOVER : CHOICE, box[0], box[1], box[2], box[3]);
            int textY = box[1] + 6;
            for (String line : choiceLines.get(i)) {
                graphics.drawString(font, line, box[0] + 16, textY, active ? CHOICE_HOVER_COLOUR : CHOICE_COLOUR, false);
                textY += LINE_HEIGHT;
            }
        }
        graphics.pose().popPose();
    }

    // ---- Input ----

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (waiting) {
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            advance(true);
            return true;
        }
        if (showingChoices()) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
                selected = (selected + choiceBoxes.size() - 1) % choiceBoxes.size();
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
                selected = (selected + 1) % choiceBoxes.size();
                return true;
            }
        }
        int number = key - GLFW.GLFW_KEY_1;
        if (number >= 0 && number < 9 && showingChoices() && number < node.choices().size()) {
            send(number);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers); // Escape closes the box
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (waiting || button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (showingChoices()) {
            for (int i = 0; i < choiceBoxes.size(); i++) {
                int[] box = choiceBoxes.get(i);
                double mx = mouseX / CHOICE_SCALE;
                double my = mouseY / CHOICE_SCALE;
                if (mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3]) {
                    send(i);
                    return true;
                }
            }
            return true;
        }
        advance(false);
        return true;
    }

    // First press: show the rest of the text at once. Second: turn the page. After the last page: go on to the next box.
    private void advance(boolean fromKeyboard) {
        if (!typingDone()) {
            shown = totalChars;
            return;
        }
        if (hasMore()) {
            startPage(page + 1);
            return;
        }
        if (node.isQuestion()) {
            if (fromKeyboard) {
                send(Math.min(selected, node.choices().size() - 1)); // Enter or Space picks the answer the keyboard points at
            }
            return;
        }
        send(DialoguePayloads.Advance.CONTINUE);
    }

    private void send(int choice) {
        waiting = true;
        PacketDistributor.sendToServer(new DialoguePayloads.Advance(choice));
    }

    /** The server said the conversation is over. */
    public void closeFromServer() {
        closedByServer = true;
        onClose();
    }

    @Override
    public void onClose() {
        if (!closedByServer) {
            PacketDistributor.sendToServer(new DialoguePayloads.Advance(DialoguePayloads.Advance.ABORT));
        }
        super.onClose();
    }

    // The world keeps running behind the box, and a conversation should not freeze a multiplayer game.
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Nothing is drawn behind the box: the world stays fully visible, exactly like in the old mod.
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }
}
