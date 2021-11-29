from abc import abstractmethod, abstractproperty

from numpy import ndarray
import bmesh
from bmesh.types import BMesh
import bpy
from bpy.types import Mesh, TimelineMarkers
from .context import VCAPContext, VCAPSettings
from mathutils import Matrix, Vector
from ..amulet_nbt import TAG_Compound, TAG_List, TAG_Int_Array, TAG_Byte_Array, TAG_String
from . import util

def load_frame(nbt: TAG_Compound, index = 0):
    t = nbt["type"].value
    if t == 0:
        return IFrame(nbt, index)
    elif t == 1:
        return PFrame(nbt, index)
    else: raise RuntimeError(f"Unknown frame type: {t}")
        

class VcapFrame:
    @abstractmethod
    def get_meshes(self, vcontext: VCAPContext, settings: VCAPSettings) -> list[Mesh]:
        """Generate the meshes of this frame.

        Returns:
            list[Mesh]: A list of the meshes.
        """
        raise RuntimeError("Can't call methods on base class")

    @abstractmethod
    def add_override(self, blocks: set[tuple[int, int, int]]):
        """Add a block override, causing the mesh to fracture at these locations.

        Args:
            blocks (set[tuple[int, int, int]]): A set of all the coordinates which will be overwritten. Should be immutable.
        """
        raise RuntimeError("Can't call methods on base class")
    
    @abstractmethod
    def get_declared_override(self) -> set[tuple[int, int, int]]:
        raise RuntimeError("Can't call methods on base class")
    
    time: float
    overrides: list[set[tuple[int, int, int]]]
    

class PFrame(VcapFrame):
    __nbt__: TAG_Compound
    index: int = 0

    def __init__(self, nbt: TAG_Compound, index: int = 0) -> None:
        """Create a PFrame object.

        Args:
            nbt (TAG_Compound): Frame NBT data.
            index (int, optional): Frame number to put in the mesh name. Defaults to 0.
        """
        self.__nbt__ = nbt
        self.index = index
        self.time = nbt['time'].value
        self.overrides = []
    
    def add_override(self, blocks: set[tuple[int, int, int]]):
        self.overrides.append(blocks)
    
    def get_meshes(self, vcontext: VCAPContext, settings: VCAPSettings) -> list[Mesh]:
        blocks: TAG_List = self.__nbt__['blocks']
        palette: TAG_List = self.__nbt__['palette']

        meshes: list[BMesh] = []
        meshes.append(bmesh.new())  # The first mesh has no override.
        for i in range(0, len(self.overrides)):
            meshes.append(bmesh.new())
        
        block: TAG_Compound
        for block in blocks:
            state: int = block['state'].value
            pos: TAG_List = block['pos']
            x = pos[0].value
            y = pos[1].value
            z = pos[2].value

            model_id: TAG_String = palette[state]
            block_mesh = vcontext.models[model_id.value]
            if len(block_mesh.vertices) == 0:
                continue

            mesh_index = 0
            for i in range(0, len(self.overrides)):
                if (x, y, z) in self.overrides[i]:
                    mesh_index = i + 1
                    break
            
            util.add_mesh(meshes[mesh_index], block_mesh, Matrix.Translation((x, y, z)))

        final_meshes = []
        for mesh in meshes:
            outMesh = bpy.data.meshes.new(f'{vcontext.name}.f{self.index}')
            mesh.to_mesh(outMesh)
            final_meshes.append(outMesh)
        
        return final_meshes
    
    def get_declared_override(self) -> set[tuple[int, int, int]]:
        overrides = set()
        blocks: TAG_List = self.__nbt__['blocks']
        block: TAG_Compound
        for block in blocks:
            pos: TAG_List = block['pos']
            x = pos[0].value
            y = pos[1].value
            z = pos[2].value
            overrides.add((x, y, z))
        
        return overrides


class IFrame(VcapFrame):
    __nbt__: TAG_Compound
    index: int

    def __init__(self, nbt: TAG_Compound, index: int = 0) -> None:
        """Create an IFrame object.

        Args:
            nbt (TAG_Compound): Frame NBT data.
            index (int, optional): Frame number to put in the mesh name. Defaults to 0.
        """
        self.__nbt__ = nbt
        self.overrides = []
        self.index = index
        self.time = nbt['time'].value

    def add_override(self, blocks: set[tuple[int, int, int]]):
        self.overrides.append(blocks)

    def get_meshes(self, vcontext: VCAPContext, settings: VCAPSettings) -> list[Mesh]:
        sections: TAG_List[TAG_Compound] = self.__nbt__['sections']
        meshes: list[BMesh] = []
        meshes.append(bmesh.new()) # The first mesh has no override.
        for i in range(0, len(self.overrides)):
            meshes.append(bmesh.new())

        section: TAG_Compound
        for i in range(0, len(sections)):
            print(f'IFrame section {i}')
            section = sections[i]
            palette: TAG_List = section['palette']
            offset = (section['x'].value, section['y'].value, section['z'].value)
            blocks: TAG_Int_Array = section['blocks']
            bblocks = blocks.value
            use_colors = False
            if settings.use_vertex_colors and ('colors' in section) and ('colorPalette' in section):
                color_palette_tag: TAG_Byte_Array = section['colorPalette']
                color_palette = color_palette_tag.value
                colors_tag: TAG_Byte_Array = section['colors']
                colors = colors_tag.value
                use_colors = True

            for y in range(0, 16):
                for z in range(0, 16):
                    for x in range(0, 16):
                        index = bblocks.item((y * 16 + z) * 16 + x)
                        model_id: str = palette[index].value
                        block_mesh = vcontext.models[model_id]
                        if len(block_mesh.vertices) == 0:
                            continue
                        if use_colors:
                            i = colors.item((y * 16 + z) * 16 + x)
                            r = _read_unsigned(color_palette, i, 8) / 255
                            g = _read_unsigned(color_palette, i + 1, 8) / 255
                            b = _read_unsigned(color_palette, i + 2, 8) / 255
                            color = [r, g, b, 1]
                        else:
                            color = [1, 1, 1, 1]

                        world_pos = (offset[0] * 16 + x, offset[1] * 16 + y, offset[2] * 16 + z)
                        mesh_index = 0

                        for i in range(0, len(self.overrides)):
                            override = self.overrides[i]
                            if world_pos in override:
                                mesh_index = i + 1
                                break
                        
                        util.add_mesh(meshes[mesh_index], block_mesh, Matrix.Translation(world_pos), color=color)
        final_meshes = []
        for mesh in meshes:
            outMesh = bpy.data.meshes.new(f'{vcontext.name}.f{self.index}')
            mesh.to_mesh(outMesh)
            final_meshes.append(outMesh)
        
        return final_meshes
    
    def get_declared_override(self) -> set[tuple[int, int, int]]:
        return set()

def _read_unsigned(array: ndarray, index: int, bit_depth: int = 8):
    item = array.item(index)
    if (item < 0):
        return item + 2**bit_depth
    else:
        return item
