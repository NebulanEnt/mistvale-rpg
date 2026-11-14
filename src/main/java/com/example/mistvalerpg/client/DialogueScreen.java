package com.example.mistvalerpg.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.example.mistvalerpg.MistvaleRpg;
import com.example.mistvalerpg.dialogue.DialogueNode;
import com.example.mistvalerpg.dialogue.DialoguePayloads;
import com.example.mistvalerpg.dialogue.DialogueScene;
import com.example.mistvalerpg.dialogue.DialogueSpeaker;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The dialogue box: a small old pixel-RPG text box low on the left of the screen, with a square portrait
 * beside it, a name plate on its top edge, three lines of text that type themselves in with little voice
 * sounds, and the answers stacked small on the right-hand side of the screen.
 *
 * While somebody talks, their portrait opens and closes its mouth (a second picture, "_talk") eight times a second;
 * the player's portrait is a live picture of their own character that nods along.
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
    private static final int PORTRAIT_SIZE = 46;    // the picture inside the frame
    private static final int LINE_HEIGHT = 10;
    private static final int PAGE_LINES = 3;
    private static final float CHOICE_SCALE = 0.55F; // the answers are drawn at about half size, tucked into the corner

    private static final int TEXT_COLOUR = 0x3B2A1A;        // dark brown ink on the paper
    private static final int CHOICE_COLOUR = 0xD9D2C0;
    private static final int CHOICE_HOVER_COLOUR = 0xFFFFFF;
    private static final String LONG_BREATH = ".!?\u2026";   // . ! ? and the ellipsis character
    private static final String SHORT_BREATH = ",;:\u2014";  // , ; : and the long dash

    // The five shades of the dark square behind a live portrait, outermost first.
    private static final int[] VIGNETTE = { 0xFF111626, 0xFF1A2038, 0xFF242C56, 0xFF30406F, 0xFF3B4D83 };

    private DialogueScene scene;
    private DialogueNode node;
    private List<String> allLines = List.of();     // the whole text, cut into lines
    private List<String> lines = List.of();        // the lines of the page being shown
    private int page;
    private int pages = 1;
    private int totalChars;
    private float[] appearAt = new float[0];   // for every character of the page: the second (counted from the page start) when it appears
    private long startNanos;    // when the page started typing
    private boolean skip;       // the player asked for the rest of the page at once
    private int shown;          // how many characters of the page are visible so far
    private int lastShown;      // how many were visible at the last tick, to know which ones are new
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

    private final Map<ResourceLocation, ResourceLocation> talkCache = new HashMap<>();
    private final Map<ResourceLocation, int[]> sizeCache = new HashMap<>();

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
        lastShown = 0;
        skip = false;
        startNanos = System.nanoTime();
        lines = allLines.subList(Math.min(allLines.size(), page * PAGE_LINES), Math.min(allLines.size(), (page + 1) * PAGE_LINES));
        totalChars = lines.stream().mapToInt(String::length).sum();

        // The typing schedule: the second at which each character appears. Every character takes 1 / speed seconds,
        // and the old mod's breaths come after punctuation: a long one after . ! ? and the ellipsis, a short one after , ; : and the dash.
        // No breath when the next character is punctuation too, so "..." does not take forever.
        appearAt = new float[totalChars];
        float perChar = 1.0F / Math.max(4, node.speaker().voice().charsPerSecond());
        float time = 0.0F;
        for (int i = 0; i < totalChars; i++) {
            time += perChar;
            char c = charAt(i);
            char following = i + 1 < totalChars ? charAt(i + 1) : ' ';
            if (SHORT_BREATH.indexOf(following) < 0 && LONG_BREATH.indexOf(following) < 0) {
                if (LONG_BREATH.indexOf(c) >= 0) {
                    time += 0.3F;
                } else if (SHORT_BREATH.indexOf(c) >= 0) {
                    time += 0.13F;
                }
            }
            appearAt[i] = time;
        }
    }

    // ---- Typing ----

    // Seconds since the page started typing (or "forever" once the player skipped ahead).
    private float elapsed() {
        return skip ? Float.MAX_VALUE : (System.nanoTime() - startNanos) / 1.0E9F;
    }

    // How many characters should be visible right now.
    private int visible() {
        float time = elapsed();
        int count = 0;
        while (count < totalChars && appearAt[count] <= time) {
            count++;
        }
        return count;
    }

    @Override
    public void tick() {
        age++;
        if (waiting) {
            return;
        }
        shown = visible();
        if (shown > lastShown) {
            DialogueSpeaker.Voice voice = node.speaker().voice();
            // One bip for the newly appeared letters, if one of them sits on a bip position (every second one).
            for (int i = lastShown; i < shown; i++) {
                if (Character.isLetterOrDigit(charAt(i)) && i % Math.max(1, voice.interval()) == 0) {
                    bip(voice);
                    break;
                }
            }
            lastShown = shown;
        }
    }

    // One little voice sound. The pitch wobbles a little from bip to bip, so it sounds like speech and not a machine.
    private void bip(DialogueSpeaker.Voice voice) {
        float wobble = 1.0F + (minecraft.level.random.nextFloat() * 2.0F - 1.0F) * voice.variance();
        float pitch = Mth.clamp(voice.pitch() * wobble, 0.5F, 2.0F);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(voice.sound().get(), pitch, voice.volume()));
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
        if (!waiting) {
            shown = visible();
        }
        graphics.blitSprite(PANEL, panelX, panelY, panelWidth, PANEL_HEIGHT);

        // portrait: the frame first (its middle is a solid dark square), then the picture inside it
        graphics.blitSprite(PORTRAIT_FRAME, frameX, panelY, FRAME_SIZE, FRAME_SIZE);
        drawPortrait(graphics, frameX + 4, panelY + 4, PORTRAIT_SIZE);

        // name plate on the top edge of the panel
        Component name = node.speaker().displayName(playerName());
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

    // ---- Portraits ----

    // Draws the speaker's portrait in a square of the given size. While they are talking, the picture swaps eight times a second.
    private void drawPortrait(GuiGraphics graphics, int x, int y, int size) {
        boolean mouthOpen = !typingDone() && (int) (elapsed() * 8.0F) % 2 == 0;
        if (node.speaker().isLive()) {
            drawLivePlayer(graphics, x, y, size, mouthOpen);
            return;
        }
        ResourceLocation base = node.speaker().portrait(node.emotion());
        ResourceLocation picture = mouthOpen ? talkPicture(base) : base;
        int[] pixels = pictureSize(picture);
        graphics.blit(picture, x, y, size, size, 0.0F, 0.0F, pixels[0], pixels[1], pixels[0], pixels[1]);
    }

    // spirit_happy.png has an open-mouth twin called spirit_happy_talk.png. If a portrait has no twin, it just stays as it is.
    private ResourceLocation talkPicture(ResourceLocation base) {
        return talkCache.computeIfAbsent(base, key -> {
            String path = key.getPath();
            ResourceLocation twin = key.withPath(path.substring(0, path.length() - ".png".length()) + "_talk.png");
            return minecraft.getResourceManager().getResource(twin).isPresent() ? twin : key;
        });
    }

    // How many pixels wide and high a picture file is. The portraits are bigger than they are drawn, so we ask the file itself.
    private int[] pictureSize(ResourceLocation picture) {
        return sizeCache.computeIfAbsent(picture, key -> {
            try (InputStream stream = minecraft.getResourceManager().open(key); NativeImage image = NativeImage.read(stream)) {
                return new int[] { image.getWidth(), image.getHeight() };
            } catch (Exception e) {
                return new int[] { 64, 64 };
            }
        });
    }

    // The player's own character, drawn live on a softly moving mist-coloured backdrop. While they speak they nod a little.
    private void drawLivePlayer(GuiGraphics graphics, int x, int y, int size, boolean nod) {
        drawBackdrop(graphics, x, y, size, node.speaker().colour());
        LivingEntity player = minecraft.player;
        if (player == null) {
            return;
        }
        double seconds = System.currentTimeMillis() / 1000.0;
        float scale = size * (1.0F + 0.012F * (float) Math.sin(seconds * 1.7));       // slowly "breathes"
        float lift = player.getEyeHeight() - 0.08F - player.getBbHeight() / 2.0F - 0.26F + 0.2F;
        float turn = 0.45F + 0.16F * (float) Math.sin(seconds * 0.8);                 // slowly looks left and right
        float tilt = 0.05F * (float) Math.sin(seconds * 0.55F) + (nod ? 0.06F : 0.0F);

        graphics.enableScissor(x, y, x + size, y + size);
        try {
            InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x, y, x + size, y + size, (int) scale, lift, turn, tilt, player);
        } finally {
            graphics.disableScissor();
        }
    }

    // A dark vignette, two slow drifting beams of mist, a few rising sparks, a shade at the bottom and a glowing edge in the speaker's colour.
    private void drawBackdrop(GuiGraphics graphics, int x, int y, int size, int rgb) {
        double seconds = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < VIGNETTE.length; i++) {
            int inset = (int) ((VIGNETTE.length - 1 - i) * size * 0.085F);
            graphics.fill(x + inset, y + inset, x + size - inset, y + size - inset, mix(VIGNETTE[i], 0xFF000000 | rgb, 0.12F + 0.05F * i));
        }
        graphics.enableScissor(x, y, x + size, y + size);
        for (int beam = 0; beam < 2; beam++) {
            double start = size * (0.15 + 0.5 * beam) + Math.sin(seconds * 0.5 + beam * 2.1) * size * 0.07;
            int width = (int) (size * (beam == 0 ? 0.22 : 0.11));
            for (int row = 0; row < size; row += 2) {
                int left = (int) (start + row * 0.45);
                graphics.fill(x + left, y + row, x + left + width, y + row + 2, beam == 0 ? 0x17E8F0D0 : 0x110C1F3F);
            }
        }
        for (int i = 0; i < 9; i++) {
            double rise = (seconds * (0.07 + 0.012 * i) + i * 0.173) % 1.0;
            int px = (int) (i * 37 % 100 / 100.0 * size + Math.sin(seconds * 0.9 + i) * 3.0);
            int py = (int) (size - rise * size);
            int alpha = (int) (Math.sin(rise * Math.PI) * (70 + 18 * (i % 3)));
            int dot = i % 4 == 0 ? 2 : 1;
            graphics.fill(x + px, y + py, x + px + dot, y + py + dot, alpha << 24 | 0xFFE9C8);
        }
        for (int i = 0; i < 7; i++) {
            graphics.fill(x, y + size - 1 - i * (size / 24), x + size, y + size - i * (size / 24), (34 + i * 10) << 24);
        }
        for (int i = 0; i < 3; i++) {
            graphics.fill(x + i, y, x + i + 1, y + size, (68 - i * 22) << 24 | rgb & 0xFFFFFF);
        }
        graphics.disableScissor();
    }

    // Blends two colours: 0 is all of a, 1 is all of b.
    private static int mix(int a, int b, float amount) {
        int red = (int) ((a >> 16 & 0xFF) * (1.0F - amount) + (b >> 16 & 0xFF) * amount);
        int green = (int) ((a >> 8 & 0xFF) * (1.0F - amount) + (b >> 8 & 0xFF) * amount);
        int blue = (int) ((a & 0xFF) * (1.0F - amount) + (b & 0xFF) * amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
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
            skip = true;
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
