package main.server;

/**
 * A global group chat that all users are a part of
 */
public class ChatRoom {
    
    /**
     * Identifies the server, may be different than the index in {@code Main.chats}.
     * Each id is unique to the ChatRoom
     */
    public final int id;
    
    /**
     * The name of the chat. Multiple chats can have the same name.
     */
    public final String name;
    
    /**
     * Constructs the group chat
     * 
     * @param id The unique ID of the chatroom
     * @param name The name of the chatroom
     */
    public ChatRoom(int id, String name) {
        this.id = id;
        this.name = name;
    }
}
