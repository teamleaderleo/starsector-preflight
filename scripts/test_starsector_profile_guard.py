import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("starsector_profile_guard.py")
spec = importlib.util.spec_from_file_location("starsector_profile_guard", MODULE_PATH)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def profile(fingerprint: str, mods: list[dict]) -> dict:
    return {
        "profileFingerprint": fingerprint,
        "fingerprintKind": "ordered-path-size-mtime-v1",
        "mods": mods,
    }


def mod(
    mod_id: str,
    files: int = 10,
    bytes_: int = 100,
    images: int = 2,
    image_bytes: int = 20,
) -> dict:
    return {
        "id": mod_id,
        "directory": f"/mods/{mod_id}",
        "files": files,
        "bytes": bytes_,
        "imageFiles": images,
        "imageBytes": image_bytes,
        "soundFiles": 0,
        "soundBytes": 0,
        "looseJavaFiles": 0,
        "looseJavaBytes": 0,
        "jarFiles": 0,
        "jarBytes": 0,
        "dataFiles": 0,
        "dataBytes": 0,
    }


class ProfileGuardTest(unittest.TestCase):
    def test_identical_profile_is_stable(self):
        expected = profile("same", [mod("shaderLib")])
        result = module.compare_profiles(expected, expected)
        self.assertTrue(result["stable"])
        self.assertEqual([], result["modDeltas"])
        self.assertFalse(result["graphicsLibRuntimeCacheCandidate"])

    def test_exact_graphicslib_image_growth_is_classified_but_unstable(self):
        before = profile("before", [mod("shaderLib")])
        after = profile(
            "after",
            [mod("shaderLib", files=12, bytes_=236, images=4, image_bytes=156)],
        )
        result = module.compare_profiles(before, after)
        self.assertFalse(result["stable"])
        self.assertTrue(result["graphicsLibRuntimeCacheCandidate"])
        self.assertEqual(
            {"files": 2, "bytes": 136, "imageFiles": 2, "imageBytes": 136},
            result["modDeltas"][0]["deltas"],
        )

    def test_non_image_or_multiple_mod_drift_is_not_graphicslib_cache_candidate(self):
        before = profile("before", [mod("shaderLib"), mod("other")])
        shader = mod("shaderLib", files=11, bytes_=101, images=2, image_bytes=20)
        other = mod("other", files=11)
        after = profile("after", [shader, other])
        result = module.compare_profiles(before, after)
        self.assertFalse(result["stable"])
        self.assertFalse(result["graphicsLibRuntimeCacheCandidate"])
        self.assertEqual(2, len(result["modDeltas"]))

    def test_extracts_profile_from_prepare_report(self):
        expected = profile("same", [mod("shaderLib")])
        report = {"stages": {"census": {"details": {"profile": expected}}}}
        self.assertEqual(expected, module.extract_profile(report))


if __name__ == "__main__":
    unittest.main()
