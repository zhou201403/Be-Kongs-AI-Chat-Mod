package com.be_kongs_ai_chat_mod.chat;

import com.be_kongs_ai_chat_mod.BeKongsAiChatMod;
import com.be_kongs_ai_chat_mod.ai.AiProvider;
import com.be_kongs_ai_chat_mod.ai.AiProviderFactory;
import com.be_kongs_ai_chat_mod.ai.ChatMessage;
import com.be_kongs_ai_chat_mod.config.AiChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class AiChatManager {
    private static AiChatManager INSTANCE;

    private AiChatConfig config;
    private AiProvider provider;

    private final List<ChatMessage> context = new ArrayList<>();

    // keep recent state for repeat detection
    private volatile String lastUserMessage = null;
    private volatile String lastAiResponse = null;

    private final Object contextLock = new Object();

    private AiChatManager(AiChatConfig config) {
        this.config = config;
        this.provider = AiProviderFactory.create(config);
    }

    public static void init(AiChatConfig config) {
        if (INSTANCE == null) {
            INSTANCE = new AiChatManager(config);
        } else {
            INSTANCE.reloadConfig(config);
        }
    }

    public static AiChatManager getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("AiChatManager not initialized");
        return INSTANCE;
    }

    public void reloadConfig(AiChatConfig newConfig) {
        this.config = newConfig;
        this.provider = AiProviderFactory.create(newConfig);
    }

    public void clearContext() {
        synchronized (contextLock) {
            context.clear();
        }
    }

    private List<ChatMessage> buildContext(String currentUserMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        if (config.contextEnabled && config.contextLength > 0) {
            synchronized (contextLock) {
                messages.addAll(context);
            }
            // Keep the current user message and trim only the oldest historical entries.
            while (messages.size() >= config.contextLength) {
                messages.remove(0);
            }
            messages.add(new ChatMessage("user", currentUserMessage));
        } else {
            messages.add(new ChatMessage("user", currentUserMessage));
        }
        return messages;
    }

    /**
     * Called by the client when a player chat/game message is received.
     * senderObj may be null for GAME events.
     */
    public void onPlayerChatMessage(String text, Object senderObj) {
        if (text == null || text.isBlank()) return;
        if (config == null || !config.enabled) return;

        String userText = text.trim();

        // simple duplicate user message detection
        if (Objects.equals(userText, lastUserMessage)) {
            // already asked the same thing recently
            notifyClient("Skipped: repeated question detected.");
            return;
        }

        // check last AI response's last sentence before asking
        String lastSentence = extractLastSentence(lastAiResponse);
        if (lastSentence != null && !lastSentence.isBlank()) {
            if (containsNormalized(lastSentence, userText) || containsNormalized(userText, lastSentence)) {
                notifyClient("Skipped: I might repeat myself (last answer already addressed this).'");
                return;
            }
        }

        // build message list and send to provider
        List<ChatMessage> messages = buildContext(userText);
        String systemPrompt = config.systemPrompt == null ? "" : config.systemPrompt;

        CompletableFuture<String> future;
        try {
            future = provider.sendRequest(systemPrompt, messages, config);
        } catch (Exception e) {
            BeKongsAiChatMod.LOGGER.error("[AiChatMod] Failed to send AI request", e);
            notifyClient("AI request failed: " + e.getMessage());
            return;
        }

        // update lastUserMessage optimistically to avoid concurrent duplicate sends
        lastUserMessage = userText;

        future.thenAccept(response -> {
            if (response == null) response = "";
            // final check: avoid posting a response that repeats the user's last sentence
            String respLastSentence = extractLastSentence(response);
            if (respLastSentence != null && containsNormalized(respLastSentence, userText)) {
                notifyClient("AI detected it would repeat the last sentence — skipping posting the answer.");
                // still update lastAiResponse so future checks have context
                lastAiResponse = response;
                synchronized (contextLock) {
                    context.add(new ChatMessage("assistant", response));
                    trimContextIfNeeded();
                }
                return;
            }

            // post the AI response to client chat/UI
            postAiResponse(response);

            // update lastAiResponse and context
            lastAiResponse = response;
            synchronized (contextLock) {
                context.add(new ChatMessage("assistant", response));
                trimContextIfNeeded();
            }
        }).exceptionally(ex -> {
            BeKongsAiChatMod.LOGGER.error("[AiChatMod] AI request failed", ex);
            notifyClient("AI request failed: " + ex.getMessage());
            return null;
        });
    }

    private void trimContextIfNeeded() {
        if (!config.contextEnabled) return;
        int max = Math.max(1, config.contextLength);
        while (context.size() > max) {
            context.remove(0);
        }
    }

    private void postAiResponse(String response) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                client.player.sendSystemMessage(Component.literal("§b[AI] §f" + response));
            }
        } catch (Exception e) {
            BeKongsAiChatMod.LOGGER.error("[AiChatMod] Failed to post AI response to client", e);
        }
    }

    private void notifyClient(String msg) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                client.player.sendSystemMessage(Component.literal("§a[AiChatMod] §f" + msg));
            }
        } catch (Exception e) {
            BeKongsAiChatMod.LOGGER.error("[AiChatMod] Failed to notify client", e);
        }
    }

    private static String extractLastSentence(String text) {
        if (text == null || text.isBlank()) return null;
        // split by common sentence terminators (both English and CJK)
        String[] parts = text.trim().split("(?<=[\\.!?。！？])\\s*");
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i].trim();
            if (!p.isEmpty()) return p;
        }
        return null;
    }

    private static boolean containsNormalized(String a, String b) {
        if (a == null || b == null) return false;
        String na = normalize(a);
        String nb = normalize(b);
        return na.contains(nb) || nb.contains(na);
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
