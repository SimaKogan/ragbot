package dev.ragbot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProps(String adminToken, Telegram telegram, Slack slack, Rag rag) {
    public record Telegram(String token, String secret) {}
    public record Slack(String botToken, String signingSecret) {}
    public record Rag(int topK, double threshold) {}
}
