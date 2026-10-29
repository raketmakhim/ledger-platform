package com.raketmakhim.ledger.accounting.adapter.web;

import java.util.UUID;

/**
 * Who is making the request. Controllers take one of these and pass only its id on, so the services never learn
 * how the caller was identified. Today that is a header (see CallerArgumentResolver); with real login it will
 * be the subject of a verified token.
 */
record Caller(UUID id) {
}
