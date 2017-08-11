package main.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    public final Requestor requestor;
    public final String username;
    
    public String nickname;
    
    public final List<Message> messages = new ArrayList<>();
    public final Map<ChatRoom, List<Integer>> chatUpdates = new HashMap<>();
    public final Map<User, List<Integer>> userUpdates = new HashMap<>();
    
    public User(Requestor requestor, String username, String nickname) {
        this.requestor = requestor;
        this.username = username;
        
        this.nickname = nickname;
    }
    
    public void addQueuedMessage(Message message) {
        messages.add(message);
    }
    
    public Message getAndRemoveMessage() {
        Message m = messages.get(0);
        messages.remove(0);
        return m;
    }
    
    public void addQueudChatUpdate(ChatRoom chat, int update) {
        if(!chatUpdates.containsKey(chat)) {
            chatUpdates.put(chat, new ArrayList<>());
        }
        
        chatUpdates.get(chat).add(update);
    }
    
    public Map.Entry<ChatRoom, List<Integer>> getAndRemoveChatUpdate() {
        Map.Entry<ChatRoom, List<Integer>> updates = chatUpdates.entrySet().iterator().next();
        chatUpdates.remove(updates.getKey());
        return updates;
    }
    
    public void addQueudUserUpdate(User user, int update) {
        if(!userUpdates.containsKey(user)) {
            userUpdates.put(user, new ArrayList<>());
        }
        
        userUpdates.get(user).add(update);
    }
    
    public Map.Entry<User, List<Integer>> getAndRemoveUserUpdate() {
        Map.Entry<User, List<Integer>> updates = userUpdates.entrySet().iterator().next();
        userUpdates.remove(updates.getKey());
        return updates;
    }
}
