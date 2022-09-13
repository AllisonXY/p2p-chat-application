package com.chat;

import java.util.List;

public class Packet {

    private final String type;

    public Packet(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }


    public static class RoomChange extends Packet {
        public final static String TYPE = "roomchange";
        private String identity;
        private String former;
        private String roomid;

        public RoomChange(String identity, String former, String roomid) {
            super(TYPE);
            this.identity = identity;
            this.former = former;
            this.roomid = roomid;
        }

        public String getIdentity() {
            return identity;
        }

        public String getFormer() {
            return former;
        }

        public String getRoomid() {
            return roomid;
        }
    }

    public static class Room extends Packet {
        protected String roomid;

        public Room(String type, String roomid) {
            super(type);
            this.roomid = roomid;
        }

        public String getRoomid() {
            return roomid;
        }
    }

    public static class JoinRoom extends Room {
        public final static String TYPE = "join";
        public JoinRoom(String roomid) {
            super(TYPE, roomid);
        }
    }


    public static class WhoInRoom extends Room {
        public final static String TYPE = "who";
        public WhoInRoom(String roomid) {
            super(TYPE, roomid);
        }
    }


    public static class RoomInfo {
        private String roomid;
        private int count;

        public RoomInfo(String roomid, int count) {
            this.roomid = roomid;
            this.count = count;
        }
    }

    public static class GetList extends Packet {
        public final static String TYPE = "list";
        public GetList() {
            super(TYPE);
        }
    }
    public static class Quit extends Packet {
        public final static String TYPE = "quit";
        public Quit() {
            super(TYPE);
        }
    }

    public static class RoomList extends Packet {
        public final static String TYPE = "roomlist";
        private List<RoomInfo> rooms;

        public RoomList(List<RoomInfo> rooms) {
            super(TYPE);
            this.rooms = rooms;
        }

        public boolean contain(String roomId) {
            for (RoomInfo roomInfo : rooms){
                if (roomInfo.roomid.equals(roomId)){
                    return true;
                }
            }
            return false;
        }

        public String display() {
            StringBuilder builder  = new StringBuilder();
            for (RoomInfo roomInfo : rooms){
                builder.append(roomInfo.roomid).append(": ").append(roomInfo.count).append(" guests\n");
            }
            return builder.toString();
        }
    }

    public static class RoomContents extends Packet {
        public final static String TYPE = "roomcontents";
        private String roomid;
        private List<String> identities;

        public RoomContents(String roomid, List<String> identities) {
            super(TYPE);
            this.roomid = roomid;
            this.identities = identities;
        }

        public String getRoomid() {
            return roomid;
        }


        public List<String> getIdentities() {
            return identities;
        }

        public String display() {
            StringBuilder builder = new StringBuilder("Room "+ roomid+" contains");
            if (identities.size()==0){
                builder.append(" no guest");
            }
            else {
                for (String identity : identities) {
                    builder.append(' ').append(identity);
                }
            }
            return builder.toString();
        }
    }

    //s2c
    public static class Message extends Packet {
        public final static String TYPE = "message";
        private final String content;
        private String identity=null;


        public Message(String identity, String content) {
            super(TYPE);
            this.content = content==null?"":content;
            this.identity = identity;
        }


        public String getContent() {
            return content;
        }

        public void setIdentity(String identity) {
            this.identity = identity;
        }

        public String getIdentity() {
            return identity;
        }

        public String display() {
            return (identity==null?"":(identity+": "))+content;
        }
    }

    //c2s
    public static class MessageToServer extends Packet {
        public final static String TYPE = "messageToServer";
        private final String content;

        public MessageToServer(String content) {
            super(TYPE);
            this.content = content==null?"":content;
        }

        public String getContent() {
            return content;
        }

    }


    public static class HostChange extends Packet {
        public final static String TYPE = "hostchange";
        private final String host;

        public HostChange(String host) {
            super(TYPE);
            this.host= host;
        }

        public String getHost() {
            return host;
        }
    }

    public static class ListNeighbors extends Packet{
        public final static String TYPE = "listneighbors";

        public ListNeighbors() {
            super(TYPE);
        }
    }


    public static class Neighbors extends Packet {
        public final static String TYPE = "neighbors";
        private List<String> neighbors;

        public Neighbors(List<String> neighbors) {
            super(TYPE);
            this.neighbors=neighbors;
        }

        public List<String> getNeighbors(){
            return neighbors;
        }
    }

    public static class KickMessage extends Packet{  //S2C to ensure client aware of getting kicked/blocked
        public final static String TYPE = "kick";
        public KickMessage() {
            super(TYPE);
        }
    }

    public static class ClientDisconnect extends Packet{  //S2C to ensure disconnection on client side
        public final static String TYPE = "clientDisconnect";
        public ClientDisconnect() {
            super(TYPE);
        }
    }


}


