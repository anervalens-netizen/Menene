import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import mehene_builder as builder


class PublicationTests(unittest.TestCase):
    def run_case(self, invalid, partial):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, destination = root / 'source', root / 'destination'
            source.mkdir(); destination.mkdir()
            live = destination / 'catalog.json'
            previous = b'{"previous":true}'
            live.write_bytes(previous)
            candidate = {'schemaVersion': 1, 'series': [], 'generatedAtEpochMs': 1}

            def build(_source, output, _language, _profile, **kwargs):
                target = kwargs.get('catalog_output', output / 'catalog.json')
                target.write_text(json.dumps(candidate))
                self.assertEqual(live.read_bytes(), previous, 'candidate became visible before validation')
                return {'errors': [{'error': 'synthetic conversion failure'}]}

            def validate(_candidate, _destination):
                self.assertEqual(live.read_bytes(), previous)
                if invalid:
                    raise ValueError('synthetic invalid catalog')

            with patch.object(builder.legacy, 'build_library', side_effect=build), patch.object(builder, 'validate_catalog', side_effect=validate):
                report = builder.build_library(source, destination, 'ron', publish_partial=partial)
            self.assertEqual(report['catalogPublished'], partial and not invalid)
            if invalid or not partial:
                self.assertEqual(live.read_bytes(), previous)

    def test_invalid_partial_keeps_previous_catalog(self):
        self.run_case(invalid=True, partial=True)

    def test_conversion_failure_keeps_previous_catalog(self):
        self.run_case(invalid=False, partial=False)

    def test_valid_partial_publishes_only_after_validation(self):
        self.run_case(invalid=False, partial=True)


if __name__ == '__main__':
    unittest.main()
