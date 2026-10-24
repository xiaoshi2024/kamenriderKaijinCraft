package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.xiaoshi2022.kamenrider_kaijin_craft.skill.StoriousSkill;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 三个快捷技能槽的客户端绑定，持久化到 config，行为与尸兄的技能槽一致。 */
public final class ClientSkillBindings {
    private static final int SLOT_COUNT = 3;
    private static final Path FILE = FMLPaths.CONFIGDIR.get()
            .resolve("kamenrider_kaijin_craft-skill-slots.properties");
    private static final Properties BINDINGS = new Properties();

    static {
        if (Files.isRegularFile(FILE)) {
            try (InputStream input = Files.newInputStream(FILE)) {
                BINDINGS.load(input);
            } catch (IOException ignored) {
                // 配置损坏时直接使用空绑定
            }
        }
    }

    private ClientSkillBindings() {
    }

    public static void bind(int slot, StoriousSkill skill) {
        if (slot < 0 || slot >= SLOT_COUNT || skill == null) {
            return;
        }
        BINDINGS.setProperty(Integer.toString(slot), skill.name());
        save();
    }

    public static StoriousSkill getSkill(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return null;
        }
        String name = BINDINGS.getProperty(Integer.toString(slot), "");
        for (StoriousSkill skill : StoriousSkill.values()) {
            if (skill.name().equals(name)) {
                return skill;
            }
        }
        return null;
    }

    public static boolean isBound(StoriousSkill skill) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (getSkill(slot) == skill) {
                return true;
            }
        }
        return false;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                BINDINGS.store(output, "Kamen Rider Kaijin Craft quick skill bindings");
            }
        } catch (IOException ignored) {
            // 保存失败不影响本次游戏内绑定
        }
    }
}
