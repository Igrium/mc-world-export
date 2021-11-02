from __future__ import annotations

from typing import (
    TYPE_CHECKING,
    Any,
    ClassVar,
    BinaryIO,
    Union,
    Dict,
    Optional,
    Iterator,
    Iterable,
)

from ..const import SNBTType

from .value import BaseTag, BaseMutableTag
from ..const import TAG_END, NON_QUOTED_KEY, CommaSpace, CommaNewline
from . import class_map

if TYPE_CHECKING:
    from . import AnyNBT

NBTDictType = Dict[str, "AnyNBT"]


class TAG_Compound(BaseMutableTag):
    tag_id: ClassVar[int] = 10
    _value: NBTDictType
    _data_type: ClassVar = dict

    def __init__(self, value: Union[NBTDictType, TAG_Compound, None] = None):
        super().__init__(value)

    def _sanitise_value(self, value: Optional[Any]) -> Any:
        if value is None:
            return self._data_type()
        else:
            if isinstance(value, BaseTag):
                value = value.value
            value = self._data_type(value)
            for key, value_ in value.items():
                self._check_entry(key, value_)
            return value

    @staticmethod
    def _check_entry(key: str, value: AnyNBT):
        if not isinstance(key, str):
            raise TypeError(
                f"TAG_Compound key must be a string. Got {key.__class__.__name__}"
            )
        if not isinstance(value, BaseTag):
            raise TypeError(
                f'Invalid type {value.__class__.__name__} for key "{key}" in TAG_Compound. Must be an NBT object.'
            )

    @property
    def value(self) -> NBTDictType:
        """The raw data stored in the object."""
        return self._value

    @classmethod
    def load_from(cls, context: BinaryIO, little_endian: bool) -> TAG_Compound:
        value = {}
        tag_id = context.read(1)[0]
        while tag_id != TAG_END:
            tag_name = cls.load_string(context, little_endian)
            child_tag = class_map.TAG_CLASSES[tag_id].load_from(context, little_endian)
            value[tag_name] = child_tag
            tag_id = context.read(1)[0]

        return cls(value)

    def write_value(self, buffer: BinaryIO, little_endian=False):
        for key, value in self._value.items():
            value.write_payload(buffer, key, little_endian)
        buffer.write(bytes((TAG_END,)))

    def _to_snbt(self) -> SNBTType:
        tags = []
        for name, elem in self._value.items():
            if NON_QUOTED_KEY.match(name) is None:
                tags.append(f'"{name}": {elem.to_snbt()}')
            else:
                tags.append(f"{name}: {elem.to_snbt()}")
        return f"{{{CommaSpace.join(tags)}}}"

    def _pretty_to_snbt(self, indent_chr="", indent_count=0, leading_indent=True):
        if self._value:
            tags = (
                f'{indent_chr * (indent_count + 1)}"{name}": {elem._pretty_to_snbt(indent_chr, indent_count + 1, False)}'
                for name, elem in self._value.items()
            )
            return f"{indent_chr * indent_count * leading_indent}{{\n{CommaNewline.join(tags)}\n{indent_chr * indent_count}}}"
        else:
            return f"{indent_chr * indent_count * leading_indent}{{}}"

    def __contains__(self, item: str) -> bool:
        return self._value.__contains__(item)

    def __delitem__(self, key: str):
        self._value.__delitem__(key)

    def __getitem__(self, key: str) -> AnyNBT:
        return self._value.__getitem__(key)

    def __iter__(self) -> Iterator[str]:
        return self._value.__iter__()

    def __len__(self) -> int:
        return self._value.__len__()

    def __setitem__(self, key: str, value: AnyNBT):
        self._check_entry(key, value)
        self._value.__setitem__(key, value)

    def copy(self):
        return TAG_Compound(self._value.copy())

    def fromkeys(self, keys: Iterable, value: AnyNBT):
        return TAG_Compound(dict.fromkeys(keys, value))

    def setdefault(self, k: str, default: AnyNBT):
        self._check_entry(k, default)
        return self._value.setdefault(k, default)

    def update(self, other: Union[NBTDictType, TAG_Compound]):
        other: NBTDictType = self.get_primitive(other)
        for k, v in other.items():
            self._check_entry(k, v)
        self._value.update(other)
