import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("starsector_core_resource_guard.py")
spec = importlib.util.spec_from_file_location("starsector_core_resource_guard", MODULE_PATH)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def create_layout(game: Path, root: Path, include_mission: bool = True) -> None:
    missions = root / "data" / "missions"
    descriptor = missions / module.REQUIRED_MISSION / "descriptor.json"
    descriptor.parent.mkdir(parents=True, exist_ok=True)
    descriptor.write_text("{}\n", encoding="utf-8")
    entries = "mission,\n"
    if include_mission:
        entries += f"{module.REQUIRED_MISSION},\n"
    (missions / "mission_list.csv").write_text(entries, encoding="utf-8")


class CoreResourceGuardTest(unittest.TestCase):
    def test_discovers_actual_macos_java_layout(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            root = game / "Contents" / "Resources" / "Java"
            create_layout(game, root)
            result = module.discover(game)
            self.assertEqual(str(root.resolve()), result["resourceRoot"])
            self.assertTrue(result["missionListContainsRequiredMission"])

    def test_discovers_alternate_packaged_resource_layout(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            root = game / "Contents" / "Resources" / "starfarer.res" / "res"
            create_layout(game, root)
            result = module.discover(game)
            self.assertEqual(str(root.resolve()), result["resourceRoot"])

    def test_rejects_list_without_required_mission(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            root = game / "Contents" / "Resources" / "Java"
            create_layout(game, root, include_mission=False)
            with self.assertRaisesRegex(ValueError, "were not found"):
                module.discover(game)

    def test_rejects_ambiguous_roots(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            create_layout(game, game / "Contents" / "Resources" / "Java")
            create_layout(game, game / "Contents" / "Resources" / "starfarer.res" / "res")
            with self.assertRaisesRegex(ValueError, "ambiguous"):
                module.discover(game)

    def test_cli_writes_json(self):
        with tempfile.TemporaryDirectory() as temp:
            game = Path(temp) / "Starsector.app"
            root = game / "Contents" / "Resources" / "Java"
            create_layout(game, root)
            output = Path(temp) / "result.json"
            subprocess.run(
                [sys.executable, str(MODULE_PATH), "--game", str(game), "--output", str(output)],
                check=True,
            )
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(str(root.resolve()), payload["resourceRoot"])


if __name__ == "__main__":
    unittest.main()
