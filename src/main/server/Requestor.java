package main.server;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Requestor {
    // All update codes for users and/or chats
    public static final int CHANGE_CONNECTED = 1;
    public static final int CHANGE_DISCONNECTED = 2;
    public static final int CHANGE_CHANGED_NICKNAME = 3;
    public static final int CHANGE_CHANGED_PICTURE = 4;
    
    // All request codes recieved from clients so we know what they want us to do
    // These also all return the result code for the Requestor
    
    // String username, string nickname -> void
    public static final int REQUEST_LOGIN = 0;
    
    // () -> int chats
    public static final int REQUEST_CHATS_ONLINE = 1;
    
    // int chatIDX -> int chatID
    public static final int REQUEST_CHAT = 2;
    
    // int chatID -> String chatName
    public static final int REQUEST_CHAT_NAME = 3;
    
    // int chatID -> encoded int updates (in binary could be: 000, 001, 010, 011, 100, 101, 110, 111)
    public static final int REQUEST_CHAT_UPDATES = 4;
    
    // () -> int chats
    public static final int REQUEST_USERS_ONLINE = 5;
    
    // int userIDX -> string username
    public static final int REQUEST_USER = 6;
    
    // String username -> String nickname
    public static final int REQUEST_USER_NICKNAME = 7;
    
    // String username -> encoded int updates (in binary could be: 000, 001, 010, 011, 100, 101, 110, 111)
    public static final int REQUEST_USER_UPDATES = 8;
    
    // () -> IntString/String chatID/username, String message, String utcTime
    public static final int REQUEST_NEW_MESSAGE = 9;
    
    // IntString/String chatID/username, String message, String utcTime -> boolean success
    public static final int REQUEST_SEND_MESSAGE = 10;
    
    // () -> void
    public static final int REQUEST_LOGOUT = 11;
    
    // () -> void
    public static final int REQUEST_KEEP_ALIVE = 12;
    
    // String nickname -> void
    public static final int REQUEST_SET_NICKNAME = 13;
    
    // String username -> imagedatastream
    public static final int REQUEST_USER_PICTURE = 14;
    
    // String imagedatastream -> void
    public static final int REQUEST_SET_USER_PICTURE = 15;
    
    // String chatroom name -> chat id
    public static final int REQUEST_CREATE_CHAT_ROOM = 16;
    
    
    // Result codes tell the client what happened with the request
    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_COULD_NOT_CONNECT = -1;
    public static final int RESULT_USERNAME_TAKEN = -2;
    public static final int RESULT_UNKNOWN_USERNAME = -3;
    public static final int RESULT_NOT_LOGGED_IN = -4;
    public static final int RESULT_ALREADY_LOGGED_IN = -5;
    public static final int RESULT_UNKNOWN_CHAT = -6;
    public static final int RESULT_BAD_REQUEST = -7;
    public static final int RESULT_FAILURE_UNKNOWN = -8;
    
    // Timeout before kicking requester
    // This timer is reset every client main loop so this should be a fair time
    public static final int TIMEOUT = 30000;
    
    private static final Set<Requestor> requestors = new HashSet<>();
    
    public static Requestor findOrCreateRequestor(String name) {
        // Look for requester with same UUID
        Requestor requestor = null;
        for(Requestor r : requestors) {
            if(r.worker.equals(name)) {
                requestor = r;
                break;
            }
        }
        
        // If a requester couldn't be found, create one
        if(requestor == null) {
            requestor = new Requestor(name);
            requestors.add(requestor);
        }
        
        return requestor;
    }
    
    public static void stopAllTimers() {
        // Shut down all times so server can exit
        for(Requestor requestor : requestors) {
            if(!requestor.timer.isShutdown()) {
                requestor.timer.shutdownNow();
            }
        }
    }
    
    private User user;
    public final String worker;
    private ScheduledExecutorService timer;
    
    private Requestor(String worker) {
        this.worker = worker;
        timer = Executors.newSingleThreadScheduledExecutor();
        timer.schedule(this::kickUser, TIMEOUT, TimeUnit.MILLISECONDS);
    }
    
    private void kickUser() {
        // Log user out if not already, and remove requester information about user
        // This means a requester won't hang around until the server closes
        requestors.remove(this);
        
        if(user != null) {
            // Kick user
            Main.users.remove(user);
            Main.distributeUserUpdate(user, CHANGE_DISCONNECTED);
        }
    }
    
    public boolean checkLoggedIn() {
        return user != null;
    }
    
    public void removeUser() {
        user = null;
    }
    
    public String handleRequest(String requestLine, String[] arguments) {
        int requestId = -1;
        
        // Find out what user is looking for
        try {
            requestId = Integer.parseInt(requestLine);
        }
        catch(NumberFormatException e) {
            return String.valueOf(RESULT_BAD_REQUEST);
        }
        
        // Perform correct task and return correct response based on the type of request
        switch(requestId) {
            // Make a user with a username and nickname to add to the server
            // This also allows most other tasks to be used that could before
            case REQUEST_LOGIN: {
                if(arguments.length != 2) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                if(Main.hasUser(arguments[0])) {
                    return String.valueOf(RESULT_USERNAME_TAKEN);
                }
                else {
                    // Create new user 
                    user = new User(this, arguments[0], arguments[1]);
                    Main.distributeUserUpdate(user, CHANGE_CONNECTED);
                    Main.users.add(user);
                    return String.valueOf(RESULT_SUCCESS);
                }
            }
            // Returns the number of chats so that they can be looped through
            case REQUEST_CHATS_ONLINE: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                else {
                    return String.valueOf(RESULT_SUCCESS) + "\n"
                         + String.valueOf(Main.chats.size()); 
                }
            }
            // Requests the ID of a chat by the index
            case REQUEST_CHAT: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                if(arguments.length != 1) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                int idx = -1;
                try {
                    idx = Integer.parseInt(arguments[0]);
                    return String.valueOf(RESULT_SUCCESS) + "\n"
                         + String.valueOf(Main.chats.get(idx).id);
                }
                catch(NumberFormatException | IndexOutOfBoundsException e) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
            }
            // Requests the name of a chat by ID
            case REQUEST_CHAT_NAME: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                if(arguments.length != 1) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                int id = -1;
                try {
                    id = Integer.parseInt(arguments[0]);
                    return String.valueOf(RESULT_SUCCESS) + "\n"
                         + String.valueOf(Main.getChat(id).name);
                }
                catch(NumberFormatException e) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                catch(NoSuchElementException e) {
                    return String.valueOf(RESULT_UNKNOWN_CHAT);
                }
            }
            // Sends all chat updates for single chat
            // This is meant to be called until it returns no updates
            case REQUEST_CHAT_UPDATES: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                
                try {
                    Entry<ChatRoom, List<Integer>> updates = user.getAndRemoveChatUpdate();
                    String updateLine = "";
                    for(int update : updates.getValue()) {
                        updateLine += update + ",";
                    }
                    updateLine = updateLine.substring(0, updateLine.length() - 1);
                    
                    return String.valueOf(RESULT_SUCCESS) + "\n"
                         + updates.getKey().id + "\n"
                         + updateLine;
                }
                catch(NoSuchElementException e) {
                    return String.valueOf(RESULT_SUCCESS);
                }
                
            }
            // Returns the number of users so that they can be looped through
            case REQUEST_USERS_ONLINE: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                else {
                    return String.valueOf(RESULT_SUCCESS) + "\n"
                         + String.valueOf(Main.users.size()); 
                }
            }
            // Requests the username of a user by the index
            case REQUEST_USER: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                if(arguments.length != 1) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                int idx = -1;
                try {
                    idx = Integer.parseInt(arguments[0]);
                    return String.valueOf(RESULT_SUCCESS) + "\n"
                         + String.valueOf(Main.users.get(idx).username);
                }
                catch(NumberFormatException | IndexOutOfBoundsException e) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
            }
            // Requests the nickname of a user by username
            case REQUEST_USER_NICKNAME: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                if(arguments.length != 1) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                
                try {
                    return String.valueOf(RESULT_SUCCESS) + "\n"
                         + String.valueOf(Main.getUser(arguments[0]).nickname);
                }
                catch(NumberFormatException e) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                catch(NoSuchElementException e) {
                    return String.valueOf(RESULT_UNKNOWN_USERNAME);
                }
            }
            // Sends all user updates for user chat
            // This is meant to be called until it returns no updates
            case REQUEST_USER_UPDATES: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                
                try {
                    Entry<User, List<Integer>> updates = user.getAndRemoveUserUpdate();
                    String updateLine = "";
                    for(int update : updates.getValue()) {
                        updateLine += update + ",";
                    }
                    updateLine = updateLine.substring(0, updateLine.length() - 1);
                    
                    return String.valueOf(RESULT_SUCCESS) + "\n"
                         + updates.getKey().username + "\n"
                         + updateLine;
                }
                catch(NoSuchElementException e) {
                    return String.valueOf(RESULT_SUCCESS);
                }
                
            }
            // Sends one new unread message
            // This is meant to be called until there are no new messages
            case REQUEST_NEW_MESSAGE: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                
                try {
                    Message message = user.getAndRemoveMessage();
                    
                    if(message.toChat.isPresent()) {
                        return String.valueOf(RESULT_SUCCESS) + "\n"
                             + message.from.username + "\n"
                             + String.valueOf(false) + "\n"
                             + message.toChat.get().id + "\n"
                             + message.message + "\n"
                             + message.date;
                    }
                    else if(message.toUser.isPresent()) {
                        return String.valueOf(RESULT_SUCCESS) + "\n"
                             + message.from.username + "\n"
                             + String.valueOf(true) + "\n"
                             + message.toUser.get().username + "\n"
                             + message.message + "\n"
                             + message.date;
                    }

                }
                catch(IndexOutOfBoundsException e) {
                    return String.valueOf(RESULT_SUCCESS);
                }   
            }
            // Sends a message to a user
            case REQUEST_SEND_MESSAGE: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                if(arguments.length != 4) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                
                Message message = null;
                
                // Chat
                if(!Boolean.parseBoolean(arguments[0])) {
                    int chatId = -1;
                    try {
                        chatId = Integer.parseInt(arguments[1]);
                    }
                    catch(NumberFormatException e) {
                        return String.valueOf(RESULT_BAD_REQUEST);
                    }
                    
                    try {
                        ZonedDateTime.parse(arguments[3]);
                    }
                    catch(DateTimeParseException e) {
                        return String.valueOf(RESULT_BAD_REQUEST);
                    }
                    
                    message = new Message(this.user, Main.getChat(chatId), arguments[2], arguments[3]);
                    
                    Main.distributeNewMessage(message);
                }
                // User
                else {
                    String username = arguments[1];
                    
                    try {
                        ZonedDateTime.parse(arguments[3]);
                    }
                    catch(DateTimeParseException e) {
                        return String.valueOf(RESULT_BAD_REQUEST);
                    }
                    
                    User user = null;
                    
                    try {
                         user = Main.getUser(username);
                    }
                    catch(NoSuchElementException e) {
                        return String.valueOf(RESULT_UNKNOWN_USERNAME);
                    }
                    
                    message = new Message(this.user, user, arguments[2], arguments[3]);
                    
                    Main.distributeNewMessage(message);
                }
                
                return String.valueOf(RESULT_SUCCESS);
            }
            // Changes the nickname of a user
            case REQUEST_SET_NICKNAME: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                if(arguments.length != 1) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                
                user.nickname = arguments[0];
                Main.distributeUserUpdate(user, CHANGE_CHANGED_NICKNAME);
            }
            // Resets the timeout timer
            // This is meant to be called every client main loop
            case REQUEST_KEEP_ALIVE: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                
                timer.shutdownNow();
                timer = Executors.newSingleThreadScheduledExecutor();
                timer.schedule(this::kickUser, TIMEOUT, TimeUnit.MILLISECONDS);
                return String.valueOf(RESULT_SUCCESS);
            }
            // Logs the user out of the server
            case REQUEST_LOGOUT: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                
                Main.distributeUserUpdate(user, CHANGE_DISCONNECTED);
                Main.users.remove(user);
                user = null;
                
                return String.valueOf(RESULT_SUCCESS);
            }
            // Sends a Base64 string of the requested user's profile picture
            // If there is no image, it will set a boolean to false and not return an image
            case REQUEST_USER_PICTURE: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                if(arguments.length != 1) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                
                User user = null;
                
                try {
                    user = Main.getUser(arguments[0]);
                }
                catch(NoSuchElementException e) {
                    return String.valueOf(RESULT_UNKNOWN_USERNAME);
                }
                
                try {
                    if(user.image != null) {
                        return String.valueOf(RESULT_SUCCESS) + "\n"
                             + String.valueOf(true) + "\n"
                             + User.encodeToString(user.image);
                    }
                    else {
                        return String.valueOf(RESULT_SUCCESS) + "\n"
                             + String.valueOf(false);
                    }
                }
                catch(IOException e) {
                    return String.valueOf(RESULT_FAILURE_UNKNOWN);
                }
            }
            // Sets the user's profile picture with a Base64 string that is decoded into an image
            // If a boolean is set to false, it will also remove the current profile picture
            case REQUEST_SET_USER_PICTURE: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                if(arguments.length != 2) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                
                
                boolean hasImage = Boolean.parseBoolean(arguments[0]);
                
                if(hasImage) {
                    try {
                        BufferedImage image = User.decodeImage(arguments[1]);
                        user.image = image;
                        Main.distributeUserUpdate(user, CHANGE_CHANGED_PICTURE);
                        return String.valueOf(RESULT_SUCCESS);
                    }
                    catch(IOException e) {
                        return String.valueOf(RESULT_BAD_REQUEST);
                    }
                }
                
                try {
                    if(user.image != null) {
                        return String.valueOf(RESULT_SUCCESS) + "\n"
                             + String.valueOf(true) + "\n"
                             + User.encodeToString(user.image);
                    }
                    else {
                        return String.valueOf(RESULT_SUCCESS) + "\n"
                             + String.valueOf(false);
                    }
                }
                catch(IOException e) {
                    return String.valueOf(RESULT_FAILURE_UNKNOWN);
                }
            }
            // Creates a chatroom with a given name
            case REQUEST_CREATE_CHAT_ROOM: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                if(arguments.length != 1) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                
                int id = -1;
                while(Main.hasChat(++id));
                
                ChatRoom chat = new ChatRoom(id, arguments[0]);
                Main.distributeChatUpdate(chat, CHANGE_CONNECTED);
                Main.chats.add(chat);
                return String.valueOf(RESULT_SUCCESS) + "\n"
                     + String.valueOf(id);
            }
            // This could only really be caused by an out of date server
            default: {
                return String.valueOf(RESULT_FAILURE_UNKNOWN);
            }
        }
    }
}
