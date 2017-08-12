package main.server;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

import javax.imageio.ImageIO;

public class User {
    public final Requestor requestor;
    public final String username;
    
    public String nickname;
    public BufferedImage image;
    
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
    
    public static String encodeToString(BufferedImage image) throws IOException {
        Encoder encoder = Base64.getEncoder();
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytesOut);
        byte[] bytes = bytesOut.toByteArray();
        bytesOut.close();
        
        return encoder.encodeToString(bytes);
    }
    
    public static BufferedImage decodeImage(String imageString) throws IOException {
        BufferedImage image = null;
        byte[] imageData;
        Decoder decoder = Base64.getDecoder();
        imageData = decoder.decode(imageString);
        ByteArrayInputStream bytesIn = new ByteArrayInputStream(imageData);
        image = ImageIO.read(bytesIn);
        bytesIn.close();
        
        return image;
    }
}
