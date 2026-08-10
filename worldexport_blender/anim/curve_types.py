"""Structural types describing anything the timeline code can measure."""

from abc import abstractmethod
from typing import Protocol, Iterable, Mapping


class CurveLike(Protocol):
    tick_offset: int
    length: int


class AnimationProvider(Protocol):
    @abstractmethod
    def get_curves(self) -> Mapping[str, Iterable[CurveLike]]:
        ...
