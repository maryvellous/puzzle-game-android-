"""Synthetic unit tests: no copyrighted audio used or committed."""
import unittest
import numpy as np

from material_bank import RATE, regions, split, join_partial, smooth_edges, rms_frames
from render_auditions import playback, blob_rate, tail_texture, contact_envelope, render_stress


class BankTest(unittest.TestCase):
    def source(self):
        duration = .42
        t = np.arange(round(duration * RATE)) / RATE
        # Nonuniform energy: opening attack, recognisable core, declining tail.
        wave = np.sin(2 * np.pi * (200 * t + 115 * t * t))
        env = np.zeros_like(t)
        active = (t > .025) & (t < .30)
        env[active] = np.interp(
            t[active], [.025, .055, .10, .19, .30],
            [.0, .22, .84, .33, .0])
        return wave * env * .7

    def test_region_boundaries_are_monotone_and_not_file_percentages(self):
        sample = self.source()
        r = regions(sample)
        self.assertLess(r["onset"], r["attack_end"])
        self.assertLess(r["attack_end"], r["tail_start"])
        self.assertLess(r["tail_start"], r["active_end"])
        self.assertLess(r["active_end"], len(sample))
        self.assertTrue(r["review_required"])
        self.assertFalse(r["tail_decay_not_found"])
        attack, tail, partial = split(sample, r)
        self.assertGreater(len(attack), 200)
        self.assertGreater(len(tail), 200)
        self.assertLess(len(partial), r["active_end"] - r["onset"])
        self.assertLess(np.max(np.abs(partial)), 1.0)
        self.assertAlmostEqual(float(partial[0]), 0.0, places=6)
        self.assertAlmostEqual(float(partial[-1]), 0.0, places=6)

    def test_no_energy_is_rejected(self):
        with self.assertRaises(ValueError):
            regions(np.zeros(RATE))
        with self.assertRaises(ValueError):
            regions(np.ones(10))

    def test_join_does_not_insert_middle(self):
        a = np.full(600, .3)
        b = np.full(900, -.25)
        cut = join_partial(a, b)
        self.assertLess(len(cut), len(a) + len(b))
        self.assertEqual(cut[0], .3)
        self.assertEqual(cut[-1], -.25)

    def test_size_controls_pitch_duration(self):
        impulse = np.zeros(1000)
        impulse[90] = .6
        self.assertGreater(len(playback(impulse, .7)), len(impulse))
        self.assertLess(len(playback(impulse, 1.35)), len(impulse))
        self.assertGreater(blob_rate(20), blob_rate(75))

    def test_granular_texture_is_finite_and_quiet_at_boundaries(self):
        tail = smooth_edges(self.source()[2200:6000])
        result = tail_texture(tail, RATE)
        self.assertTrue(np.isfinite(result).all())
        self.assertEqual(result[0], 0.)
        self.assertEqual(result.shape, (RATE,))

    def test_discrete_contact_controls_are_bounded(self):
        rows = [
            {"time": ".02", "dt": ".008", "tangent_speed": "12",
             "compression": ".1", "normal_speed": "2"},
            {"time": ".028", "dt": ".008", "tangent_speed": "44",
             "compression": ".2", "normal_speed": "3"},
        ]
        slip, pressure, normal = contact_envelope(rows, RATE)
        self.assertEqual(float(np.max(slip)), 44)
        self.assertEqual(float(np.max(pressure)), .2)
        self.assertEqual(float(np.max(normal)), 3)
        self.assertEqual(float(slip[0]), 0)

    def test_stress_preserves_every_full_fusion(self):
        full = [self.source()] * 4
        attacks = [self.source()[:800]] * 4
        audio, info = render_stress(full, attacks)
        self.assertEqual(info["complete_merge_voices"], 12)
        self.assertTrue(info["no_voice_was_truncated"])
        self.assertLessEqual(np.max(np.abs(audio)), .871)


if __name__ == "__main__":
    unittest.main()
