package com.biocompass.pkb.records.persistence;

import java.time.Instant;

public record PkbQuantityWindow(Instant from, Instant before) {
}
