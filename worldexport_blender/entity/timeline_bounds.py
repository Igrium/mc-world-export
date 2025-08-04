from dataclasses import dataclass
from typing import Iterable, Tuple
from ..anim.animation_curve import AnimationCurve
from .captured_entity import CapturedEntity

@dataclass
class TimelineRange:
    start_tick: int
    """The first tick in the range, inclusive
    """
    
    length: int
    """The length of the range in ticks.
    """
    
    @property
    def end_tick(self):
        """The last tick in the range, exclusive
        """
        return self.start_tick + self.length

    @staticmethod
    def min_max(start_tick: int, end_tick: int):
        return TimelineRange(start_tick, end_tick - start_tick)
    
    @staticmethod
    def from_curve(curve: AnimationCurve):
        return TimelineRange(curve.tick_offset, curve.length)
    
def get_range_bounds(ranges: Iterable[TimelineRange]) -> TimelineRange:
    """Return a timeline range that spans all of the supplied ranges

    Args:
        ranges (Iterable[TimelineRange]): All relevent ranges.
    """
    init = False
    min_val = 0
    max_val = 0
    
    for r in ranges:
        if not init or r.start_tick < min_val:
            min_val = r.start_tick
        if not init or r.end_tick > max_val:
            max_val = r.end_tick
        init = True
    
    return TimelineRange.min_max(min_val, max_val)


    
def get_part_bounds(curves: Iterable[AnimationCurve]) -> TimelineRange:
    """Return a timeline range that spans all ticks where a model part is visible.

    Args:
        curves (Iterable[AnimationCurve]): All the model part's curves

    """
    return get_range_bounds((TimelineRange(curve.tick_offset, curve.length) for curve in curves))


def get_entity_bounds(entity: CapturedEntity) -> TimelineRange:
    """Return a timeline range that spans all ticks where an entity is visible.

    Args:
        entity (CapturedEntity): Entity to use
    """
    return get_range_bounds(get_part_bounds(c) for c in entity.curves.values())

def get_animation_bounds(entities: Iterable[CapturedEntity]) -> TimelineRange:
    """Return a timeline range that spans all ticks where any entity is visible.

    Args:
        entities (Iterable[CapturedEntity]): All entities in animation
    """
    return get_range_bounds(get_entity_bounds(e) for e in entities)



def merge_ranges(ranges: Iterable[TimelineRange]):
    """Merge any overlapping ranges into one

    Args:
        ranges (Iterable[TimelineRange]): Timeline ranges to merge

    Returns:
        list[TimelineRange]: A list of discrete timeline ranges covering all the time covered by the input ranges.
    """
    res = _merge_overlap([[r.start_tick, r.end_tick] for r in ranges])
    return [TimelineRange.min_max(l[0], l[1]) for l in res]
    
# Max exclusive
def _merge_overlap(arr: list[list[int]]) -> list[list[int]]:
    # Sort intervals based on start values
    arr.sort()

    res: list[list[int]] = []
    res.append(arr[0])

    for i in range(1, len(arr)):
        last = res[-1]
        curr = arr[i]

        # If current interval overlaps with the last merged
        # interval, merge them.
        if curr[0] <= last[1]:
            last[1] = max(last[1], curr[1])
        else:
            res.append(curr)

    return res