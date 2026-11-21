"""Regression tests for C3 subtle sound hierarchy and tail-only friction."""
import unittest
import numpy as np
from analyze_and_render import SAMPLE_RATE
from refine_subtle_mix import (
    active_end, friction_tail_from_previous, friction_from_physics, reduced, peak, rms,
)


class SubtleAudioHierarchyTest(unittest.TestCase):
    def setUp(self):
        self.rate=SAMPLE_RATE
        t=np.arange(SAMPLE_RATE*2,dtype=float)/SAMPLE_RATE
        self.old=np.zeros(len(t))
        # Intentionally loud unwanted onset and quieter wanted final tail.
        active_early=(t<.48)
        active_tail=(t>=.65)&(t<.86)
        self.old[active_early]=.18*np.sin(2*np.pi*430*t[active_early])
        self.old[active_tail]=.065*np.sin(2*np.pi*270*t[active_tail])
        self.trace={
            "time":np.arange(96)/60,
            "contact":np.ones(96),
            "tangent_speed":np.ones(96)*160.,
        }

    def test_tail_excludes_original_loud_onset(self):
        end=active_end(self.old)
        part,meta=friction_tail_from_previous(self.old)
        self.assertGreater(end/SAMPLE_RATE,.80)
        self.assertGreater(meta["tail_start_sec"],.60)
        self.assertLess(meta["tail_end_sec"],.90)
        self.assertGreater(len(part),int(.10*SAMPLE_RATE))
        self.assertTrue(np.all(np.isfinite(part)))

    def test_frictions_are_barely_audible_relative_to_fusion(self):
        fake_fusion_peak=.50
        for maximum,brightness in ((fake_fusion_peak*.055,.75),
                                   (fake_fusion_peak*.027,.35)):
            sound,info=friction_from_physics(self.old,self.trace,maximum,brightness)
            self.assertLessEqual(peak(sound),maximum+1e-8)
            self.assertGreater(rms(sound),.0001)
            self.assertGreater(info["tail_start_sec"],.6)

    def test_no_slide_means_silence(self):
        static=dict(self.trace)
        static["tangent_speed"]=np.zeros(96)
        sound,_=friction_from_physics(self.old,static,.024,.75)
        self.assertLess(peak(sound),1e-8)

    def test_levels_are_only_reduced_not_boosted(self):
        audio=np.array([0.,-.18,.18])
        self.assertAlmostEqual(peak(reduced(audio,.06)),.06)
        self.assertAlmostEqual(peak(reduced(audio,.50)),.18)
        self.assertLessEqual(peak(reduced(audio,.06)),.06)


if __name__=="__main__":
    unittest.main()
