"""Offline audition renderer invariants, without any recorded sound sources."""
import unittest
import numpy as np
from material_bank import RATE
from render_auditions import render_scenario, render_stress, TOTAL_SECONDS


class PreviewTest(unittest.TestCase):
    def fixtures(self):
        t = np.arange(round(.18 * RATE)) / RATE
        full = [np.sin(2 * np.pi * (145 + 20*i) * t) * .6 for i in range(4)]
        attacks = [np.sin(2 * np.pi * (400 + 20*i) * t[:1000]) * .2 for i in range(4)]
        tails = [np.sin(2 * np.pi * (175 + 15*i) * t[:2200]) * .2
                 * np.sin(np.linspace(0, np.pi, 2200))**2 for i in range(4)]
        return full, attacks, tails

    def row(self, kind, time, normal=0, slide=0, compression=.12):
        return {
            "scenario": "impact", "type": kind, "time": str(time), "dt": ".008",
            "blob_a": "1", "blob_b": "2", "result_id": "3",
            "x": "100", "y": "200", "nx": "1", "ny": "0",
            "normal_speed": str(normal), "tangent_speed": str(slide),
            "compression": str(compression), "radius_a": "32",
            "radius_b": "32", "projection_samples": "5", "contact_age": ".008",
        }

    def test_fusion_uses_entire_source_not_truncated_partial(self):
        full, attack, tail = self.fixtures()
        rows = [self.row("merge", .2)]
        audio, info = render_scenario("fusion", rows, full, attack, tail)
        left = round(.2 * RATE)
        original = full[0]
        np.testing.assert_allclose(audio[left:left+len(original)], original * .48,
                                   atol=1e-12)
        self.assertEqual(info["complete_fusion_voices"], 1)
        self.assertEqual(np.max(np.abs(audio[:left])), 0)
        self.assertEqual(np.max(np.abs(audio[left+len(original):])), 0)

    def test_nonfusions_cannot_access_full_bloop_buffers(self):
        full, attack, tail = self.fixtures()
        source = [self.row("contact", .05, normal=180)]
        normal, info = render_scenario("impact", source, full, attack, tail)
        # Replace full fusion with nonsense: output cannot change at all.
        different_full = [np.ones(RATE) for _ in range(4)]
        same, _ = render_scenario("impact", source, different_full, attack, tail)
        np.testing.assert_array_equal(normal, same)
        self.assertGreater(np.max(np.abs(normal)), .005)
        self.assertEqual(info["complete_fusion_voices"], 0)

    def test_sliding_tail_is_audible_but_much_quieter_than_fusion(self):
        full, attack, tail = self.fixtures()
        rows = [self.row("contact", i * .008, normal=0, slide=135) for i in range(35)]
        texture, data = render_scenario("slide", rows, full, attack, tail)
        fusion, _ = render_scenario("fusion", [self.row("merge", .1)],
                                    full, attack, tail)
        self.assertGreater(float(np.max(np.abs(texture))), .008)
        self.assertLess(float(np.max(np.abs(texture))),
                        .2 * float(np.max(np.abs(fusion))))
        self.assertEqual(data["nonfusion_full_bloops"], 0)

    def test_no_touch_is_silent(self):
        full, attack, tail = self.fixtures()
        result, _ = render_scenario("impact", [self.row("end", .2)],
                                    full, attack, tail)
        self.assertEqual(float(np.max(np.abs(result))), 0.)

    def test_overlaps_do_not_clip_or_truncate(self):
        full, attack, _ = self.fixtures()
        sound, info = render_stress(full, attack)
        self.assertTrue(np.all(np.isfinite(sound)))
        self.assertLessEqual(float(np.max(np.abs(sound))), .87 + 1e-9)
        self.assertTrue(info["no_voice_was_truncated"])


if __name__ == "__main__":
    unittest.main()
