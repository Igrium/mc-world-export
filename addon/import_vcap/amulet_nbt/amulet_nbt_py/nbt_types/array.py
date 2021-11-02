from __future__ import annotations

from abc import ABC, abstractmethod
from typing import BinaryIO, ClassVar, Union, Iterable, Optional, Any
import numpy as np

from ..const import SNBTType
from .int import TAG_Int
from .value import BaseMutableTag, BaseTag
from ..const import CommaSpace


class BaseArrayTag(BaseMutableTag, ABC):
    _value: np.ndarray
    _data_type: ClassVar = np.ndarray

    def __init__(
        self,
        value: Union[
            np.ndarray,
            Iterable[int],
            TAG_Byte_Array,
            TAG_Int_Array,
            TAG_Long_Array,
            None,
        ] = None,
    ):
        super().__init__(value)

    @property
    @classmethod
    @abstractmethod
    def big_endian_data_type(cls) -> np.dtype:
        raise NotImplementedError

    @property
    @classmethod
    @abstractmethod
    def little_endian_data_type(cls) -> np.dtype:
        raise NotImplementedError

    def _sanitise_value(self, value: Optional[Any]) -> Any:
        if value is None:
            return np.zeros((0,), self.big_endian_data_type)
        else:
            if isinstance(value, BaseTag):
                value = value.value
            return np.array(value, self.big_endian_data_type)

    @property
    def value(self) -> np.ndarray:
        return self._value

    @classmethod
    def load_from(cls, context: BinaryIO, little_endian: bool):
        data_type: np.dtype = (
            cls.little_endian_data_type if little_endian else cls.big_endian_data_type
        )
        if little_endian:
            (string_len,) = TAG_Int.tag_format_le.unpack(
                context.read(TAG_Int.tag_format_le.size)
            )
        else:
            (string_len,) = TAG_Int.tag_format_be.unpack(
                context.read(TAG_Int.tag_format_be.size)
            )
        value = np.frombuffer(
            context.read(string_len * data_type.itemsize), dtype=data_type
        )
        return cls(value)

    def write_value(self, buffer: BinaryIO, little_endian=False):
        data_type = (
            self.little_endian_data_type if little_endian else self.big_endian_data_type
        )
        if self._value.dtype != data_type:
            if (
                self._value.dtype != self.big_endian_data_type
                if little_endian
                else self.little_endian_data_type
            ):
                print(
                    f"[Warning] Mismatch array dtype. Expected: {data_type.str}, got: {self._value.dtype.str}"
                )
            self._value = self._value.astype(data_type)
        value = self._value.tobytes()
        if little_endian:
            buffer.write(TAG_Int.tag_format_le.pack(self._value.size))
        else:
            buffer.write(TAG_Int.tag_format_be.pack(self._value.size))
        buffer.write(value)

    def __eq__(self, other):
        return np.array_equal(self._value, self.get_primitive(other))

    def __getitem__(self, item):
        return self._value.__getitem__(item)

    def __setitem__(self, key, value):
        self._value.__setitem__(key, value)

    def __array__(self):
        return self._value

    def __len__(self):
        return self._value.__len__()

    def __add__(self, other):
        return (self._value + other).astype(self.big_endian_data_type)

    def __radd__(self, other):
        return (other + self._value).astype(self.big_endian_data_type)

    def __iadd__(self, other):
        self._value += other
        return self

    def __sub__(self, other):
        return (self._value - other).astype(self.big_endian_data_type)

    def __rsub__(self, other):
        return (other - self._value).astype(self.big_endian_data_type)

    def __isub__(self, other):
        self._value -= other
        return self

    def __mul__(self, other):
        return (self._value - other).astype(self.big_endian_data_type)

    def __rmul__(self, other):
        return (other * self._value).astype(self.big_endian_data_type)

    def __imul__(self, other):
        self._value *= other
        return self

    def __matmul__(self, other):
        return (self._value @ other).astype(self.big_endian_data_type)

    def __rmatmul__(self, other):
        return (other @ self._value).astype(self.big_endian_data_type)

    def __imatmul__(self, other):
        self._value @= other
        return self

    def __truediv__(self, other):
        return (self._value / other).astype(self.big_endian_data_type)

    def __rtruediv__(self, other):
        return (other / self._value).astype(self.big_endian_data_type)

    def __itruediv__(self, other):
        self._value /= other
        return self

    def __floordiv__(self, other):
        return (self._value // other).astype(self.big_endian_data_type)

    def __rfloordiv__(self, other):
        return (other // self._value).astype(self.big_endian_data_type)

    def __ifloordiv__(self, other):
        self._value //= other
        return self

    def __mod__(self, other):
        return (self._value % other).astype(self.big_endian_data_type)

    def __rmod__(self, other):
        return (other % self._value).astype(self.big_endian_data_type)

    def __imod__(self, other):
        self._value %= other
        return self

    def __divmod__(self, other):
        return divmod(self._value, other)

    def __rdivmod__(self, other):
        return divmod(other, self._value)

    def __pow__(self, power, modulo):
        return pow(self._value, power, modulo).astype(self.big_endian_data_type)

    def __rpow__(self, other, modulo):
        return pow(other, self._value, modulo).astype(self.big_endian_data_type)

    def __ipow__(self, other):
        self._value **= other
        return self

    def __lshift__(self, other):
        return (self._value << other).astype(self.big_endian_data_type)

    def __rlshift__(self, other):
        return (other << self._value).astype(self.big_endian_data_type)

    def __ilshift__(self, other):
        self._value <<= other
        return self

    def __rshift__(self, other):
        return (self._value >> other).astype(self.big_endian_data_type)

    def __rrshift__(self, other):
        return (other >> self._value).astype(self.big_endian_data_type)

    def __irshift__(self, other):
        self._value >>= other
        return self

    def __and__(self, other):
        return (self._value & other).astype(self.big_endian_data_type)

    def __rand__(self, other):
        return (other & self._value).astype(self.big_endian_data_type)

    def __iand__(self, other):
        self._value &= other
        return self

    def __xor__(self, other):
        return (self._value ^ other).astype(self.big_endian_data_type)

    def __rxor__(self, other):
        return (other ^ self._value).astype(self.big_endian_data_type)

    def __ixor__(self, other):
        self._value ^= other
        return self

    def __or__(self, other):
        return (self._value | other).astype(self.big_endian_data_type)

    def __ror__(self, other):
        return (other | self._value).astype(self.big_endian_data_type)

    def __ior__(self, other):
        self.value |= other
        return self

    def __invert__(self):
        return self._value.__invert__()

    def __neg__(self):
        return self._value.__neg__().astype(self.big_endian_data_type)

    def __pos__(self):
        return self._value.__pos__().astype(self.big_endian_data_type)

    def __abs__(self):
        return self._value.__abs__().astype(self.big_endian_data_type)

    def __int__(self):
        return self._value.__int__()

    def __float__(self):
        return self._value.__float__()

    def __bool__(self):
        return self._value.__bool__()


class TAG_Byte_Array(BaseArrayTag):
    big_endian_data_type = little_endian_data_type = np.dtype("int8")
    tag_id: ClassVar[int] = 7

    def _to_snbt(self) -> SNBTType:
        return f"[B;{'B, '.join(str(val) for val in self._value)}B]"


class TAG_Int_Array(BaseArrayTag):
    big_endian_data_type = np.dtype(">i4")
    little_endian_data_type = np.dtype("<i4")
    tag_id: ClassVar[int] = 11

    def _to_snbt(self) -> SNBTType:
        return f"[I;{CommaSpace.join(str(val) for val in self._value)}]"


class TAG_Long_Array(BaseArrayTag):
    big_endian_data_type = np.dtype(">i8")
    little_endian_data_type = np.dtype("<i8")
    tag_id: ClassVar[int] = 12

    def _to_snbt(self) -> SNBTType:
        return f"[L;{CommaSpace.join(str(val) for val in self._value)}]"


BaseArrayType = BaseArrayTag
