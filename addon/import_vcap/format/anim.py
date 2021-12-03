from typing import Any

from bpy.types import Object


class TesselatedFrame:
    time: float = 0
    objects: dict[Any, Object]

    def __init__(self) -> None:
        self.objects = {}