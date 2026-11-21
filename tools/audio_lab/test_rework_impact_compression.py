"""Regression tests for the redesigned physics-excited, Bloop-derived sounds."""
import unittest

import numpy as np

from analyze_and_render import SAMPLE_RATE
from rework_impact_compression import (
    bloop_resonance, contact_excitation, deformation_driver, elastic_impact,
    phase_coherent_loop, resample_controls, verify_near_silence_when_still,
    wet_flex, wet_texture,
)


class ImpactCompressionReworkTest(unittest.TestCase):
    def setUp(self):
        self.n=96
        t=np.arange(self.n)/60
        self.trace={
            "time":t,
            "contact":np.array([0.0 if i<12 or i>53 else 1.0 for i in range(self.n)]),
            "normal_speed":np.array([260. if 12<=i<20 else 0. for i in range(self.n)]),
            "tangent_speed":np.zeros(self.n),
            "compression":np.array([0.0 if i<12 else .25*min(1.,(i-12)/12.)
                                     if i<30 else .25*max(0.,1-(i-30)/24.)
                                     if i<55 else 0.0 for i in range(self.n)]),
            "radius":np.ones(self.n)*29.0,
            "merge":np.zeros(self.n),
        }
        src_t=np.arange(int(.25*SAMPLE_RATE))/SAMPLE_RATE
        env=(1-np.exp(-src_t/.006))*np.exp(-src_t/.060)
        self.source=.43*np.sin(2*np.pi*(220*src_t+24*src_t**2))*env

    def test_still_contact_does_not_sing(self):
        verify_near_silence_when_still()

    def test_physics_derivative_drives_onset(self):
        _,control=resample_controls(self.trace)
        e=contact_excitation(control)
        self.assertGreater(float(e.sum()), .30)
        self.assertLess(float(e.sum()), 3.0)
        compression=deformation_driver(control)
        self.assertGreater(float(compression.sum()),.03)

    def test_impact_is_bounded_and_not_continuous_sine(self):
        wav,metrics=elastic_impact(self.trace,self.source)
        self.assertTrue(np.all(np.isfinite(wav)))
        self.assertGreater(float(np.max(np.abs(wav))),.001)
        self.assertLess(float(np.max(np.abs(wav))), .71)
        self.assertGreater(metrics["driver_integral"],.1)
        silent=np.copy(self.trace["normal_speed"])
        self.trace["normal_speed"]=np.zeros_like(silent)
        no_motion,_=elastic_impact(self.trace,self.source)
        self.assertLess(float(np.max(np.abs(no_motion))), 1e-7)

    def test_compression_is_motion_sensitive_and_both_versions_nonzero(self):
        a,ma=wet_flex(self.trace,self.source)
        b,mb=wet_texture(self.trace,self.source)
        self.assertGreater(float(np.max(np.abs(a))),.001)
        self.assertGreater(float(np.max(np.abs(b))),.001)
        self.assertLess(ma["peak"],.71)
        self.assertLess(mb["peak"],.71)
        self.assertTrue(np.all(np.isfinite(a)))
        self.assertTrue(np.all(np.isfinite(b)))
        self.assertGreater(float(np.sqrt(np.mean((a-b)**2))), .001)

    def test_periodic_texture_has_finite_loop_seam(self):
        period=phase_coherent_loop(self.source,start=.025,length=.14,overlap=.025)
        self.assertGreater(len(period),1000)
        self.assertTrue(np.all(np.isfinite(period)))
        self.assertLess(abs(period[-1]-period[0]),.06)


if __name__ == "__main__":
    unittest.main()
