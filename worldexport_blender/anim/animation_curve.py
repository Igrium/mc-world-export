import struct
from typing import BinaryIO

class AnimationCurve:
    xPosCurve: list[float] = []
    yPosCurve: list[float] = []
    zPosCurve: list[float] = []
    
    wRotCurve: list[float] = []
    xRotCurve: list[float] = []
    yRotCurve: list[float] = []
    zRotCurve: list[float] = []
    
    xScaleCurve: list[float] = []
    yScaleCurve: list[float] = []
    zScaleCurve: list[float] = []
    
    def read(self, f: BinaryIO):
        length: int = struct.unpack('>i', f.read(4))[0]
        
        read_curve(self.xPosCurve, f, length)
        read_curve(self.yPosCurve, f, length)
        read_curve(self.zPosCurve, f, length)
        
        read_curve(self.wRotCurve, f, length)
        read_curve(self.xRotCurve, f, length)
        read_curve(self.yRotCurve, f, length)
        read_curve(self.zRotCurve, f, length)
        
        read_curve(self.xScaleCurve, f, length)
        read_curve(self.yScaleCurve, f, length)
        read_curve(self.zScaleCurve, f, length)

def read_curve(curve: list[float], f: BinaryIO, length: int):
    data = f.read(length * 4)
    floats = struct.unpack('>f', data)
    curve.extend(floats)