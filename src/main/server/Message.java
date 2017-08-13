package main.server;

import java.util.Optional;

/**
 * Holds data for messages send from users to chats or other users
 */
public class Message {

    /**
     * The sender of the message
     */
    public final User from;

    /**
     * The addressed user. This may be empty if the message was
     * addressed to a chat.
     */
    public final Optional<User> toUser;
    
    /**
     * The addressed chat. This may be empty if the message was
     * addressed to a user.
     */
    public final Optional<ChatRoom> toChat;
    
    /**
     * The contents of the message
     */
    public final String message;
    
    /**
     * The {@code DateTimeFormatter.ZONED_ISO_DATE_TIME} formatted date/time at UTC
     */
    public final String date;
    
    /**
     * Constructs a message object to send to a chat
     * 
     * @param from The user that sent the message
     * @param to The chatroom that the user sent the message to
     * @param message The contents of the message
     * @param date The {@code DateTimeFormatter.ZONED_ISO_DATE_TIME} formatted date/time at UTC
     */
    public Message(User from, ChatRoom to, String message, String date) {
        this(from, Optional.of(to), Optional.empty(), message, date);
    }
    
    /**
     * Constructs a message object to send to a user
     * 
     * @param from The user that sent the message
     * @param to The user that the user sent the message to
     * @param message The contents of the message
     * @param date The {@code DateTimeFormatter.ZONED_ISO_DATE_TIME} formatted date/time at UTC
     */
    public Message(User from, User to, String message, String date) {
        this(from, Optional.empty(), Optional.of(to), message, date);
    }
    
    private Message(User from, Optional<ChatRoom> toChat, Optional<User> toUser, String message, String date) {
        this.from = from;
        this.toUser = toUser;
        this.toChat = toChat;
        this.message = message;
        this.date = date;
    }
}
