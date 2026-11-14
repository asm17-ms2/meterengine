package com.meterengine.event.dto;

// 컨슈머가 메시지 하나를 event 한 행으로 넣을 때 쓰는 값. properties는 jsonb로 넣을 JSON 글자다.
public record EventMessageInsertion(EventIngestMessage message, String properties) {}
