package com.open.lcp.biz.passport.api.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.open.lcp.biz.passport.api.AccountInfoApi;
import com.open.lcp.biz.passport.dto.PassportOAuthAccountDTO;
import com.open.lcp.biz.passport.service.AccountInfoService;
import com.open.lcp.biz.passport.service.AccountTicketService;
import com.open.lcp.core.api.info.BasicUserAccountInfo;
import com.open.lcp.core.api.info.BasicUserAccountTicketInfo;

public class AccountInfoApiThriftImpl implements AccountInfoApi {

	@Autowired
	private AccountTicketService accountTicketService;

	@Autowired
	protected AccountInfoService accountInfoService;

	@Override
	public BasicUserAccountTicketInfo validateTicket(String t) {
		return accountTicketService.validateTicket(t);
	}

	@Override
	public BasicUserAccountInfo getUserInfo(String ticket) {
		BasicUserAccountTicketInfo info = accountTicketService.validateTicket(ticket);
		Long userId = info.getUserId();
		return accountInfoService.getUserAccountInfo(userId);
	}

	@Override
	public BasicUserAccountInfo getUserInfo(Long userId) {
		return accountInfoService.getUserAccountInfo(userId);
	}

	@Override
	public List<PassportOAuthAccountDTO> getOAuthAccountList(Long userId) {
		return accountInfoService.getOAuthAccountList(userId);
	}
}
