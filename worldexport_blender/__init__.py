import bpy

from .ops import import_replay_operator, split_test_operator

def register():
    import_replay_operator.register()
    split_test_operator.register()

def unregister():
    import_replay_operator.unregister()
    split_test_operator.unregister()
