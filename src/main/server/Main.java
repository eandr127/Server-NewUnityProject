package main.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import org.zeromq.ZMQ;

public class Main {

    public static List<User> users = new ArrayList<>();
    public static List<ChatRoom> chats = new ArrayList<>();
    
    // These are volatile because they are accessed on different threads
    public static volatile boolean stop = false;
    public static volatile long stopTime;
    
    public static void main(String[] args) throws InterruptedException {
        ZMQ.Context context = ZMQ.context(1);

        //  Socket to talk to clients
        ZMQ.Socket responder = context.socket(ZMQ.REP);
        responder.bind("tcp://*:8743");
        
        new Thread(() -> {
            // Console command viewer
            Scanner sc = new Scanner(System.in);
            String line = null;
            while((line = sc.nextLine()) != null) {
                // Wait 5 seconds to close so clients will get RESULT_NOT_LOGGED_IN and return to login screen
                if(line.trim().equalsIgnoreCase("/stop")) {
                    sc.close();
                    stop = true;
                    stopTime = System.currentTimeMillis();
                    System.out.println("Shutting down in 5 seconds");
                    break;
                }
                else if(line.toLowerCase().trim().startsWith("/addchat")) {
                    String[] command = line.split(" ");
                    
                    // Start from 0 and continue to increment until free id is found
                    int id = -1;
                    while(Main.hasChat(++id));
                    
                    ChatRoom chat = new ChatRoom(id, command[1]);
                    
                    System.out.println(chat.id + " " + chat.name);
                    
                    // Add chat to server, and announce change to server
                    distributeChatUpdate(chat, Requestor.CHANGE_CONNECTED);
                    chats.add(chat);
                }
                else if(line.toLowerCase().trim().startsWith("/removechat")) {
                    String[] command = line.split(" ");
                    
                    List<ChatRoom> chats = new ArrayList<>();
                    try {
                        // Remove single chat by ID
                        int id = Integer.parseInt(command[1]);
                        
                        try {
                            chats.add(getChat(id));
                        }
                        catch(NoSuchElementException e) {
                            System.out.println("Chat not id found");
                        }
                    }
                    catch(NumberFormatException e) {
                        // If no ID was given, remove all by name
                        for(ChatRoom chat : Main.chats) {
                            if(chat.name.equals(command[1])) {
                                chats.add(chat);
                            }
                        }
                    }
                    
                    // Remove chat and distribute update
                    for(ChatRoom chat : chats) {
                        distributeChatUpdate(chat, Requestor.CHANGE_DISCONNECTED);
                        Main.chats.remove(chat);
                    }
                }
                else if(line.toLowerCase().trim().startsWith("/removeuser") ) {
                    String[] command = line.split(" ");
                    
                    if(!hasUser(command[1])) {
                        System.out.println("User not found");
                    }
                    
                    // Get user from username
                    User user = getUser(command[1]);
                    
                    // Remove user and distribute update
                    Main.distributeUserUpdate(user, Requestor.CHANGE_DISCONNECTED);
                    Main.users.remove(user);
                    user.requestor.removeUser();
                }
            }
        }).start();
        
        // Stop on thread interrupt or when the server has been stopping for more than 5 seconds
        while (!Thread.currentThread().isInterrupted() && (!stop || System.currentTimeMillis() - stopTime < 5000)) {
            
            // Don't block to allow server to stop if necessary
            String request = responder.recvStr(ZMQ.NOBLOCK);
            
            // Loop until a request was received
            if(request != null) {
                // Tell the requester that the server is stopping so that it can log out
                if(stop) {
                    responder.send(String.valueOf(Requestor.RESULT_COULD_NOT_CONNECT));
                }
                else {
                    // Separate each part of the request by newlines
                    String[] paramaters = request.split("\\n");
                    
                    // Get information for requester, or create it if it doesn't exist
                    Requestor requestor = Requestor.findOrCreateRequestor(paramaters[0]);
                    
                    // With the information about the requester, parse the request making a reply to send back to the requester
                    String reply = requestor.handleRequest(paramaters[1], Arrays.copyOfRange(paramaters, 2, paramaters.length));
                    
                    // Send the data
                    responder.send(reply.getBytes(), 0);
                }
            }
        }
        
        // Stop timeout timers so program can exit
        Requestor.stopAllTimers();
        
        // Close ZeroQM server
        responder.close();
        context.term();
        
        // In case of occasional lingering thread, exit program
        System.exit(0);
    }
    
    public static boolean hasUser(String username) {
        for(User user : users) {
            if(user.username.equals(username)) {
                return true;
            }
        }
        
        return false;
    }
    
    public static boolean hasChat(int chatId) {
        for(ChatRoom chat : chats) {
            if(chat.id == chatId) {
                return true;
            }
        }
        
        return false;
    }
    
    
    public static ChatRoom getChat(int id) throws NoSuchElementException {
        for(ChatRoom chat : chats) {
            if(chat.id == id) {
                return chat;
            }
        }
        
        throw new NoSuchElementException();
    }
    
    public static User getUser(String username) throws NoSuchElementException {
        for(User user : users) {
            if(user.username.equals(username)) {
                return user;
            }
        }
        
        throw new NoSuchElementException();
    }
    
    public static void distributeNewMessage(Message message) {
        // Distribute chat messages to correct users
        for(User user : users) {
            // Send new message to everyone except sender if in chat
            if(message.toChat.isPresent() && !message.from.username.equals(user.username)) {
                user.addQueuedMessage(message);
            }
            // Send message to user addressed if not in chat
            else if(message.toUser.isPresent() && message.toUser.get().username.equals(user.username)) {
                user.addQueuedMessage(message);
            }
        }
    }
    
    public static void distributeChatUpdate(ChatRoom chat, int update) {
        // Add updates to users so the update will not be removed from each user until they are given it
        for(User user : users) {
            user.addQueudChatUpdate(chat, update);
        }
    }
    
    public static void distributeUserUpdate(User user, int update) {
        // Add updates to users so the update will not be removed from each user until they are given it
        for(User onlineUser : users) {
            onlineUser.addQueudUserUpdate(user, update);
        }
    }
}
