package com.chat;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

public class Peer {
    public static final String ROOMID_PATTERN = "^[a-zA-Z][a-zA-Z0-9]{2,31}";

    private final String ip;
    private final int port;
    private int connPort=0;
    private Socket socket;
    private Map<String, Room> roomList = new WeakHashMap<>();


    public Peer(String ip, int port) {
        this.ip = ip;
        this.port = port;

        try {
            socket = new Socket(ip, port);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Room> getRoomList() {
        return roomList;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    } //listening port

    public Socket getSocket() {
        return socket;
    }

    public int getConnPort(){ //connecting port
        if (connPort==0){
            return port;
        }else{
            return connPort;
        }
    }

    public void setConnPort(int connPort){this.connPort= connPort;}


    public boolean valid(String regex, String string) {
        if (string == null) {
            return false;
        }
        return Pattern.matches(regex, string);
    }


    //for local commands
    public void createRoom(String roomId) {
        if (validNewRoomId(roomId)) {
            final Room room = new Room(roomId);
            synchronized (roomList) {
                roomList.put(roomId, room);
                System.out.println("Room " + room.roomId + " is created");
            }
        }
        else{
            System.out.println("Room "+ roomId +" is invalid or already in use.");
        }
    }

    public void deleteRoom(String roomId) {
        synchronized (getRoomList()) {
            if (getRoomList().size() > 0) {
                if (validRoom(roomId)) {
                    roomList.remove(roomId);
                    System.out.println("Room " + roomId + " is successfully deleted");
                }
            } else {
                System.out.println("Room deletion failed. The requested room is non existent.");
            }
        }
    }

    public void whoInRoom(String roomId) {
        if (validRoom(roomId)) {
            Room room = roomList.get(roomId);
            System.out.println("Room "+ roomId+ " contains: "+room.getIdentities().toString());
        }
    }

    public void getList() {
        for (String roomid: roomList.keySet()){
            System.out.println(roomid+ " : "+ roomList.get(roomid).getConnectionList().size() + " guests");
        }
    }


    //helper methods
    public boolean validNewRoomId(String roomId) {
        return valid(ROOMID_PATTERN, roomId) && !roomList.containsKey(roomId);
    }
    public boolean validRoom(String roomId) {
        return roomId != null && roomList.containsKey(roomId);
    }


    public class Room {
        private final String roomId;
        private final List<Peer> connectionList = new ArrayList<>();

        public Room(String roomId) {
            this.roomId = roomId;
        }

        public String getRoomId(){
            return roomId;
        }

        public List<Peer> getConnectionList(){
            return connectionList;
        }

        public ArrayList<String> getIdentities(){
            ArrayList<String> identities= new ArrayList();
            for (Peer peer: getConnectionList()) {
                identities.add(peer.getIp()+":"+peer.getConnPort());
            }
            return identities;
        }

        public void removeClient(Peer client){
            synchronized(getConnectionList()){
                getConnectionList().remove(client);
            }
        }

        public void addClient(Peer client){
            synchronized(getConnectionList()){
                getConnectionList().add(client);
            }
        }
    }


}
