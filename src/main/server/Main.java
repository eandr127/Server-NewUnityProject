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
    
    public static volatile boolean stop = false;
    
    public static void main(String[] args) throws InterruptedException {
        ZMQ.Context context = ZMQ.context(1);

        //  Socket to talk to clients
        ZMQ.Socket responder = context.socket(ZMQ.REP);
        responder.bind("tcp://*:8743");
        
        new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            String line = null;
            while((line = sc.nextLine()) != null) {
                if(line.trim().equalsIgnoreCase("stop")) {
                    stop = true;
                    sc.close();
                    responder.close();
                    System.setErr(null);
                    Thread.setDefaultUncaughtExceptionHandler((st, fu) -> {});
                    break;
                }
            }
        }).start();
        
        while (!Thread.currentThread().isInterrupted() && !stop) {
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
            if(message.toChat.isPresent()) {
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
