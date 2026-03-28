package com.example.eventlog.service;

import com.example.eventlog.repository.EventRecordRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Transactional
public abstract class PersistenceService {

    protected final EventRecordRepository repository;
    protected final Clock clock;

    protected PersistenceService(EventRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }
}
