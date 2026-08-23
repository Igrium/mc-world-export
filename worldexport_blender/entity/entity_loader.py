import json
import os

from ..replay_types import ReplayImportContext
from .captured_entity import CapturedEntity


def import_entities(context: ReplayImportContext):
    dir = context.replay_root

    json_path = os.path.join(dir, 'entities.json')
    if not os.path.exists(json_path): return

    with open(json_path, 'r') as file:
        entity_json: dict[str, dict[str, str]] = json.load(file)

    num_ents = len(entity_json)
    for i, (name, parents) in enumerate(entity_json.items(), start=1):
        print(f"Importing entity {i}/{num_ents} ({name})")
        entity = CapturedEntity(name)
        entity.parents = parents

        try:
            entity.load(dir, context)
            entity.apply_animation(context)
        except Exception as e:
            # TODO: Propogate this to the UI
            print(f"Failed to import entity '{name}': {e}")
