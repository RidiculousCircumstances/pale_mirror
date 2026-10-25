"""Bounded loopback diagnostic transport. HTTP never supplies endpoints or commands."""
import json
import socket
import struct
import time

LIMIT = 65536


def frame(identity, kind, text):
    body = struct.pack('<ii', identity, kind) + text.encode() + b'\0\0'
    return struct.pack('<i', len(body)) + body


def receive(connection, deadline):
    def exact(size):
        data = bytearray()
        while len(data) < size:
            connection.settimeout(max(0.001, deadline - time.monotonic()))
            if time.monotonic() >= deadline:
                raise TimeoutError('diagnostic response timed out')
            chunk = connection.recv(size - len(data))
            if not chunk:
                raise ConnectionError('diagnostic connection closed')
            data.extend(chunk)
        return bytes(data)
    size = struct.unpack('<i', exact(4))[0]
    if not 10 <= size <= LIMIT:
        raise ValueError('invalid RCON frame size')
    body = exact(size)
    if body[-2:] != b'\0\0':
        raise ValueError('invalid RCON terminator')
    return (*struct.unpack('<ii', body[:8]), body[8:-2].decode())


def query(source, command, expected_kind):
    # The endpoint and read-only command are fixed here, never supplied by HTTP.
    properties = dict(line.split('=', 1) for line in (source/'server.properties').read_text().splitlines()
                      if '=' in line and not line.startswith('#'))
    if properties.get('enable-rcon') != 'true':
        raise ValueError('RCON disabled; diagnostic unavailable')
    password = properties.get('rcon.password', '')
    if not password:
        raise ValueError('RCON credential missing')
    deadline = time.monotonic() + 3
    with socket.create_connection(('127.0.0.1', int(properties.get('rcon.port', 25575))), timeout=3) as connection:
        connection.sendall(frame(1, 3, password))
        for _ in range(4):
            identity, kind, _ = receive(connection, deadline)
            if identity == -1:
                raise PermissionError('diagnostic authentication rejected')
            if identity == 1 and kind == 2:
                break
        else:
            raise ValueError('diagnostic authentication incomplete')
        connection.sendall(frame(2, 2, command))
        response = ''
        while len(response.encode()) < LIMIT:
            identity, _, text = receive(connection, deadline)
            if identity != 2:
                raise ValueError('unexpected diagnostic response identity')
            response += text
            if 'PMV3_DIAG ' not in response:
                continue
            try:
                value = json.loads(response.split('PMV3_DIAG ', 1)[1])
            except json.JSONDecodeError:
                continue
            if value.get('kind') != expected_kind or value.get('status') != 'ok':
                raise ValueError('server diagnostic diagnostic unavailable')
            return properties.get('level-name', 'world'), value
        raise ValueError('diagnostic response exceeds limit')


