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

/**
 * Holds data for each user, including queued messages and updates
 */
public class User {
    
    /**
     * The requester that logged in as this user
     */
    public final Requestor requestor;
    
    /**
     * The unique username of the user
     */
    public final String username;

    /**
     * The nickname of the user
     */
    public String nickname;
    
    /**
     * The user's profile picture
     */
    public BufferedImage image;
    
    // Updates to return when requested
    /**
     * The messages that the user has received but the client has not taken
     */
    public final List<Message> messages = new ArrayList<>();
    
    /**
     * The chat updates that the user has received but the client has not taken.
     * The int is a constant in {@code Requester} that starts with CHANGE_*
     */
    public final Map<ChatRoom, List<Integer>> chatUpdates = new HashMap<>();
    
    /**
     * The user updates that the user has received but the client has not taken.
     * The int is a constant in {@code Requester} that starts with CHANGE_*
     */
    public final Map<User, List<Integer>> userUpdates = new HashMap<>();
    
    /**
     * Creates a new user object
     * 
     * @param requestor The requester creating the user
     * @param username The unique username of the user
     * @param nickname The nickname of the user
     */
    public User(Requestor requestor, String username, String nickname) {
        this.requestor = requestor;
        this.username = username;
        
        this.nickname = nickname;
    }
    
    /**
     * Queues a message to be given to requester when requested
     * 
     * @param message The message to queue
     */
    public void addQueuedMessage(Message message) {
        messages.add(message);
    }
    
    /**
     * Gets the message to send to the client,
     * then removes it from the queue
     * 
     * @return The message to send
     */
    public Message getAndRemoveMessage() {
        // Remove the message so the requester doesn't get it twice
        Message m = messages.get(0);
        messages.remove(0);
        return m;
    }
    
    /**
     * Queues a chat update to be given to requester when requested
     * 
     * @param chat The chat to queue
     * @param update The update to the chat
     */
    public void addQueudChatUpdate(ChatRoom chat, int update) {
        // Add update to queue for requester to request
        if(!chatUpdates.containsKey(chat)) {
            chatUpdates.put(chat, new ArrayList<>());
        }
        
        chatUpdates.get(chat).add(update);
    }
    
    /**
     * Gets the chat update to send to the client,
     * then removes it from the queue
     * 
     * @return The chat update to send
     */
    public Map.Entry<ChatRoom, List<Integer>> getAndRemoveChatUpdate() {
        // Remove the chat update so the requester doesn't get it twice
        Map.Entry<ChatRoom, List<Integer>> updates = chatUpdates.entrySet().iterator().next();
        chatUpdates.remove(updates.getKey());
        return updates;
    }
    
    /**
     * Queues a user update to be given to requester when requested
     * 
     * @param user The user to queue
     * @param update The update to the user
     */
    public void addQueudUserUpdate(User user, int update) {
        // Add update to queue for requester to request
        if(!userUpdates.containsKey(user)) {
            userUpdates.put(user, new ArrayList<>());
        }
        
        userUpdates.get(user).add(update);
    }
    
    /**
     * Gets the user update to send to the client,
     * then removes it from the queue
     * 
     * @return The user update to send
     */
    public Map.Entry<User, List<Integer>> getAndRemoveUserUpdate() {
        // Remove the user update so the requester doesn't get it twice
        Map.Entry<User, List<Integer>> updates = userUpdates.entrySet().iterator().next();
        userUpdates.remove(updates.getKey());
        return updates;
    }
    
    /**
     * Encodes an image to a Base64 String to send over the network
     * 
     * @param image The image to encode
     * @return The Base64 encoded String
     * 
     * @throws IOException If the image couldn't be encoded properly
     */
    public static String encodeToString(BufferedImage image) throws IOException {
        // Turns an image into a String Base64 that can be send over the network and read back into an image
        Encoder encoder = Base64.getEncoder();
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytesOut);
        byte[] bytes = bytesOut.toByteArray();
        bytesOut.close();
        
        return encoder.encodeToString(bytes);
    }
    
    /**
     * Decodes an image from a Base64 String received over the network
     * 
     * @param imageString The String to decode
     * @return The decoded image
     * 
     * @throws IOException If the image couldn't be decoded properly
     */
    public static BufferedImage decodeImage(String imageString) throws IOException {
        // Turns a Base64 String received over the network to an image
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
