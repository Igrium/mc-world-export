"""Readers for the big-endian binary primitives used by the replay format."""

import struct

from typing import BinaryIO


def read_int(f: BinaryIO) -> int:
    data: bytes = f.read(4)
    if len(data) != 4:
        raise EOFError("Unexpected end of file while reading int")
    return struct.unpack('>i', data)[0]

def read_float(f: BinaryIO) -> float:
    data: bytes = f.read(4)
    if len(data) != 4:
        raise EOFError("Unexpected end of file while reading float")
    return struct.unpack('>f', data)[0]


def read_utf(f: BinaryIO) -> str:
    length_bytes: bytes = f.read(2)
    if len(length_bytes) != 2:
        raise EOFError("Unexpected end of file while reading UTF length")
    length: int = struct.unpack('>H', length_bytes)[0]
    utf8_bytes: bytes = f.read(length)
    if len(utf8_bytes) != length:
        raise EOFError("Unexpected end of file while reading UTF data")
    return utf8_bytes.decode('utf-8')
