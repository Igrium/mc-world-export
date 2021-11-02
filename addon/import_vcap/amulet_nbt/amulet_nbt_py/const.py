import re

SNBTType = str

CommaNewline = ",\n"
CommaSpace = ", "

TAG_END = 0
TAG_BYTE = 1
TAG_COMPOUND = 10

NON_QUOTED_KEY = re.compile(r"^[a-zA-Z0-9-]+$")
