"""C4.1 regressions: no hidden merger; byte-identical approved sounds; audible impacts."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

import numpy as np

from prepare_c41_previews import (
    SR, audible_excerpt, impact_for_audition, peak, read_pcm16, rms, run,
    sequence, write_pcm16,
)


class C41PreviewTest(unittest.TestCase):
    def setUp(self):
        self.time = np.arange(SR) / SR
        env = np.where(self.time > .18, np.exp(-45 * (self.time - .18)), 0)
        self.impact = .06 * np.sin(2*np.pi*410*self.time) * env

    def test_impact_louder_but_always_below_merge(self):
        a, details = impact_for_audition(self.impact, .51, .32)
        b, _ = impact_for_audition(self.impact, .51, .40)
        self.assertAlmostEqual(peak(a), .51*.32, places=5)
        self.assertAlmostEqual(peak(b), .51*.40, places=5)
        self.assertGreater(rms(a), rms(self.impact))
        self.assertGreater(rms(b), rms(a))
        self.assertLess(peak(b), .51)
        self.assertGreater(details["gain_db"], 0)

    def test_no_fusion_can_be_added_to_shortened_impact(self):
        excerpt = audible_excerpt(self.impact)
        self.assertLess(len(excerpt), len(self.impact))
        mono = sequence([excerpt, excerpt])
        self.assertEqual(len(mono), len(excerpt)*2 + round(.42*SR)*2)
        self.assertGreater(float(np.max(np.abs(mono))), 0)

    def test_integration_original_c4_good_sounds_are_unchanged(self):
        with TemporaryDirectory() as temp:
            inp = Path(temp)/"c4"
            out = Path(temp)/"c41"
            inp.mkdir()
            signals = {
                "01_impact_A_pitch_plus_9pct.wav":self.impact,
                "02_impact_B_pitch_plus_16pct.wav":self.impact*.83,
                "04_friction_adhesive_ghost.wav":np.sin(2*np.pi*75*self.time)*.007,
                "06_compression_B_whisper.wav":np.sin(2*np.pi*123*self.time)*.003,
                "fusion_anchor_UNCHANGED.wav":np.sin(2*np.pi*190*self.time)*.53,
            }
            for name,samples in signals.items():
                write_pcm16(inp/name,samples)
            report=run(inp,out)
            self.assertFalse(report["isolated_previews_contain_fusion"])
            self.assertEqual((inp/"04_friction_adhesive_ghost.wav").read_bytes(),
                             (out/"04_friction_adhesive_UNCHANGED.wav").read_bytes())
            self.assertEqual((inp/"06_compression_B_whisper.wav").read_bytes(),
                             (out/"05_compression_B_UNCHANGED.wav").read_bytes())
            compare=read_pcm16(out/"08_COMPARE_IMPACT_FRICTION_COMPRESSION_NO_MERGE.wav")
            individual=read_pcm16(out/"02_impact_A_plus9_audible_40percent.wav")
            friction=read_pcm16(out/"04_friction_adhesive_UNCHANGED.wav")
            compression=read_pcm16(out/"05_compression_B_UNCHANGED.wav")
            expected=sequence([audible_excerpt(individual),friction,compression])
            self.assertEqual(len(compare),len(expected))
            self.assertTrue(np.allclose(compare,expected,atol=1/32768*1.1))
            # No extra full-length fusion appended to the comparison.
            self.assertLess(len(compare), len(expected) + len(signals["fusion_anchor_UNCHANGED.wav"]))


if __name__=="__main__":
    unittest.main()
