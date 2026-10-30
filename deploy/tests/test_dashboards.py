import json
from pathlib import Path
import unittest


STORAGE_DASHBOARD = Path(__file__).resolve().parents[1] / "grafana/dashboards/meterengine-storage.json"
INGESTION_URIS = 'uri=~"/v1/events|/v1/event-batches"'


class StorageDashboardTest(unittest.TestCase):
    def test_ingestion_panels_cover_single_and_batch_ingestion_per_uri(self):
        panels = json.loads(STORAGE_DASHBOARD.read_text())["panels"]
        ingestion = [panel for panel in panels if panel.get("title", "").startswith("수집 API")]
        self.assertGreaterEqual(len(ingestion), 4)
        for panel in ingestion:
            for target in panel["targets"]:
                self.assertIn(INGESTION_URIS, target["expr"], panel["title"])
                self.assertNotIn('uri="/v1/events"', target["expr"], panel["title"])
                self.assertIn("{{uri}}", target.get("legendFormat", ""), panel["title"])


if __name__ == "__main__":
    unittest.main()
