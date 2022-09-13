package com.chat;
import com.google.gson.Gson;
import com.chat.Peer.*;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.Argument;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public class Chatpeer {
    private final String myIP;
    private ServerSocket serverSocket;
    private Peer myPeer;

    private Integer listenPort;
    private Integer connectPort;

    @Option(name = "-p", usage = "Set Listening Port")
    public static int PORT = 4444;
    @Option(name = "-i", usage = "Set Connection Port")
    public static int CONNECTIONPORT = 55555;

    private boolean alive = false;
    private static final Gson gson = new Gson();
    private String roomID; //my current roomID

    private FileTransmission fileTransmission;

    BufferedReader userInput = null;
    private Map<Peer, PrintWriter> peerOutputMap;
    private List<String> blockedList = new ArrayList<>();

    private List<Peer> server;
    private List<Peer> clientList;
    private Map<Peer,PeerHandler>clientConnectionList;
    private boolean quit;


    public Chatpeer() throws IOException {
        myIP = InetAddress.getLocalHost().getHostAddress();
        this.userInput = new BufferedReader(new InputStreamReader(System.in));
        peerOutputMap = new HashMap<Peer, PrintWriter>();  // map each peer to an output stream
        server= new ArrayList<>();
        clientList = new ArrayList<>();
        clientConnectionList= new WeakHashMap<>();
        roomID=null;
        fileTransmission=new FileTransmission();
    }

    public static void main(String[] args) throws IOException {
        Chatpeer chatpeer = new Chatpeer();
        CmdLineParser parser = new CmdLineParser(chatpeer);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
        }
        chatpeer.startServer();
    }

    public String getRoomID(){
        return roomID;
    }

    public void setRoomID(String roomID){
        this.roomID=roomID;
    }

    public boolean getQuit(){
        return quit;
    }

    public void setQuit(){
        quit=true;
    }

    //start the server, listen to connection, and respond to user's command line input.
    private void startServer() throws IOException {
        listenPort = PORT;
        connectPort = CONNECTIONPORT;
        serverSocket = new ServerSocket(listenPort);
        System.out.printf("listening from ip " + myIP + " on port %d\n", listenPort);
        myPeer= new Peer(myIP,listenPort);
        PeerHandler myConn= new PeerHandler(myPeer.getSocket());
        peerOutputMap.put(myPeer,myConn.writer);

        Thread userThread = new Thread(new UserHandler());
        userThread.start();
        alive = true;

        new Thread(() -> {
            while (alive) {
                try {
                    Socket connectionSocket = serverSocket.accept();
                    new Thread(new PeerHandler(connectionSocket)).start(); // serve each conn on separate thread
                } catch (IOException e) {
                    e.printStackTrace();
                    alive = false;
                }
            }
        }).start();
    }

    private void send(Peer peer, String json) {
        try {
            peerOutputMap.get(peer).println(json);
        } catch (Exception e) {
            System.out.println(e);
        }
    }


    public class UserHandler implements Runnable {
        @Override
        public void run() {
            alive = true;
            String input;
            try {
                while (alive && (input = userInput.readLine()) != null) {
                    if (!input.isEmpty()) {
                        handleUserInput(input);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                alive = false;
                close();
            }
        }

        public void close() {
            System.out.println("This peer is shut down.");
            try {
                userInput.close();
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }


        public void handleUserInput(String input) {
            if (input.startsWith("#")) {
                String[] args = input.substring(1).split(" ");
                if (args.length > 1) {
                    switch (args[0]) {
                        case Packet.JoinRoom.TYPE:
                            if (server.size() == 1) {
                                send(server.get(0), gson.toJson
                                        (new Packet.JoinRoom(args[1])));
                            } else if (server.size() == 0) {
                                if(getRoomID()!=null) {
                                    localJoinRoom(args[1], getRoomID());
                                }else{
                                    localJoinRoom(args[1], "");
                                }
                            }
                            break;

                        case Packet.WhoInRoom.TYPE:
                            if (server.size() == 1) {
                                send(server.get(0), gson.toJson(new Packet.WhoInRoom(args[1])));
                            } else if (server.size() == 0) {
                                myPeer.whoInRoom(args[1]);
                            }
                            break;

                        case "delete":
                            if (server.size() == 0) {
                                if (myPeer.validRoom(args[1])) {
                                    Room room = myPeer.getRoomList().get(args[1]);
                                    for (Peer peer : room.getConnectionList()) {
                                        send(peer, gson.toJson(new Packet.RoomChange(
                                                concat(peer.getIp(), peer.getPort()), args[1], "")));
                                    }
                                    myPeer.deleteRoom(args[1]);
                                }
                            } else {
                                System.out.println("Cannot delete rooms when connected to a server.");
                            }
                            break;

                        case "connect":
                            // #connect 142.250.70.238:4444
                            String[] connStr = args[1].split(":");
                            String peerIP = connStr[0];
                            String pPortStr = connStr[1];
                            if (args.length == 3) {
                                String strConnPort = args[2]; //user chooses connection port for myPeer
                                if (isValidPort(strConnPort)) {
                                    connectPort = Integer.parseInt(strConnPort);
                                }
                            }
                            try {
                                clientConnection(peerIP, pPortStr);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;

                        // local commands
                        case "createroom":  //no packet
                            if (server.size() == 0) {
                                myPeer.createRoom(args[1]);
                            } else {
                                System.out.println("Cannot create rooms when connected to a server.");
                            }
                            break;

                        case "kick": //no packet
                            if (server.size() == 0) {
                                blockedList.add(args[1]);//block this ip address
                                System.out.println("Successfully blocked all connections from ip: "+
                                        args[1]);
                            } else {
                                System.out.println("Cannot kick clients when connected to a server.");
                            }
                            break;

                        case "download":
                            String downloadFilePath = args[1];
                            try {
                                fileTransmission.FileDownload(downloadFilePath,new Socket(myIP,53333));
                            }catch (Exception e){
                                System.out.println("File downloaded.");
                            }
                            break;

                        case "upload":
                            String uploadFilePath = args[1];
                            try {
                                fileTransmission.FileUpload(uploadFilePath,new Socket(myIP,53360));
                            }catch (IOException e){
                                System.out.println("File uploaded.");
                            }
                            break;


                        default:
                            System.out.println("wrong command or argument");
                            break;
                    }
                } else if (args.length == 1) {
                    switch (args[0]) {
                        case Packet.JoinRoom.TYPE:
                            if (server.size() == 1) { //join empty room
                                send(server.get(0), gson.toJson
                                        (new Packet.JoinRoom("")));
                            } else if (server.size() == 0) {
                                localJoinRoom("", getRoomID());
                            }
                            break;

                        case Packet.GetList.TYPE:
                            if (server.size() == 1) {
                                send(server.get(0), gson.toJson(new Packet.GetList()));
                            } else if (server.size() == 0) {
                                myPeer.getList();
                            }
                            break;

                        case Packet.ListNeighbors.TYPE:
                            if (server.size() == 1) {
                                send(server.get(0), gson.toJson(new Packet.ListNeighbors()));
                            } else if (server.size() == 0) {
                                List<String> neighbors= new ArrayList();
                                for (Peer client:clientList){
                                    neighbors.add(concat(client.getIp(),client.getPort()));
                                }
                                System.out.println("Neighbors: "+ neighbors);

                            }
                            break;

                        case "help":
                            displayHelp();
                            break;

                        case Packet.Quit.TYPE:
                            if (server.size() == 1) { //discon from server
                                send(server.get(0), gson.toJson(new Packet.Quit()));
                                setQuit();
                            } else if (server.size() == 0) {
                                //send RoomChangeMsg and ClientDisconnect msg to all clients
                                //to remove the clients from room & close its connection to the client
                                if (clientList.size()>0) {
                                    for (Peer client:clientList){
                                        send(client,gson.toJson(new Packet.RoomChange(
                                                concat(client.getIp(),client.getPort()),
                                                clientConnectionList.get(client).getClientRoomId(),
                                                "")));
                                        send(client,gson.toJson(new Packet.ClientDisconnect()));
                                        clientConnectionList.get(client).close();
                                    }
                                    clearAllClients();
                                }
                            }
                            break;

                        default:
                            System.out.println("wrong command or argument");
                            break;
                    }
                } else {
                    System.out.println("wrong command or argument");
                }
                System.out.println(getPrefix());
            } //message
            else {
                if (server.size() == 1) {
                    if (getRoomID() != null) { //when not in room, msg sent are ignored
                        send(server.get(0), gson.toJson(new Packet.MessageToServer(input)));
                    }
                } else if (server.size() == 0) {
                    if (getRoomID() != null) {
                        broadcastInRoom(gson.toJson(new Packet.Message(
                                concat(myIP, listenPort), input)), getRoomID());
                    }
                }
            }
        }
    }


    private void serverConnection(PeerHandler conn,String ip, int port) throws IOException {
        boolean blocked=false;
        for (String blockedIp:blockedList){
            if (blockedIp.equals(ip)){
                blocked= true;
            }
        }
        if(!blocked) {
            Peer peer = new Peer(ip, port);
            conn.setPeerIp(ip);
            conn.setPeerPort(port);
            clientList.add(peer);
            clientConnectionList.put(peer, conn);
            peerOutputMap.put(peer, conn.writer);
            System.out.println(concat(ip, port) + " connects to you");
        }
        else{
            conn.sendToClient(gson.toJson(new Packet.KickMessage()));
            conn.connectionAlive=false;
            System.out.println("Fail to connect with "+concat(ip, port) +" due to blocked ip address");
        }
    }


    public class PeerHandler implements Runnable {
        private Socket peerSocket;
        private final Gson gson = new Gson();
        private final BufferedReader reader;
        private final PrintWriter writer;
        private String peerIp;
        private int peerPort; //listen port
        private String clientRoomId = "";
        private boolean connectionAlive = false;

        public PeerHandler(Socket peerSocket) throws IOException {
            this.peerSocket = peerSocket;
            this.reader = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
            this.writer = new PrintWriter(peerSocket.getOutputStream(), true);
        }

        public String getPeerIp(){
            return peerIp;
        }
        public int getPeerPort(){
            return peerPort;
        }

        public String getClientRoomId(){
            return clientRoomId;
        }
        public void setPeerIp(String ip){
            this.peerIp=ip;
        }
        public void setPeerPort(int port){
            this.peerPort=port;
        }
        public void setClientRoomId(String roomid){
            this.clientRoomId=roomid;
        }


        @Override
        public void run() {
            connectionAlive = true;
            String in;
            while (connectionAlive) {
                try {
                    in = reader.readLine();
                    if (in != null) {
                        handlePeerInput(in);
                    }
                } catch (IOException e) {
                    connectionAlive = false;
                }
            }
            System.out.println(concat(getPeerIp(),getPeerPort()) + " disconnected" );
            close();
        }


        public void close() {
            try {
                leave(this);
                peerSocket.close();
                reader.close();
                writer.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }

        public void handlePeerInput(String msgIn) {
            switch (gson.fromJson(msgIn, Packet.class).getType()) {
                case Packet.HostChange.TYPE:
                    Packet.HostChange hostChangeMsg = gson.fromJson(msgIn, Packet.HostChange.class);
                    String host = hostChangeMsg.getHost();
                    String[] hostStr = host.split(":");
                    String peerIp = hostStr[0];
                    int peerPort = Integer.parseInt(hostStr[1]);
                    try {
                        serverConnection(this, peerIp, peerPort);
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                    System.out.println(getPrefix());
                    break;

                case Packet.JoinRoom.TYPE:
                    Packet.JoinRoom joinMsg = gson.fromJson(msgIn, Packet.JoinRoom.class);
                    joinRoom(this, joinMsg.getRoomid());
                    break;

                case Packet.RoomChange.TYPE:
                    Packet.RoomChange roomChangeMsg = gson.fromJson(msgIn, Packet.RoomChange.class);
                    String formerRoom= roomChangeMsg.getFormer();
                    String newRoom= roomChangeMsg.getRoomid();
                    String msgId= roomChangeMsg.getIdentity();
                    roomChange(this,formerRoom,newRoom,msgId);
                    break;

                case Packet.WhoInRoom.TYPE: //who
                    Packet.WhoInRoom whoInRoomMsg = gson.fromJson(msgIn, Packet.WhoInRoom.class);
                    String roomid = whoInRoomMsg.getRoomid();
                    whoInRoom(this, roomid);
                    break;

                case Packet.RoomContents.TYPE:
                    System.out.println(gson.fromJson(msgIn, Packet.RoomContents.class).display());
                    System.out.println(getPrefix());
                    break;

                case Packet.GetList.TYPE:
                    listRoom(this);
                    break;

                case Packet.RoomList.TYPE:
                    Packet.RoomList roomListMsg = gson.fromJson(msgIn, Packet.RoomList.class);
                    System.out.println(roomListMsg.display());
                    System.out.println(getPrefix());
                    break;

                case Packet.ListNeighbors.TYPE:
                    ListNeighbors(this);
                    break;

                case Packet.Neighbors.TYPE:
                    Packet.Neighbors neighborsMsg = gson.fromJson(msgIn, Packet.Neighbors.class);
                    System.out.println("Neighbors: " + neighborsMsg.getNeighbors());
                    System.out.println(getPrefix());
                    break;

                case Packet.Quit.TYPE:
                    //send RoomChangeMsg to the client and the room,send ClientDisconnect msg to client
                    //then remove the client from room & close its connection to the client
                    joinRoom(this,"");
                    connectionAlive=false;
                    clearClient(findClient(this));
                    break;

                case Packet.MessageToServer.TYPE: //received from client
                    Packet.MessageToServer msg = gson.fromJson(msgIn, Packet.MessageToServer.class);
                    broadcastInRoom(gson.toJson(new Packet.Message(
                                    concat(getPeerIp(), getPeerPort()), msg.getContent())),
                            getClientRoomId());
                    break;

                case Packet.Message.TYPE: //received from server
                    System.out.println(gson.fromJson(msgIn, Packet.Message.class).display());
                    break;

                case Packet.KickMessage.TYPE:
                    clearServer();
                    System.out.println("Connection failed: blocked ip address.");
                    break;

                case Packet.ClientDisconnect.TYPE:
                    clearServer();
                    connectionAlive=false;
                    break;

            }
        }

        public void sendToClient(String json) {
            writer.println(json);
        }
    }


    // **************** peer side methods ****************
    public Peer findClient(PeerHandler conn){
        Peer client= null;
        for(Peer peer:clientList){
            if ((peer.getIp().equals(conn.getPeerIp()) && peer.getPort()==conn.getPeerPort())){
                client=peer;
            }
        }
        return client;
    }

    private void joinRoom(PeerHandler conn, String roomId) {
        Peer client= findClient(conn);
        if (client!=null) {
            String identity = concat(conn.getPeerIp(), conn.getPeerPort());
            String currRoomId= conn.getClientRoomId();

            if (!roomId.equals(currRoomId)){
                if (myPeer.validRoom(roomId) || roomId.equals("")){
                    if (currRoomId!=null) {  //exit current room first
                        Room currRoom = myPeer.getRoomList().get(currRoomId);
                        if (currRoom!=null) {
                            currRoom.removeClient(client);
                        }
                    }
                    if (myPeer.validRoom(roomId)){
                        Room newRoom = myPeer.getRoomList().get(roomId);
                        newRoom.addClient(client);
                        conn.setClientRoomId(roomId);
                        String roomChangeMsg = gson.toJson(new Packet.RoomChange(identity,
                                currRoomId, roomId));
                        System.out.println(identity + " moved" +
                                (currRoomId.isEmpty() ? "" : " from " + currRoomId) +
                                " to " + roomId);
                        broadcastInRoom(roomChangeMsg, roomId);
                        broadcastInRoom(roomChangeMsg, currRoomId);
                        whoInRoom(conn, roomId);
                        listRoom(conn);
                    }
                    else {
                        conn.setClientRoomId("");
                        String roomChangeMsg = gson.toJson(new Packet.RoomChange(identity,
                                currRoomId, ""));
                        System.out.println(identity + " leaves " + currRoomId);
                        broadcastInRoom(roomChangeMsg, currRoomId);
                        conn.sendToClient(gson.toJson(new Packet.RoomChange(identity,currRoomId, "")));
                        listRoom(conn);
                    }
                }
                else{ //invalid room id
                    conn.sendToClient(gson.toJson(new Packet.RoomChange(identity,currRoomId, currRoomId)));
                }
            }
            else { //equal room id
                conn.sendToClient(gson.toJson(new Packet.RoomChange(identity,currRoomId, currRoomId)));
            }
        }
    }

    private void roomChange(PeerHandler conn,String formerRoomId,
                            String newRoomId,String msgIdentity){
        if (newRoomId.isEmpty()){
            System.out.println(msgIdentity + " leaves " + formerRoomId);
            if (msgIdentity.equals(concat(myIP,listenPort))) {
                setRoomID("");
                if (getQuit()){
                    conn.connectionAlive=false;
                    clearServer();
                    System.out.println("You are now disconnected from server.");
                }
            }
        }
        else if (newRoomId.equals(formerRoomId)) {
            if (msgIdentity.equals(concat(myIP,listenPort))) {
                System.out.println("The requested room is invalid or non existent.");
            }
        }
        else{
            if (msgIdentity.equals(concat(myIP,listenPort))) {
                setRoomID(newRoomId);
            }
            System.out.println(msgIdentity + " moved" +
                    ((formerRoomId.isEmpty())? "" : (" from " + formerRoomId)) + " to " + newRoomId);
        }
    }

    private void broadcastInRoom(String json, String roomId) {
        if (myPeer.validRoom(roomId)) {
            Room room = myPeer.getRoomList().get(roomId);
            if(room.getConnectionList().size()>0) {
                synchronized (room.getConnectionList()) {
                    for (Peer peer : room.getConnectionList()) {
                        send(peer, json);
                    }
                }
            }
        }
    }

    public void ListNeighbors(PeerHandler conn){
        if (clientList.size()<=1 && server.size()==0){ //only connected to this requesting client or 0 client
            conn.sendToClient(gson.toJson(new Packet.Neighbors(null))); //no neighbor
        }
        else{
            List<String> neighbors= new ArrayList<>();
            if (server.size()==1){
                neighbors.add(concat(server.get(0).getIp(),server.get(0).getPort()));
            }
            if (clientList.size()>1){
                String requestingHost= concat(conn.getPeerIp(),conn.getPeerPort());
                for (Peer peer:clientList){
                    String peerStr= concat(peer.getIp(),peer.getPort());
                    if (!peerStr.equals(requestingHost)){
                        neighbors.add(peerStr);
                    }
                }
            }
            conn.sendToClient(gson.toJson(new Packet.Neighbors(neighbors)));
        }
    }


    private void whoInRoom(PeerHandler conn, String roomId) {
        if (myPeer.validRoom(roomId)) {
            Room room = myPeer.getRoomList().get(roomId);
            conn.sendToClient(gson.toJson(new Packet.RoomContents(room.getRoomId(),
                    room.getIdentities())));
        }
    }


    private void listRoom(PeerHandler conn) {
        conn.sendToClient(gson.toJson(new Packet.RoomList(myPeer.getRoomList().values().stream().map(it ->
                new Packet.RoomInfo(it.getRoomId(),
                        it.getConnectionList().size())
        ).collect(Collectors.toList()))));
    }


    private void leave(PeerHandler conn) {
        Peer client= findClient(conn);
        if (conn.getClientRoomId().length() > 2) { //exit old room first
            Room currRoom = myPeer.getRoomList().get(conn.getClientRoomId());
            if(currRoom!=null) {
                currRoom.removeClient(client);
            }
        }
    }

    private void clientConnection(String peerip, String pPortStr) throws IOException {

        if (!isValidPort(pPortStr)) {
            System.out.println("Connection failed: Invalid port number.");
        } else {
            int pPort = Integer.parseInt(pPortStr);
            if (!isUniqueConnection(peerip, pPort)) {
                System.out.println("Connection failed: Self or duplicate connection is not allowed.");
            } else {
                clientConnectInternal(peerip, pPort);
            }
        }
    }

    private void clientConnectInternal(String ip, int pPort) throws IOException {
        Socket peerSocket = null;
        try{
            peerSocket = new Socket(ip, pPort);
            Peer peer = new Peer(ip, pPort);
            PeerHandler conn = new PeerHandler(peerSocket);
            new Thread(conn).start();
            conn.setPeerIp(ip);
            conn.setPeerPort(pPort);
            peerOutputMap.put(peer,conn.writer);
            conn.sendToClient(gson.toJson(new Packet.HostChange(concat(myIP,listenPort))));

            server.add(peer);
            System.out.println("Connection to "+concat(ip,pPort)+ " was successful.");

        }catch (IOException e) {
            e.printStackTrace();
            System.out.println("Connection was unsuccessful, please try again later");
        }
    }



    // **************** local method ****************
    private void localJoinRoom(String roomId,String currRoomId) {

        String identity = concat(myIP, listenPort);

        if (!roomId.equals(currRoomId)){
            if (myPeer.validRoom(roomId) || roomId.equals("")) { //move to new room or empty room
                if (currRoomId.length() > 2) { //exit old room first
                    Room currRoom = myPeer.getRoomList().get(currRoomId);
                    currRoom.removeClient(myPeer);
                }
                if (myPeer.validRoom(roomId)){
                    Room newRoom = myPeer.getRoomList().get(roomId);
                    setRoomID(roomId);

                    String roomChangeMsg = gson.toJson(new Packet.RoomChange(identity,
                            currRoomId, roomId));
                    System.out.println(identity + " moved" +
                            (currRoomId.isEmpty() ? "" : " from " + currRoomId) +
                            " to " + roomId);
                    broadcastInRoom(roomChangeMsg, roomId);
                    broadcastInRoom(roomChangeMsg, currRoomId);
                    newRoom.addClient(myPeer); //add now,make sure myPeer does not receive roomChange packet
                    myPeer.whoInRoom(roomId);
                    myPeer.getList();
                }
                else{
                    setRoomID("");
                    String roomChangeMsg = gson.toJson(new Packet.RoomChange(identity,
                            currRoomId, ""));
                    System.out.println(identity + " leaves " + currRoomId);
                    broadcastInRoom(roomChangeMsg, currRoomId);
                    myPeer.getList();
                }
            }else{
                System.out.println("The requested room is invalid or non existent.");
            }
        }
        else {
            System.out.println("The requested room is invalid or non existent.");
        }

    }


    // **************** helper methods ****************
    private void clearServer(){
        peerOutputMap.remove(server.get(0));
        server.clear();
    }

    private void clearAllClients(){
        for (Peer client:clientList){
            peerOutputMap.remove(client);
        }
        clientList.clear();
        clientConnectionList.clear();
    }

    public void clearClient(Peer client){
        peerOutputMap.remove(client);
        clientList.remove(client);
        clientConnectionList.remove(client);
    }

    public boolean isValidPort(String input) {
        return isNumeric(input) && isInPortRange(Integer.parseInt(input));
    }

    private boolean isNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isInPortRange(int port) {
        return port >= 1024 && port <= 65535;
    }

    public boolean isUniqueConnection(String ip, int port) {
        return !isSelfConnection(ip, port) && isUniquePeer(ip, port);
    }

    private boolean isSelfConnection(String ip, int port) {
        return ip.equals(myIP) && listenPort == port;
    }

    //return true if this peer is not an active connection
    private boolean isUniquePeer(String ip, int port) {
        if (server.size()==1){
            if (server.get(0).getPort()==port && server.get(0).getIp().equals(ip)){
                return false;
            }
        }
        if (clientList.size()>0){
            for (Peer client:clientList){
                if (client.getIp().equals(ip)&& client.getPort()==port){
                    return false;
                }
            }
        }
        return true;
    }


    public String getPrefix() {
        if (myIP == null || listenPort == null) {
            return "";    //[] 192.168.1.10:38283>
        }
        if (server.size()==0){
            if(roomID == null ||getRoomID().length()<2 ) { //if not connected to server,display listening port
                return "[]" + " " + myIP + ":" + listenPort + ">";
            }else{
                return getRoomID() + " " + myIP + ":" + listenPort + ">";
            }
        }
        else if (server.size()==1){ //if connected to a server, display connection port
            if (roomID == null ||getRoomID().length()<2) {
                return "[]" + " " + myIP + ":" +connectPort  + ">";
            }else {
                return getRoomID() + " " + myIP + ":" + connectPort + ">";
            }
        }
        else {
            return "";
        }
    }

    private String concat(String ip, int port) {
        return ip + ":" + port;
    }

    private void displayHelp() {
        System.out.println("Available commands are as follows:");
        System.out.println("#help - list this information");
        System.out.println("#connect IP[:port] [local port] - connect to another peer");
        System.out.println("#kick [peerIP:port number] - disconnect and block the user from the peer");
        System.out.println("#createroom [roomID] - create a room on this peer");
        System.out.println("#join [roomID] - join a room");
        System.out.println("#who [roomID] - display a list of who is in the room");
        System.out.println("#list - display a list of rooms that the peer is maintaining");
        System.out.println("#listneighbors - display a list of peer network addresses connected to this peer");
        System.out.println("#delete [roomID] - delete a room");
        System.out.println("#quit - disconnect from a peer");
        System.out.println("#upload [path] - upload a file to share with other peers in the room");
        System.out.println("#download [path] - download a file shared by other peers in the room");
        System.out.println();
    }

}

