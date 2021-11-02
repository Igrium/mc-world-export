class NBTError(Exception):
    """Some error in the NBT library."""


class NBTLoadError(NBTError):
    """The NBT data failed to load for some reason."""


class NBTFormatError(NBTLoadError):
    """Indicates the NBT format is invalid."""


class SNBTParseError(NBTError):
    """Indicates the SNBT format is invalid."""
