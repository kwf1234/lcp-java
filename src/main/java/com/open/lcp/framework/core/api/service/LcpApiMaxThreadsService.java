package com.open.lcp.framework.core.api.service;

import com.open.lcp.framework.core.api.service.dao.entity.LcpApiMaxThreadsEntity;

public interface LcpApiMaxThreadsService {

	LcpApiMaxThreadsEntity getLcpApiMaxThreads(String api);

}