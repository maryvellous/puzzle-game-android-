"""Synchronized preview geometry tests: actual runtime files are made by JVM."""
import csv
import tempfile
import unittest
from pathlib import Path

from render_sync_video import (
    PHYSICS_SECONDS, SCENARIO_SECONDS, closest_frame, load_mesh, to_screen,
)


class PhysicalVideoTest(unittest.TestCase):
    def test_mesh_trace_rejects_missing_nodes_and_preserves_24_node_geometry(self):
        with tempfile.TemporaryDirectory() as temp:
            file = Path(temp) / "mesh.csv"
            with file.open("w", encoding="utf-8", newline="") as f:
                writer = csv.writer(f)
                writer.writerow(
                    ("scenario", "frame", "time", "blob_id",
                     "tier", "node", "x", "y"))
                for frame in range(97):
                    for node in range(24):
                        writer.writerow(("impact", frame, frame / 60,
                                         1, 2, node, 210 + node, 400 - node))
            frames = load_mesh(file)["impact"]
            self.assertEqual(len(frames), 97)
            self.assertEqual(len(frames[0]["blobs"][1]["nodes"]), 24)
            self.assertEqual(frames[0]["blobs"][1]["nodes"][7], (217., 393.))
            self.assertEqual(closest_frame(frames, .025)["time"], 1 / 60)
            self.assertAlmostEqual(closest_frame(frames, 2.0)["time"], PHYSICS_SECONDS)
            self.assertGreater(SCENARIO_SECONDS, PHYSICS_SECONDS)
            # An absent node must fail instead of silently drawing a fake circle.
            rows = file.read_text().splitlines()
            file.write_text("\n".join(rows[:-1]) + "\n")
            with self.assertRaises(ValueError):
                load_mesh(file)

    def test_world_coordinates_map_inside_visible_debug_glass(self):
        l, t = to_screen(29, 155)
        r, b = to_screen(391, 850)
        self.assertAlmostEqual(l, 45)
        self.assertAlmostEqual(t, 150)
        self.assertAlmostEqual(r, 495)
        self.assertAlmostEqual(b, 846)


if __name__ == "__main__":
    unittest.main()
