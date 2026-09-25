#!/usr/bin/env python3
"""Minimal Source RCON client - no dependencies beyond the stdlib.

Used to send a graceful command (usually "stop") to the dev server instead
of force-killing the java process. A forceful kill skips Minecraft's own
save-on-shutdown routine entirely, silently losing anything since the last
autosave tick - confirmed as the cause of a real data-loss report ("I
deposited money / force-loaded a chunk and it was suddenly gone" after a
dev-server restart).

Usage: rcon.py [command] [--host HOST] [--port PORT] [--password PASSWORD] [--timeout SECONDS]
Defaults match scripts/dev/server.properties: localhost:25575, password "devonly".
"""
import argparse
import socket
import struct
import sys

PACKET_AUTH = 3
PACKET_AUTH_RESPONSE = 2
PACKET_EXEC_COMMAND = 2
PACKET_RESPONSE_VALUE = 0


def send_packet(sock: socket.socket, packet_id: int, packet_type: int, body: str) -> None:
    payload = struct.pack("<ii", packet_id, packet_type) + body.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(payload)) + payload)


def read_packet(sock: socket.socket) -> tuple[int, int, str]:
    length_bytes = _recv_exact(sock, 4)
    length = struct.unpack("<i", length_bytes)[0]
    payload = _recv_exact(sock, length)
    packet_id, packet_type = struct.unpack("<ii", payload[:8])
    body = payload[8:-2].decode("utf-8", errors="replace")
    return packet_id, packet_type, body


def _recv_exact(sock: socket.socket, n: int) -> bytes:
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError("RCON connection closed unexpectedly")
        data += chunk
    return data


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", nargs="?", default="stop", help='Command to send (default: "stop")')
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password", default="devonly")
    parser.add_argument("--timeout", type=float, default=10.0)
    args = parser.parse_args()

    try:
        with socket.create_connection((args.host, args.port), timeout=args.timeout) as sock:
            sock.settimeout(args.timeout)
            send_packet(sock, 1, PACKET_AUTH, args.password)
            auth_id, _auth_type, _ = read_packet(sock)
            if auth_id == -1:
                print("RCON auth failed - check rcon.password in run/server.properties.", file=sys.stderr)
                return 1

            send_packet(sock, 2, PACKET_EXEC_COMMAND, args.command)
            _resp_id, _resp_type, body = read_packet(sock)
            if body:
                print(body)
            return 0
    except (ConnectionRefusedError, OSError) as e:
        print(f"Could not reach RCON at {args.host}:{args.port} - is the server running with enable-rcon=true? ({e})", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
