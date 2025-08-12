import bpy

from .ops import import_replay_operator

def register():
    import_replay_operator.register()

def unregister():
    import_replay_operator.unregister()
