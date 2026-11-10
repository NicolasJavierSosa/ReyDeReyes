package com.example.demo.sync;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sincroniza cada 5 minutos (configurable) y al iniciar el sistema. */
@Component
public class SyncScheduler {

	private final SyncService syncService;

	public SyncScheduler(SyncService syncService) {
		this.syncService = syncService;
	}

	@Scheduled(initialDelayString = "${app.sync.demora-inicial:PT20S}", fixedDelayString = "${app.sync.intervalo:PT5M}")
	public void sincronizarPeriodicamente() {
		syncService.sincronizar();
	}
}
