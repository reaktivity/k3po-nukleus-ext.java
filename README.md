# K3PO Nukleus Extension

[![Build Status][build-status-image]][build-status]

[build-status-image]: https://travis-ci.org/reaktivity/k3po-nukleus-ext.java.svg?branch=develop
[build-status]: https://travis-ci.org/reaktivity/k3po-nukleus-ext.java

## Nukleus `streams` Transport
Flow control with `WINDOW` update frames are managed inside the transport.

Requires external configuration of directory where streams are discovered.

```
# accept new unidirectional (simplex) streams at "receiver" from source "sender"
# for route reference ${routeRef} (required)
# set initial window size to 8192 (required)
# do no subsequent window updates (optional: default do updates)
# on explicit partition "part0" (optional: default to any partition)
# with explicit correlation ${correlationId} (optional: default to any correlation)
#
# note: throttle (default "stream") is not relevant on accept for "simplex" transmission (not writing)

property routeRef ${nukleus:newRouteRef()}
property correlationId ${nukleus:newCorrelationId()}

accept "nukleus://receiver/streams/sender"
       option nukleus:route ${routeRef}
       option nukleus:window 8192
       option nukleus:update "none"
       option nukleus:partition "part0"
       option nukleus:correlation ${correlationId}

accepted

# receive BEGIN w/ extension
read nukleus:begin.ext [0x...]

# send BEGIN w/ extension
write nukleus:begin.ext [0x...]

connected

# expect data on explicit partition (optional)
read option nukleus:partition "part1"

# receive DATA w/ extension
read nukleus:data.ext [0x...]
read [0x...]

# send RESET
read abort

# receive ABORT
read aborted

# receive END w/ extension
read nukleus:end.ext [0x...]
read closed
```

```
# connect new unidirectional stream at "receiver" from source "sender"
# for route reference ${routeRef} (required)
# on explicit partition "part0" (optional: default to any partition)
# with explicit correlation ${correlationId} (optional: default to any correlation)
# with throttle "none" (allows negative testing of flow control)

property routeRef ${nukleus:newRouteRef()}
property correlationId ${nukleus:newCorrelationId()}

connect "nukleus://receiver/streams/sender"
        option nukleus:route ${routeRef}
        option nukleus:partition "part0"
        option nukleus:correlation ${correlationId}
        option nukleus:throttle "none"

connected

# switch writes to explicit partition (optional)
write option nukleus:partition "part1"

# send DATA w/ extension
write nukleus:data.ext [0x...]
write [0x...]

# receive RESET
write aborted

# send ABORT
write abort

# END w/ extension
write nukleus:end.ext [0x...]
write close
```

```
# accept new bidirectional streams at "receiver" from source "sender"
# for route reference ${routeRef} (required)
# with initial window size to 8192 (required)
# with no subsequent window updates (optional: default do updates)
# with throttle "none" (optional: default "stream", or "message" for per-message acknowledgment)
# with "duplex" transmission for bidirectional (optional: default "simplex")
#
# note: partition, correlationId not supported for "duplex" transmission

property routeRef ${nukleus:newRouteRef()}

accept "nukleus://receiver/streams/sender"
       option nukleus:route ${routeRef}
       option nukleus:window 8192
       option nukleus:update "none"
       option nukleus:throttle "none"
       option nukleus:transmission "duplex"

accepted

# receive BEGIN w/ extension
read nukleus:begin.ext [0x...]

# send BEGIN w/ extension
write nukleus:begin.ext [0x...]

connected

# expect data on explicit partition (optional)
read option nukleus:partition "part1"

# receive DATA w/ extension
read nukleus:data.ext [0x...]
read [0x...]

# send RESET
read abort

# receive ABORT
read aborted

# receive END w/ extension
read nukleus:end.ext [0x...]
read closed

# switch writes to explicit partition (optional)
write option nukleus:partition "part1"

# send DATA w/ extension
write nukleus:data.ext [0x...]
write [0x...]

# receive RESET
write aborted

# send ABORT
write abort

# END w/ extension
write nukleus:end.ext [0x...]
write close
```

```
# connect new bidirectional stream at "receiver" from source "sender"
# for route reference ${routeRef} (required)
# with initial window size to 8192 (required)
# with no subsequent window updates (optional: default do updates)
# with throttle "none" (optional: default "stream", or "message" for per-message acknowledgment)
# with "duplex" transmission for bidirectional (optional: default "simplex")
#
# note: partition, correlationId not supported for "duplex" transmission

property routeRef ${nukleus:newRouteRef()}

connect "nukleus://receiver/streams/sender"
        option nukleus:route ${routeRef}
        option nukleus:window 8192
        option nukleus:update "none"
        option nukleus:throttle "none"
        option nukleus:transmission "duplex"

# send BEGIN w/ extension
write nukleus:begin.ext [0x...]

# receive BEGIN w/ extension
read nukleus:begin.ext [0x...]

connected

# switch writes to explicit partition (optional)
write option nukleus:partition "part1"

# send DATA w/ extension
write [0x...]
write nukleus:data.ext [0x...]

# receive RESET
write aborted

# send ABORT
write abort

# END w/ extension
write nukleus:end.ext [0x...]
write close

# expect data on explicit partition (optional)
read option nukleus:partition "part1"

# receive DATA w/ extension
read nukleus:data.ext [0x...]
read [0x...]

# send RESET
read abort

# receive ABORT
read aborted

# receive END w/ extension
read nukleus:end.ext [0x...]
read closed
```
