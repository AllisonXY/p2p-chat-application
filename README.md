A distributed P2P chat system which allows users establish TCP connection, communicate, find nearby users, engage in group chat, and receive group notifications within LAN.   
This Java program uses multi-threading, socket programming, JSON serialization, TCP/IP protocols and Gradle. 
Author: Cheng,A.

# Optional JAR Starter


- Uses gradle to manage dependencies and build scripts
- Automatically includes json library jackson

Stores the output

```sh
server/target/lib/server.jar
client/target/lib/client.jar
```

The Uber jar is a fat jar that packages dependencies as well.


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

## Wire

### Types

```json
identity: [a-zA-Z0-9]{3,16}
string: char[]
i32: int
```

### C2S


#### join

```json
roomchange s2c response expected 
(both to the sender & current room if != “” & entering room if != “”)
{
    "type":"join",
    "roomid": "string"
}
```


#### who

```json
roomcontents s2c response expected.
on response output formatted room contents to stdout
{
      "type":"who",
      "roomid": "string"
}
```

#### list

```json
roomlist s2c response expected.
on response output formatted list to stdout
{
    "type":"list"
}
```


#### quit

```json
roomchange s2c response expected (both to the sender & current room).
on response close connection socket.
{
    "type":"quit"
}
```

#### message
```json
message s2c response expected (both to the sender & current room)
on response output formatted message to stdout
{
    "type":"message",
    "content": string
}
```


#### hostchange
```json
no s2c response expected
{
"type":"hostchange",
"host": "string"
}
```

#### listneighbors
```json
neighbors s2c response expected
on response output formatted message to stdout

{
"type": "listneighbors"
}
```



### S2C

#### roomchange

```json
response to join, quit c2s and disconnection (abrupt/unplanned) 
if in a room, to other connections in that room
{
    "type":"roomchange",
    "identity": "string",
    "former": "string",
    "roomid": "string"
}
```

#### roomcontents
```json
response to who c2s.
{
      "type":"roomcontents",
      "roomid": string,
      "identities": ["string"],
}
```

#### roomlist
```json
response to list c2s.
{
    "type":"roomlist",
    "rooms": [{"roomid": "string", "count": "int"}]
}
```

#### message
```json
response to message c2s.
{
    "type":"message",
    "identity": "string",
    "content": "string"
}
```

#### neighbors
```json
response to a listneighbors c2s.
{
    "type": "neighbors",
    "neighbors": ["string"]
}
```


### Connect Protocol
```json
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
```


### List Neighbors Protocol
```json
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

{
    "type": "neighbors"
    "neighbors": ["127.0.0.1:6666"]
}

```

### Search Network Protocol
```json
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

---

peerA "#searchnetwork" 

# see that only neighbor of this node is peerB

peerA connects to localhost:5555 (peerB)
peerA->peerB sends a listneighbors packet
peerB->peerA sends a neighbors packet (neighbors: ["127.0.0.1:6666", "127.0.0.1:4444"])

peerA->peerB sends a list packet
peerB->peerA sends a roomlist packet (rooms: [{"roomid": "roomB", "count": 1}])

peerA->peerB sends a quit packet

# new peer to check 127.0.0.1:6666 (peerC)

... (same as above, swap B with C)

# new peer to check 127.0.0.1:7777 (peerD)

... (no rooms found, no neighbors other than peerC)

```
