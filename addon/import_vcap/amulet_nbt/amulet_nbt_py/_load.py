from __future__ import annotations

from typing import (
    Dict,
    List,
    Tuple,
    Union,
    BinaryIO,
    Optional,
    overload,
    # Literal,
)
import re
import os
import gzip
from io import BytesIO

import numpy as np

from .errors import NBTLoadError, SNBTParseError, NBTFormatError
from .nbt_types import (
    TAG_Byte,
    TAG_Short,
    TAG_Int,
    TAG_Long,
    TAG_Float,
    TAG_Double,
    TAG_Byte_Array,
    TAG_Int_Array,
    TAG_Long_Array,
    TAG_String,
    TAG_List,
    TAG_Compound,
    NBTFile,
    AnyNBT,
)
from .const import SNBTType

TAG_COMPOUND = 10


@overload
def load(
    filepath_or_buffer: Union[str, bytes, BinaryIO],
    compressed: bool = True,
    count: None = None,
    offset=False,  # Literal[False] = False,
    little_endian: bool = False,
) -> NBTFile:
    ...


@overload
def load(
    filepath_or_buffer: Union[str, bytes, BinaryIO],
    compressed: bool = True,
    count: None = None,
    offset=False,  # Literal[True] = False,
    little_endian: bool = False,
) -> Tuple[NBTFile, int]:
    ...


@overload
def load(
    filepath_or_buffer: Union[str, bytes, BinaryIO],
    compressed: bool = True,
    count: int = None,
    offset=False,  # Literal[False] = False,
    little_endian: bool = False,
) -> List[NBTFile]:
    ...


@overload
def load(
    filepath_or_buffer: Union[str, bytes, BinaryIO],
    compressed: bool = True,
    count: int = None,
    offset=False,  # Literal[True] = False,
    little_endian: bool = False,
) -> Tuple[List[NBTFile], int]:
    ...


def load(
    filepath_or_buffer: Union[str, bytes, BinaryIO],
    compressed: bool = True,
    count: Optional[int] = None,
    offset: bool = False,
    little_endian: bool = False,
):
    """Read binary NBT from a file or bytes.

    :param filepath_or_buffer: A path or `read`able object to read the data from.
    :param compressed: Is the input data compressed using gzip.
    :param count: If None only one NBTFile is returned. If int a list of `NBTFile`s is returned.
    :param offset: If True return Tuple[data, pointer] where pointer is the int pointer location. Useful when reading multiple from one file.
    :param little_endian: Should the binary NBT read in little endian format.
    :return: The NBTFile data. The output varies based on inputs.
    """
    if isinstance(filepath_or_buffer, str):
        # if a string load from the file path
        if not os.path.isfile(filepath_or_buffer):
            raise NBTLoadError(f"There is no file at {filepath_or_buffer}")
        with open(filepath_or_buffer, "rb") as f:
            data_in = f.read()

    elif isinstance(filepath_or_buffer, bytes):
        data_in = filepath_or_buffer
    elif hasattr(filepath_or_buffer, "read"):
        data_in = filepath_or_buffer.read()
        if not isinstance(data_in, bytes):
            raise NBTLoadError(
                f"buffer.read() must return a bytes object. Got {type(data_in)} instead."
            )
        if hasattr(filepath_or_buffer, "close"):
            filepath_or_buffer.close()
        elif hasattr(filepath_or_buffer, "open"):
            print(
                "[Warning]: Input buffer didn't have close() function. Memory leak may occur!"
            )
    else:
        raise NBTLoadError(
            "filepath_or_buffer must be a file path, bytes or file like object."
        )

    if not type(data_in) is bytes:
        raise ValueError("Expected a bytes object.")

    if compressed and data_in[:2] == b"\x1f\x8b":
        # if the first two bytes are this it should be gzipped
        try:
            data_in = gzip.GzipFile(fileobj=BytesIO(data_in)).read()
        except IOError as e:
            pass

    context = BytesIO(data_in)
    results = []

    for i in range(1 if count is None else count):
        tag_type = context.read(1)[0]
        if tag_type != TAG_COMPOUND:
            raise NBTFormatError(
                f"Expecting tag type {TAG_COMPOUND}, got {tag_type} instead"
            )

        tag_name = TAG_Compound.load_string(context, little_endian)
        tag: TAG_Compound = TAG_Compound.load_from(context, little_endian)

        results.append(NBTFile(tag, tag_name))

    if count is None:
        results = results[0]

    if offset:
        return results, context.tell()

    return results


# this is going to be rather slow but should exist as a starting point for functionality

whitespace = re.compile("[ \t\r\n]*")
int_numeric = re.compile("-?[0-9]+[bBsSlL]?")
float_numeric = re.compile("-?[0-9]+\.?[0-9]*[fFdD]?")
alnumplus = re.compile("[-.a-zA-Z0-9_]*")
comma = re.compile("[ \t\r\n]*,[ \t\r\n]*")
colon = re.compile("[ \t\r\n]*:[ \t\r\n]*")
array_lookup = {"B": TAG_Byte_Array, "I": TAG_Int_Array, "L": TAG_Long_Array}


def from_snbt(snbt: SNBTType) -> AnyNBT:
    def strip_whitespace(index) -> int:
        match = whitespace.match(snbt, index)
        if match is None:
            return index
        else:
            return match.end()

    def strip_comma(index, end_chr) -> int:
        match = comma.match(snbt, index)
        if match is None:
            index = strip_whitespace(index)
            if snbt[index] != end_chr:
                raise SNBTParseError(
                    f"Expected a comma or {end_chr} at {index} but got ->{snbt[index:index + 10]} instead"
                )
        else:
            index = match.end()
        return index

    def strip_colon(index) -> int:
        match = colon.match(snbt, index)
        if match is None:
            raise SNBTParseError(
                f"Expected : at {index} but got ->{snbt[index:index + 10]} instead"
            )
        else:
            return match.end()

    def capture_string(index) -> Tuple[str, bool, int]:
        if snbt[index] in ('"', "'"):
            quote = snbt[index]
            strict_str = True
            index += 1
            end_index = index
            while not (  # keep running this until
                snbt[end_index]
                == quote  # the last character is a quote of the same type
                and not (  # and there is an even number of backslashes before it (including 0)
                    len(snbt[:end_index]) - len(snbt[:end_index].rstrip("\\"))
                )
            ):
                end_index += 1

            val = TAG_String.unescape(snbt[index:end_index])
            index = end_index + 1
        else:
            strict_str = False
            match = alnumplus.match(snbt, index)
            val = match.group()
            index = match.end()

        return val, strict_str, index

    def parse_snbt_recursive(index=0) -> Tuple[AnyNBT, int]:
        index = strip_whitespace(index)
        if snbt[index] == "{":
            data_: Dict[str, AnyNBT] = {}
            index += 1
            index = strip_whitespace(index)
            while snbt[index] != "}":
                # read the key
                key, _, index = capture_string(index)

                # get around the colon
                index = strip_colon(index)

                # load the data and save it to the dictionary
                nested_data, index = parse_snbt_recursive(index)
                data_[key] = nested_data

                index = strip_comma(index, "}")
            data = TAG_Compound(data_)
            # skip the }
            index += 1

        elif snbt[index] == "[":
            index += 1
            index = strip_whitespace(index)
            if snbt[index : index + 2] in {"B;", "I;", "L;"}:
                # array
                array = []
                array_type_chr = snbt[index]
                array_type = array_lookup[array_type_chr]
                index += 2
                index = strip_whitespace(index)

                while snbt[index] != "]":
                    match = int_numeric.match(snbt, index)
                    if match is None:
                        raise SNBTParseError(
                            f"Expected an integer value or ] at {index} but got ->{snbt[index:index + 10]} instead"
                        )
                    else:
                        val = match.group()
                        if val[-1].isalpha():
                            if val[-1] == array_type_chr:
                                val = val[:-1]
                            else:
                                raise SNBTParseError(
                                    f'Expected the datatype marker "{array_type_chr}" at {index} but got ->{snbt[index:index + 10]} instead'
                                )
                        array.append(int(val))
                        index = match.end()

                    index = strip_comma(index, "]")
                data = array_type(
                    np.asarray(array, dtype=array_type.big_endian_data_type)
                )
            else:
                # list
                array = []
                first_data_type = None
                while snbt[index] != "]":
                    nested_data, index_ = parse_snbt_recursive(index)
                    if first_data_type is None:
                        first_data_type = nested_data.__class__
                    if not isinstance(nested_data, first_data_type):
                        raise SNBTParseError(
                            f"Expected type {first_data_type.__name__} but got {nested_data.__class__.__name__} at {index}"
                        )
                    else:
                        index = index_
                    array.append(nested_data)
                    index = strip_comma(index, "]")

                if first_data_type is None:
                    data = TAG_List()
                else:
                    data = TAG_List(array, list_data_type=first_data_type.tag_id)

            # skip the ]
            index += 1

        else:
            val, strict_str, index = capture_string(index)
            if strict_str:
                data = TAG_String(val)
            else:
                int_match = int_numeric.match(val)
                if int_match is not None and int_match.end() == len(val):
                    # we have an int type
                    if val[-1] in {"b", "B"}:
                        data = TAG_Byte(int(val[:-1]))
                    elif val[-1] in {"s", "S"}:
                        data = TAG_Short(int(val[:-1]))
                    elif val[-1] in {"l", "L"}:
                        data = TAG_Long(int(val[:-1]))
                    else:
                        data = TAG_Int(int(val))
                else:
                    float_match = float_numeric.match(val)
                    if float_match is not None and float_match.end() == len(val):
                        # we have a float type
                        if val[-1] in {"f", "F"}:
                            data = TAG_Float(float(val[:-1]))
                        elif val[-1] in {"d", "D"}:
                            data = TAG_Double(float(val[:-1]))
                        else:
                            data = TAG_Double(float(val))
                    else:
                        # we just have a string type
                        data = TAG_String(val)

        return data, index

    try:
        return parse_snbt_recursive()[0]
    except SNBTParseError as e:
        raise SNBTParseError(e)
    except IndexError:
        raise SNBTParseError(
            "SNBT string is incomplete. Reached the end of the string."
        )
