public class AiChatManager {
    private final List<ChatMessage> context = new ArrayList<>();
    private final AiChatConfig config;

    private List<ChatMessage> buildContext(String currentUserMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        if (config.contextEnabled && config.contextLength > 0) {
            synchronized (context) {
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
}
