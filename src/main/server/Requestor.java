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
    public static final int CHANGE_CONNECTED = 1;
    public static final int CHANGE_DISCONNECTED = 2;
    public static final int CHANGE_CHANGED_NICKNAME = 3;
    public static final int CHANGE_CHANGED_PICTURE = 4;
    
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
    
    
    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_COULD_NOT_CONNECT = -1;
    public static final int RESULT_USERNAME_TAKEN = -2;
    public static final int RESULT_UNKNOWN_USERNAME = -3;
    public static final int RESULT_NOT_LOGGED_IN = -4;
    public static final int RESULT_ALREADY_LOGGED_IN = -5;
    public static final int RESULT_UNKNOWN_CHAT = -6;
    public static final int RESULT_BAD_REQUEST = -7;
    public static final int RESULT_FAILURE_UNKNOWN = -8;
    
    public static final int TIMEOUT = 30000;
    
    private static final Set<Requestor> requestors = new HashSet<>();
    
    public static Requestor findOrCreateRequestor(String name) {
        Requestor requestor = null;
        for(Requestor r : requestors) {
            if(r.worker.equals(name)) {
                requestor = r;
                break;
            }
        }
        
        if(requestor == null) {
            requestor = new Requestor(name);
            requestors.add(requestor);
        }
        
        return requestor;
    }
    
    public static void stopAllTimers() {
        for(Requestor requestor : requestors) {
            if(!requestor.timer.isShutdown()) {
                requestor.timer.shutdownNow();
            }
        }
    }
    
    private User user;
    public final String worker;
    private ScheduledExecutorService  timer;
    
    private Requestor(String worker) {
        this.worker = worker;
        timer = Executors.newSingleThreadScheduledExecutor();
        timer.schedule(this::kickUser, TIMEOUT, TimeUnit.MILLISECONDS);
    }
    
    private void kickUser() {
        requestors.remove(this);
        
        if(user != null) {
            Main.users.remove(user);
            Main.distributeUserUpdate(user, CHANGE_DISCONNECTED);
        }
    }
    
    public boolean checkLoggedIn() {
        return user != null;
    }
    
    public String handleRequest(String requestLine, String[] arguments) {
        int requestId = -1;
        
        try {
            requestId = Integer.parseInt(requestLine);
        }
        catch(NumberFormatException e) {
            return String.valueOf(RESULT_BAD_REQUEST);
        }
        
        switch(requestId) {
            case REQUEST_LOGIN: {
                if(arguments.length != 2) {
                    return String.valueOf(RESULT_BAD_REQUEST);
                }
                if(Main.hasUser(arguments[0])) {
                    return String.valueOf(RESULT_USERNAME_TAKEN);
                }
                else {
                    user = new User(this, arguments[0], arguments[1]);
                    Main.distributeUserUpdate(user, CHANGE_CONNECTED);
                    Main.users.add(user);
                    return String.valueOf(RESULT_SUCCESS);
                }
            }
            case REQUEST_CHATS_ONLINE: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                else {
                    return String.valueOf(RESULT_SUCCESS) + "\n"
                         + String.valueOf(Main.chats.size()); 
                }
            }
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
            case REQUEST_USERS_ONLINE: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                else {
                    return String.valueOf(RESULT_SUCCESS) + "\n"
                         + String.valueOf(Main.users.size()); 
                }
            }
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
            case REQUEST_KEEP_ALIVE: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                
                timer.shutdownNow();
                timer = Executors.newSingleThreadScheduledExecutor();
                timer.schedule(this::kickUser, TIMEOUT, TimeUnit.MILLISECONDS);
                return String.valueOf(RESULT_SUCCESS);
            }
            case REQUEST_LOGOUT: {
                if(!checkLoggedIn()) {
                    return String.valueOf(RESULT_NOT_LOGGED_IN);
                }
                
                Main.distributeUserUpdate(user, CHANGE_DISCONNECTED);
                Main.users.remove(user);
                user = null;
                
                return String.valueOf(RESULT_SUCCESS);
            }
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
            default: {
                return String.valueOf(RESULT_FAILURE_UNKNOWN);
            }
        }
    }
}
