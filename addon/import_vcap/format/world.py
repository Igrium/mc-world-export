from ..amulet_nbt import TAG_Compound

class VCAPWorld:
    data: TAG_Compound

    def __init__(self, data: TAG_Compound) -> None:
        self.data = data
        print(data)

    def getframes(self):
        return self.data.value