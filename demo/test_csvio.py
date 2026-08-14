"""CSV 소스 읽기 검증. 스키마는 MS2-142 확정 전의 잠정 인터페이스다."""

import os
import tempfile
import unittest

from csvio import read_csv_events


class ReadCsvEventsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = os.path.join(self.tmp.name, "events.csv")

    def _write(self, text):
        with open(self.path, "w", encoding="utf-8") as f:
            f.write(text)

    def test_행을_이벤트로_읽고_properties_원문을_보존한다(self):
        self._write(
            "transaction_id,customer_id,event_type,timestamp,properties\n"
            'evt-1,a728e7b6-d82b-4f3c-a960-a66a02794c1d,chat_completion,2026-08-05T10:00:00+09:00,'
            '"{""token"": 501, ""model"": ""claude""}"\n'
        )
        events = read_csv_events(self.path)
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].transaction_id, "evt-1")
        self.assertEqual(events[0].properties_text, '{"token": 501, "model": "claude"}')

    def test_컬럼_순서가_달라도_읽는다(self):
        self._write(
            "properties,timestamp,event_type,customer_id,transaction_id\n"
            '"{""token"": 1}",2026-08-05T10:00:00+09:00,chat_completion,c-1,evt-1\n'
        )
        events = read_csv_events(self.path)
        self.assertEqual(events[0].transaction_id, "evt-1")
        self.assertEqual(events[0].properties_text, '{"token": 1}')

    def test_잘못된_값도_거르지_않고_그대로_담는다(self):
        # 400 거절 시연이 목적이므로 내용 검증은 서버 경계의 몫이다.
        self._write(
            "transaction_id,customer_id,event_type,timestamp,properties\n"
            'evt-1,not-a-uuid,,bad-timestamp,"{""token"": 1}"\n'
        )
        events = read_csv_events(self.path)
        self.assertEqual(events[0].customer_id, "not-a-uuid")
        self.assertEqual(events[0].event_type, "")

    def test_note_컬럼이_있으면_읽는다(self):
        self._write(
            "transaction_id,customer_id,event_type,timestamp,properties,note\n"
            'evt-1,c-1,chat_completion,2026-08-05T10:00:00+09:00,"{""token"": 1}",미등록 고객\n'
            'evt-2,c-1,chat_completion,2026-08-05T10:00:00+09:00,"{""token"": 1}",\n'
        )
        events = read_csv_events(self.path)
        self.assertEqual(events[0].note, "미등록 고객")
        self.assertEqual(events[1].note, "")

    def test_note_컬럼이_없으면_빈_설명이다(self):
        self._write(
            "transaction_id,customer_id,event_type,timestamp,properties\n"
            'evt-1,c-1,chat_completion,2026-08-05T10:00:00+09:00,"{""token"": 1}"\n'
        )
        self.assertEqual(read_csv_events(self.path)[0].note, "")

    def test_필수_컬럼이_없으면_거부한다(self):
        self._write("transaction_id,customer_id\nevt-1,c-1\n")
        with self.assertRaises(ValueError):
            read_csv_events(self.path)

    def test_헤더만_있고_행이_없으면_빈_목록이다(self):
        self._write("transaction_id,customer_id,event_type,timestamp,properties\n")
        self.assertEqual(read_csv_events(self.path), [])

    def test_bom이_있어도_읽는다(self):
        self._write(
            "﻿transaction_id,customer_id,event_type,timestamp,properties\n"
            'evt-1,c-1,chat_completion,2026-08-05T10:00:00+09:00,"{""token"": 1}"\n'
        )
        self.assertEqual(read_csv_events(self.path)[0].transaction_id, "evt-1")


if __name__ == "__main__":
    unittest.main()
