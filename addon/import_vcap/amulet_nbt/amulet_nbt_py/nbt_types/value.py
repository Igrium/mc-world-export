from __future__ import annotations

from abc import ABC, abstractmethod
from typing import (
    TYPE_CHECKING,
    Any,
    ClassVar,
    BinaryIO,
    Union,
    Optional,
)
from struct import Struct
from copy import deepcopy, copy

from ..const import SNBTType

if TYPE_CHECKING:
    from . import AnyNBT

_string_len_fmt_be = Struct(">H")
_string_len_fmt_le = Struct("<H")


class BaseTag(ABC):
    _value: Any

    def __init__(self, value: Optional[Any] = None):
        value = self._sanitise_value(value)
        if not isinstance(value, self._data_type):
            raise ValueError(
                f"value of {self.__class__.__name__} must be of type {self._data_type}"
            )
        self._value = value

    @property
    @classmethod
    @abstractmethod
    def _data_type(cls) -> Any:
        raise NotImplementedError

    @property
    @classmethod
    @abstractmethod
    def tag_id(cls) -> int:
        raise NotImplementedError

    def _sanitise_value(self, value: Optional[Any]) -> Any:
        if value is None:
            return self._data_type()
        else:
            if isinstance(value, BaseTag):
                value = value.value
            return self._data_type(value)

    @property
    def value(self) -> Any:
        """The raw data stored in the object."""
        return self._value

    @classmethod
    @abstractmethod
    def load_from(cls, context: BinaryIO, little_endian: bool) -> AnyNBT:
        """Load binary NBT from a file like object."""
        raise NotImplementedError

    @staticmethod
    def load_string(context: BinaryIO, little_endian=False) -> str:
        """A helper method to load a string from a file like object."""
        data = context.read(2)
        if little_endian:
            str_len = _string_len_fmt_le.unpack(data)[0]
        else:
            str_len = _string_len_fmt_be.unpack(data)[0]
        encoded = context.read(str_len)
        try:
            return encoded.decode("utf-8")
        except:
            return encoded.decode("Latin-1")

    def write_payload(self, buffer: BinaryIO, name="", little_endian=False):
        """Write the tag id, name and value to a file like object."""
        self.write_tag_id(buffer)
        self.write_string(buffer, name, little_endian)
        self.write_value(buffer, little_endian)

    def write_tag_id(self, buffer: BinaryIO):
        """Write the current class's tag id to the file like object."""
        buffer.write(bytes((self.tag_id,)))

    @staticmethod
    def write_string(buffer: BinaryIO, _str, little_endian=False):
        """Write a string to a file like object."""
        encoded_str = _str.encode("utf-8")
        if little_endian:
            buffer.write(_string_len_fmt_le.pack(len(encoded_str)))
        else:
            buffer.write(_string_len_fmt_be.pack(len(encoded_str)))
        buffer.write(encoded_str)

    @abstractmethod
    def write_value(self, buffer: BinaryIO, little_endian=False):
        """Write the value to a file like object."""
        raise NotImplementedError

    def to_snbt(self, indent_chr: Union[str, int, None] = None) -> SNBTType:
        """Return the NBT data in Stringified NBT format."""
        if isinstance(indent_chr, int):
            return self._pretty_to_snbt(" " * indent_chr)
        elif isinstance(indent_chr, str):
            return self._pretty_to_snbt(indent_chr)
        return self._to_snbt()

    @abstractmethod
    def _to_snbt(self) -> SNBTType:
        """Internal method to format the class data as SNBT."""
        raise NotImplementedError

    def _pretty_to_snbt(self, indent_chr="", indent_count=0, leading_indent=True):
        """Internal method to format the class data as SNBT with indentation."""
        return f"{indent_chr * indent_count * leading_indent}{self._to_snbt()}"

    def __getattr__(self, item):
        if item == "_value":
            # there is a case during unpickling when self._value is not defined
            # without this a recursion error will be hit.
            raise AttributeError
        return getattr(self._value, item)

    @staticmethod
    def get_primitive(obj):
        """Get the primitive object of the data.
        If obj is an instance of BaseTag then obj.value is used.
        Else obj is returned."""
        return obj.value if isinstance(obj, BaseTag) else obj

    def __repr__(self):
        return self._to_snbt()

    def __str__(self):
        return str(self.value)

    def __dir__(self):
        return self._value.__dir__()

    def __eq__(self, other):
        return self._value == other

    def strict_equals(self, other):
        """Extension of equals that also compares types."""
        return (
            isinstance(other, self.__class__)
            and self.tag_id == other.tag_id
            and self == other
        )

    def __ge__(self, other):
        return self.value >= other

    def __gt__(self, other):
        return self.value > other

    def __le__(self, other):
        return self.value <= other

    def __lt__(self, other):
        return self.value < other

    def __reduce__(self):
        return self.__class__, (self.value,)

    def copy(self):
        return copy(self)

    def __deepcopy__(self, memo=None):
        return self.__class__(deepcopy(self._value, memo=memo))

    def __copy__(self):
        return self.__class__(copy(self._value))


class BaseImmutableTag(BaseTag, ABC):
    def __hash__(self):
        return hash((self.tag_id, self._value))


class BaseMutableTag(BaseTag, ABC):
    pass


BaseValueType = BaseTag
