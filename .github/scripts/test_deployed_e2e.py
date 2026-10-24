import importlib.util
import os
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('deployed_e2e', Path(__file__).with_name('deployed-e2e.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class GateSafetyTest(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {
            'PKB_E2E_NAMESPACE': 'testing',
            'PKB_E2E_BASE_URL': 'http://127.0.0.1:18080',
            'PKB_E2E_AUTH_URL': 'http://127.0.0.1:18081',
            'PKB_E2E_USERNAME': 'synthetic@example.test',
            'PKB_E2E_PASSWORD': 'never-print-this',
        }, clear=True)
        self.env.start()
        self.addCleanup(self.env.stop)

    def test_production_target_is_rejected_before_any_request(self):
        os.environ['PKB_E2E_NAMESPACE'] = 'prod'
        with patch.object(gate, 'request') as request:
            with self.assertRaisesRegex(RuntimeError, 'namespace testing'):
                gate.DeployedApiE2e.setUpClass()
            request.assert_not_called()

    def test_non_local_auth_destination_is_rejected_before_sending_credentials(self):
        os.environ['PKB_E2E_AUTH_URL'] = 'https://example.test'
        with patch.object(gate, 'request') as request:
            with self.assertRaisesRegex(RuntimeError, 'loopback'):
                gate.DeployedApiE2e.setUpClass()
            request.assert_not_called()

    def test_missing_account_cannot_silently_skip_e2e(self):
        os.environ['PKB_E2E_PASSWORD'] = ''
        with self.assertRaisesRegex(RuntimeError, 'secrets are required'):
            gate.DeployedApiE2e.setUpClass()

    def test_failed_authentication_is_fatal_and_response_body_stays_private(self):
        with patch.object(gate, 'request', side_effect=[(200, {'status': 'UP'}), (401, {'password': 'never-print-this'})]):
            with self.assertRaises(RuntimeError) as error:
                gate.DeployedApiE2e.setUpClass()
            self.assertIn('HTTP 401', str(error.exception))
            self.assertNotIn('never-print-this', str(error.exception))

    def test_unverified_account_is_fatal(self):
        with patch.object(gate, 'request', side_effect=[
            (200, {'status': 'UP'}), (200, {'access': 'private-token'}),
            (200, {'id': 'bad-id', 'is_email_verified': False}),
        ]):
            with self.assertRaisesRegex(RuntimeError, 'verified test user'):
                gate.DeployedApiE2e.setUpClass()

    def test_readiness_timeout_blocks_authentication(self):
        with patch.object(gate, 'request', return_value=(503, {'status': 'DOWN'})) as request, \
             patch.object(gate.time, 'monotonic', side_effect=[0, 61]):
            with self.assertRaisesRegex(RuntimeError, 'did not become ready'):
                gate.DeployedApiE2e.setUpClass()
            self.assertEqual(request.call_count, 1)


if __name__ == '__main__':
    unittest.main()
