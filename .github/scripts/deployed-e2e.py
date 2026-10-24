#!/usr/bin/env python3
"""Read-only API E2E against testing: real BAS login, PKB and PostgreSQL."""
import json
import os
import time
import unittest
import uuid
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError("Unexpected redirect from an internal test service")


def request(base, path, token=None, data=None, host=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if host:
        headers["Host"] = host
        # Port-forward already carries this request through the authenticated TLS API tunnel.
        # Supply the same scheme metadata BAS normally receives from its trusted ingress.
        headers["X-Forwarded-Proto"] = "https"
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = Request(base + path, headers=headers,
                  data=None if data is None else json.dumps(data).encode())
    try:
        response = build_opener(NoRedirect).open(req, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        body = response.read()
        try:
            result = json.loads(body) if body else None
        except ValueError:
            result = None
        return response.status, result


class DeployedApiE2e(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if os.environ.get("PKB_E2E_NAMESPACE") != "testing":
            raise RuntimeError("Deployed E2E must target namespace testing")
        cls.pkb = os.environ["PKB_E2E_BASE_URL"].rstrip("/")
        cls.bas = os.environ["PKB_E2E_AUTH_URL"].rstrip("/")
        for url in (cls.pkb, cls.bas):
            parsed = urlsplit(url)
            if parsed.scheme != "http" or parsed.hostname != "127.0.0.1":
                raise RuntimeError("E2E must use the testing namespace's loopback port forwards")
        username = os.environ["PKB_E2E_USERNAME"]
        password = os.environ["PKB_E2E_PASSWORD"]
        if not username or not password:
            raise RuntimeError("Testing E2E account secrets are required")
        deadline = time.monotonic() + 60
        while True:
            try:
                status, body = request(cls.pkb, "/actuator/health/readiness")
                if status == 200 and body and body.get("status") == "UP":
                    break
            except (URLError, TimeoutError):
                pass
            if time.monotonic() >= deadline:
                raise RuntimeError("Testing service did not become ready")
            time.sleep(1)
        # BAS remains the real identity authority; no introspection stub or DB seeding.
        status, login = request(cls.bas, "/api/v1/auth/token/",
                                data={"email": username, "password": password}, host="bio-compass-auth")
        if status != 200 or not isinstance(login, dict) or not login.get("access"):
            raise RuntimeError(f"BAS test-account login failed (HTTP {status})")
        cls.token = login["access"]
        status, user = request(cls.bas, "/api/v1/me/", token=cls.token, host="bio-compass-auth")
        if status != 200 or not isinstance(user, dict) or not user.get("is_email_verified"):
            raise RuntimeError("BAS did not return a verified test user")
        cls.user_id = str(uuid.UUID(user["id"]))
        cls.items = "/api/pkb/items?userId=" + cls.user_id

    def test_health_and_openapi(self):
        for path in ("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"):
            status, body = request(self.pkb, path)
            self.assertEqual(status, 200, path)
            self.assertEqual(body["status"], "UP", path)
        status, document = request(self.pkb, "/v3/api-docs")
        self.assertEqual(status, 200)
        self.assertIn("/api/pkb/items", document["paths"])

    def test_real_bearer_authentication_and_user_scoped_search(self):
        status, items = request(self.pkb, self.items, token=self.token)
        self.assertEqual(status, 200)
        self.assertIsInstance(items, list)
        self.assertTrue(all(item["userId"] == self.user_id for item in items))

    def test_unauthenticated_and_inactive_tokens_are_denied(self):
        for token in (None, "invalid-e2e-token"):
            status, _ = request(self.pkb, self.items, token=token)
            self.assertEqual(status, 401)

    def test_cross_user_access_is_denied(self):
        status, _ = request(self.pkb, "/api/pkb/items?userId=" + str(uuid.uuid4()), token=self.token)
        self.assertEqual(status, 403)

    def test_validation_and_missing_item(self):
        for path in ("/api/pkb/items", "/api/pkb/items?userId=invalid", self.items + "&offset=-1"):
            status, _ = request(self.pkb, path, token=self.token)
            self.assertEqual(status, 400, path)
        status, body = request(self.pkb, f"/api/pkb/items/{uuid.uuid4()}?userId={self.user_id}", token=self.token)
        self.assertEqual(status, 404)
        self.assertEqual(body["code"], "pkb_item_not_found")


if __name__ == "__main__":
    unittest.main(verbosity=2)
