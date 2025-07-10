import bpy

from .ops import import_replay_operator

def register():
    print("Hello from Igrium's Replay Importer")
    import_replay_operator.register()

def unregister():
    import_replay_operator.unregister()