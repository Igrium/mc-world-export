from __future__ import annotations

from typing import TYPE_CHECKING, overload, BinaryIO, Optional
from io import BytesIO
import gzip

from ..const import SNBTType
from .compound import TAG_Compound

if TYPE_CHECKING:
    from . import AnyNBT


class NBTFile:
    _value: TAG_Compound
    _name: str

    def __init__(self, tag: Optional[TAG_Compound] = None, name: str = ""):
        if tag is None:
            tag = TAG_Compound()
        if type(tag) is not TAG_Compound:
            raise ValueError("tag must be a TAG_Compound")
        self._value = tag
        self._name = name

    @property
    def value(self):
        return self._value

    @value.setter
    def value(self, value: TAG_Compound):
        if type(value) is not TAG_Compound:
            raise ValueError("value must be TAG_Compound")
        self._value = value

    @property
    def name(self):
        return self._name

    @name.setter
    def name(self, name: str):
        if type(name) is not str:
            raise ValueError("name must be a string")
        self._name = name

    def to_snbt(self, indent_chr=None) -> SNBTType:
        return self._value.to_snbt(indent_chr)

    @overload
    def save_to(
        self,
        filepath_or_buffer: None = None,
        compressed: bool = True,
        little_endian: bool = False,
    ) -> bytes:
        """If filepath_or_buffer is None the raw bytes will be returned."""
        ...

    @overload
    def save_to(
        self,
        filepath_or_buffer: str = None,
        compressed: bool = True,
        little_endian: bool = False,
    ) -> bytes:
        """If filepath_or_buffer is a valid file path in string form the data will be written to that file."""
        ...

    @overload
    def save_to(
        self,
        filepath_or_buffer: BinaryIO = None,
        compressed: bool = True,
        little_endian: bool = False,
    ) -> bytes:
        """If filepath_or_buffer is a file like object the bytes will be written to it using `write`."""
        ...

    def save_to(self, filepath_or_buffer=None, compressed=True, little_endian=False):
        """Save the data in binary NBT format.
        If filepath_or_buffer is None the raw bytes will be returned.
        If filepath_or_buffer is a valid file path in string form the data will be written to that file.
        If filepath_or_buffer is a file like object the bytes will be written to it using `write`.

        :param filepath_or_buffer: A path or `write`able object to write the data to. If None the bytes will be returned.
        :param compressed: Should the bytes be compressed with gzip.
        :param little_endian: Should the bytes be saved in little endian format.
        :return: If filepath_or_buffer is None the raw bytes will be returned. None otherwise
        """
        buffer = BytesIO()
        self._value.write_payload(buffer, self.name, little_endian)

        data = buffer.getvalue()

        if compressed:
            gzip_buffer = BytesIO()
            with gzip.GzipFile(fileobj=gzip_buffer, mode="wb") as gz:
                gz.write(data)
            data = gzip_buffer.getvalue()

        if isinstance(filepath_or_buffer, str):
            with open(filepath_or_buffer, "wb") as fp:
                fp.write(data)
        elif hasattr(filepath_or_buffer, "write"):
            filepath_or_buffer.write(data)
        else:
            return data

    def __eq__(self, other):
        return isinstance(other, NBTFile) and self._value.__eq__(other._value)

    def __len__(self) -> int:
        return self._value.__len__()

    def keys(self):
        return self._value.keys()

    def values(self):
        self._value.values()

    def __getitem__(self, key: str) -> AnyNBT:
        return self._value[key]

    def __setitem__(self, key: str, tag: AnyNBT):
        self._value[key] = tag

    def __delitem__(self, key: str):
        del self._value[key]

    def __contains__(self, key: str) -> bool:
        return key in self._value

    def pop(self, k, default=None) -> AnyNBT:
        return self._value.pop(k, default)

    def get(self, k, default=None) -> AnyNBT:
        return self._value.get(k, default)

    def __repr__(self):
        return f'NBTFile("{self.name}":{self.to_snbt()})'
