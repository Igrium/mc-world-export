import bpy

from . import pbr_packs
from .ops import import_replay_operator, split_test_operator

def register():
    import_replay_operator.register()
    split_test_operator.register()
    pbr_packs.register()

def unregister():
    pbr_packs.unregister()
    import_replay_operator.unregister()
    split_test_operator.unregister()
