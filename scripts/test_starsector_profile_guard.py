import importlib.util
import os
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("starsector_profile_guard.py")
spec = importlib.util.spec_from_file_location("starsector_profile_guard", MODULE_PATH)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def write(path: Path, value: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(value)


def make_profile(game: Path, second_mutable: bool = False) -> dict:
    mods = game / "mods"
    graphics = mods / "zz GraphicsLib-1.12.1"
    other = mods / "Other Mod"
    write(mods / "enabled_mods.json", b'{"enabledMods":["shaderLib","other"]}\n')
    write(graphics / "mod_info.json", b'{"id":"shaderLib"}\n')
    write(graphics / "data/config/settings.json", b"immutable\n")
    write(graphics / "cache/ship___SHIP_normal.png", b"normal-map")
    write(other / "mod_info.json", b'{"id":"other"}\n')
    write(game / "saves/common/shaderlib_cache_hash.data", b"12345")
    entries = [
        {"id": "shaderLib", "directory": str(graphics)},
        {"id": "other", "directory": str(other)},
    ]
    enabled = ["shaderLib", "other"]
    if second_mutable:
        duplicate = mods / "Duplicate GraphicsLib"
        write(duplicate / "mod_info.json", b'{"id":"shaderLib"}\n')
        entries.append({"id": "shaderLib", "directory": str(duplicate)})
        enabled.append("shaderLib")
    return {
        "installRoot": str(game),
        "modsDirectory": str(mods),
        "enabledModsFile": str(mods / "enabled_mods.json"),
        "enabledModIds": enabled,
        "mods": entries,
        "profileFingerprint": "census-before",
        "fingerprintKind": "ordered-path-size-mtime-v1",
    }


class ProfileGuardTest(unittest.TestCase):
    def test_ordinary_immutable_drift_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            profile = make_profile(game)
            baseline = module.capture_state(profile)
            immutable = game / "mods/Other Mod/mod_info.json"
            write(immutable, b'{"id":"other","changed":true}\n')
            actual = module.capture_state(profile)
            result = module.compare_states(baseline, actual)
            self.assertFalse(result["immutableProfileStable"])
            self.assertTrue(result["immutableDrift"]["changed"])

    def test_exact_graphicslib_cache_behavior_is_bounded_not_hidden(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            profile = make_profile(game)
            baseline = module.capture_state(profile)
            cache = game / "mods/zz GraphicsLib-1.12.1/cache"
            write(cache / "weapon___TURRET0_normal.png", b"generated")
            actual = module.capture_state(profile)
            result = module.compare_states(baseline, actual)
            self.assertTrue(result["immutableProfileStable"])
            self.assertTrue(result["mutableCache"]["validRuntimeShape"])
            self.assertTrue(result["mutableCache"]["changedDuringRun"])
            self.assertEqual(
                "weapon___TURRET0_normal.png",
                result["mutableCache"]["drift"]["added"][0]["relativePath"],
            )

    def test_unexpected_file_in_cache_subtree_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            profile = make_profile(game)
            write(
                game / "mods/zz GraphicsLib-1.12.1/cache/unexpected.json",
                b"not a generated normal",
            )
            with self.assertRaisesRegex(ValueError, "Unexpected file"):
                module.capture_state(profile)

    def test_changed_existing_cache_content_records_hashes_and_timestamps(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            profile = make_profile(game)
            baseline = module.capture_state(profile)
            cache_file = (
                game / "mods/zz GraphicsLib-1.12.1/cache/ship___SHIP_normal.png"
            )
            write(cache_file, b"replacement-normal-map")
            os.utime(cache_file, ns=(1_700_000_000_000_000_000,) * 2)
            actual = module.capture_state(profile)
            result = module.compare_states(baseline, actual)
            change = result["mutableCache"]["drift"]["changed"][0]
            self.assertNotEqual(change["before"]["sha256"], change["after"]["sha256"])
            self.assertNotEqual(change["before"]["mtimeNs"], change["after"]["mtimeNs"])
            self.assertFalse(result["mutableCache"]["contentEquivalentToBaseline"])

    def test_hash_control_rewrite_is_bounded_metadata_drift(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            profile = make_profile(game)
            baseline = module.capture_state(profile)
            control = game / "saves/common/shaderlib_cache_hash.data"
            os.utime(control, ns=(1_700_000_000_000_000_000,) * 2)
            actual = module.capture_state(profile)
            result = module.compare_states(baseline, actual)
            self.assertTrue(result["immutableProfileStable"])
            self.assertTrue(result["mutableCache"]["changedDuringRun"])
            self.assertTrue(result["mutableCache"]["contentEquivalentToBaseline"])
            self.assertIsNotNone(
                result["mutableCache"]["drift"]["hashControlChanged"]
            )

    def test_multiple_mutable_mods_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            profile = make_profile(game, second_mutable=True)
            with self.assertRaisesRegex(ValueError, "Multiple mutable mods"):
                module.capture_state(profile)

    def test_second_mod_cache_is_immutable_not_silently_allowed(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            profile = make_profile(game)
            other_cache = game / "mods/Other Mod/cache/generated.png"
            write(other_cache, b"before")
            baseline = module.capture_state(profile)
            write(other_cache, b"after")
            actual = module.capture_state(profile)
            result = module.compare_states(baseline, actual)
            self.assertFalse(result["immutableProfileStable"])
            changed = result["immutableDrift"]["changed"]
            self.assertEqual("other", changed[0]["after"]["modId"])
            self.assertEqual("cache/generated.png", changed[0]["after"]["relativePath"])

    def test_equivalent_prewarmed_state_is_restored_exactly(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game = root / "Starsector.app"
            profile = make_profile(game)
            snapshot = root / "snapshot"
            baseline = module.capture_state(profile, snapshot)
            cache = game / "mods/zz GraphicsLib-1.12.1/cache"
            write(cache / "ship___SHIP_normal.png", b"changed")
            write(cache / "new___MISSILE_normal.png", b"generated")
            write(game / "saves/common/shaderlib_cache_hash.data", b"99999")
            restored = module.restore_state(baseline, snapshot)
            self.assertTrue(restored["restored"])
            self.assertEqual(["new___MISSILE_normal.png"], restored["removedAddedFiles"])
            actual = module.capture_state(profile)
            result = module.compare_states(baseline, actual)
            self.assertTrue(result["mutableCache"]["exactEquivalentToBaseline"])

    def test_discovers_actual_macos_root_mod_directory_not_java_mods(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            profile = make_profile(game)
            state = module.capture_state(profile)
            self.assertEqual(str((game / "mods").resolve()), state["contract"]["modsDirectory"])
            self.assertEqual(
                str((game / "mods/zz GraphicsLib-1.12.1/cache").resolve()),
                state["contract"]["cacheDirectory"],
            )
            self.assertNotIn(
                "Contents/Resources/Java/mods", state["contract"]["cacheDirectory"]
            )


if __name__ == "__main__":
    unittest.main()
