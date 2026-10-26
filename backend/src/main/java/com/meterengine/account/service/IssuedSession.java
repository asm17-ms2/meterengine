package com.meterengine.account.service;

public record IssuedSession(String token, CurrentSession session) {

  @Override
  public String toString() {
    return "IssuedSession[token=(redacted), session=" + session.session().getId() + "]";
  }
}
