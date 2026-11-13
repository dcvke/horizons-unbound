package com.horizonsunbound.client;

import com.horizonsunbound.StormsConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.ArrayList;
import java.util.List;

/** Shared Forge/Fabric settings screen; edits are applied together when Save is pressed. */
public final class ConfigScreen extends Screen {
    private record Entry(String path, String section, ForgeConfigSpec.ConfigValue<?> value, ForgeConfigSpec.ValueSpec spec) {}
    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Object> draft = new ArrayList<>();
    private int page, section;
    private static final List<String> SECTIONS = List.of("spawning", "protection", "dimensionPieces", "effects", "client");
    private List<Integer> visible() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).section.equals(SECTIONS.get(section))) indices.add(i);
        return indices;
    }

    public ConfigScreen(Screen parent) {
        super(Component.translatable("horizons_unbound.configuration.title"));
        this.parent = parent;
        collect(StormsConfig.SPEC, StormsConfig.SPEC.getValues(), "");
        collect(StormConfig.SPEC, StormConfig.SPEC.getValues(), "");
    }
    private void collect(ForgeConfigSpec owner, UnmodifiableConfig config, String prefix) {
        for (var entry : config.entrySet()) {
            String path = prefix + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof UnmodifiableConfig nested) collect(owner, nested, path + ".");
            else if (value instanceof ForgeConfigSpec.ConfigValue<?> setting) {
                entries.add(new Entry(path, owner == StormConfig.SPEC ? "client" : path.substring(0, path.indexOf('.')), setting, owner.getSpec().get(path)));
                Object current = setting.get();
                draft.add(current instanceof List<?> list ? new ArrayList<>(list) : current);
            }
        }
    }
    private int rows() { return Math.max(1, (height - 130) / 24); }
    private Component label(Entry entry) {
        String translation = entry.spec.getTranslationKey();
        if (translation == null) translation = "horizons_unbound.configuration." + entry.path.substring(entry.path.lastIndexOf('.') + 1);
        return Language.getInstance().has(translation) ? Component.translatable(translation) : Component.literal(entry.path);
    }
    private Component hint(Entry entry) {
        String key = entry.spec.getTranslationKey();
        if (key == null) key = "horizons_unbound.configuration." + entry.path.substring(entry.path.lastIndexOf('.') + 1);
        return Language.getInstance().has(key + ".tooltip") ? Component.translatable(key + ".tooltip")
                : Component.literal(entry.spec.getComment() == null ? entry.path : entry.spec.getComment());
    }
    @Override protected void init() {
        int count = rows();
        List<Integer> indices = visible();
        page = Math.min(page, Math.max(0, (indices.size() - 1) / count));
        addRenderableWidget(Button.builder(Component.translatable("horizons_unbound.configuration." + SECTIONS.get(section)), button -> {
            section = (section + 1) % SECTIONS.size(); page = 0; rebuildWidgets();
        }).bounds(20, 34, width - 40, 20).build());
        for (int row = page * count; row < Math.min(indices.size(), (page + 1) * count); row++) {
            int i = indices.get(row);
            final int index = i;
            Entry entry = entries.get(i);
            Button button = Button.builder(Component.literal(String.valueOf(draft.get(i))), b -> edit(index))
                    .bounds(width / 2 + 8, 64 + (row % count) * 24, Math.max(80, width / 2 - 30), 20).build();
            button.setTooltip(Tooltip.create(hint(entry)));
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(20, height - 55, 30, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(width - 50, height - 55, 30, 20).build()).active = (page + 1) * count < indices.size();
        addRenderableWidget(Button.builder(Component.translatable("controls.reset"), b -> {
            for (int i : visible()) draft.set(i, entries.get(i).spec.getDefault());
            rebuildWidgets();
        }).bounds(width / 2 - 75, height - 55, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save()).bounds(width / 2 - 155, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 + 5, height - 28, 150, 20).build());
    }
    private void edit(int index) {
        Object value = draft.get(index);
        if (value instanceof Boolean flag) { draft.set(index, !flag); rebuildWidgets(); }
        else if (value instanceof Enum<?> choice) {
            Object[] choices = choice.getDeclaringClass().getEnumConstants();
            draft.set(index, choices[(choice.ordinal() + 1) % choices.length]);
            rebuildWidgets();
        } else minecraft.setScreen(new ValueScreen(index));
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void save() {
        for (int i = 0; i < entries.size(); i++) ((ForgeConfigSpec.ConfigValue) entries.get(i).value).set(draft.get(i));
        StormsConfig.SPEC.save();
        StormConfig.SPEC.save();
        onClose();
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 12, 0xffffff);
        int count = rows();
        List<Integer> indices = visible();
        for (int row = page * count; row < Math.min(indices.size(), (page + 1) * count); row++)
            g.drawString(font, font.plainSubstrByWidth(label(entries.get(indices.get(row))).getString(), Math.max(80, width / 2 - 32)), 20, 70 + (row % count) * 24, 0xffffff);
        g.drawCenteredString(font, (page + 1) + " / " + Math.max(1, (indices.size() + count - 1) / count), width / 2, height - 70, 0xaaaaaa);
        super.render(g, mouseX, mouseY, partial);
    }
    boolean verifySaveRoundTrip() {
        int volume = -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).value == StormConfig.VOLUME) volume = i;
        if (volume < 0) throw new IllegalStateException("Volume control missing");
        ValueScreen editor = new ValueScreen(volume);
        editor.values.set(0, "0.25");
        if (!editor.valid()) throw new IllegalStateException("Valid volume rejected");
        draft.set(volume, editor.parsed());
        save();
        return StormConfig.VOLUME.get() == .25;
    }
    private final class ValueScreen extends Screen {
        private final int index;
        private final List<String> values = new ArrayList<>();
        private int offset;
        private Button done;
        private final boolean list;
        ValueScreen(int index) {
            super(label(entries.get(index)));
            this.index = index;
            Object value = draft.get(index);
            list = value instanceof List<?>;
            if (value instanceof List<?> strings) for (Object string : strings) values.add(String.valueOf(string));
            else values.add(String.valueOf(value));
        }
        @Override protected void init() {
            int count = Math.max(1, (height - 112) / 24);
            offset = Math.min(offset, Math.max(0, values.size() - count));
            for (int i = offset; i < Math.min(values.size(), offset + count); i++) {
                final int row = i;
                EditBox input = new EditBox(font, 20, 38 + (i - offset) * 24, width - (list ? 70 : 40), 20, title);
                input.setMaxLength(32767);
                input.setValue(values.get(i));
                input.setTooltip(Tooltip.create(hint(entries.get(index))));
                input.setResponder(text -> { values.set(row, text); if (done != null) done.active = valid(); });
                addRenderableWidget(input);
                if (list) addRenderableWidget(Button.builder(Component.literal("-"), b -> { values.remove(row); rebuildWidgets(); }).bounds(width - 45, input.getY(), 25, 20).build());
            }
            if (list) {
                addRenderableWidget(Button.builder(Component.literal("+"), b -> { values.add(""); offset = Math.max(0, values.size() - count); rebuildWidgets(); }).bounds(20, height - 65, 30, 20).build());
                addRenderableWidget(Button.builder(Component.literal("<"), b -> { offset = Math.max(0, offset - count); rebuildWidgets(); }).bounds(60, height - 65, 30, 20).build()).active = offset > 0;
                addRenderableWidget(Button.builder(Component.literal(">"), b -> { offset += count; rebuildWidgets(); }).bounds(100, height - 65, 30, 20).build()).active = offset + count < values.size();
            }
            done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
                draft.set(index, parsed()); minecraft.setScreen(ConfigScreen.this);
            }).bounds(width / 2 - 155, height - 28, 150, 20).build());
            done.active = valid();
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 + 5, height - 28, 150, 20).build());
        }
        private Object parsed() {
            if (list) return new ArrayList<>(values);
            Object original = draft.get(index);
            String value = values.get(0).strip();
            if (original instanceof Integer) return Integer.valueOf(value);
            if (original instanceof Long) return Long.valueOf(value);
            if (original instanceof Double) return Double.valueOf(value);
            return value;
        }
        private boolean valid() {
            try { return entries.get(index).spec.test(parsed()); }
            catch (IllegalArgumentException ex) { return false; }
        }
        @Override public void onClose() { minecraft.setScreen(ConfigScreen.this); }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            renderBackground(g);
            g.drawCenteredString(font, title, width / 2, 12, 0xffffff);
            super.render(g, mouseX, mouseY, partial);
        }
    }
}
