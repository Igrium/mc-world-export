from __future__ import annotations

from typing import ClassVar, BinaryIO

from ..const import SNBTType
from .value import BaseImmutableTag


class TAG_String(BaseImmutableTag):
    tag_id: ClassVar[int] = 8
    _value: str
    _data_type: ClassVar = str

    @classmethod
    def load_from(cls, context: BinaryIO, little_endian: bool) -> TAG_String:
        return cls(cls.load_string(context, little_endian))

    def write_value(self, buffer: BinaryIO, little_endian=False):
        self.write_string(buffer, self._value, little_endian)

    def _to_snbt(self) -> SNBTType:
        return f'"{self.escape(self._value)}"'

    @staticmethod
    def escape(string: str):
        return string.replace("\\", "\\\\").replace('"', '\\"')

    @staticmethod
    def unescape(string: str):
        return string.replace('\\"', '"').replace("\\\\", "\\")

    def __len__(self) -> int:
        return len(self._value)

    def __getitem__(self, item):
        return self._value.__getitem__(item)

    def __add__(self, other):
        return self._value + self.get_primitive(other)

    def __radd__(self, other):
        return self.get_primitive(other) + self._value

    def __iadd__(self, other):
        return self.__class__(self + other)

    def __mul__(self, other):
        return self._value * self.get_primitive(other)

    def __rmul__(self, other):
        return self.get_primitive(other) * self._value

    def __imul__(self, other):
        return self.__class__(self * other)
