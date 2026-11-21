"""C4 DSP regression: impact pitch, physically gated friction, silent pressure."""
import unittest
import numpy as np
from refine_c4 import SAMPLE_RATE, pitch_impact, sticky_friction, compression_whisper, rms, peak


class SoundC4Test(unittest.TestCase):
    def setUp(self):
        self.n=44100*2
        t=np.arange(self.n)/SAMPLE_RATE
        self.wave=.12*np.sin(2*np.pi*340*t)*np.exp(-10*t)
        self.trace={"time":np.arange(96)/60,
                    "tangent_speed":np.full(96,130.),
                    "contact":np.ones(96),
                    "compression":np.clip(np.sin(np.arange(96)*.12)*.10+.2,0,1)}

    def test_impact_pitch_and_peak_bound(self):
        a=pitch_impact(self.wave,1.09,self.n,.08)
        b=pitch_impact(self.wave,1.16,self.n,.08)
        self.assertTrue(np.all(np.isfinite(a)))
        self.assertLessEqual(peak(a),.080001)
        self.assertLessEqual(peak(b),.080001)
        self.assertGreater(rms(a),0)
        self.assertGreater(float(np.sqrt(np.mean((a-b)**2))),.001)

    def test_friction_follows_sliding_and_stops_without_motion(self):
        a=sticky_friction(self.wave,self.trace,False,.02)
        self.assertLessEqual(peak(a),.020001)
        self.assertGreater(rms(a),.00001)
        static=dict(self.trace)
        static["tangent_speed"]=np.zeros(96)
        self.assertLess(peak(sticky_friction(self.wave,static,True,.01)),1e-9)
        no_contact=dict(self.trace)
        no_contact["contact"]=np.zeros(96)
        self.assertLess(peak(sticky_friction(self.wave,no_contact,True,.01)),1e-9)

    def test_static_compression_is_silent(self):
        still=dict(self.trace)
        still["compression"]=np.ones(96)*.25
        self.assertLess(peak(compression_whisper(self.wave,still,self.n,.01)),1e-8)

    def test_motion_can_create_very_quiet_compression(self):
        a=compression_whisper(self.wave,self.trace,self.n,.008)
        self.assertGreater(rms(a),.00001)
        self.assertLessEqual(peak(a),.008001)
        self.assertTrue(np.all(np.isfinite(a)))


if __name__=="__main__":
    unittest.main()
