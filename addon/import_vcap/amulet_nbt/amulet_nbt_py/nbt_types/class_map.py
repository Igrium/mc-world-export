from .int import TAG_Byte, TAG_Short, TAG_Int, TAG_Long
from .float import TAG_Float, TAG_Double
from .array import TAG_Byte_Array, TAG_Int_Array, TAG_Long_Array
from .string import TAG_String
from .list import TAG_List
from .compound import TAG_Compound

TAG_CLASSES = {
    TAG_Byte.tag_id: TAG_Byte,
    TAG_Short.tag_id: TAG_Short,
    TAG_Int.tag_id: TAG_Int,
    TAG_Long.tag_id: TAG_Long,
    TAG_Float.tag_id: TAG_Float,
    TAG_Double.tag_id: TAG_Double,
    TAG_Byte_Array.tag_id: TAG_Byte_Array,
    TAG_String.tag_id: TAG_String,
    TAG_List.tag_id: TAG_List,
    TAG_Compound.tag_id: TAG_Compound,
    TAG_Int_Array.tag_id: TAG_Int_Array,
    TAG_Long_Array.tag_id: TAG_Long_Array,
}
