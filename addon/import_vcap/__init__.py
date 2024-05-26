# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTIBILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program. If not, see <http://www.gnu.org/licenses/>.

bl_info = {
    "name" : "Import Minecraft Replay",
    "author" : "Igrium",
    "description" : "The Blender component of Igrium's Replay Exporter",
    "blender" : (3, 6, 0),
    "version" : (0, 0, 0),
    "location" : "",
    "warning" : "This addon is still in development.",
    "category" : "Import-Export"
}

from . import operators, import_replay_operator, data

def register():
    data.register()
    operators.register()
    import_replay_operator.register()

def unregister():
    data.unregister()
    operators.unregister()
    import_replay_operator.unregister()
