# Run during a fresh recording near the recorded player.
playsound minecraft:block.note_block.harp block @a ~ ~ ~ .7 1.1
playsound minecraft:ambient.cave ambient @a ~ ~ ~ .4 .8
# The isolated Java harness also emits an entity-bound sound from the nearby pig,
# starts a client loop and a three-tick delayed sound during scene export, then stops
# the loop explicitly. Those client-loop checks are not server-command fixtures.
