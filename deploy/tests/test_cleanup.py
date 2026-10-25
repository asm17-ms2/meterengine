import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(os.environ.get("DEPLOY_CLEANUP_SCRIPT", Path(__file__).resolve().parents[1] / "deploy.sh"))

HARNESS = r'''
set -euo pipefail
source "$1"
REPO_DIR=$2
COMPOSE_PROJECT=meterengine-prod
DEPLOY_TIMING=off
DRAIN_SECONDS=5
compose=(docker compose)
target_color=$TARGET
live_color=$LIVE
legacy_container=$LEGACY
serving=$SERVING
switched=$SWITCHED
discard_target_on_failure=$DISCARD

docker() {
    local url upstream route list
    case "$1" in
    ps) printf 'caddy-test\n' ;;
    exec)
        url=${!#}
        if [[ "$url" == http://localhost:8081/* ]]; then
            route=$ROUTE_OVERRIDE
            if [ -z "$route" ]; then
                for upstream in $(cat "$REPO_DIR/active"); do
                    if [ "$(cat "$REPO_DIR/ready/$upstream" 2>/dev/null || true)" = UP ]; then
                        route=$upstream
                        break
                    fi
                done
            fi
            printf 'http %s\n' "$route" >>"$REPO_DIR/events"
            printf '  HTTP/1.1 200 OK\n  X-Upstream: %s\n' "$route" >&2
            if [ "$HTTP_FAIL" = all ] || { [ "$HTTP_FAIL" = after_drain ] && [ -f "$REPO_DIR/drained" ]; }; then
                return 8
            fi
            [ -n "$route" ]
        else
            upstream=${url#http://}
            upstream=${upstream%%/*}
            printf 'ready %s\n' "$upstream" >>"$REPO_DIR/events"
            if [ -f "$REPO_DIR/ready/$upstream" ]; then
                printf '{"status":"%s"}\n' "$(cat "$REPO_DIR/ready/$upstream")"
            else
                return 1
            fi
        fi
        ;;
    compose)
        shift
        case "$1" in
        exec)
            [ "$*" = 'exec -T caddy caddy reload --config /etc/caddy/Caddyfile' ] || return 98
            list=$(sed 's/^to //' "$REPO_DIR/deploy/caddy/upstreams.caddy")
            printf 'reload %s\n' "$list" >>"$REPO_DIR/events"
            if [ "$RELOAD_FAIL" = all ] ||
                { [ "$RELOAD_FAIL" = previous ] && [ "$list" != "backend-$target_color:8080" ]; } ||
                { [ "$RELOAD_FAIL" = target ] && [ "$list" = "backend-$target_color:8080" ]; }; then
                return 1
            fi
            printf '%s\n' "$list" >"$REPO_DIR/active"
            ;;
        stop | rm)
            printf '%s\n' "$*" >>"$REPO_DIR/events"
            [ "$REMOVE_FAIL" != yes ]
            ;;
        *) return 98 ;;
        esac
        ;;
    *) return 98 ;;
    esac
}

sleep() {
    printf 'sleep %s\n' "$1" >>"$REPO_DIR/events"
    if [ "$1" = "$DRAIN_SECONDS" ]; then
        touch "$REPO_DIR/drained"
        if [ -n "$DRAIN_DIES" ]; then
            rm -f "$REPO_DIR/ready/$DRAIN_DIES"
        fi
        [ "$SLEEP_FAIL" != yes ] || return 1
    fi
}

aws() { return 98; }
curl() { return 98; }
git() { return 98; }

mv() {
    local list
    list=$(sed 's/^to //' "$1")
    if [ "$WRITE_FAIL" = all ] ||
        { [ "$WRITE_FAIL" = previous ] && [ "$list" != "backend-$target_color:8080" ]; } ||
        { [ "$WRITE_FAIL" = target ] && [ "$list" = "backend-$target_color:8080" ]; }; then
        return 1
    fi
    command mv "$@"
}

trap on_exit EXIT
exit "$DEPLOY_EXIT"
'''


class CleanupTests(unittest.TestCase):
    def run_cleanup(self, *, ready=("backend-green:8080",), down=(), **settings):
        defaults = dict(
            TARGET="green", LIVE="blue", LEGACY="", SERVING="backend-blue:8080",
            SWITCHED="yes", DISCARD="yes", DEPLOY_EXIT="23", ACTIVE="backend-green:8080",
            ROUTE_OVERRIDE="", HTTP_FAIL="", RELOAD_FAIL="", DRAIN_DIES="",
            REMOVE_FAIL="", SLEEP_FAIL="", WRITE_FAIL="",
        )
        defaults.update(settings)
        active = defaults.pop("ACTIVE")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "deploy/caddy").mkdir(parents=True)
            (root / "ready").mkdir()
            for upstream in ready:
                (root / "ready" / upstream).write_text("UP")
            for upstream in down:
                (root / "ready" / upstream).write_text("DOWN")
            (root / "active").write_text(active)
            (root / "events").touch()
            upstreams = root / "deploy/caddy/upstreams.caddy"
            upstreams.write_text(f"to {active}\n")
            source = SCRIPT.read_text()
            functions = root / "functions.sh"
            functions.write_text(source[source.index("log() {"):source.rindex('\nmain "$@"')])
            result = subprocess.run(
                ["bash", "-c", HARNESS, "cleanup-test", str(functions), str(root)],
                env={**os.environ, **defaults}, capture_output=True, text=True, timeout=10,
            )
            self.assertEqual(result.returncode, int(defaults["DEPLOY_EXIT"]), result.stderr)
            return dict(
                events=(root / "events").read_text().splitlines(),
                active=(root / "active").read_text().strip(),
                configured=upstreams.read_text().strip(), output=result.stdout,
            )

    def assert_kept(self, result):
        self.assertFalse(any(event.startswith(("stop ", "rm ")) for event in result["events"]), result)

    def assert_discarded(self, result, previous="backend-blue:8080", target="green"):
        self.assertEqual(result["active"], previous)
        self.assertEqual(result["configured"], f"to {previous}")
        removals = [event for event in result["events"] if event.startswith(("stop ", "rm "))]
        self.assertEqual(removals, [f"stop backend-{target}", f"rm -f backend-{target}"])
        drain = result["events"].index("sleep 5")
        stop = result["events"].index(f"stop backend-{target}")
        self.assertIn(f"http {previous}", result["events"][:drain])
        self.assertIn(f"http {previous}", result["events"][drain + 1:stop])
        self.assertEqual(result["events"][stop - 1], f"ready {previous}")
        self.assertLess(result["events"].index(f"ready {previous}"), result["events"].index(f"reload {previous}"))

    def test_dead_previous_keeps_healthy_target_and_routes_to_it(self):
        result = self.run_cleanup()
        self.assert_kept(result)
        self.assertEqual(result["active"], "backend-green:8080")
        self.assertEqual(result["configured"], "to backend-green:8080")

    def test_healthy_previous_is_confirmed_before_and_after_drain(self):
        for switched in ("no", "yes"):
            with self.subTest(switched=switched):
                result = self.run_cleanup(
                    ready=("backend-blue:8080", "backend-green:8080"),
                    SWITCHED=switched,
                    ACTIVE="backend-blue:8080" if switched == "no" else "backend-green:8080",
                )
                self.assert_discarded(result)

    def test_dead_previous_before_switch_restores_healthy_target(self):
        result = self.run_cleanup(SWITCHED="no", ACTIVE="backend-blue:8080")
        self.assert_kept(result)
        self.assertEqual(result["active"], "backend-green:8080")

    def test_readiness_down_is_not_a_restore_candidate(self):
        for previous in ("backend-blue:8080", "backend:8080"):
            with self.subTest(previous=previous):
                result = self.run_cleanup(
                    down=(previous,), SERVING=previous,
                    LEGACY="legacy-test" if previous == "backend:8080" else "",
                )
                self.assert_kept(result)
                self.assertEqual(result["active"], "backend-green:8080")
                self.assertNotIn(f"reload {previous}", result["events"])

    def test_legacy_survives_or_dies_before_cleanup(self):
        for alive in (False, True):
            for switched in ("no", "yes"):
                with self.subTest(alive=alive, switched=switched):
                    result = self.run_cleanup(
                        ready=("backend-green:8080", "backend:8080") if alive else ("backend-green:8080",),
                        LIVE="", LEGACY="legacy-test", SERVING="backend:8080",
                        SWITCHED=switched, ACTIVE="backend:8080" if switched == "no" else "backend-green:8080",
                    )
                    if alive:
                        self.assert_discarded(result, previous="backend:8080")
                    else:
                        self.assert_kept(result)
                        self.assertEqual(result["active"], "backend-green:8080")

    def test_target_in_serving_is_never_a_restore_candidate(self):
        result = self.run_cleanup(SERVING="backend-green:8080 backend-blue:8080")
        self.assert_kept(result)
        self.assertNotIn("sleep 5", result["events"])

    def test_only_ready_previous_upstreams_are_reloaded(self):
        result = self.run_cleanup(
            ready=("backend-green:8080", "backend:8080"), LEGACY="legacy-test",
            SERVING="backend-blue:8080 backend-green:8080 backend:8080",
        )
        self.assert_discarded(result, previous="backend:8080")

    def test_live_snapshot_does_not_prevent_current_readiness_check(self):
        result = self.run_cleanup(
            ready=("backend-blue:8080", "backend-green:8080"), LIVE="",
            SERVING="backend-blue:8080 backend-green:8080",
        )
        self.assert_discarded(result)

    def test_failed_http_with_a_previous_header_cannot_authorize_removal(self):
        result = self.run_cleanup(
            ready=("backend-blue:8080", "backend-green:8080"), HTTP_FAIL="all",
        )
        self.assert_kept(result)
        self.assertEqual(result["active"], "backend-green:8080")
        self.assertNotIn("sleep 5", result["events"])

    def test_unexpected_route_cannot_authorize_removal(self):
        for route in ("backend-green:8080", "backend-blue:80801", "unknown:8080"):
            with self.subTest(route=route):
                result = self.run_cleanup(
                    ready=("backend-blue:8080", "backend-green:8080"), ROUTE_OVERRIDE=route,
                )
                self.assert_kept(result)
                self.assertEqual(result["active"], "backend-green:8080")

    def test_previous_dies_during_drain(self):
        for previous in ("backend-blue:8080", "backend:8080"):
            for stale_http in (False, True):
                with self.subTest(previous=previous, stale_http=stale_http):
                    result = self.run_cleanup(
                        ready=(previous, "backend-green:8080"), SERVING=previous,
                        DRAIN_DIES=previous, LEGACY="legacy-test" if previous == "backend:8080" else "",
                        ROUTE_OVERRIDE=previous if stale_http else "",
                    )
                    self.assert_kept(result)
                    self.assertEqual(result["active"], "backend-green:8080")
                    self.assertIn("sleep 5", result["events"])
                    self.assertEqual(result["configured"], "to backend-green:8080")

    def test_caddy_confirmation_fails_after_drain(self):
        result = self.run_cleanup(
            ready=("backend-blue:8080", "backend-green:8080"), HTTP_FAIL="after_drain",
        )
        self.assert_kept(result)
        self.assertEqual(result["active"], "backend-green:8080")
        self.assertIn("sleep 5", result["events"])

    def test_previous_reload_failure_keeps_target_and_attempts_recovery(self):
        result = self.run_cleanup(
            ready=("backend-blue:8080", "backend-green:8080"), RELOAD_FAIL="previous",
        )
        self.assert_kept(result)
        self.assertEqual(result["events"].count("reload backend-blue:8080"), 10)
        self.assertEqual(result["active"], "backend-green:8080")
        self.assertIn("reload backend-green:8080", result["events"])

    def test_target_recovery_failure_still_keeps_target(self):
        result = self.run_cleanup(
            RELOAD_FAIL="target", SWITCHED="no", ACTIVE="backend-blue:8080",
        )
        self.assert_kept(result)
        self.assertEqual(result["active"], "backend-blue:8080")
        self.assertEqual(result["configured"], "to backend-green:8080")
        self.assertEqual(result["events"].count("reload backend-green:8080"), 10)

    def test_both_reload_attempts_fail(self):
        result = self.run_cleanup(
            ready=("backend-blue:8080", "backend-green:8080"), RELOAD_FAIL="all",
        )
        self.assert_kept(result)
        self.assertEqual(result["events"].count("reload backend-blue:8080"), 10)
        self.assertEqual(result["events"].count("reload backend-green:8080"), 10)

    def test_upstream_write_failures_keep_target(self):
        for failure in ("previous", "target", "all"):
            with self.subTest(failure=failure):
                result = self.run_cleanup(
                    ready=("backend-blue:8080", "backend-green:8080"),
                    WRITE_FAIL=failure, HTTP_FAIL="all" if failure == "target" else "",
                )
                self.assert_kept(result)

    def test_no_previous_backend_at_start(self):
        for switched in ("no", "yes"):
            for healthy in (False, True):
                with self.subTest(switched=switched, healthy=healthy):
                    result = self.run_cleanup(
                        ready=("backend-green:8080",) if healthy else (),
                        LIVE="", SERVING="backend-green:8080", SWITCHED=switched,
                    )
                    self.assert_kept(result)
                    self.assertEqual(result["active"], "backend-green:8080")

    def test_neither_backend_ready_keeps_target_without_reloading(self):
        result = self.run_cleanup(ready=())
        self.assert_kept(result)
        self.assertFalse(any(event.startswith("reload ") for event in result["events"]))

    def test_verified_switch_failure_and_success_skip_cleanup(self):
        for code, discard in (("23", "no"), ("0", "yes"), ("0", "no")):
            for switched in ("no", "yes"):
                with self.subTest(code=code, discard=discard, switched=switched):
                    result = self.run_cleanup(DEPLOY_EXIT=code, DISCARD=discard, SWITCHED=switched)
                    self.assertEqual(result["events"], [])
                    self.assertEqual(result["active"], "backend-green:8080")

    def test_drain_error_preserves_original_failure_code(self):
        result = self.run_cleanup(
            ready=("backend-blue:8080", "backend-green:8080"), SLEEP_FAIL="yes", DEPLOY_EXIT="37",
        )
        self.assert_kept(result)
        self.assertEqual(result["active"], "backend-green:8080")

    def test_removal_errors_preserve_original_failure_code(self):
        result = self.run_cleanup(
            ready=("backend-blue:8080", "backend-green:8080"), REMOVE_FAIL="yes", DEPLOY_EXIT="37",
        )
        self.assert_discarded(result)

    def test_blue_target_uses_the_same_cleanup(self):
        result = self.run_cleanup(
            ready=("backend-blue:8080", "backend-green:8080"), TARGET="blue", LIVE="green",
            SERVING="backend-green:8080", ACTIVE="backend-blue:8080",
        )
        self.assert_discarded(result, previous="backend-green:8080", target="blue")


if __name__ == "__main__":
    unittest.main()
