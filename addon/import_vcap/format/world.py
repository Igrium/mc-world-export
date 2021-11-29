from abc import abstractmethod, abstractproperty
from typing import Iterable, Iterator
from bpy.types import Mesh
from ..amulet_nbt import TAG_Compound, TAG_List

class VcapFrame(Iterable[tuple[int, int, int]]):
    @abstractmethod
    def get_meshes() -> dict[str, Mesh]:   
        raise RuntimeError("Can't call methods on base class")
    
    @abstractmethod
    def __iter__(self) -> Iterator[tuple[int, int, int]]:
        raise RuntimeError("Can't call methods on base class")

class IFrame(VcapFrame):
    _nbt: TAG_Compound

    def __init__(self, nbt: TAG_Compound) -> None:
        self._nbt = nbt

    def get_meshes(self) -> dict[str, Mesh]:
        pass

    def __iter__(self) -> Iterator[tuple[int, int, int]]:
        return self.Iter()

    class Iter(Iterator[tuple[int, int, int]]):
        _section = 0
        _head = 0

        
        


