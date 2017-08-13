package main.server;

import java.util.Optional;

public class Message {

    public final User from;
  /* Only one of these will be present at a time
     because you can't send to one user and a chat
     at the same time */
    public final Optional<User> toUser;
    public final Optional<ChatRoom> toChat;
    public final String message;
    public final String date;
    
    public Message(User from, ChatRoom to, String message, String date) {
        this(from, Optional.of(to), Optional.empty(), message, date);
    }
    
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
