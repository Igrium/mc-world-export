from __future__ import annotations

from abc import ABC
from typing import (
    ClassVar,
    BinaryIO,
    Union,
)
from struct import Struct
import numpy as np

from ..const import SNBTType

from .value import BaseImmutableTag


class BaseNumericTag(BaseImmutableTag, ABC):
    _value: np.number
    _data_type: ClassVar = np.number
    tag_format_be: ClassVar[Struct] = None
    tag_format_le: ClassVar[Struct] = None
    fstring: str = None

    def __init__(
        self, value: Union[int, float, np.number, BaseNumericTag, None] = None
    ):
        super().__init__(value)

    @classmethod
    def load_from(cls, context: BinaryIO, little_endian: bool):
        if little_endian:
            data = context.read(cls.tag_format_le.size)
            tag = cls(cls.tag_format_le.unpack_from(data)[0])
        else:
            data = context.read(cls.tag_format_be.size)
            tag = cls(cls.tag_format_be.unpack_from(data)[0])
        return tag

    def write_value(self, buffer: BinaryIO, little_endian=False):
        if little_endian:
            buffer.write(self.tag_format_le.pack(self._value))
        else:
            buffer.write(self.tag_format_be.pack(self._value))

    def _to_snbt(self) -> SNBTType:
        return self.fstring.format(self._value)

    def _to_python(self, value):
        """Convert numpy data types to their python equivalent."""
        if isinstance(value, np.floating):
            return float(value)
        elif isinstance(value, np.integer):
            return int(value)
        elif isinstance(value, np.generic):
            raise ValueError(f"Unexpected numpy type {type(value)}")
        else:
            return value

    def __add__(self, other):
        return self._to_python(self.value + other)

    def __radd__(self, other):
        return self._to_python(other + self.value)

    def __iadd__(self, other):
        return self.__class__(self + other)

    def __sub__(self, other):
        return self._to_python(self.value - other)

    def __rsub__(self, other):
        return self._to_python(other - self.value)

    def __isub__(self, other):
        return self.__class__(self - other)

    def __mul__(self, other):
        return self._to_python(self.value * other)

    def __rmul__(self, other):
        return self._to_python(other * self.value)

    def __imul__(self, other):
        return self.__class__(self * other)

    def __truediv__(self, other):
        return self._to_python(self.value / other)

    def __rtruediv__(self, other):
        return self._to_python(other / self.value)

    def __itruediv__(self, other):
        return self.__class__(self.__class__(self / other))

    def __floordiv__(self, other):
        return self._to_python(self.value // other)

    def __rfloordiv__(self, other):
        return self._to_python(other // self.value)

    def __ifloordiv__(self, other):
        return self.__class__(self // other)

    def __mod__(self, other):
        return self._to_python(self._value % other)

    def __rmod__(self, other):
        return self._to_python(other % self._value)

    def __imod__(self, other):
        return self.__class__(self % other)

    def __divmod__(self, other):
        return self._to_python(divmod(self.value, other))

    def __rdivmod__(self, other):
        return self._to_python(divmod(other, self.value))

    def __pow__(self, other, modulo=None):
        return self._to_python(self.value ** other)

    def __rpow__(self, other):
        return self._to_python(other ** self.value)

    def __ipow__(self, other):
        return self.__class__(self ** other)

    def __neg__(self):
        return self._to_python(self._value.__neg__())

    def __pos__(self):
        return self._to_python(self._value.__pos__())

    def __abs__(self):
        return self._to_python(self._value.__abs__())

    def __int__(self):
        return self._value.__int__()

    def __float__(self):
        return self._value.__float__()

    def __round__(self, n=None):
        return self._value.__round__(n)

    def __trunc__(self):
        return self._value.__trunc__()

    def __floor__(self):
        return self._value.__floor__()

    def __ceil__(self):
        return self._value.__ceil__()

    def __bool__(self):
        return self._value.__bool__()
