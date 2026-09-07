[PATCH]
*** Begin Patch
*** Update File: src/client/java/com/be_kongs_ai_chat_mod/chat/AiChatManager.java
@@
     private List<ChatMessage> buildContext(String currentUserMessage) {
         List<ChatMessage> messages = new ArrayList<>();
         if (config.contextEnabled && config.contextLength > 0) {
-            synchronized (context) { messages.addAll(context); }
-            if (messages.isEmpty()) messages.add(new ChatMessage("user", currentUserMessage));
-            while (messages.size() > config.contextLength) messages.remove(0);
+            synchronized (context) { messages.addAll(context); }
+            // Ensure the current user message is always included as the last message.
+            // Trim oldest entries so the total number of messages does not exceed contextLength.
+            while (messages.size() >= config.contextLength) messages.remove(0);
+            messages.add(new ChatMessage("user", currentUserMessage));
         } else {
             messages.add(new ChatMessage("user", currentUserMessage));
         }
         return messages;
     }
*** End Patch
