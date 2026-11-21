"""Deterministic unit tests for the offline audio lab (no Android runtime)."""
import unittest
import numpy as np
from analyze_and_render import (
    SAMPLE_RATE, active_excerpt, spectral_features, modal_synthesis,
    hybrid_synthesis, protect, variation,
)


class AudioLabTest(unittest.TestCase):
    def setUp(self):
        t = np.arange(int(SAMPLE_RATE * .5)) / SAMPLE_RATE
        env = (1 - np.exp(-t / .012)) * np.exp(-t / .16)
        self.sample = (.55 * np.sin(2 * np.pi * (330 * t + 8*t*t)) +
                       .22 * np.sin(2 * np.pi * 730 * t)) * env

    def test_detects_active_excerpt(self):
        padded = np.concatenate((np.zeros(int(.08*SAMPLE_RATE)),
                                 self.sample, np.zeros(int(.12*SAMPLE_RATE))))
        x, meta = active_excerpt(padded)
        self.assertGreater(meta['active_start_seconds'], .06)
        self.assertLess(meta['active_seconds'], .55)
        self.assertGreater(float(np.max(np.abs(x))), .1)

    def test_modal_and_granular_are_finite_and_distinct(self):
        features = spectral_features(self.sample)
        modes = features['candidate_modes_hz']
        self.assertGreaterEqual(len(modes), 4)
        b = modal_synthesis(self.sample, modes)
        c = hybrid_synthesis(self.sample, b, 123)
        self.assertEqual(len(b), len(self.sample))
        self.assertEqual(len(c), len(self.sample))
        self.assertTrue(np.all(np.isfinite(b)))
        self.assertTrue(np.all(np.isfinite(c)))
        self.assertGreater(float(np.sqrt(np.mean(b*b))), .01)
        self.assertGreater(float(np.sqrt(np.mean(c*c))), .01)
        self.assertLess(float(np.max(np.abs(c))), .95)
        self.assertGreater(float(np.sqrt(np.mean((b-c)**2))), .01)

    def test_size_affects_length_and_energy_affects_gain(self):
        small = variation(self.sample, 'small', .8)
        large = variation(self.sample, 'large', .8)
        self.assertLess(len(small), len(self.sample))
        self.assertGreater(len(large), len(self.sample))
        low = variation(self.sample, 'medium', .2)
        high = variation(self.sample, 'medium', 1.)
        self.assertGreater(np.sqrt(np.mean(high*high)), np.sqrt(np.mean(low*low)))

    def test_protect_prevents_peak_clipping(self):
        a = np.array([1.2, -1.3, np.nan, np.inf])
        z = protect(a)
        self.assertTrue(np.all(np.isfinite(z)))
        self.assertLessEqual(float(np.max(np.abs(z))), .87+1e-9)


if __name__ == '__main__':
    unittest.main()
