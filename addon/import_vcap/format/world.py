from ..amulet_nbt import TAG_Compound, TAG_List

class VCAPWorld:
    data: TAG_Compound

    def __init__(self, data: TAG_Compound) -> None:
        self.data = data

    def get_frames(self) -> TAG_List:
        return self.data.value['frames']
    
    def get_frame(self, index: int) -> TAG_Compound:
        return self.get_frames()[index]