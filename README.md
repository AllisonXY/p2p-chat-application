# P2P Chat Application
This is a distributed P2P (Point-to-point) chat app written in Java that enables users to establish TCP connections, communicate, find nearby users, engage in group chat, and receive group notifications within a LAN environment. The application uses multi-threading, socket programming, JSON serialization, TCP/IP protocols, and Gradle. Developed by Allison Cheng.


## JAR Starter

The program is managed by Gradle. The following files will be generated in the target/lib directory:

```sh
server/target/lib/server.jar
client/target/lib/client.jar
```


## Commands

```
#identitychange
#join
#who
#list
#createroom
#delete
#quit
```

### C2S

##### join
- roomchange s2c response expected 
- (both to the sender & current room if != “” & entering room if != “”)
```json
{
    "type":"join",
    "roomid": "string"
}
```


#### who
- roomcontents s2c response expected.
- on response output formatted room contents to stdout
```json
{
      "type":"who",
      "roomid": "string"
}
```

#### list
- roomlist s2c response expected.
- on response output formatted list to stdout
```json
{
    "type":"list"
}
```


#### quit
- roomchange s2c response expected (both to the sender & current room).
- on response close connection socket.
```json
{
    "type":"quit"
}
```

#### message
- message s2c response expected (both to the sender & current room)
- on response output formatted message to stdout
```json
{
    "type":"message",
    "content": "string"
}
```


#### hostchange
- no s2c response expected
```json
{
"type":"hostchange",
"host": "string"
}
```

#### listneighbors
- neighbors s2c response expected
- on response output formatted message to stdout
```json
{
"type": "listneighbors"
}
```



### S2C

#### roomchange
- response to join, quit c2s and disconnection (abrupt/unplanned) 
- if in a room, to other connections in that room
```json
{
    "type":"roomchange",
    "identity": "string",
    "former": "string",
    "roomid": "string"
}
```

#### roomcontents
- response to who c2s.
```json
{
      "type":"roomcontents",
      "roomid": "string",
      "identities": ["string"],
}
```

#### roomlist
- response to list c2s.
```json
{
    "type":"roomlist",
    "rooms": [{"roomid": "string", "count": "int"}]
}
```

#### message
- response to message c2s.
```json
{
    "type":"message",
    "identity": "string",
    "content": "string"
}
```

#### neighbors
- response to a listneighbors c2s.
```json
{
    "type": "neighbors",
    "neighbors": ["string"]
}
```


### Connect Protocol
peerA (server) listens on localhost:4444
peerB (client) listens on localhost:5555

...

peerB (client) connects to localhost:4444

... (tcp handshake)

peerB [(client) -> (server) peerA] sends a hostchange packet:

{
    "type": "hostchange",
    "host": "127.0.0.1:5555"
}

<br>
### List Neighbors Protocol
peerA (server) listens on localhost:4444
peerB (client) listens on localhost:5555
peerC (client) listens on localhost:6666

...

peerB (client) connects to localhost:4444
peerB [(client) -> (server) peerA] sends a hostchange packet (host:127.0.0.1:5555)

peerC (client) connects to localhost:4444
peerC [(client) -> (server) peerA] sends a hostchange packet (host:127.0.0.1:6666)

peerB [(client) -> (server) peerA] sends a listneighbors packet

... (peerA checks the mapping it has for connection -> host)

peerA [(server) -> (client) peerB] sends a neighbors packet containing:
```json
{
    "type": "neighbors"
    "neighbors": ["127.0.0.1:6666"]
}

```

<br>
### Search Network Protocol
peerA  listens on localhost:4444
peerB  listens on localhost:5555
peerC  listens on localhost:6666

...

peerA "#createroom roomA" (locally)
peerB "#createroom roomB" (locally)
peerC "#createroom roomC" (locally)

peerD (client) connects to localhost:6666 (peerC)
peerD->peerC sends a hostchange packet (host: 127.0.0.1:7777)
peerD->peerC  sends join packet (roomid: roomC)
...
peerC->peerD sends a roomchange packet (roomid: roomC)

peerC (client) connects to localhost:5555 (peerB)
peerC->peerB  sends a hostchange packet (host: 127.0.0.1:6666)
peerC->peerB  sends join packet (roomid: roomB)
...
peerB->peerC sends a roomchange packet (roomid: roomB)

peerB (client) connects to localhost:4444 (peerA)
peerB->peerA sends a hostchange packet (host: 127.0.0.1:5555)
peerB->peerA sends a join packet (roomid: roomA)
...
peerA->peerB sends a roomchange packet (roomid: roomA)
...

peerA "#searchnetwork" - see that only neighbor of this node is peerB

peerA connects to localhost:5555 (peerB)
peerA->peerB sends a listneighbors packet
peerB->peerA sends a neighbors packet (neighbors: ["127.0.0.1:6666", "127.0.0.1:4444"])

peerA->peerB sends a list packet
peerB->peerA sends a roomlist packet (rooms: [{"roomid": "roomB", "count": 1}])

peerA->peerB sends a quit packet

new peer to check 127.0.0.1:6666 (peerC)

... (same as above, swap B with C)

new peer to check 127.0.0.1:7777 (peerD)

... (no rooms found, no neighbors other than peerC)

