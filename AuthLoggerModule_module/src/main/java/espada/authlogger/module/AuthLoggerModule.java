package espada.authlogger.module;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import pro.gravit.launcher.modules.LauncherInitContext;
import pro.gravit.launcher.modules.LauncherModule;
import pro.gravit.launcher.modules.LauncherModuleInfo;
import pro.gravit.launchserver.auth.AuthProviderResult;
import pro.gravit.launchserver.modules.events.LaunchServerPostInitPhase;
import pro.gravit.launchserver.socket.Client;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AuthLoggerModule extends LauncherModule {
    public static final LauncherModuleInfo MODULE_INFO = new LauncherModuleInfo(
        "AuthLoggerModule",
        "1.0.0",
        new String[]{}
    );

    private static final DataTimeFormatter DATA_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private Config config;

    public AuthLoggerModule() {
        super(MODULE_INFO);
    }

    @Override
    public void init(LauncherInitContext initContext) {
        loadConfig();
        registerEvent(this::onServerPostInit, LauncherServerPostInitPhase.class)
    }

    private void onServerPostInit(LaunchServerPostInitPhase event) {
        event.server.authHookManager.postHook.registerHook(this::onAuthSuccess);
    }

    private AuthProviderResult onAuthSuccess(AuthProviderResult result, Client client) {
        if (result != null && result.username != null) {
            String username = result.username;
            String ip = client.getIP();
            String dateTime = LocalDateTime.now().format(DATA_TIME_FORMATTER);
            sendToDiscord(username, ip, dateTime);
        }
        return result;
    }

    private void sendToDiscord(String username, String ip, String dateTime) {
        if (config.discordWebhookUrl == null || config.discordWebhookUrl.isEmpty()) {
            LogHelper.error("Discord Webhook URL не был указан в конфиге!");
            return;
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(config.discordWebhookUrl);
            httpPost.setHeader("Content-Type", "application/json");

            String jsonPayload = String.format(
                    "{\"embeds\": [{" +
                            "\"title\": \"Вход в аккаунт\"," +
                            "\"color\": 65280," +
                            "\"fields\": [" +
                            "{\"name\": \"Ник\", \"value\": \"%s\", \"inline\": true}," +
                            "{\"name\": \"IP\", \"value\": \"%s\", \"inline\": true}," +
                            "{\"name\": \"Дата и время\", \"value\": \"%s\", \"inline\": true}" +
                            "]" +
                            "}]}",
                    username, ip, dateTime
            );

            httpPost.setEntity(new StringEntity(jsonPayload, "UTF-8"));
            httpClient.execute(httpPost);
            LogHelper.info("Отправлено сообщение в Discord о входе: %s (%s)", username, ip);
        } catch (Exception e) {
            LogHelper.error("Ошибка отправки в Discord: %s", e.getMessage());
        }
    }

    private void loadConfig() {
        try {
            Path configPath = modulesConfigManager.getModuleConfigDir(getInfo().name).resolve("Config.json");
            config = modulesConfigManager.getConfig(getInfo().name, "Config.json", Config.class, () -> {
                Config defaultConfig = new Config();
                defaultConfig.discordWebhookUrl = "https://discord.com/api/webhooks/YOUR_WEBHOOK_ID/YOUR_WEBHOOK_TOKEN";
                return defaultConfig;
            });
            LogHelper.info("Загружен Discord Webhook URL: %s", config.discordWebhookUrl);
        } catch (Exception e) {
            LogHelper.error("Ошибка загрузки конфигурации: %s", e.getMessage());
            config = new Config();
            config.discordWebhook = "";
        }
    }

    public static class Config {
        public String discordWebhookUrl;
    }
}