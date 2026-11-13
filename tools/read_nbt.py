"""Read a gzip-compressed NBT file for save compatibility checks (no game libraries required)."""
import gzip
import io
import json
from pathlib import Path
import struct
import sys

def read_nbt(path):
    raw = Path(path).read_bytes()
    stream = io.BytesIO(gzip.decompress(raw) if raw[:2] == b'\x1f\x8b' else raw)
    def number(fmt):
        return struct.unpack('>' + fmt, stream.read(struct.calcsize('>' + fmt)))[0]
    def string():
        return stream.read(number('H')).decode('utf-8')
    def payload(kind):
        if kind in range(1, 7): return number({1:'b', 2:'h', 3:'i', 4:'q', 5:'f', 6:'d'}[kind])
        if kind == 7: return list(stream.read(number('i')))
        if kind == 8: return string()
        if kind == 9:
            element, length = number('B'), number('i')
            return [payload(element) for _ in range(length)]
        if kind == 10:
            result = {}
            while (element := number('B')) != 0:
                key = string()
                result[key] = payload(element)
            return result
        if kind in (11, 12):
            return [number('i' if kind == 11 else 'q') for _ in range(number('i'))]
        raise ValueError('Unknown NBT type ' + str(kind))
    kind = number('B')
    string()
    return payload(kind)

if __name__ == '__main__':
    print(json.dumps(read_nbt(sys.argv[1]), ensure_ascii=False, indent=2))
