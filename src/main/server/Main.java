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
    
    public static final Thread MAIN = Thread.currentThread();
    
    public static void main(String[] args) throws InterruptedException {
        ZMQ.Context context = ZMQ.context(1);

        //  Socket to talk to clients
        ZMQ.Socket responder = context.socket(ZMQ.REP);
        responder.bind("tcp://*:8743");
        
        new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            String line = null;
            while((line = sc.nextLine()) != null) {
                if(line.trim().equalsIgnoreCase("/stop")) {
                    sc.close();
                    responder.close();
                    System.setErr(null);
                    Thread.setDefaultUncaughtExceptionHandler((st, fu) -> {});
                    MAIN.interrupt();
                    break;
                }
                else if(line.toLowerCase().trim().startsWith("/addchat")) {
                    String[] command = line.split(" ");
                    
                    int id = -1;
                    while(Main.hasChat(++id));
                    
                    ChatRoom chat = new ChatRoom(id, command[1]);
                    
                    System.out.println(chat.id + " " + chat.name);
                    
                    distributeChatUpdate(chat, Requestor.CHANGE_CONNECTED);
                    chats.add(chat);
                }
                else if(line.toLowerCase().trim().startsWith("/removechat")) {
                    String[] command = line.split(" ");
                    
                    List<ChatRoom> chats = new ArrayList<>();
                    try {
                        int id = Integer.parseInt(command[1]);
                        
                        try {
                            chats.add(getChat(id));
                        }
                        catch(NoSuchElementException e) {
                            System.out.println("Chat not id found");
                        }
                    }
                    catch(NumberFormatException e) {
                        for(ChatRoom chat : Main.chats) {
                            if(chat.name.equals(command[1])) {
                                chats.add(chat);
                            }
                        }
                    }
                    
                    for(ChatRoom chat : chats) {
                        distributeChatUpdate(chat, Requestor.CHANGE_DISCONNECTED);
                        Main.chats.remove(chat);
                    }
                }
            }
        }).start();
        
        while (!Thread.currentThread().isInterrupted()) {
            
            
            // Wait for next request from the client
            String[] paramaters = responder.recvStr().split("\\n");
            
            Requestor requestor = Requestor.findOrCreateRequestor(paramaters[0]);

            String reply = requestor.handleRequest(paramaters[1], Arrays.copyOfRange(paramaters, 2, paramaters.length));
            
            responder.send(reply.getBytes(), 0);
        }
        
        Requestor.stopAllTimers();
        
        responder.close();
        context.term();
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
        for(User user : users) {
            if(message.toChat.isPresent() && !message.from.username.equals(user.username)) {
                user.addQueuedMessage(message);
            }
            else if(message.toUser.isPresent() && message.toUser.get().username.equals(user.username)) {
                user.addQueuedMessage(message);
            }
        }
    }
    
    public static void distributeChatUpdate(ChatRoom chat, int update) {
        for(User user : users) {
            user.addQueudChatUpdate(chat, update);
        }
    }
    
    public static void distributeUserUpdate(User user, int update) {
        for(User onlineUser : users) {
            onlineUser.addQueudUserUpdate(user, update);
        }
    }
}
