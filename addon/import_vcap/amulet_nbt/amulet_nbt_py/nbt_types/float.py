from __future__ import annotations

from struct import Struct
from typing import ClassVar, Union
import numpy as np

from ..const import SNBTType
from .numeric import BaseNumericTag


class BaseFloatTag(BaseNumericTag):
    _value: np.floating
    _data_type: ClassVar = np.floating

    @property
    def value(self) -> float:
        return float(self._value)

    def _to_snbt(self) -> SNBTType:
        return self.fstring.format(f"{self._value:.20f}".rstrip("0"))


class TAG_Float(BaseFloatTag):
    tag_id: ClassVar[int] = 5
    _value: np.float32
    _data_type: ClassVar = np.float32
    tag_format_be = Struct(">f")
    tag_format_le = Struct("<f")
    fstring = "{}f"


class TAG_Double(BaseFloatTag):
    tag_id: ClassVar[int] = 6
    _value: np.float64
    _data_type: ClassVar = np.float64
    tag_format_be = Struct(">d")
    tag_format_le = Struct("<d")
    fstring = "{}d"
