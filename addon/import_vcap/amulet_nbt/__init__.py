try:
    from .amulet_cy_nbt import (
        TAG_Byte,
        TAG_Short,
        TAG_Int,
        TAG_Long,
        TAG_Float,
        TAG_Double,
        TAG_Byte_Array,
        TAG_String,
        TAG_List,
        TAG_Compound,
        TAG_Int_Array,
        TAG_Long_Array,
        NBTFile,
        load,
        from_snbt,
        BaseValueType,
        BaseArrayType,
        AnyNBT,
        SNBTType,
    )
except (ImportError, ModuleNotFoundError) as e:
    print(
        "Failed to import cython nbt library. Falling back to python version. This will run a lot slower."
    )
    from .amulet_nbt_py import (
        TAG_Byte,
        TAG_Short,
        TAG_Int,
        TAG_Long,
        TAG_Float,
        TAG_Double,
        TAG_Byte_Array,
        TAG_String,
        TAG_List,
        TAG_Compound,
        TAG_Int_Array,
        TAG_Long_Array,
        NBTFile,
        load,
        from_snbt,
        BaseValueType,
        BaseArrayType,
        AnyNBT,
        SNBTType,
    )

from ._version import get_versions

__version__ = get_versions()["version"]
del get_versions
