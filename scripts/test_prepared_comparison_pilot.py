"""Regression tests for run-prepared-pixel-main-menu-comparison-pilot.sh.

These cover the comparison-pilot defect where a prepared half that merely executed
(but whose adapter failed open on a stale index) was reported the same way as a half
that never ran, and where each half was launched with the initial global artifacts
instead of the artifacts validated for that half.
"""

import json
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).with_name("run-prepared-pixel-main-menu-comparison-pilot.sh")
SCRIPT_TEXT = SCRIPT_PATH.read_text(encoding="utf-8")


def extract(pattern: str) -> str:
    match = re.search(pattern, SCRIPT_TEXT, re.DOTALL)
    assert match, f"could not locate pattern in pilot script: {pattern}"
    return match.group("filter")


def jq_accepts(jq_filter: str, document: dict, *extra_args: str) -> bool:
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as handle:
        json.dump(document, handle)
        path = handle.name
    result = subprocess.run(
        ["jq", "-e", jq_filter, *extra_args, path],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )
    Path(path).unlink(missing_ok=True)
    return result.returncode == 0


class ArtifactSelectionTest(unittest.TestCase):
    def test_each_half_launches_with_its_validated_artifacts(self):
        run_args = re.search(r"run_args=\((?P<body>.*?)\n    \)", SCRIPT_TEXT, re.DOTALL)
        self.assertIsNotNone(run_args, "run_args launch block not found")
        body = run_args.group("body")
        # The launch must use the per-half validated pair.
        self.assertIn('--texture-manifest "$half_texture_manifest"', body)
        self.assertIn('--texture-index "$half_resource_index"', body)
        # It must NOT reuse the initial global preparation artifacts, which can go stale.
        self.assertNotIn("$TEXTURE_MANIFEST", body)
        self.assertNotIn("$RESOURCE_INDEX", body)

    def test_per_half_artifacts_come_from_the_validated_report(self):
        self.assertIn(
            'half_resource_index="$(jq -er \'.resourceIndex\' "$profile_before_report")"',
            SCRIPT_TEXT,
        )
        self.assertIn(
            'half_texture_manifest="$(jq -er \'.stages.textures.details.manifest\' '
            '"$profile_before_report")"',
            SCRIPT_TEXT,
        )


class FailureClassificationTest(unittest.TestCase):
    def test_message_distinguishes_execution_from_passing(self):
        self.assertIn(
            "executed but did not pass all acceptance checks", SCRIPT_TEXT
        )

    def test_message_distinguishes_the_four_failure_classes(self):
        self.assertIn("operator rejected one or more visual/lifecycle questions", SCRIPT_TEXT)
        self.assertIn("lifecycle/exit-code/texture-mode acceptance failed", SCRIPT_TEXT)
        self.assertIn("prepared telemetry rejected", SCRIPT_TEXT)
        self.assertIn("prepared-pixel path was NOT exercised", SCRIPT_TEXT)
        # The stale-index disable reason must be surfaced to the operator.
        self.assertIn("disableReasons=", SCRIPT_TEXT)


@unittest.skipUnless(shutil.which("jq"), "jq is required for acceptance-predicate tests")
class AcceptancePredicateTest(unittest.TestCase):
    """A clean prepared run whose adapter failed open must be a telemetry rejection,
    not a lifecycle/Starsector-failure rejection."""

    LIFECYCLE_FILTER = extract(r"jq -e '(?P<filter>[^']*?)' --arg expectedTextureMode")
    TELEMETRY_FILTER = extract(
        r"jq -e '(?P<filter>[^']*?)'\s*\"\$run_dir/adapter\.json\""
    )

    CLEAN_PREPARED_RUN = {
        "outcome": "COMPLETED",
        "exitCode": 0,
        "launcherExitCode": 0,
        "lifecycleEvidence": {"fatalDetected": False},
        "textureAdapterMode": "PREPARED_PIXELS",
        "started": "2026-07-23T12:50:00.123456Z",
        "ended": "2026-07-23T12:51:07.654321Z",
    }

    # Mirrors the archived incident: the adapter failed open on a stale index.
    FAILED_OPEN_ADAPTER = {
        "transformationsApplied": 0,
        "containedFailures": 0,
        "textureCompatibility": {
            "disableReasons": ["index-stale"],
            "preparedPixels": {
                "coherentDirectEnabled": True,
                "hits": 0,
                "coherentDirectHits": 0,
                "fallbacks": 0,
                "internalErrors": 0,
                "activeDirectBytes": 0,
                "activeBuffers": 0,
                "pendingBuffers": 0,
            },
        },
    }

    HEALTHY_ADAPTER = {
        "transformationsApplied": 1,
        "containedFailures": 0,
        "textureCompatibility": {
            "disableReasons": [],
            "preparedPixels": {
                "coherentDirectEnabled": True,
                "hits": 5,
                "coherentDirectHits": 5,
                "fallbacks": 0,
                "internalErrors": 0,
                "activeDirectBytes": 0,
                "activeBuffers": 0,
                "pendingBuffers": 0,
            },
        },
    }

    def test_clean_run_lifecycle_is_accepted(self):
        self.assertTrue(
            jq_accepts(
                self.LIFECYCLE_FILTER,
                self.CLEAN_PREPARED_RUN,
                "--arg",
                "expectedTextureMode",
                "PREPARED_PIXELS",
                "--argjson",
                "minRuntime",
                "30",
            ),
            "a COMPLETED, clean-exit prepared run must satisfy the lifecycle predicate",
        )

    def test_failed_open_adapter_is_a_telemetry_rejection(self):
        self.assertFalse(
            jq_accepts(self.TELEMETRY_FILTER, self.FAILED_OPEN_ADAPTER),
            "transformationsApplied=0 with no prepared-pixel hits must be rejected",
        )

    def test_healthy_adapter_is_accepted(self):
        self.assertTrue(
            jq_accepts(self.TELEMETRY_FILTER, self.HEALTHY_ADAPTER),
            "a real prepared-pixel run must satisfy the telemetry predicate",
        )


if __name__ == "__main__":
    unittest.main()
