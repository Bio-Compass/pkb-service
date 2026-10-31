"""Local BWTS -> live PKB -> PostgreSQL acceptance with explicit authority contract doubles.

Run with BWTS's external Python environment. Requires a built PKB bootJar and disposable
PKB_TEST_DATABASE_URL, PKB_TEST_DATABASE_USER, PKB_TEST_DATABASE_PASSWORD environment variables.
Never point this harness at shared or production data. It adds random test-owned facts only.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import logging
import os
from pathlib import Path
import socket
import subprocess
import sys
from threading import Thread
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from uuid import uuid4
from datetime import UTC, datetime, timedelta

PRODUCER = "biocompass-watertracker-service"
PERMISSIONS = {"create": "hydration:intake:create", "read": "hydration:intake:read",
               "aggregate": "hydration:summary:read", "delete": "hydration:intake:delete"}
USER, OTHER = str(uuid4()), str(uuid4())
TOKENS = {name: USER for name in ["mobile", "desktop-api", "browser", "telegram", "summary-only"]}
TOKENS["other-user"] = OTHER
AU_DOWN = False


def permissions(token):
    if token == "summary-only":
        return [PERMISSIONS["aggregate"]]
    return [value for key, value in PERMISSIONS.items() if key != "read" or token != "telegram"]


class Contracts(BaseHTTPRequestHandler):
    """Deliberately a fixture, not a substitute for BAS/BUPS/AU release evidence."""
    def log_message(self, *_):
        pass

    def reply(self, status, data):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = []
            while True:
                size = int(self.rfile.readline().split(b";", 1)[0], 16)
                if not size:
                    while self.rfile.readline().strip():
                        pass
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            raw = b"".join(chunks)
        else:
            raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        payload = json.loads(raw)
        if self.path == "/verify":
            assert self.headers.get("Authorization") == "Bearer fixture-pkb-to-bas"
            assert self.headers.get("X-BioCompass-Service") == "pkb-service"
            token = payload["token"]
            if token not in TOKENS or payload["resource_service"] != PRODUCER:
                return self.reply(200, {"active": False})
            return self.reply(200, {"active": True, "user_id": TOKENS[token], "actor_id": TOKENS[token],
                                   "resource_service": PRODUCER, "permissions": permissions(token), "purpose_of_use": "self"})
        if self.path == "/introspect":
            assert self.headers.get("Authorization") == "Bearer fixture-bwts-to-bas"
            assert self.headers.get("X-BioCompass-Service") == PRODUCER
            token = payload["token"]
            if token not in TOKENS:
                return self.reply(200, {"active": False})
            result = {"active": True, "user_id": TOKENS[token], "is_staff": False}
            if token in {"browser", "summary-only"}:
                result.update(token_type="browser_resource", aud=[PRODUCER], scope=" ".join(permissions(token)))
            if token == "telegram":
                result.update(delegated=True, aud=[PRODUCER], scope=" ".join(permissions(token)),
                              client_id="biocompass-telegram-bot", act={"sub": "biocompass-telegram-bot"})
            return self.reply(200, result)
        if self.path == "/decision":
            if AU_DOWN:
                return self.reply(503, {"detail": "fixture intentionally unavailable"})
            request = payload["input"]
            actor, resource = request["actor"], request["resource"]
            allowed = (request["producer_service"] == PRODUCER
                       and actor["user_id"] == resource["target_user_id"]
                       and PERMISSIONS.get(request["action"]) in actor["scopes"]
                       and "restricted" not in resource["privacy_scope"])
            return self.reply(200, {"result": {"allowed": allowed, "decision_reference": "fixture-au-decision",
                "action": request["action"], "target_user_id": resource["target_user_id"], "obligations": {}}})
        self.reply(404, {})

    def do_GET(self):
        assert self.headers.get("Authorization") == "Bearer fixture-bwts-to-bups"
        user = self.path.split("/")[-2]
        self.reply(200, {"user_id": user, "time_zone": "Europe/Amsterdam", "effective_time_zone": "Europe/Amsterdam",
                         "time_zone_source": "user"})


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


async def verify(bwts_checkout, base, contracts):
    import httpx
    from pydantic import SecretStr
    sys.path.insert(0, str(bwts_checkout))
    from app.core.config import Settings
    from app.main import create_app
    settings = Settings(_env_file=None, storage_backend="pkb", pkb_base_url=base,
        pkb_service_token=SecretStr("fixture-bwts-to-pkb"), auth_introspection_url=contracts + "/introspect",
        auth_introspection_service_token=SecretStr("fixture-bwts-to-bas"), bups_internal_base_url=contracts,
        bups_internal_service_token=SecretStr("fixture-bwts-to-bups"))
    app = create_app(settings)
    for name in ["httpx", "httpcore"]:
        logging.getLogger(name).setLevel(logging.WARNING)
    assert app.state.database is None, "PKB backend must not open the old database"
    root = "/api/v1/me/hydration"
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://bwts.test") as client:
            async def call(method, path, token="mobile", expected=200, **kwargs):
                response = await client.request(method, root + path, headers={"Authorization": "Bearer " + token}, **kwargs)
                assert response.status_code == expected, (method, path, response.status_code)
                return response.json() if response.content else None
            observed = (datetime.now(UTC) - timedelta(days=1)).replace(hour=12, minute=0, second=0, microsecond=0).isoformat()
            payload = {"value_ml": 250, "observed_at": observed, "idempotency_key": str(uuid4())}
            original = await call("POST", "/intake", expected=201, json=payload)
            for token in ["desktop-api", "browser", "telegram"]:
                replay = await call("POST", "/intake", token=token, json=payload)
                assert replay == original, "Replay must preserve UUID and source time across clients"
            await call("POST", "/intake", expected=409, json={**payload, "value_ml": 300})
            local_date = datetime.fromisoformat(observed).astimezone(__import__("zoneinfo").ZoneInfo("Europe/Amsterdam")).date().isoformat()
            for token in ["mobile", "desktop-api", "browser", "telegram"]:
                summary = await call("GET", "/today", token=token, params={"date": local_date})
                assert summary["actual_ml"] == 250 and summary["effective_time_zone"] == "Europe/Amsterdam"
            history = await call("GET", "/intakes", params={"date": local_date})
            assert [row["intake_id"] for row in history["intakes"]] == [original["intake_id"]]
            await call("GET", "/intakes", token="telegram", expected=403, params={"date": local_date})
            quick = await call("GET", "/quick-add-amounts", token="telegram")
            assert quick["quick_add_amounts_ml"][0] == 250
            for period in [7, 30]:
                statistics = await call("GET", "/statistics", token="browser", params={"period_days": period})
                assert len(statistics["days"]) == period
                assert sum(day["actual_ml"] for day in statistics["days"]) == 250
            other = await call("POST", "/intake", token="other-user", expected=201, json={**payload, "idempotency_key": str(uuid4())})
            await call("DELETE", "/intake/" + other["intake_id"], expected=204)
            assert (await call("GET", "/today", token="other-user", params={"date": local_date}))["actual_ml"] == 250
            race = {"value_ml": 333, "idempotency_key": str(uuid4())}
            responses = await asyncio.gather(*(client.post(root + "/intake", json=race,
                headers={"Authorization": "Bearer mobile"}) for _ in range(5)))
            assert sorted(r.status_code for r in responses) == [200, 200, 200, 200, 201]
            assert len({r.json()["intake_id"] for r in responses}) == 1
            for moment, amount in [("2026-03-28T23:00:00Z", 125), ("2026-03-29T21:59:59Z", 175), ("2026-03-29T22:00:00Z", 300)]:
                await call("POST", "/intake", expected=201, json={"value_ml": amount, "observed_at": moment})
            assert (await call("GET", "/today", params={"date": "2026-03-29"}))["actual_ml"] == 300
            assert len((await call("GET", "/intakes", params={"date": "2026-03-29"}))["intakes"]) == 2
            # Direct PKB checks prove independent authority, no ownership override and no raw-data escalation.
            async with httpx.AsyncClient(base_url=base, trust_env=False) as pkb:
                headers = {"Authorization": "Bearer fixture-bwts-to-pkb", "X-BioCompass-Service": PRODUCER,
                           "X-BioCompass-User-Token": "summary-only"}
                assert (await pkb.get("/api/v1/internal/items/" + original["intake_id"], headers=headers)).status_code == 403
                assert (await pkb.get("/api/v1/internal/items", headers=headers, params={"user_id": OTHER})).status_code == 422
                assert (await pkb.get("/api/v1/internal/items/" + original["intake_id"])).status_code == 401
            global AU_DOWN
            AU_DOWN = True
            await call("POST", "/intake", expected=503, json={"value_ml": 777})
            AU_DOWN = False
            await call("DELETE", "/intake/" + original["intake_id"], token="telegram", expected=204)
            await call("DELETE", "/intake/" + original["intake_id"], expected=204)
            await call("POST", "/intake", expected=409, json=payload)
            assert (await call("GET", "/today", params={"date": local_date}))["actual_ml"] == 0
    # Verify canonical quantity/provenance/retained-key/outbox boundary in real PostgreSQL.
    import psycopg
    with psycopg.connect(os.environ["PKB_TEST_DATABASE_URL"], user=os.environ["PKB_TEST_DATABASE_USER"],
                         password=os.environ["PKB_TEST_DATABASE_PASSWORD"]) as connection:
        rows = connection.execute("SELECT status, payload, recorded_at, ingested_at FROM pkb_item WHERE pkb_item_id=%s",
                                  (original["intake_id"],)).fetchall()
        assert len(rows) == 1 and rows[0][0] == "deleted" and rows[0][1] == {"amount": 250, "unit": "mL"}
        for table in ["pkb_provenance", "pkb_item_idempotency", "pkb_outbox_event"]:
            assert connection.execute(f"SELECT count(*) FROM {table} WHERE pkb_item_id=%s", (original["intake_id"],)).fetchone()[0] == 1
        assert connection.execute("SELECT count(*) FROM pkb_item WHERE user_id=%s AND payload->>'amount'='777'", (USER,)).fetchone()[0] == 0
    return {"result": "passed", "boundary": "BWTS ASGI -> PKB HTTP -> real PostgreSQL",
            "upstreams": "BAS/BUPS/AU contract doubles; not production authority", "scenarios": 25}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bwts-checkout", type=Path, required=True)
    parser.add_argument("--pkb-jar", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    args = parser.parse_args()
    for key in ["PKB_TEST_DATABASE_URL", "PKB_TEST_DATABASE_USER", "PKB_TEST_DATABASE_PASSWORD"]:
        if not os.environ.get(key):
            parser.error(key + " is required for a disposable database")
    args.evidence_dir.mkdir(parents=True, exist_ok=True)
    fixture = ThreadingHTTPServer(("127.0.0.1", 0), Contracts)
    Thread(target=fixture.serve_forever, daemon=True).start()
    upstream = f"http://127.0.0.1:{fixture.server_port}"
    base = f"http://127.0.0.1:{free_port()}"
    env = {key: value for key, value in os.environ.items() if key in {"PATH", "HOME", "JAVA_HOME", "LANG"}}
    env.update(PKB_DATASOURCE_URL="jdbc:" + os.environ["PKB_TEST_DATABASE_URL"],
        PKB_DATASOURCE_USERNAME=os.environ["PKB_TEST_DATABASE_USER"], PKB_DATASOURCE_PASSWORD=os.environ["PKB_TEST_DATABASE_PASSWORD"],
        PKB_RECORD_BWTS_SERVICE_TOKEN="fixture-bwts-to-pkb", PKB_RECORD_AUTHORITY_URL=upstream + "/verify",
        PKB_RECORD_AUTHORITY_SERVICE_TOKEN="fixture-pkb-to-bas", PKB_AU_DECISION_URL=upstream + "/decision",
        PKB_EVENTING_ENABLED="false", PKB_KAFKA_COMMAND_INGRESS_ENABLED="false")
    with (args.evidence_dir / "pkb-startup.log").open("w") as log:
        process = subprocess.Popen(["java", "-jar", str(args.pkb_jar.resolve()), "--spring.profiles.active=local",
            "--server.address=127.0.0.1", "--server.port=" + base.rsplit(":", 1)[1]], env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            import urllib.request
            for _ in range(300):
                if process.poll() is not None:
                    raise RuntimeError("PKB startup failed; inspect the evidence log")
                try:
                    with urllib.request.urlopen(base + "/actuator/health", timeout=1) as health:
                        if health.status == 200:
                            break
                except (OSError, TimeoutError):
                    pass
                time.sleep(0.2)
            else:
                raise RuntimeError("PKB health timeout")
            result = asyncio.run(verify(args.bwts_checkout.resolve(), base, upstream))
            (args.evidence_dir / "result.json").write_text(json.dumps(result, indent=2) + "\n")
            print(json.dumps(result))
        finally:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            fixture.shutdown()
            fixture.server_close()
    text = (args.evidence_dir / "pkb-startup.log").read_text()
    assert all(token not in text for token in ["fixture-pkb-to-bas", "fixture-bwts-to-pkb", os.environ["PKB_TEST_DATABASE_PASSWORD"]]), "Credential leakage in PKB log"


if __name__ == "__main__":
    main()
