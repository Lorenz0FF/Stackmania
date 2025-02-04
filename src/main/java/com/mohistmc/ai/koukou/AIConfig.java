package com.mohistmc.ai.koukou;

import com.mohistmc.plugins.config.MohistPluginConfig;
import java.io.File;
import java.util.List;

public class AIConfig extends MohistPluginConfig {

    public static AIConfig INSTANCE;

    public AIConfig(File file) {
        super(file);
    }

    public static void init() {
        INSTANCE = new AIConfig(new File("mohist-config", "qq.yml"));
        INSTANCE.yaml.addDefault("enable", false);
        INSTANCE.yaml.addDefault("debug", false);
        INSTANCE.yaml.addDefault("server_name", "群消息");
        INSTANCE.yaml.addDefault("post_server", "http://0.0.0.0:3000");
        INSTANCE.yaml.addDefault("http_server.hostname", "0.0.0.0");
        INSTANCE.yaml.addDefault("http_server.port", 2025);
        INSTANCE.yaml.addDefault("chat_post_group", List.of("123456789"));
        INSTANCE.yaml.addDefault("command.enable", false);
        INSTANCE.yaml.addDefault("command.owners", List.of("123456789"));
        INSTANCE.yaml.addDefault("command.name", "执行");
        INSTANCE.save();
    }

    public boolean enable() {
        return yaml.getBoolean("enable", false);
    }

    public boolean debug() {
        return yaml.getBoolean("debug", false);
    }

    public String post_server() {
        return yaml.getString("post_server", "http://0.0.0.0:3000");
    }

    public String http_server_hostname() {
        return yaml.getString("http_server.hostname", "0.0.0.0");
    }

    public int http_server_port() {
        return yaml.getInt("http_server.port", 2025);
    }

    public List<String> chat_post_group() {
        return yaml.getStringList("chat_post_group");
    }

    public boolean command_enable() {
        return yaml.getBoolean("command.enable");
    }

    public List<String> command_owners() {
        return yaml.getStringList("command.owners");
    }

    public String command_name() {
        return yaml.getString("command.name", "执行");
    }

    public String server_name() {
        return yaml.getString("server_name", "群消息");
    }

}
